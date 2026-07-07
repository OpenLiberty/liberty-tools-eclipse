/*******************************************************************************
* Copyright (c) 2022, 2026 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.model;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import io.openliberty.tools.eclipse.LibertyNature;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

/**
 * This class models Eclipse workspace projects and their relationships in the context
 * of Liberty development. It tracks build types (Maven/Gradle), multi-module project
 * hierarchies, and Liberty server configuration presence.
 *
 * @see WorkspaceProjectsModel
 */
public class ProjectModel {

    /** Maven project nature identifier used by Eclipse M2E plugin. */
    public static final String MAVEN_NATURE = "org.eclipse.m2e.core.maven2Nature";

    /** Gradle project nature identifier used by Eclipse Buildship plugin. */
    public static final String GRADLE_NATURE = "org.eclipse.buildship.core.gradleprojectnature";

    /** Java project nature identifier used by Eclipse JDT. */
    public static final String JAVA_NATURE_ID = "org.eclipse.jdt.core.javanature";

    /** Enumeration of supported build types. */
    public static enum BuildType {
        Unknown,
        Gradle,
        Maven
    };

    /**
     * The child projects associated with this project in a multi-module structure.
     * Thread-safe set to support concurrent access during workspace model building.
     */
    private Set<ProjectModel> childDirProjects = ConcurrentHashMap.newKeySet();

    /**
     * The set of peer projects (siblings in the same parent directory).
     * Thread-safe set to support concurrent access during workspace model building.
     */
    private Set<ProjectModel> peerDirProjects = ConcurrentHashMap.newKeySet();

    /**
     * The set of dependent projects explicitly declared in the build configuration
     * (Maven modules or Gradle subprojects). This is a subset of childDirProjects
     * that represents actual build dependencies.
     * Thread-safe set to support concurrent access during workspace model building.
     */
    private Set<ProjectModel> dependentProjects = ConcurrentHashMap.newKeySet();

    /** The Eclipse project reference. */
    private IProject iProject;

    /** Build type associated with this project (Maven, Gradle, or Unknown). */
    private BuildType type;

    /** The parent of this project in a multi-module structure, or {@code null} if standalone. */
    private ProjectModel parentProjectModel;

    /**
     * Indicates whether this module has Liberty server configuration files.
     * Set to {@code true} if server.xml, bootstrap.properties, server.env, or
     * the Liberty plugin configuration exists.
     */
    private boolean libertyServerModule;

    /**
     * Indicates whether this project is a parent/aggregator of modules with Liberty configuration.
     */
    private boolean isParentOfServerModule;

    /** Indicates whether this module has test source files. */
    private boolean hasTests;

    /** The metadata associated with the project's build configuration. */
    private Metadata buildConfigMetadata;

    /**
     * Constructor.
     * 
     * @param project The Eclipse project reference.
     */
    public ProjectModel(IProject project) {
        this.iProject = project;
        this.type = findBuildType();
    }

    /**
     * Checks whether this project has the Liberty nature.
     *
     * <p>The Liberty nature is added to projects that have Liberty server configuration
     * or are parents of modules with Liberty configuration. This nature enables
     * Liberty-specific UI elements and commands in Eclipse.</p>
     *
     * @return {@code true} if the project has the Liberty nature, {@code false} otherwise
     */
    public boolean hasLibertyNature() {
        try {
            if (iProject.getDescription().hasNature(LibertyNature.NATURE_ID)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                        "An error occurred while attempting to find the nature of project " + iProject.getName(), e);
            }
            return false;
        }
    }

    /**
     * Returns the build type associated with this project.
     * 
     * @return The build type associated with this project.
     */
    public BuildType getBuildType() {
        return type;
    }

    /**
     * Returns the build configuration metadata for this project.
     *
     * @return The build configuration metadata.
     */
    public Metadata getBuildConfigMetadata() {
        return buildConfigMetadata;
    }

    /**
     * Finds the build type to be associated with this project.
     *
     * <p>The detection strategy is:</p>
     * <ol>
     * <li>Check for Maven or Gradle nature in the project description</li>
     * <li>If no nature found, check for build files (pom.xml or build.gradle)</li>
     * <li>If both Maven and Gradle are present, Maven takes precedence</li>
     * </ol>
     *
     * @return the detected build type (MAVEN, GRADLE, or UNKNOWN)
     */
    private BuildType findBuildType() {

        // Check the installed project's nature.
        try {
            if (iProject.getDescription().hasNature(MAVEN_NATURE)) {
                return BuildType.Maven;
            } else if (iProject.getDescription().hasNature(GRADLE_NATURE)) {
                return BuildType.Gradle;
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                        "An error occurred while attempting to find the nature of project " + iProject.getName(), e);
            }
        }

        // Check the build configuration file.
        if (iProject.getFile("pom.xml").exists()) {
            return BuildType.Maven;
        } else if ((iProject.getFile("build.gradle").exists())) {
            return BuildType.Gradle;
        }

        return BuildType.Unknown;

    }

    /**
     * Gets the associated Eclipse project reference.
     * 
     * @return The associated Eclipse project reference.
     */
    public IProject getIProject() {
        return iProject;
    }

    /**
     * Retrieves the absolute path of this project.
     *
     * @param selectedProject The project object
     *
     * @return The absolute path of this project or null if the path could not be obtained.
     */
    public String getPath() {
        if (iProject != null) {
            IPath path = iProject.getLocation();
            if (path != null) {
                return path.toOSString();
            }
        }

        return null;
    }

    /**
     * Returns the list child projects that contain Liberty server configuration.
     * 
     * @return The list child projects that contain Liberty server configuration.
     */
    public List<ProjectModel> getChildLibertyServerProjects() {
        ArrayList<ProjectModel> clsps = new ArrayList<ProjectModel>();

        for (ProjectModel child : childDirProjects) {
            if (child.isLibertyServerModule()) {
                clsps.add(child);
            }
        }

        return clsps;
    }

    /**
     * Returns the list child projects.
     * 
     * @return The list child projects..
     */
    public List<ProjectModel> getChildProjects() {
        return new ArrayList<>(childDirProjects);
    }

    /**
     * Returns the list child projects that contain the java nature.
     * 
     * @return The list child projects that contain the java nature.
     */
    public List<ProjectModel> getChildJavaProjects() {
        return filterJavaProjects(childDirProjects);
    }

    /**
     * Returns the list of peer projects that contain the java nature.
     * 
     * @return The list of peer projects that contain the java nature.
     */
    public List<ProjectModel> getPeerJavaProjects() {
        return filterJavaProjects(peerDirProjects);
    }

    /**
     * Returns the list of projects that contain the Java nature from the input set.
     * 
     * @param projects The set of projects to filter.
     * 
     * @return The list of projects that contain the Java nature from the input set.
     */
    public List<ProjectModel> filterJavaProjects(Set<ProjectModel> projects) {
        ArrayList<ProjectModel> javaProjecs = new ArrayList<ProjectModel>();
        for (ProjectModel child : projects) {
            try {
                if (child.getIProject().hasNature(JAVA_NATURE_ID)) {
                    javaProjecs.add(child);
                }
            } catch (CoreException e) {
                ErrorHandler.processWarningMessage(Messages.getMessage("determine_java_project_error", child.getName()), e, false);
            }
        }
        return javaProjecs;
    }

    /**
     * Returns a Java project that is a peer or child of the input project.
     * 
     * @param project The project to search for.
     * 
     * @return A Java project that is a peer or child of the input project.
     * 
     * @throws Exception If none of the associated projecs
     */
    public ProjectModel getAssociatedJavaProject(ProjectModel project) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, project);
        }

        ProjectModel aJProject = null;

        // Find a child java project.
        List<ProjectModel> jProjects = project.getChildJavaProjects();
        if (!jProjects.isEmpty()) {
            aJProject = jProjects.get(0);
        }

        // find a peer Java project.
        if (aJProject == null) {
            jProjects = project.getPeerJavaProjects();
            if (!jProjects.isEmpty()) {
                aJProject = jProjects.get(0);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, aJProject);
        }

        return aJProject;
    }

    /**
     * Classifies this project as a project able to run on a Liberty server.
     */
    public void classifyAsLibertyServerModule() {
        try {
            IFile serverxml = iProject.getFile(new Path("src/main/liberty/config/server.xml"));
            IFile bootstrapProps = iProject.getFile(new Path("src/main/liberty/config/bootstrap.properties"));
            IFile serverenv = iProject.getFile(new Path("src/main/liberty/config/server.env"));
            boolean isLibertyPluginConfigured = (buildConfigMetadata != null) ? buildConfigMetadata.isLibertyPluginConfigured() : false;
            boolean isModuleDisabled = (buildConfigMetadata != null) ? buildConfigMetadata.isModuleDisabled() : false;

            if (!isModuleDisabled && (serverxml.exists() || bootstrapProps.exists() || serverenv.exists() || isLibertyPluginConfigured)) {
                libertyServerModule = true;
            } else {
                libertyServerModule = false;
            }
        } catch (Exception e) {
            ErrorHandler.processWarningMessage(Messages.getMessage("liberty_nature_add_error"), e, false);
        }
    }

    /**
     * Adds the Liberty nature to the project if it is not already present.
     */
    public void classifyAsLibertyNature() {
        try {
            if (libertyServerModule) {
                ProjectModel.addNature(iProject, LibertyNature.NATURE_ID);
            }

            if (type.equals(BuildType.Maven)) {
                for (ProjectModel child : childDirProjects) {
                    if (child.isLibertyServerModule()) {
                        ProjectModel.addNature(iProject, LibertyNature.NATURE_ID);
                        isParentOfServerModule = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            ErrorHandler.processWarningMessage(Messages.getMessage("liberty_nature_add_error"), e, false);
        }
    }

    /**
     * Adds the specified nature ID to the project's description/metadata (.project).
     * 
     * @param project  The project to process.
     * @param natureId The nature ID to add.
     * 
     * @throws Exception
     */
    public static void addNature(IProject project, String natureId) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { project, natureId });
        }

        if (project.getDescription().hasNature(natureId)) {
            return;
        }

        IPath projectPath = project.getLocation().addTrailingSeparator().append(".project");

        IProjectDescription projectDesc = ResourcesPlugin.getWorkspace().loadProjectDescription(projectPath);
        String[] currentNatures = projectDesc.getNatureIds();
        String[] newNatures = new String[currentNatures.length + 1];
        System.arraycopy(currentNatures, 0, newNatures, 0, currentNatures.length);
        newNatures[currentNatures.length] = natureId;
        projectDesc.setNatureIds(newNatures);
        project.setDescription(projectDesc, new NullProgressMonitor());

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, new Object[] { project, newNatures });
        }
    }

    /**
     * Removes the specified nature ID from the project's description/metadata (.project).
     *
     * @param project  The project to process.
     * @param natureId The nature ID to remove.
     *
     * @throws Exception
     */
    public static void removeNature(IProject project, String natureId) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, project);
        }

        IProjectDescription projectDesc = project.getDescription();
        String[] currentNatures = projectDesc.getNatureIds();
        ArrayList<String> newNatures = new ArrayList<String>(currentNatures.length - 1);

        for (int i = 0; i < currentNatures.length; i++) {
            if (currentNatures[i].equals(natureId)) {
                continue;
            }
            newNatures.add(currentNatures[i]);
        }

        projectDesc.setNatureIds(newNatures.toArray(new String[newNatures.size()]));
        project.setDescription(projectDesc, new NullProgressMonitor());

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, new Object[] { project, newNatures });
        }
    }

    /**
     * Returns true if the project has the specified nature. False; otherwise.
     * 
     * @param nature The nature to check for.
     * 
     * @return True if the project has the specified nature. False; otherwise.
     * 
     * @throws CoreException
     */
    public boolean hasNature(String nature) throws CoreException {
        return iProject.hasNature(nature);
    }

    /**
     * Filters and saves the list of peer projects associated with this project.
     * 
     * @param peerProjects The raw list of peer projects.
     */
    public void setPeerDirProjects(List<ProjectModel> peerProjects) {
        this.peerDirProjects.addAll(peerProjects);
    }

    /**
     * Sets the build configuration metadata for this project.
     *
     * @param buildConfigMetadata The build configuration metadata to set.
     */
    public void setBuildConfigMetadata(Metadata buildConfigMetadata) {
        this.buildConfigMetadata = buildConfigMetadata;
    }

    /**
     * Formats the child projects as a string for debugging purposes.
     *
     * @return a string representation of child projects, or "<empty>" if no children
     */
    private String formatChildProjectToString() {
        if (childDirProjects.isEmpty()) {
            return "<empty>";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (ProjectModel p : childDirProjects) {
                sb.append(p.getName()).append(",");
            }
            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * Returns a string representation of this project model for debugging.
     *
     * @return a detailed string containing project information including build type,
     *         Liberty configuration status, and parent/child relationships
     */
    @Override
    public String toString() {
        return "IProject: " + iProject.toString() + ". BuildType: " + type + ". Liberty Server Module: " + libertyServerModule
               + ". IsParentOfServerModule:" + isParentOfServerModule + ". HasTests: " + hasTests + ". ParentProject: "
               + (parentProjectModel != null ? parentProjectModel.getName() : "<null> ") + ". childDirProjects: "
               + formatChildProjectToString() + ". DependentProjects: " + dependentProjects;
    }

    /**
     * Checks whether this project is classified as a Liberty server module.
     *
     * <p>A project is a Liberty server module if it contains Liberty configuration files
     * such as server.xml, bootstrap.properties, or server.env in the standard location.</p>
     *
     * @return {@code true} if this is a Liberty server module, {@code false} otherwise
     */
    public boolean isLibertyServerModule() {
        return libertyServerModule;
    }

    /**
     * Sets the parent project for this project in a multi-module structure.
     *
     * @param parent the parent project model
     */
    public void setParentProjectModel(ProjectModel parent) {
        this.parentProjectModel = parent;
    }

    /**
     * Get the parent project model.
     *
     * @return The parent project
     */
    public ProjectModel getParentProjectModel() {
        return parentProjectModel;
    }

    /**
     * Adds a child project to this project's set of child projects.
     *
     * @param child the child project model to add
     */
    public void addChildDirProject(ProjectModel child) {
        this.childDirProjects.add(child);
    }

    /**
     * Returns the name of the Eclipse project.
     *
     * @return the project name
     */
    public String getName() {
        return buildConfigMetadata.getProjectName();
    }

    /**
     * Checks whether this project is part of a multi-module structure (has a parent).
     *
     * @return {@code true} if this project has a parent project, {@code false} otherwise
     */
    public boolean isAggregated() {
        return parentProjectModel != null;
    }

    /**
     * Checks whether this project is a parent of a module with Liberty server configuration.
     *
     * <p>This is used to expose parent/aggregator projects in the Liberty Dashboard
     * even when they don't have Liberty configuration themselves.</p>
     *
     * @return {@code true} if this is a parent of a Liberty server module, {@code false} otherwise
     */
    public boolean isParentOfServerModule() {
        return isParentOfServerModule;
    }

    /**
     * Returns whether this project has test source files.
     *
     * @return true if test source directories exist, false otherwise.
     */
    public boolean hasTests() {
        return hasTests;
    }

    /**
     * Classifies this project as having tests by checking for the src/test directory with content.
     * Should be called during workspace model building.
     *
     * Checks for the standard Maven/Gradle test directory (src/test) and verifies it contains files.
     * This approach supports any language (Java, Kotlin, Groovy, Scala, etc.) and any custom
     * directory structure under src/test.
     */
    public void classifyAsHavingTests() {
        String projectPath = getPath();
        if (projectPath == null) {
            this.hasTests = false;
            return;
        }

        // Check for standard test directory (Maven and Gradle convention)
        java.nio.file.Path testDir = Paths.get(projectPath, "src", "test");
        this.hasTests = hasTestFiles(testDir);
    }

    /**
     * Recursively checks if a directory contains any files.
     *
     * @param dir The directory to check
     * @return true if the directory contains files (indicating tests exist), false otherwise
     */
    private boolean hasTestFiles(java.nio.file.Path dir) {
        if (!java.nio.file.Files.exists(dir) || !java.nio.file.Files.isDirectory(dir)) {
            return false;
        }

        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(dir, 20)) {
            // Check if there are any regular files in the directory tree
            // This indicates the directory is not empty and likely contains test source files
            return paths.anyMatch(path -> java.nio.file.Files.isRegularFile(path));
        } catch (Exception e) {
            // If we can't read the directory, assume no tests
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                        "Error checking for test files in directory: " + dir, e);
            }
            return false;
        }
    }

    /**
     * Adds a dependent project (one declared in build configuration) to this project.
     *
     * @param dependent the dependent project model to add
     */
    public void addDependentProject(ProjectModel dependent) {
        this.dependentProjects.add(dependent);
    }

    /**
     * Returns the list of dependent projects that have test source files.
     * This filters to only projects explicitly declared as dependencies in the build configuration.
     *
     * @return The list of dependent projects that have test source files.
     */
    public List<ProjectModel> getDependentProjectsWithTests() {
        ArrayList<ProjectModel> projectsWithTests = new ArrayList<ProjectModel>();

        for (ProjectModel dependent : dependentProjects) {
            if (dependent.hasTests()) {
                projectsWithTests.add(dependent);
            }
        }

        return projectsWithTests;
    }
}