/*******************************************************************************
* Copyright (c) 2023, 2024 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial implementation
*******************************************************************************/
package io.openliberty.tools.eclipse.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.sourcelookup.ISourceContainer;
import org.eclipse.debug.core.sourcelookup.ISourcePathComputerDelegate;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.gradle.tooling.BuildException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.ExternalDependency;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.eclipse.EclipseProject;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.ui.launch.StartTab;
import io.openliberty.tools.eclipse.utils.Utils;

public class LibertySourcePathComputer implements ISourcePathComputerDelegate {

    /**
     * Gradle distribution that supports Java 21.
     * Gradle version 8.4+ supports Java 21.
     */
    private static String GRADLE_DISTRIBUTION_VERISION = "8.8";

    ArrayList<IRuntimeClasspathEntry> unresolvedClasspathEntries;

    @Override
    public ISourceContainer[] computeSourceContainers(ILaunchConfiguration configuration, IProgressMonitor monitor) throws CoreException {

        unresolvedClasspathEntries = new ArrayList<IRuntimeClasspathEntry>();

        /*
         * This method computes the default source lookup paths for a particular launch configuration. We are doing this in two ways:
         * .
         * 1. We are first computing the runtime classpath entries. This is in-line with what Remote Java Application does.
         * .
         * 2. We are finding any project dependencies that are also present in the current Eclipse workspace and adding those projects.
         * . For this step we are using m2e and gradle/buildship APIs to get lists of the dependency artifacts and then checking if those
         * . artifact coordinates map to any existing projects in the workspace. At the moment, the dependency projects must be Maven
         * . projects. M2e offers APIs to lookup Maven projects in the workspace based on artifact coordinates,
         * . but Gradle/Buildship does not offer similar capabilities for Gradle projects.
         */

        // Get current project
        String projectName = configuration.getAttribute(StartTab.PROJECT_NAME, (String) null);

        Project project = DevModeOperations.getInstance().getProjectModel().getProject(projectName);

        // Get full list of projects (multi-mod, children, siblings, etc)
        List<Project> baseProjects = getBaseProjects(project);

        // Loop through each
        for (Project baseProject : baseProjects) {

            addRuntimeDependencies(baseProject.getIProject());

            // Get project dependencies that are open in the same workspace
            List<IProject> projectDependencies = getProjectDependencies(baseProject);

            // Create the classpath entry for the project dependencies found
            for (IProject dependencyProject : projectDependencies) {

                if (dependencyProject.isNatureEnabled(JavaCore.NATURE_ID)) {
                    IJavaProject dependencyJavaProject = JavaCore.create(dependencyProject);
                    unresolvedClasspathEntries.add(JavaRuntime.newDefaultProjectClasspathEntry(dependencyJavaProject));
                }
            }
        }

        // Resolve and get final list of source containers
        IRuntimeClasspathEntry[] resolvedClasspathDependencies = JavaRuntime.resolveSourceLookupPath(
                                                                                                     unresolvedClasspathEntries.toArray(new IRuntimeClasspathEntry[unresolvedClasspathEntries.size()]),
                                                                                                     configuration);

        ArrayList<ISourceContainer> containersList = new ArrayList<ISourceContainer>();

        containersList.addAll(Arrays.asList(JavaRuntime.getSourceContainers(resolvedClasspathDependencies)));

        ISourceContainer[] containers = new ISourceContainer[containersList.size()];
        containersList.toArray(containers);

        return containers;
    }

    private List<Project> getBaseProjects(Project project) {
        List<Project> baseProjects = new ArrayList<Project>();

        baseProjects.add(project);

        baseProjects.addAll(project.getChildJavaProjects());
        baseProjects.addAll(project.getPeerJavaProjects());

        return baseProjects;
    }

    private List<IProject> getProjectDependencies(Project project) throws CoreException {
        List<IProject> projectDependencies = new ArrayList<IProject>();

        if (project.getBuildType() == Project.BuildType.MAVEN) {

            MavenProject mavenModuleProject = MavenPlugin.getMavenModelManager().readMavenProject(project.getIProject().getFile("pom.xml"),
                                                                                                  new NullProgressMonitor());
            Set<Artifact> artifacts = mavenModuleProject.getArtifacts();

            for (Artifact artifact : artifacts) {

                IProject localProject = getLocalProject(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
                if (localProject != null) {
                    projectDependencies.add(localProject);
                }
            }
        } else {
            ProjectConnection connection = getProjectGradleConnection(project.getIProject(), null);

            try {
                EclipseProject eclipseProject = null;

                try {
                    eclipseProject = connection.getModel(EclipseProject.class);
                } catch (BuildException e) {
                    // When using Eclipse IDE 2024-06, this exception could have been caused by the 
                    // Gradle tooling API using a Gradle distribution that does not support Java 21.
                    //
                    // Per the GradleConnector documentation, if no Gradle version is defined for the 
                    // build (Gradle wrapper properties file), the connection will use the tooling API's 
                    // version as the Gradle version to run the build.
                    // Therefore, if a Gradle version is not defined for the build and given that the 
                    // tooling version currently being used is 8.1.1, Gradle 8.1.1 
                    // is downloaded and used by the connector. Gradle 8.1.1 does not support Java 21, 
                    // which causes runtime issues (Unsupported class file major version 65).
                    // As a workaround, specify a Java 21 compatible Gradle version that the tooling
                    // can use (i.e. 8.4+). Note that since it is preferable to use the default version 
                    // provided by the tooling API, setting the version can be revised at a later time.
                    Throwable rootCause = Utils.findRootCause(e);
                    if (rootCause != null && rootCause instanceof IllegalArgumentException) {
                        String message = rootCause.getMessage();

                        if (message != null && message.contains("Unsupported class file major version 65")) {
                            connection = getProjectGradleConnection(project.getIProject(), GRADLE_DISTRIBUTION_VERISION);
                            eclipseProject = connection.getModel(EclipseProject.class);
                        }
                    } else {
                        throw e;
                    }
                }

                for (ExternalDependency externalDependency : eclipseProject.getClasspath()) {

                    GradleModuleVersion gradleModuleVersion = externalDependency.getGradleModuleVersion();

                    IProject localProject = getLocalProject(gradleModuleVersion.getGroup(), gradleModuleVersion.getName(),
                                                            gradleModuleVersion.getVersion());
                    if (localProject != null) {
                        projectDependencies.add(localProject);
                    }
                }
            } finally {
                connection.close();
            }
        }

        return projectDependencies;
    }

    /**
     * Returns a connection to the input gradle project.
     * 
     * @param project           The Gradle project.
     * @param gradleDistVersion The gradle distribution version to be used by the
     *                              Gradle API tooling.
     * 
     * @return A connection to the input gradle project.
     */
    private ProjectConnection getProjectGradleConnection(IProject project, String gradleDistVersion) {
        GradleConnector connector = GradleConnector.newConnector();

        if (gradleDistVersion != null) {
            connector.useGradleVersion(gradleDistVersion);
        }

        connector.forProjectDirectory(project.getLocation().toFile());
        return connector.connect();
    }

    /**
     * Get project if found in local workspace based on artifact coordinates
     * 
     * @param groupId
     * @param artifactId
     * @param version
     * 
     * @return
     */
    private IProject getLocalProject(String groupId, String artifactId, String version) {

        // Check Maven projects
        IMavenProjectRegistry mavenProjectRegistry = MavenPlugin.getMavenProjectRegistry();

        IMavenProjectFacade mavenProjectFacade = mavenProjectRegistry.getMavenProject(groupId, artifactId, version);

        if (mavenProjectFacade != null) {
            return mavenProjectFacade.getProject();
        }

        // TODO: Check Gradle projects

        return null;
    }

    /**
     * Adds classpath entries for runtime dependencies to the unresolved classpath entries list
     * 
     * @param project
     * 
     * @throws CoreException
     */
    private void addRuntimeDependencies(IProject project) throws CoreException {

        // If the project is a java project, get classpath entries for runtime dependencies
        if (project.isNatureEnabled(JavaCore.NATURE_ID)) {
            List<IRuntimeClasspathEntry> runtimeDependencies = Arrays.asList(JavaRuntime.computeUnresolvedRuntimeClasspath(JavaCore.create(project)));
            for (IRuntimeClasspathEntry runtimeDependency : runtimeDependencies) {
                if (!unresolvedClasspathEntries.contains(runtimeDependency)) {
                    unresolvedClasspathEntries.add(runtimeDependency);
                }
            }
        }
    }
}