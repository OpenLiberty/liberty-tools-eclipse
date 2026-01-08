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
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher.RuntimeEnv;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationHelper;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Liberty start configuration dialog action shortcut.
 */
public class StartConfigurationDialogAction implements ILaunchShortcut {

    /** Run Configuration group dialod ID. */
    public static final String LAUNCH_GROUP_RUN_ID = "org.eclipse.debug.ui.launchGroup.run";

    /** Debug Configuration group dialod ID. */
    public static final String LAUNCH_GROUP_DEBUG_ID = "org.eclipse.debug.ui.launchGroup.debug";

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(ISelection selection, String mode) {
        IProject iProject = Utils.getProjectFromSelection(selection);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { iProject, mode });
        }

        try {
            run(iProject, mode);
        } catch (Exception e) {
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG
                    + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(
                    Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, iProject);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(IEditorPart part, String mode) {
        IProject iProject = Utils.getProjectFromPart(part);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { iProject, part, mode });
        }

        try {
            run(iProject, mode);
        } catch (Exception e) {
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG
                    + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(
                    Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, iProject);
        }
    }

    /**
     * Processes the start... shortcut action.
     * 
     * @param iProject The project to process.
     * @param mode The operation mode type. Run or debug.
     * 
     * @throws Exception
     */
    public static void run(IProject iProject, String mode) throws Exception {
        if (iProject == null) {
            throw new Exception("Invalid project. Be sure to select a project first.");
        }

        // Validate that the project is supported.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        devModeOps.verifyProjectSupport(iProject);

        // Determine what configuration to use.
        LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
        ILaunchConfiguration configuration = launchConfigHelper.getLaunchConfiguration(iProject, mode, RuntimeEnv.UNKNOWN);

        // Open the configuration in a configuration dialog.
        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

        if (ILaunchManager.RUN_MODE.equals(mode)) {
            DebugUITools.openLaunchConfigurationDialogOnGroup(shell, new StructuredSelection(configuration), LAUNCH_GROUP_RUN_ID);
        } else if (ILaunchManager.DEBUG_MODE.equals(mode)) {
            DebugUITools.openLaunchConfigurationDialogOnGroup(shell, new StructuredSelection(configuration), LAUNCH_GROUP_DEBUG_ID);
        }
    }
}
