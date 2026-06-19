/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.dashboard;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.WorkspaceModel;

/**
 * Content provider for the Liberty Dashboard tree view.
 * Provides hierarchical display of multi-module projects.
 */
public class DashboardContentProvider implements ITreeContentProvider {

    private WorkspaceModel workspaceModel;

    public DashboardContentProvider(WorkspaceModel workspaceModel, DashboardView dashboardView) {
        this.workspaceModel = workspaceModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] getElements(Object inputElement) {
        if (inputElement instanceof List) {
            @SuppressWarnings("unchecked")
            List<ProjectModel> projects = (List<ProjectModel>) inputElement;
            return projects.toArray();
        }
        return new Object[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object[] getChildren(Object parentElement) {
        if (parentElement instanceof ProjectModel) {
            ProjectModel projectModel = (ProjectModel) parentElement;
            List<ProjectModel> childrenSet = projectModel.getChildProjects();

            List<ProjectModel> visibleChildren = new ArrayList<>();
            for (ProjectModel child : childrenSet) {
                if (child.isLibertyServerModule() ||
                    !workspaceModel.findLibertyDescendants(child).isEmpty()) {
                    visibleChildren.add(child);
                }
            }
            // Sort children alphabetically
            visibleChildren.sort((p1, p2) -> p1.getName().compareTo(p2.getName()));
            return visibleChildren.toArray();
        }
        return new Object[0];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getParent(Object element) {
        if (element instanceof ProjectModel) {
            ProjectModel projectModel = (ProjectModel) element;
            return projectModel.getParentProjectModel();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasChildren(Object element) {
        if (element instanceof ProjectModel) {
            ProjectModel projectModel = (ProjectModel) element;

            // Check if any children are Liberty-enabled or have Liberty descendants
            for (ProjectModel child : projectModel.getChildProjects()) {
                if (child.isLibertyServerModule() ||
                    !workspaceModel.findLibertyDescendants(child).isEmpty()) {
                    return true;
                }
            }
            return false;

        }
        return false;
    }

    /**
     * Get root projects for hierarchical dashboard display.
     *
     * @return List of root projects (projects without parents)
     */
    public List<ProjectModel> getRootDashboardProjects() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS);
        }

        List<ProjectModel> result;
        List<ProjectModel> rootProjects = workspaceModel.getRootProjects();

        result = filterLibertyEnabledProjects(rootProjects);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, result);
        }

        return result;
    }

    /**
     * Filter projects to show only Liberty-enabled ones.
     * For parent aggregators, only include them if they have Liberty-enabled children.
     * For leaf projects, only include if Liberty-enabled.
     *
     * @param projects List of projects to filter
     * @return Filtered list containing only Liberty-enabled projects
     */
    private List<ProjectModel> filterLibertyEnabledProjects(List<ProjectModel> projectModels) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, projectModels);
        }

        List<ProjectModel> filtered = new ArrayList<>();

        for (ProjectModel projectModel : projectModels) {
            // Include if it's Liberty-enabled OR has Liberty descendants
            if (projectModel.getParentProjectModel() == null || projectModel.isLibertyServerModule() ||
                !workspaceModel.findLibertyDescendants(projectModel).isEmpty()) {
                filtered.add(projectModel);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, filtered);
        }

        return filtered;
    }
}