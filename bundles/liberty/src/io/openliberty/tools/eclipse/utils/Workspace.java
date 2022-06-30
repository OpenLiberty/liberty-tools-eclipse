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
package io.openliberty.tools.eclipse.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Resource workspace utilities.
 */
public class Workspace {

    /**
     * Retrieves the IProject object associated with the input name.
     *
     * @param name The name of the project.
     *
     * @return The IProject object associated with the input name.
     */
    public static IProject getOpenProjectByName(String name) {

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
     * @return A map of suitable projects from the workspace.
     */
    public static Map<String, Project> getOpenWokspaceProjects() {
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] iProjects = workspaceRoot.getProjects();

        HashMap<String, Project> projects = new HashMap<String, Project>();
        for (int i = 0; i < iProjects.length; i++) {
            projects.put(iProjects[i].getLocation().toOSString(), new Project(iProjects[i]));
        }

        try {
            for (int i = 0; i < iProjects.length; i++) {
                IProject iProject = iProjects[i];
                IResource[] projResources = iProject.members();
                boolean multimodSet = false;

                for (int j = 0; j < projResources.length; j++) {
                    IResource res = projResources[j];

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
     * Returns the list of projects that are configured to run on Liberty.
     *
     * @return The list of projects that are configured to run on Liberty.
     *
     * @throws Exception
     */
    public static List<String> getDashboardProjects(boolean refresh) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, refresh);
        }

        Map<String, Project> projectList = getOpenWokspaceProjects();
        Iterator<Map.Entry<String, Project>> iterator = projectList.entrySet().iterator();
        ArrayList<String> finalList = new ArrayList<String>();

        while (iterator.hasNext()) {
            Entry<String, Project> e = iterator.next();
            if (e.getValue().isSupported(refresh)) {
                finalList.add(e.getValue().getProject().getName());
            } else {
                iterator.remove();
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, finalList);
        }

        return finalList;
    }
}
