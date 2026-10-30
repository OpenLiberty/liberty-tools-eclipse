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
package io.openliberty.tools.eclipse;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Display;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.WorkspaceModel;

public class LibertyResourceChangeListener implements IResourceChangeListener {

    /**
     * Flags reported for a project that has markers when it is opened, closed, or added.
     * Closing a project removes its markers and opening it restores them.
     */
    private static final int OPEN_WITH_MARKERS = IResourceDelta.OPEN | IResourceDelta.MARKERS;

    /**
     * {@inheritDoc}
     */
    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        Display.getDefault().syncExec(new Runnable() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void run() {
                if (Trace.isEnabled()) {
                    Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { event.getType(), event.getSource() });
                }

                DevModeOperations devModeOps = DevModeOperations.getInstance();
                WorkspaceModel workspaceModel = devModeOps.getWorkspaceModel();
                IResourceDelta delta = event.getDelta();
                if (delta == null) {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "No delta. No-op.");
                    }
                    return;
                }

                // On entry the resource type is the root workspace. Find the child resources affected.
                IResourceDelta[] resourcesChanged = delta.getAffectedChildren();

                boolean refreshNeeded = false;

                // Iterate over the affected resources.
                for (IResourceDelta resourceChanged : resourcesChanged) {
                    IResource iResource = resourceChanged.getResource();
                    if (iResource.getType() != IResource.PROJECT) {
                        continue;
                    }

                    IProject iProject = (IProject) iResource;

                    int updateFlag = resourceChanged.getFlags();

                    switch (resourceChanged.getKind()) {
                        // Project opened/closed.
                        // Flag OPEN (16384): "Change constant (bit mask) indicating that the resource was opened or closed".
                        // Flag OPEN | MARKERS (147456): The same action on a project that has markers.
                        case IResourceDelta.CHANGED:
                            if (updateFlag == IResourceDelta.OPEN || updateFlag == OPEN_WITH_MARKERS) {
                                String projectLocation = iProject.getLocation().toOSString();
                                ProjectModel projectModel = workspaceModel.getProjectByLocation(projectLocation);

                                // Project closed: There is a reference to the project model.
                                // Project opened: The reference to the project model is removed on close.
                                // Rely on the Liberty nature, which is only accessible if the project is open.
                                try {
                                    if ((projectModel != null && projectModel.isLibertyServerModule())
                                        || iProject.getDescription().hasNature(LibertyNature.NATURE_ID)) {
                                        if (Trace.isEnabled()) {
                                            Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                                                    "Project changed. Project: " + iProject.getName() + ". Flag: " + updateFlag);
                                        }
                                        refreshNeeded = true;
                                    }
                                } catch (CoreException e) {
                                    if (Trace.isEnabled()) {
                                        Trace.getTracer().trace(Trace.TRACE_TOOLS, "Failed to read project description for: " + iProject.getName(), e);
                                    }
                                }
                            }
                            break;
                        // Project created/imported.
                        // Flag OPEN (16384): "This flag is ... set when the project did not exist in the "before" state."
                        // Flag OPEN | MARKERS (147456): The same action for a project that has markers.
                        case IResourceDelta.ADDED:
                            String projectLocation = iProject.getLocation().toOSString();
                            ProjectModel projectModel = workspaceModel.getProjectByLocation(projectLocation);

                            if (projectModel == null && (updateFlag == IResourceDelta.OPEN || updateFlag == OPEN_WITH_MARKERS)) {
                                if (Trace.isEnabled()) {
                                    Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                                            "Project added. Project: " + iProject.getName() + ". Flag: " + updateFlag);
                                }
                                refreshNeeded = true;
                            }
                            break;
                        // Project deleted or renamed.
                        // Flag NO_CHANGE (0).
                        // Flag MARKERS (131072).
                        // Flag MOVED_TO (8192): The project was renamed. For example, Buildship renames a project
                        // after a successful Gradle synchronization. The model holds a handle to the old project,
                        // which no longer exists and has no location, so the model must be rebuilt.
                        case IResourceDelta.REMOVED:
                            if ((updateFlag == IResourceDelta.NO_CHANGE || updateFlag == IResourceDelta.MARKERS
                                 || (updateFlag & IResourceDelta.MOVED_TO) != 0)) {
                                if (Trace.isEnabled()) {
                                    Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                                            "Project removed/renamed. Project: " + iProject.getName() + ". Flag: " + updateFlag);
                                }
                                refreshNeeded = true;
                            }
                            break;
                        default:
                            break;
                    }
                }

                if (refreshNeeded) {
                    // We leave this commented out as a marker of the idea that maybe one day we'll only
                    // build the "delta" model instead of the whole workspace model
                    // workspaceProjectsModel.buildMultiProjectModel(projectsChanged, true);
                    workspaceModel.createNewCompleteWorkspaceModelWithClassify();
                    devModeOps.refreshDashboardView(false);
                }

                if (Trace.isEnabled()) {
                    Trace.getTracer().traceExit(Trace.TRACE_TOOLS, refreshNeeded);
                }
            }
        });
    }
}
