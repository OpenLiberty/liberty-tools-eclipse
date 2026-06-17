/*******************************************************************************
* Copyright (c) 2025, 2026 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.process;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.utils.Utils;

public class LibertyDebugEventListener implements IDebugEventSetListener {

    private String projectName;

    public LibertyDebugEventListener(String projectName) {
        this.projectName = projectName;
    }

    @Override
    public void handleDebugEvents(DebugEvent[] events) {
        for (int i = 0; i < events.length; i++) {
            Object source = events[i].getSource();
            DevModeOperations devModeOps = DevModeOperations.getInstance();

            if (source instanceof IProcess && events[i].getKind() == DebugEvent.TERMINATE) {

                // This is an IProcess terminate event. Check if the IProcess matches
                IProcess iProcess = (IProcess) source;
                if (projectName.equals(iProcess.getLabel())) {
                    // We match - cleanup
                    ProjectModel project = devModeOps.getWorkspaceModel().getProjectByName(projectName);

                    if (project != null) {
                        Utils.reEnableAppMonitoring(project);
                    }
                    devModeOps.cleanupProcess(projectName);
                    DebugPlugin.getDefault().removeDebugEventListener(this);
                }
            } else if (events[i].getKind() == DebugEvent.CHANGE && source instanceof IDebugTarget) {
                IDebugTarget target = (IDebugTarget) source;
                if (target.isDisconnected()) {
                    ILaunch launch = target.getLaunch();
                    if (launch != null) {
                        IProcess[] processes = launch.getProcesses();
                        if (processes.length > 0) {
                            String label = processes[0].getLabel();
                            if (projectName.equals(label)) {
                                ProjectModel project = devModeOps.getWorkspaceModel().getProjectByName(projectName);
                                if (project != null) {
                                    Utils.reEnableAppMonitoring(project);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
