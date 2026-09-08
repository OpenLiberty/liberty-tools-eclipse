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
 * Liberty view Maven integration test report action shortcut.
 */
public class OpenMavenITestReportAction implements ILaunchShortcut {

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
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT), e, true);
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
            String msg = "An error was detected when the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, iProject);
        }
    }

    /**
     * Processes the view integration test report shortcut action.
     *
     * @param iProject The project to process.
     *
     * @throws Exception If an error occurs while processing the view integration test report request.
     */
    public static void run(IProject iProject) throws Exception {
        // Make sure the project is valid.
        if (iProject == null) {
            throw new Exception(Messages.getMessage("mvn_int_test_report_no_project_found"));
        }

        // Resolve the selected project.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        String selectedProjectName = iProject.getName();
        String selectedProjectLocation = iProject.getLocation().toOSString();
        ProjectModel selectedProjectModel = devModeOps.getWorkspaceModel().getProjectByLocation(selectedProjectLocation);

        // Validate that we know about the selected project.
        if (selectedProjectModel == null) {
            throw new Exception(Messages.getMessage("internal_project_not_found", selectedProjectName));
        }

        // Resolve the target module. This action accepts on a single project executions.
        List<ProjectModel> targetProjects = devModeOps.resolveCommandTargets(
                                                                             selectedProjectModel, DashboardAction.OPEN_MVN_IT_TEST_REPORT, DevModeOperations.ModuleStateFilter.ALL,
                                                                             false);
        if (targetProjects.isEmpty()) {
            return;
        }
        ProjectModel targetProjectModel = targetProjects.get(0);

        // Update the active selection to the target project.
        Utils.updateActiveSelection(targetProjectModel);

        // Resolve the test report to view.
        targetProjectModel = devModeOps.resolveTestReportTarget(targetProjectModel, DashboardAction.OPEN_MVN_IT_TEST_REPORT);
        if (targetProjectModel == null) {
            // User cancelled the selection dialog.
            return;
        }

        // Process the action.
        devModeOps.openMavenIntegrationTestReport(targetProjectModel);
    }
}
