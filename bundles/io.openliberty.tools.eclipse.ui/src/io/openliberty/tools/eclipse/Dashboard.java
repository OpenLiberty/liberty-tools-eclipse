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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

/**
 * Represents the Liberty tools dashboard and the project model behind it.
 */
public class Dashboard {

    private Map<String, Project> projectsByLocation;
    private Map<String, Project> projectsByName;

    /**
     * Constructor.
     */
    public Dashboard() {
        projectsByLocation = new ConcurrentHashMap<String, Project>();
        projectsByName = new ConcurrentHashMap<String, Project>();
    }

    private void createProjectModels(List<IProject> projects, boolean classify) {
        for (IProject iProject : new ArrayList<IProject>(projects)) {
            if (iProject.isOpen()) {
                Project projModel = projectsByLocation.get(iProject.getLocation().toOSString());
                if (projModel == null) {
                    projModel = new Project(iProject);
                    projectsByLocation.put(iProject.getLocation().toOSString(), projModel);
                    projectsByName.put(iProject.getName(), projModel);
                }
                if (classify) {
                    projModel.classify();
                }
            }
        }
    }

    /**
     * Build model
     * 
     * @param whether to classify or not
     */
    public void buildCompleteWorkspaceModel(boolean classify) {
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] iProjects = workspaceRoot.getProjects();
        buildMultiProjectModel(Arrays.asList(iProjects), classify);
    }

    public void buildMultiProjectModel(List<IProject> projectsToScan, boolean classify) {

        createProjectModels(projectsToScan, classify);

        try {
            for (IProject iProject : projectsToScan) {
                for (IResource res : iProject.members()) {
                    if (res.getType() == IResource.FOLDER) {
                        String resLocation = res.getLocation().toOSString();
                        Project child = projectsByLocation.get(resLocation);
                        if (child != null) {
                            Project parent = projectsByLocation.get(iProject.getLocation().toOSString());
                            child.setParentDirProject(parent);
                            parent.addChildDirProject(child);
                        }
                    }
                }
            }
        } catch (Exception e) {
            String msg = "An error occurred while analyzing projects in the workspace.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UTILS, msg + " Workspace projects: " + projectsByLocation.values(), e);
            }
            ErrorHandler.processWarningMessage(msg, e, false);
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_UTILS, "Projects: " + projectsByLocation.values());
        }
    }

    /**
     * Returns the Liberty server project associated with the input name or null if none is found.
     * 
     * @param name The name of the project (not the location)
     * 
     * @return The project associated with the input name or null if none is found. (Note that 'null' may be returned because this is
     *         not a server project).
     */
    public Project getLibertyServerProject(String name) {
        Project proj = projectsByName.get(name);
        if (proj != null && proj.isLibertyServerModule()) {
            return proj;
        }
        return null;
    }

    public List<String> getSortedDashboardProjectList() {

        List<String> mavenDashboardProjects = new ArrayList<String>();
        List<String> gradleDashboardProjects = new ArrayList<String>();
        List<String> retVal = new ArrayList<String>();

        for (Project p : projectsByName.values()) {
            if (p.isLibertyServerModule()) {
                if (p.getBuildType() == Project.BuildType.MAVEN) {
                    mavenDashboardProjects.add(p.getName());
                } else if (p.getBuildType() == Project.BuildType.GRADLE) {
                    gradleDashboardProjects.add(p.getName());
                } else {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UTILS,
                                "Project " + p.getIProject().getName() + " could not be identified as being a Maven or Gradle project.");
                    }
                }
            }
        }
        Collections.sort(mavenDashboardProjects);
        Collections.sort(gradleDashboardProjects);

        retVal.addAll(mavenDashboardProjects);
        retVal.addAll(gradleDashboardProjects);
        return retVal;

    }

    public String getDefaultStartParameters(IProject iProject) {
        Project proj = projectsByName.get(iProject.getName());
        if (proj.isAggregated()) {
            return "-f ../pom.xml -am -pl " + getModuleNameSegment(iProject);
        } else {
            return "";
        }
    }

    public String getModuleNameSegment(IProject iProject) {
        return iProject.getRawLocation().lastSegment();
    }

}
