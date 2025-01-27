/*******************************************************************************
* Copyright (c) 2024 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.debug;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.actions.AbstractDebugActionDelegate;
import org.eclipse.osgi.util.NLS;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.ui.launch.StartTab;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

/**
 * This class represents the executable for the "Connect Liberty Debugger" action
 * in the Debug view context menu.
 */
public class LibertyDebugReconnectActionDelegate extends AbstractDebugActionDelegate {

    @Override
    protected void doAction(Object object) {
        // This action can be performed from either a launch or debug target.
        // The object param will therefore either be an ILaunch or IDebugTarget object.
        ILaunch launch = null;
        IDebugTarget debugTarget = null;
        if (object instanceof ILaunch) {
            launch = (ILaunch) object;
            debugTarget = launch.getDebugTarget();
        } else {
            debugTarget = (IDebugTarget) object;
            launch = DebugUIPlugin.getLaunch(object);
        }

        if (launch != null) {
            DevModeOperations devModeOps = DevModeOperations.getInstance();

            String projectName = null;
            try {
                projectName = launch.getLaunchConfiguration().getAttribute(StartTab.PROJECT_NAME, "");
            } catch (CoreException e) {
                String msg = "An error was detected during debugger reconnect";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                }
                ErrorHandler.processErrorMessage(NLS.bind(Messages.project_name_error, null), e, true);
            }

            if (projectName != null && !projectName.isBlank()) {
                Project project = devModeOps.getProjectModel().getProject(projectName);

                // Reconnect debugger
                if (!devModeOps.isProjectTerminalTabMarkedClosed(projectName)) {
                    DebugModeHandler debugModeHandler = devModeOps.getDebugModeHandler();
                    debugModeHandler.startDebugAttacher(project, launch, null);
                }

                // Remove old debug target
                launch.removeDebugTarget(debugTarget);
            }
        }
    }
}
