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
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

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
     * Gets all open projects currently in the workspace.
     *
     * @return All open projects currently in the workspace.
     */
    public static List<IProject> getOpenWokspaceProjects() {
        List<IProject> jProjects = new ArrayList<IProject>();

        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = workspaceRoot.getProjects();
        for (int i = 0; i < projects.length; i++) {
            IProject project = projects[i];

            if (project.isOpen()) {
                jProjects.add(project);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_UTILS, "Opened workspace projects: " + jProjects);
        }

        return jProjects;
    }

    /**
     * Returns a list of projects configured to run on Liberty.
     *
     * @return A list of projects configured to run on Liberty.
     *
     * @throws Exception
     */
    public static List<String> getLibertyProjects(boolean refresh) throws Exception {
        ArrayList<String> libertyProjects = new ArrayList<String>();
        List<IProject> projectList = getOpenWokspaceProjects();
        Iterator<IProject> projects = projectList.iterator();
        while (projects.hasNext()) {
            IProject project = projects.next();
            if (Project.isLiberty(project, refresh)) {
                libertyProjects.add(project.getName());
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_UTILS, "Refresh input: " + refresh + ". Liberty projects: " + libertyProjects);
        }

        return libertyProjects;
    }
}
