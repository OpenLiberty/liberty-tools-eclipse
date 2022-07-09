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
 * Resource workspace utilities.
 */
public class Workspace {

    /**
     * Map that holds the current set of installed Projects to display on the dashboard.
     */
    public ConcurrentHashMap<String, Project> dashboardProjects;

    /**
     * Constructor.
     */
    public Workspace() {
        dashboardProjects = new ConcurrentHashMap<String, Project>();
    }

    /**
     * Retrieves the IProject object associated with the input name.
     *
     * @param name The name of the project.
     *
     * @return The IProject object associated with the input name.
     */
    public IProject getProjectByName(String name) {

        try {
            IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

            IProject[] projects = workspaceRoot.getProjects();
            for (int i = 0; i < projects.length; i++) {
                IProject project = projects[i];
                if (project.isOpen() && (project.getName().equals(name))) {
                    return project;
                }
            }
        } catch (Exception ce) {

        }
        return null;
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
     * Returns the project associated with the input name.
     * 
     * @param name The name of the project.
     * 
     * @return The project associated with the input name.
     */
    public Project getDashboardProject(String name) {
        return dashboardProjects.get(name);
    }

    /**
     * Returns the list of projects that are configured to run on Liberty.
     *
     * @return The list of projects that are configured to run on Liberty.
     *
     * @throws Exception
     */
    public List<String> getDashboardProjects() throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS);
        }

        Map<String, Project> projectList = getTopLevelWorkspaceProjects();
        ConcurrentHashMap<String, Project> finalDashboardContent = new ConcurrentHashMap<String, Project>();
        for (Project p : projectList.values()) {
            if (p.isSupported()) {
                finalDashboardContent.put(p.getProject().getName(), p);
            }
        }

        dashboardProjects = finalDashboardContent;
        ArrayList<String> finalList = new ArrayList<String>(dashboardProjects.keySet());

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, finalList);
        }

        return finalList;
    }
}
