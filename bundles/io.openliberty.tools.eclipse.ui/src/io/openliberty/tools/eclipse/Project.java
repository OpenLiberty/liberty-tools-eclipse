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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Represents a project in the Liberty tools dashboard.
 */
public class Project {

    /**
     * Maven project nature.
     */
    public final String MAVEN_NATURE = "org.eclipse.m2e.core.maven2Nature";

    /**
     * Gradle project nature.
     */
    public final String GRADLE_NATURE = "org.eclipse.buildship.core.gradleprojectnature";

    /**
     * Project build types.
     */
    public static enum BuildType {
        UNKNOWN, GRADLE, MAVEN
    };

    /**
     * The Eclipse project reference.
     */
    IProject iProject;

    /**
     * Multi-module project indicator. It is based on the existence of one or more projects inside a project.
     */
    boolean multiModule;

    /**
     * Build type associated with this project.
     */
    BuildType type;

    /**
     * Constructor.
     * 
     * @param project The Eclipse project reference.
     */
    public Project(IProject project) {
        this.iProject = project;
        type = findBuildType();
    }

    /**
     * Returns the build type associated with this project.
     * @return The build type associated with this project.
     */
    public BuildType getBuildType() {
        return type;
    }

    /**
     * Finds the build type to be associated with this project. If a project can be built
     * as a Maven or Gradle project, the Maven build type takes precedence.
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
     * Returns true if the project was detected to have one or more projects in it. False, otherwise.
     * 
     * @return true if the project was detected to have one or more projects in it. False, otherwise.
     */
    public boolean isMultiModule() {
        return multiModule;
    }

    /**
     * Sets the multi-module indicator.
     */
    public void setMultimodule(boolean multiModule) {
        this.multiModule = multiModule;
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
     * Returns true if the input project is configured to run in Liberty and it uses a supported build mechanism. False,
     * otherwise. If it is determined that the project is a supported type, the outcome is persisted by associating the project
     * with a Liberty type/nature.
     *
     * @param iProject The project to check.
     *
     * @return True if the input project is configured to run in Liberty's dev mode. False, otherwise.
     *
     * @throws Exception
     */
    public boolean isSupported() throws Exception {
        // Note: Currently, we do not cleanup the nature from the project's metadata. Even if it is
        // determined that the input project is not a supported Liberty project, but it is marked as being
        // one. The reason for it is that we currently allow users to add the liberty nature to
        // those projects that do not fit the generic structure the code checks for. Once the checks are
        // are improved to handle customized projects, Liberty natures should be cleaned up automatically.

        // Check if the input project is already marked as being a supported liberty project.
        if (iProject.getDescription().hasNature(LibertyNature.NATURE_ID)) {
            return true;
        }

        // If we are here, the project is not marked as being able to run on Liberty
        boolean supported = false;

        // Check if the project has a server.xml config file in a specific location.
        // Gradle built multi-module projects are excluded. Dev mode currently does not support
        // that type of project.
        if (isMultiModule() && (type != BuildType.GRADLE)) {
            for (IResource resource : iProject.members()) {
                if (resource.getType() == IResource.FOLDER) {
                    IFolder folder = ((IFolder) resource);
                    Path path = new Path("src/main/liberty/config/server.xml");
                    if (path != null) {
                        IFile serverxml = folder.getFile(path);
                        if (serverxml.exists()) {
                            supported = true;
                            break;
                        }
                    }
                }
            }
        } else {
            IFile serverxml = iProject.getFile(new Path("src/main/liberty/config/server.xml"));
            if (serverxml.exists()) {
                supported = true;
            }
        }

        // If it is determined that the input project can run on Liberty, persist the outcome (if not
        // done so already) by adding a Liberty type/nature marker to the project's metadata.
        if (supported) {
            addLibertyNature(iProject);
        }

        return supported;
    }

    /**
     * Adds the Liberty type/nature entry to the project's description/metadata (.project).
     *
     * @param project The project to process.
     *
     * @throws Exception
     */
    public void addLibertyNature(IProject project) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, project);
        }

        IProjectDescription projectDesc = project.getDescription();
        String[] currentNatures = projectDesc.getNatureIds();
        String[] newNatures = new String[currentNatures.length + 1];
        System.arraycopy(currentNatures, 0, newNatures, 0, currentNatures.length);
        newNatures[currentNatures.length] = LibertyNature.NATURE_ID;
        projectDesc.setNatureIds(newNatures);
        project.setDescription(projectDesc, new NullProgressMonitor());

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, new Object[] { project, newNatures });
        }
    }

    /**
     * Removes the Liberty type/nature entry from the project's description/metadata (.project).
     *
     * @param project The project to process.
     *
     * @throws Exception
     */
    public void removeLibertyNature(IProject project) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, project);
        }

        IProjectDescription projectDesc = project.getDescription();
        String[] currentNatures = projectDesc.getNatureIds();
        ArrayList<String> newNatures = new ArrayList<String>(currentNatures.length - 1);

        for (int i = 0; i < currentNatures.length; i++) {
            if (currentNatures[i].equals(LibertyNature.NATURE_ID)) {
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

    @Override
    public String toString() {
        return "IProject: " + iProject.toString() + ". BuildType: " + type + ". Muti-module: " + multiModule;
    }

}