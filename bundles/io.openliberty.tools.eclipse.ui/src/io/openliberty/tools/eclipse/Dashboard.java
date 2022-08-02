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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Represents the Liberty tools dashboard.
 */
public class Dashboard {

    /**
     * Maven projects displayed on the dashboard.
     */
    public ConcurrentHashMap<String, Project> mavenProjects;

    /**
     * Gradle projects displayed on the dashboard.
     */
    public ConcurrentHashMap<String, Project> gradleProjects;

    /**
     * Constructor.
     */
    public Dashboard() {
        mavenProjects = new ConcurrentHashMap<String, Project>();
        gradleProjects = new ConcurrentHashMap<String, Project>();
    }

    /**
     * Retrieves a map of suitable projects from the workspace.
     *
     * @return A map projects from the workspace that are each themselves NOT nested/contained in another workspace project
     */
    private Map<String, Project> getTopLevelWorkspaceProjects() {
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] iProjects = workspaceRoot.getProjects();
        ConcurrentHashMap<String, Project> projects = new ConcurrentHashMap<String, Project>();

        for (IProject iProject : iProjects) {
            projects.put(iProject.getLocation().toOSString(), new Project(iProject));
        }

        try {
            for (IProject iProject : iProjects) {
                boolean multimodSet = false;

                for (IResource res : iProject.members()) {
                    if (res.getType() == IResource.FOLDER) {
                        IFolder folder = ((IFolder) res);
                        IFile file = folder.getFile(".project");
                        if (file.exists()) {
                            if (!multimodSet) {
                                projects.get(iProject.getLocation().toOSString()).setMultimodule(true);
                                multimodSet = true;
                            }

                            String resLocation = res.getLocation().toOSString();
                            Project matchLocProj = projects.get(resLocation);

                            if (matchLocProj != null) {
                                projects.remove(resLocation);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UTILS, "Error while retrieving eclipse projects: ", e);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_UTILS, "Projects: " + projects);
        }

        return projects;
    }

    /**
     * Returns the project associated with the input name or null if none is found.
     * 
     * @param name The name of the project.
     * 
     * @return The project associated with the input name or null if none is found.
     */
    public Project getProject(String name) {
        Project p = mavenProjects.get(name);
        if (p == null) {
            p = gradleProjects.get(name);
        }
        return p;
    }

    /**
     * Retrieves and caches the set of installed projects that are to appear on the dashboard.
     *
     * @throws Exception
     */
    public void retrieveSupportedProjects() throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS);
        }

        Map<String, Project> projectList = getTopLevelWorkspaceProjects();
        ConcurrentHashMap<String, Project> finalMavenContent = new ConcurrentHashMap<String, Project>();
        ConcurrentHashMap<String, Project> finalGradleContent = new ConcurrentHashMap<String, Project>();
        for (Project p : projectList.values()) {
            if (p.isSupported()) {
                String projectName = p.getIProject().getName();
                if (p.getBuildType() == Project.BuildType.MAVEN) {
                    finalMavenContent.put(projectName, p);
                } else if (p.getBuildType() == Project.BuildType.GRADLE) {
                    finalGradleContent.put(projectName, p);
                } else {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UTILS,
                                "Project " + projectName + " could not be identified as being a Maven or Gradle project.");
                    }
                }
            }
        }

        mavenProjects = finalMavenContent;
        gradleProjects = finalGradleContent;

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, new Object[] { mavenProjects, gradleProjects });
        }
    }

    /**
     * Returns a list of names associated with the Maven projects on the dashboard.
     * 
     * @return A list of names associated with the Maven projects on the dashboard.
     */
    public List<String> getMavenProjectNames() {
        return new ArrayList<String>(mavenProjects.keySet());
    }

    /**
     * Returns a list of names associated with the Gradle projects on the dashboard.
     * 
     * @return A list of names associated with the Gradle projects on the dashboard.
     */
    public List<String> getGradleProjectNames() {
        return new ArrayList<String>(gradleProjects.keySet());
    }

}
