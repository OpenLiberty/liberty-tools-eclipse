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
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

/**
 * Represents the project model informing the Liberty tools dashboard and the Run Configurations
 */
public class WorkspaceProjectsModel {

    private Map<String, Project> projectsByLocation;
    private Map<String, Project> projectsByName;

    /**
     * Constructor.
     */
    public WorkspaceProjectsModel() {
        initProjectModels();
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
     * Build complete workspace project model. Do classify projects (add Liberty nature if conditions warrant) Should only be called
     * on UI thread
     */
    public void createNewCompleteWorkspaceModelWithClassify() {
        createNewCompleteWorkspaceModel(true);
    }

    /**
     * Discard previous model and build new model from open projects
     * 
     * @param whether to classify or not
     */
    private void createNewCompleteWorkspaceModel(boolean classify) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, new Object[] { classify });
        }

        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] iProjects = workspaceRoot.getProjects();

        List<IProject> openProjects = Arrays.stream(iProjects).filter(project -> project.isOpen()).collect(Collectors.toList());

        initProjectModels();
        createProjectModels(openProjects, classify);
        buildMultiProjectModel(openProjects, classify);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS);
        }
    }

    private void initProjectModels() {
        // Start over. Throw away existing model
        projectsByLocation = new ConcurrentHashMap<String, Project>();
        projectsByName = new ConcurrentHashMap<String, Project>();
    }

    /**
     * @param projectsToScan Projects to include in model update
     * @param classify Whether to classify
     */
    private void buildMultiProjectModel(List<IProject> projectsToScan, boolean classify) {

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

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, name);
        }

        Project retVal = null;

        Project proj = projectsByName.get(name);
        if (proj != null && proj.isLibertyServerModule()) {
            retVal = proj;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, retVal);
        }

        return retVal;
    }

    /**
     * Returns Liberty server modules grouped into two groups: Maven, then Gradle. Within each of the two groups, modules of that
     * group will be sorted in alphabetic order by project name. So you will get the sorted list of Maven Liberty server project names
     * followed by the sorted list of Gradle Liberty server project names
     * 
     * @return Liberty server project names sorted and grouped.
     */
    public List<String> getSortedDashboardProjectList() {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS);
        }

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

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, retVal);
        }

        return retVal;

    }

    /**
     * @param iProject
     * 
     * @return start parameters to serve as default populating something like a Run Configuration, depending on whether this looks
     *         like there is a multi-module relationship or not
     */
    public String getDefaultStartParameters(IProject iProject) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, new Object[] { iProject });
        }

        String retVal = null;

        Project proj = projectsByName.get(iProject.getName());
        if (proj.isAggregated()) {
            retVal = "-f ../pom.xml -am -pl " + getModuleNameSegment(iProject);
        } else {
            retVal = "";
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, retVal);
        }

        return retVal;
    }

    private String getModuleNameSegment(IProject iProject) {
        return iProject.getRawLocation().lastSegment();
    }

}