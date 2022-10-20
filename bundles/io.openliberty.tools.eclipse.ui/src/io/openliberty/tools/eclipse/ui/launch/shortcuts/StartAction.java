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
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher.RuntimeEnv;
import io.openliberty.tools.eclipse.ui.launch.MainTab;
import io.openliberty.tools.eclipse.utils.Dialog;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Liberty start action shortcut.
 */
public class StartAction implements ILaunchShortcut {

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(ISelection selection, String mode) {
        try {
            IProject iProject = Utils.getProjectFromSelection(selection);
            run(iProject, null, mode);
        } catch (Exception e) {
            String msg = "An error was detected while processing the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START
                    + "\" launch shortcut.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(IEditorPart part, String mode) {
        try {
            IProject selectedProject = Utils.getProjectFromPart(part);
            run(selectedProject, null, mode);
        } catch (Exception e) {
            String msg = "An error was detected while processing the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START
                    + "\" launch shortcut.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
        }
    }

    /**
     * Processes the start shortcut action.
     * 
     * @param iProject The project to process.
     * @param mode The operation mode type. Run or debug.
     * 
     * @throws Exception
     */
    public static void run(IProject iProject, ILaunchConfiguration iConfiguration, String mode) throws Exception {
        if (iProject == null) {
            throw new Exception("Invalid project. Be sure to select a project first.");
        }

        // Validate that the project is supported.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        devModeOps.verifyProjectSupport(iProject);

        // If the configuration was not provided by the caller, determine what configuration to use.
        ILaunchConfiguration configuration = (iConfiguration != null) ? iConfiguration
                : LaunchConfigurationDelegateLauncher.getLaunchConfiguration(iProject, mode, RuntimeEnv.LOCAL);

        // Save the time when this configuration was processed.
        LaunchConfigurationDelegateLauncher.saveConfigProcessingTime(configuration);

        // Retrieve configuration data.
        boolean runInContainer = configuration.getAttribute(MainTab.PROJECT_RUN_IN_CONTAINER, false);
        String startParms = configuration.getAttribute(MainTab.PROJECT_START_PARM, (String) null);

        // Process the action.
        if (runInContainer) {
            devModeOps.startInContainer(iProject, startParms);
        } else {
            devModeOps.start(iProject, startParms);
        }
    }
}