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

import java.util.ArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.ui.launch.MainTab;
import io.openliberty.tools.eclipse.utils.Dialog;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Liberty Tools start action shortcut.
 */
public class StartAction implements ILaunchShortcut {

    /** Configuration selection Dialog title. */
    private static final String SELECTION_DIALOG_TITLE = "Liberty Tools Configuration Selection";

    /** Run mode configuration selection Dialog message. */
    private static final String SELECTION_DIALOG_RUN_MSG = "Select a run configuration";

    /** Debug mode configuration selection Dialog message. */
    private static final String SELECTION_DIALOG_DEBUG_MSG = "Select a debug configuration";

    /** DevModeOperations instance. */
    private DevModeOperations devModeOps = DevModeOperations.getInstance();

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(ISelection selection, String mode) {
        IProject iProject = null;
        String startParms = null;

        try {
            ILaunchConfiguration configuration = getLaunchConfiguration(mode);
            // If the configuration is null, there is nothing else to do. A null configuration can be caused by:
            // 1. An error. In this case an error dialog was already displayed.
            // 2. The user pressed the cancel button when asked to choose a configuration from a list configurations
            // associated with the selected project.
            if (configuration == null) {
                return;
            }

            iProject = Utils.getProjectFromSelection(selection);
            if (iProject == null) {
                throw new Exception("Unable to find the selected project.");
            }

            devModeOps.verifyProjectSupport(iProject);

            startParms = configuration.getAttribute(MainTab.START_PARM, "");
        } catch (Exception e) {
            String msg = "An error was detected during \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START
                    + "\" launch shortcut processing.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
            return;
        }

        if (startParms == null || startParms.isEmpty()) {
            devModeOps.start(iProject);
        } else {
            devModeOps.startWithParms(iProject, startParms);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(IEditorPart part, String mode) {
        IProject iProject = null;
        String startParms = null;

        try {
            ILaunchConfiguration configuration = getLaunchConfiguration(mode);
            iProject = Utils.getProjectFromPart(part);
            if (iProject == null) {
                throw new Exception("Unable to find the selected project.");
            }

            devModeOps.verifyProjectSupport(iProject);

            startParms = configuration.getAttribute(MainTab.START_PARM, "");
        } catch (Exception e) {
            String msg = "An error was detected during \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START
                    + "\" launch shortcut processing.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
            return;
        }

        if (startParms == null || startParms.isEmpty()) {
            devModeOps.start(iProject);
        } else {
            devModeOps.startWithParms(iProject, startParms);
        }
    }

    /**
     * Returns the configuration to be used by the project associated with the action being processed.
     * 
     * @param mode The mode.
     * 
     * @return The configuration to be used by the project associated with the action being processed.
     */
    private ILaunchConfiguration getLaunchConfiguration(String mode) {
        ILaunchConfiguration configuration = null;
        ILaunchManager iLaunchMgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType iLaunchConfigType = iLaunchMgr
                .getLaunchConfigurationType(LaunchConfigurationDelegateLauncher.LAUNCH_CONFIG_TYPE_ID);
        IProject currentProject = Utils.getActiveProject();

        try {
            // Find the set of configurations that were used by the currently active project last.
            ILaunchConfiguration[] existingConfigs = iLaunchMgr.getLaunchConfigurations(iLaunchConfigType);
            ArrayList<ILaunchConfiguration> matchingConfigList = new ArrayList<>();
            for (ILaunchConfiguration existingConfig : existingConfigs) {
                String configProjName = existingConfig.getAttribute(MainTab.PROJECT_NAME, "");
                if (configProjName == null || currentProject == null) {
                    continue;
                }

                if (currentProject.getName().equals(configProjName)) {
                    matchingConfigList.add(existingConfig);
                }
            }

            switch (matchingConfigList.size()) {
            case 0:
                // Create a new configuration.
                String newName = iLaunchMgr.generateLaunchConfigurationName(currentProject.getName());
                ILaunchConfigurationWorkingCopy workingCopy = iLaunchConfigType.newInstance(null, newName);
                workingCopy.setAttribute(MainTab.PROJECT_NAME, currentProject.getName());
                workingCopy.setAttribute(MainTab.START_PARM, "");
                configuration = workingCopy.doSave();
                break;

            case 1:
                // Return the found configuration.
                configuration = matchingConfigList.get(0);
                break;
            default:
                // Allow the user to select one of the matching configurations to use.
                final IDebugModelPresentation debugLabelProvider = DebugUITools.newDebugModelPresentation();
                Shell activeShell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                ElementListSelectionDialog selectionDialog = new ElementListSelectionDialog(activeShell, debugLabelProvider);
                selectionDialog.setTitle(SELECTION_DIALOG_TITLE);

                if (ILaunchManager.RUN_MODE.equals(mode)) {
                    selectionDialog.setMessage(SELECTION_DIALOG_RUN_MSG);
                } else {
                    selectionDialog.setMessage(SELECTION_DIALOG_DEBUG_MSG);
                }

                selectionDialog.setMultipleSelection(false);
                selectionDialog.setElements(matchingConfigList.toArray());
                int rc = selectionDialog.open();
                debugLabelProvider.dispose();

                if (rc == Window.OK) {
                    configuration = (ILaunchConfiguration) selectionDialog.getFirstResult();
                }

                break;
            }
        } catch (CoreException ce) {
            // Trace and ignore.
            String msg = "An error was detected while attempting to retrieve the run configuration for project: "
                    + currentProject.getName();
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, ce);
            }
        }

        return configuration;
    }
}