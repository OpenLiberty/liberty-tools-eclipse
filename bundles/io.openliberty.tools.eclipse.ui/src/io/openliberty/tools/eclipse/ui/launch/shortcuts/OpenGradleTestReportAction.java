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

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Liberty view Gradle test report action shortcut.
 */
public class OpenGradleTestReportAction implements ILaunchShortcut {

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
            String msg = "An error was detected when the \""
                         + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(
                                             Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT),
                                             e, true);
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
            String msg = "An error was detected when the \""
                         + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT + "\" launch shortcut was processed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(
                                             Messages.getMessage("launch_shortcut_error", LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT),
                                             e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, iProject);
        }
    }

    /**
     * Processes the view test report shortcut action.
     * 
     * @param iProject The project to process.
     * 
     * @throws Exception
     */
    public static void run(IProject iProject) throws Exception {
        // Make sure the project is valid.
        if (iProject == null) {
            throw new Exception(Messages.getMessage("launch_shortcut_project_not_found"));
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

        // Resolve the target project taking into account only those that are actively running.
        ProjectModel targetProjectModel = devModeOps.resolveCommandTarget(selectedProjectModel, "View Gradle Test Report", DevModeOperations.ServerFilterMode.ACTIVE_ONLY);
        if (targetProjectModel == null) {
            return;
        }

        // Update the active selection to the selected target project if the original selection does match the target.
        String targetProjectName = targetProjectModel.getName();
        if (!selectedProjectName.equals(targetProjectName)) {
            Utils.updateActiveSelection(targetProjectModel);
        }

        // Resolve the target project containing the test report to view.
        targetProjectModel = devModeOps.resolveTestReportTarget(targetProjectModel, "View Gradle Test Report");
        if (targetProjectModel == null) {
            return;
        }

        // Process the actions.
        devModeOps.openGradleTestReport(targetProjectModel);
    }
}