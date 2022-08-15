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
package io.openliberty.tools.eclipse.ui.launch.shortcuts;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.utils.Dialog;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Liberty Tools stop action shortcut.
 */
public class StopAction implements ILaunchShortcut {

    /**
     * DevModeOperations instance.
     */
    private DevModeOperations devModeOps = DevModeOperations.getInstance();

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(ISelection selection, String mode) {
        IProject iProject = null;

        try {
            iProject = Utils.getProjectFromSelection(selection);
            if (iProject == null) {
                throw new Exception("Unable to find the selected project.");
            }

            devModeOps.verifyProjectSupport(iProject);
        } catch (Exception e) {
            String msg = "An error was detected during \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP
                    + "\" launch shortcut processing.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
            return;
        }

        devModeOps.stop(iProject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(IEditorPart part, String mode) {
        IProject iProject = null;

        try {
            iProject = Utils.getProjectFromPart(part);
            if (iProject == null) {
                throw new Exception("Unable to find the selected project.");
            }

            devModeOps.verifyProjectSupport(iProject);
        } catch (Exception e) {
            String msg = "An error was detected during \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP
                    + "\" launch shortcut processing.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
            return;
        }

        devModeOps.stop(iProject);
    }
}