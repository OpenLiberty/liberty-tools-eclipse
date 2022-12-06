/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
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
package io.openliberty.tools.eclipse;

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

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

/**
 * Represents a project in the Liberty tools dashboard.
 */
public class Project {

    /** Maven project nature. */
    public static final String MAVEN_NATURE = "org.eclipse.m2e.core.maven2Nature";

    /** Gradle project nature. */
    public static final String GRADLE_NATURE = "org.eclipse.buildship.core.gradleprojectnature";

    /** Java project nature. */
    public static final String JAVA_NATURE_ID = "org.eclipse.jdt.core.javanature";

    /** Project build types. */
    public static enum BuildType {
        UNKNOWN, GRADLE, MAVEN
    };

    /** The child projects associated with this project. */
    private Set<Project> childDirProjects = ConcurrentHashMap.newKeySet();

    /** The set of peer projects */
    private Set<Project> peerDirProjects = ConcurrentHashMap.newKeySet();

    /** The Eclipse project reference. */
    private IProject iProject;

    /** Build type associated with this project. */
    private BuildType type;

    /** The parent of this project. */
    private Project parentDirProject;

    private boolean libertyServerModule;

    private boolean isParentOfServerModule;

    /**
     * Constructor.
     * 
     * @param project The Eclipse project reference.
     */
    public Project(IProject project) {
        this.iProject = project;
        this.type = findBuildType();
    }

    public boolean hasLibertyNature() {
        try {
            if (iProject.getDescription().hasNature(LibertyNature.NATURE_ID)) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UTILS,
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
     * Finds the build type to be associated with this project. If a project can be built as a Maven or Gradle project, the Maven
     * build type takes precedence.
     */
    private BuildType findBuildType() {

        // Check the installed project's nature.
        try {
            if (iProject.getDescription().hasNature(MAVEN_NATURE)) {
                return BuildType.MAVEN;
            } else if (iProject.getDescription().hasNature(GRADLE_NATURE)) {
                return BuildType.GRADLE;
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UTILS,
                        "An error occurred while attempting to find the nature of project " + iProject.getName(), e);
            }
        }

        // Check the build configuration file.
        if (iProject.getFile("pom.xml").exists()) {
            return BuildType.MAVEN;
        } else if ((iProject.getFile("build.gradle").exists())) {
            return BuildType.GRADLE;
        }

        return BuildType.UNKNOWN;

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
    public List<Project> getChildLibertyServerProjects() {
        ArrayList<Project> clsps = new ArrayList<Project>();

        for (Project child : childDirProjects) {
            if (child.isLibertyServerModule()) {
                clsps.add(child);
            }
        }

        return clsps;
    }

    /**
     * Returns the list child projects that contain the java nature.
     * 
     * @return The list child projects that contain the java nature.
     */
    public List<Project> getChildJavaProjects() {
        return filterJavaProjects(childDirProjects);
    }

    /**
     * Returns the list of peer projects that contain the java nature.
     * 
     * @return The list of peer projects that contain the java nature.
     */
    public List<Project> getPeerJavaProjects() {
        return filterJavaProjects(peerDirProjects);
    }

    /**
     * Returns the list of projects that contain the Java nature from the input set.
     * 
     * @param projects The set of projects to filter.
     * 
     * @return The list of projects that contain the Java nature from the input set.
     */
    public List<Project> filterJavaProjects(Set<Project> projects) {
        ArrayList<Project> javaProjecs = new ArrayList<Project>();
        for (Project child : projects) {
            try {
                if (child.getIProject().hasNature(JAVA_NATURE_ID)) {
                    javaProjecs.add(child);
                }
            } catch (CoreException e) {
                ErrorHandler.processWarningMessage("Unable to determine if project : " + child.getName() + " is a Java project.", e, false);
            }
        }
        return javaProjecs;
    }

    /**
     */
    public void classifyAsServerModule() {
        try {
            IFile serverxml = iProject.getFile(new Path("src/main/liberty/config/server.xml"));
            IFile bootstrapProps = iProject.getFile(new Path("src/main/liberty/config/bootstrap.properties"));
            IFile serverenv = iProject.getFile(new Path("src/main/liberty/config/server.env"));
            if (serverxml.exists() || bootstrapProps.exists() || serverenv.exists()) {
                libertyServerModule = true;
            } else {
                libertyServerModule = false;
            }
        } catch (Exception e) {
            String msg = "Error querying and adding Liberty nature";
            ErrorHandler.processWarningMessage(msg, e, false);
        }
    }

    /**
     * Adds the Liberty nature to the project if it is not already present.
     */
    public void classifyAsLibertyNature() {
        try {
            if (libertyServerModule) {
                Project.addNature(iProject, LibertyNature.NATURE_ID);
            }
            // If this is looks like a Maven multi-module project. It may not be however but we take the risk of exposing it
            if (type.equals(BuildType.MAVEN)) {
                for (Project child : childDirProjects) {
                    if (child.isLibertyServerModule()) {
                        Project.addNature(iProject, LibertyNature.NATURE_ID);
                        isParentOfServerModule = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            String msg = "Error querying and adding Liberty nature";
            ErrorHandler.processWarningMessage(msg, e, false);
        }
    }

    /**
     * Adds the specified nature ID to the project's description/metadata (.project).
     * 
     * @param project The project to process.
     * @param natureId The nature ID to add.
     * 
     * @throws Exception
     */
    public static void addNature(IProject project, String natureId) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, new Object[] { project, natureId });
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
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, new Object[] { project, newNatures });
        }
    }

    /**
     * Removes the specified nature ID from the project's description/metadata (.project).
     *
     * @param project The project to process.
     * @param natureId The nature ID to remove.
     *
     * @throws Exception
     */
    public static void removeNature(IProject project, String natureId) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, project);
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
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, new Object[] { project, newNatures });
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
    public void setPeerDirProjects(List<Project> peerProjects) {
        for (Project project : peerProjects) {
            if (!getName().equals(project.getName())) {
                this.peerDirProjects.add(project);
            }
        }
    }

    private String formatChildProjectToString() {
        if (childDirProjects.isEmpty()) {
            return "<empty>";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            for (Project p : childDirProjects) {
                sb.append(p.getName()).append(",");
            }
            sb.append("]");
            return sb.toString();
        }
    }

    @Override
    public String toString() {
        return "IProject: " + iProject.toString() + ". BuildType: " + type + ". Liberty Server Module: " + libertyServerModule
                + ". isParentOfServerModule:" + isParentOfServerModule + ". parentDirProj: "
                + (parentDirProject != null ? parentDirProject.getName() : "<null> ") + ". childDirProjects: "
                + formatChildProjectToString() + ";";
    }

    public boolean isLibertyServerModule() {
        return libertyServerModule;
    }

    public void setParentDirProject(Project parent) {
        this.parentDirProject = parent;
    }

    public void addChildDirProject(Project child) {
        this.childDirProjects.add(child);
    }

    public String getName() {
        return iProject.getName();
    }

    public boolean isAggregated() {
        return parentDirProject != null;
    }

    public boolean isParentOfServerModule() {
        return isParentOfServerModule;
    }
}