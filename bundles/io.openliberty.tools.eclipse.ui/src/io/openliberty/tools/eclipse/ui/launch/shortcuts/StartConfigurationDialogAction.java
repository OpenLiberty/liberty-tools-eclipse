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
package io.openliberty.tools.eclipse.ui.launch.shortcuts;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.DevModeOperations.DashboardAction;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher.RuntimeEnv;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationHelper;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Liberty start configuration dialog action shortcut.
 */
public class StartConfigurationDialogAction implements ILaunchShortcut {

    /** Run Configuration group dialog ID. */
    public static final String LAUNCH_GROUP_RUN_ID = "org.eclipse.debug.ui.launchGroup.run";

    /** Debug Configuration group dialog ID. */
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
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG), e, true);
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
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG), e, true);
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
     * @param mode     The operation mode type. Run or debug.
     *
     * @throws Exception If an error occurs while processing the start... request.
     */
    public static void run(IProject iProject, String mode) throws Exception {
        // Make sure the project is valid.
        if (iProject == null) {
            String msg = (ILaunchManager.DEBUG_MODE.equals(mode)) ? Messages.getMessage("debug_cfg_no_project_found") : Messages.getMessage("start_cfg_no_project_found");
            throw new Exception(msg);
        }

        String selectedProjectName = null;
        ProjectModel targetProjectModel = null;
        String targetProjectName = null;
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        try {
            selectedProjectName = iProject.getName();
            String selectedProjectLocation = iProject.getLocation().toOSString();
            ProjectModel selectedProjectModel = devModeOps.getWorkspaceModel().getProjectByLocation(selectedProjectLocation);

            // Validate that we know about the selected project.
            if (selectedProjectModel == null) {
                throw new IllegalStateException(Messages.getMessage("internal_project_not_found", selectedProjectName));
            }

            // Resolve the target project taking into account only those that are not actively running.
            // This action accepts on a single project executions.
            List<ProjectModel> targetProjects = devModeOps.resolveCommandTargets(selectedProjectModel, DashboardAction.START_CFG, DevModeOperations.ModuleStateFilter.INACTIVE,
                                                                                 false);
            if (targetProjects.isEmpty()) {
                return;
            }
            targetProjectModel = targetProjects.get(0);

            // Update the active selection to the target project.
            targetProjectName = targetProjectModel.getName();
            Utils.updateActiveSelection(targetProjectModel);

            // Check if the target project is already started.
            if (devModeOps.isProjectStarted(targetProjectModel)) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "The start... request was already issued on project " + targetProjectName + ".");
                }
                ErrorHandler.processErrorMessage(Messages.getMessage("start_with_config_already_issued", targetProjectName), true);
                return;
            }
        } catch (Exception e) {
            String msg = Messages.getMessage("start_with_config_general_error", (targetProjectName == null) ? selectedProjectName : targetProjectName);
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        // Determine what configuration to use.
        LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
        ILaunchConfiguration configuration = launchConfigHelper.getLaunchConfiguration(targetProjectModel, mode, RuntimeEnv.UNKNOWN);

        // Open the configuration in a configuration dialog.
        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();

        if (ILaunchManager.RUN_MODE.equals(mode)) {
            DebugUITools.openLaunchConfigurationDialogOnGroup(shell, new StructuredSelection(configuration), LAUNCH_GROUP_RUN_ID);
        } else if (ILaunchManager.DEBUG_MODE.equals(mode)) {
            DebugUITools.openLaunchConfigurationDialogOnGroup(shell, new StructuredSelection(configuration), LAUNCH_GROUP_DEBUG_ID);
        }
    }
}
