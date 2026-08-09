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
import org.eclipse.ui.IEditorPart;

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
 * Liberty start action shortcut.
 */
public class StartAction implements ILaunchShortcut {

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
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START), e, true);
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
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, iProject);
        }
    }

    /**
     * Processes the start shortcut action.
     * In run mode, multiple target projects could be selected.
     * In debug mode, only a single project can be selected.
     *
     * @param iProject The project to process.
     * @param mode     The operation mode type. Run or debug.
     *
     * @throws Exception If an error occurs while processing the start request.
     */
    public static void run(IProject iProject, String mode) throws Exception {
        // Make sure the project is valid.
        if (iProject == null) {
            String msg = (ILaunchManager.DEBUG_MODE.equals(mode)) ? Messages.getMessage("debug_no_project_found") : Messages.getMessage("start_no_project_found");
            throw new Exception(msg);
        }

        // Resolve the selected project.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        String selectedProjectName = iProject.getName();
        String selectedProjectLocation = iProject.getLocation().toOSString();
        ProjectModel selectedProjectModel = devModeOps.getWorkspaceModel().getProjectByLocation(selectedProjectLocation);

        // Validate that we know about the selected project.
        if (selectedProjectModel == null) {
            throw new IllegalStateException(Messages.getMessage("internal_project_not_found", selectedProjectName));
        }

        // Resolve the target projects taking into account only those that are inactive.
        // This action accepts batch project execution.
        boolean multiSelect = !ILaunchManager.DEBUG_MODE.equals(mode);
        List<ProjectModel> targetProjects = devModeOps.resolveCommandTargets(selectedProjectModel, DashboardAction.START, DevModeOperations.ModuleStateFilter.INACTIVE,
                                                                             multiSelect);
        if (targetProjects.isEmpty()) {
            return;
        }

        // If the user selected more than one child module to start, gather/reserve some data before
        // processing the launch.
        if (targetProjects.size() > 1) {
            for (ProjectModel targetProjectModel : targetProjects) {
                String targetProjectName = targetProjectModel.getName();

                // Mark the module as being part of a batch launch before calling DebugUITools.launch()
                // to start dev mode. DebugUITools.launch is an asynchronous process and the
                // configuration delegate launcher processing the launch request needs to know
                // if the request had multiple targets.
                targetProjectModel.setBachStarted(true);

                // In run mode with multiple targets, pre-reserve a unique OS-assigned Liberty
                // debug port for each module. Liberty Maven/Gradle plugin defaults libertyDebug=true
                // and binds a debug port (default 7777) on every start. Without pre-reservation,
                // parallel launches race to bind the same port. The reservation socket is kept
                // open until start() consumes it, so no two modules can receive the same port.
                if (!ILaunchManager.DEBUG_MODE.equals(mode)) {
                    try {
                        devModeOps.reserveLibertyDebugPort(targetProjectName);
                    } catch (Exception e) {
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_TOOLS, "Failed to reserve Liberty debug port for project " + targetProjectName + ". Using plugin default.", e);
                        }
                    }
                }
            }
        }

        // Launch all target projects/modules. 
        LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
        for (ProjectModel targetProjectModel : targetProjects) {
            String targetProjectName = targetProjectModel.getName();

            // Update the active selection to the selected target project if the original selection does not match the target.
            if (!selectedProjectName.equals(targetProjectName)) {
                Utils.updateActiveSelection(targetProjectModel);
            }

            ILaunchConfiguration configuration = launchConfigHelper.getLaunchConfiguration(targetProjectModel, mode, RuntimeEnv.LOCAL);
            DebugUITools.launch(configuration, mode);
        }
    }
}
