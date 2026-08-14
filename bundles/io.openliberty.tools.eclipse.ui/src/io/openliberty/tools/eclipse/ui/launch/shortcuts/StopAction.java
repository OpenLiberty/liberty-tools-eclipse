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
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.DevModeOperations.DashboardAction;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Liberty stop action shortcut.
 */
public class StopAction implements ILaunchShortcut {

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
            run(iProject);
        } catch (Exception e) {
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP), e, true);
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
            run(iProject);
        } catch (Exception e) {
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, iProject);
        }
    }

    /**
     * Processes the stop shortcut action.
     *
     * @param iProject The project to process.
     *
     * @throws Exception If an error occurs while processing the stop request.
     */
    public static void run(IProject iProject) throws Exception {
        // Make sure the project is valid.
        if (iProject == null) {
            throw new Exception(Messages.getMessage("stop_no_project_found"));
        }

        // Resolve the selected project.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        String selectedProjectName = iProject.getName();
        String selectedProjectLocation = iProject.getLocation().toOSString();
        ProjectModel selectedProjectModel = devModeOps.getWorkspaceModel().getProjectByLocation(selectedProjectLocation);

        if (selectedProjectModel == null) {
            throw new Exception(Messages.getMessage("internal_project_not_found", selectedProjectName));
        }

        // Resolve the target projects taking into account only those that are actively running.
        // This action accepts batch project execution.
        List<ProjectModel> targetProjects = devModeOps.resolveCommandTargets(selectedProjectModel, DashboardAction.STOP, DevModeOperations.ModuleStateFilter.ACTIVE, true);
        if (targetProjects.isEmpty()) {
            return;
        }

        // Stop all targeted projects.
        for (ProjectModel targetProjectModel : targetProjects) {
            // Reset batch started indicator.
            targetProjectModel.setBatchStarted(false);

            // Update the active selection to the target project.
            Utils.updateActiveSelection(targetProjectModel);

            // Process the stop action.
            devModeOps.stop(targetProjectModel);
        }
    }
}
