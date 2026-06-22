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
import org.eclipse.swt.widgets.Display;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.WorkspaceModel;

public class LibertyResourceChangeListener implements IResourceChangeListener {

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
                DevModeOperations devModeOps = DevModeOperations.getInstance();
                WorkspaceModel workspaceModel = devModeOps.getWorkspaceModel();
                IResourceDelta delta = event.getDelta();
                if (delta == null) {
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
                    ProjectModel projectModel = null;

                    String projectLocation = iProject.getLocation().toOSString();
                    projectModel = workspaceModel.getProjectByLocation(projectLocation);

                    if (projectModel == null) {
                        String msg = "Project " + iProject.getName()
                                     + " is not a supported project. Verify that the project is configured to run on a WebSphere Liberty server.";
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_UI, msg);
                        }
                        continue;
                    }

                    int updateFlag = resourceChanged.getFlags();

                    switch (resourceChanged.getKind()) {
                        // Project opened/closed.
                        // Flag OPEN (16384): "Change constant (bit mask) indicating that the resource was opened or closed"
                        // Flag 147456: Although IResourceDelta does not have a predefined constant, this flag value is used to
                        // denote open/close actions.
                        case IResourceDelta.CHANGED:
                            if (updateFlag == IResourceDelta.OPEN || updateFlag == 147456) {
                                refreshNeeded = true;
                            }
                            break;
                        // Project created/imported.
                        // Flag OPEN (16384): "This flag is ... set when the project did not exist in the "before" state."
                        // Flag 147456: Although IResourceDelta does not have a predefined constant, this flag
                        // value is set when a project, that previously did not exist, is created.
                        case IResourceDelta.ADDED:
                            if (projectModel == null && (updateFlag == IResourceDelta.OPEN || updateFlag == 147456)) {
                                refreshNeeded = true;
                            }
                            break;
                        // Project deleted.
                        // Flag NO_CHANGE (0).
                        // Flag MARKERS (130172).
                        case IResourceDelta.REMOVED:
                            if (projectModel != null && (updateFlag == IResourceDelta.NO_CHANGE || updateFlag == IResourceDelta.MARKERS)) {

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
            }
        });
    }
}