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
package io.openliberty.tools.eclipse.ui.launch;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Launch configuration entry point for running an application on Liberty and in dev mode.
 */
public class LaunchConfigurationDelegateLauncher extends LaunchConfigurationDelegate {

    /** Launch configuration type ID as specified in plugin.xml. */
    public static final String LAUNCH_CONFIG_TYPE_ID = "io.openliberty.tools.eclipse.launch.type";

    /** Launch shortcuts */
    public static final String LAUNCH_SHORTCUT_START = "Liberty Start";
    public static final String LAUNCH_SHORTCUT_START_CONFIG = "Liberty Start...";
    public static final String LAUNCH_SHORTCUT_STOP = "Liberty Stop";
    public static final String LAUNCH_SHORTCUT_START_CONTAINER = "Liberty Start in Container";
    public static final String LAUNCH_SHORTCUT_RUN_TESTS = "Liberty Run Tests";
    public static final String LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT = "Liberty View Integration Test Report";
    public static final String LAUNCH_SHORTCUT_MVN_VIEW_UT_REPORT = "Liberty View Unit Test Report";
    public static final String LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT = "Liberty View Test Report";

    /** Runtime environments */
    public static enum RuntimeEnv {
        UNKNOWN, LOCAL, CONTAINER
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { configuration, mode, launch, monitor });
        }

        // Processing paths:
        // - Explorer-> Run As-> Run Configurations
        // - Dashboard-> project -> Start...
        IWorkbench workbench = PlatformUI.getWorkbench();
        Display display = workbench.getDisplay();

        // Launch dev mode.
        display.syncExec(new Runnable() {
            public void run() {
                try {
                    String configProjectName = configuration.getAttribute(StartTab.PROJECT_NAME, (String) null);
                    validateProjectsMatch(configuration, configProjectName);

                    DevModeOperations devModeOps = DevModeOperations.getInstance();
                    ProjectModel targetProjectModel = devModeOps.getWorkspaceModel().getProjectByName(configProjectName);

                    // Validate that we know about the selected project.
                    if (targetProjectModel == null) {
                        throw new IllegalStateException(Messages.getMessage("internal_project_not_found", configProjectName));
                    }

                    launchDevMode(targetProjectModel, configuration, launch, mode);
                } catch (Exception e) {
                    String msg = "An error was detected when configuration was launched" + configuration.getName() + ".";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("launch_config_error", configuration.getName()), e, true);
                    return;
                }
            }

            private void validateProjectsMatch(ILaunchConfiguration configuration, String configProjectName) throws CoreException {
                IProject activeProject = Utils.getActiveProject();
                if (activeProject != null) {
                    assertProjectsMatch(configuration, configProjectName, activeProject);
                }
            }

            private void assertProjectsMatch(ILaunchConfiguration configuration, String configProjectName, IProject selectedProject) throws CoreException {
                DevModeOperations devModeOps = DevModeOperations.getInstance();
                String selectedProjectLocation = selectedProject.getLocation().toOSString();
                ProjectModel selectedProjectModel = devModeOps.getWorkspaceModel().getProjectByLocation(selectedProjectLocation);

                // Validate that we know about the selected project.
                if (selectedProjectModel == null) {
                    throw new IllegalStateException(Messages.getMessage("internal_project_not_found", selectedProject.getName()));
                }

                if (!configProjectName.equals(selectedProjectModel.getName())) {
                    String configurationName = configuration.getName();
                    String msg = Messages.getMessage("config_project_mismatch", configurationName, selectedProjectModel.getName(),
                                                     configProjectName);
                    throw new IllegalStateException(msg);
                }
            }

        });

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    /**
     * Starts dev mode
     * 
     * @param iProject       The project to process.
     * @param iConfiguration The configuration for this start.
     * @param launch         The launch associated with this start
     * @param mode           The operation mode type. Run or debug.
     * 
     * @throws Exception
     */
    private void launchDevMode(ProjectModel targetProjectModel, ILaunchConfiguration iConfiguration, ILaunch launch, String mode) throws Exception {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { targetProjectModel, iConfiguration, mode });
        }

        // If the configuration was not provided by the caller, determine what configuration to use.
        LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
        ILaunchConfiguration configuration = (iConfiguration != null) ? iConfiguration : launchConfigHelper.getLaunchConfiguration(targetProjectModel, mode, RuntimeEnv.LOCAL);

        // Save the time when this configuration was processed.
        launchConfigHelper.saveConfigProcessingTime(configuration);

        // Retrieve configuration data.
        boolean runInContainer = configuration.getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, false);
        boolean runProjectClean = configuration.getAttribute(StartTab.PROJECT_CLEAN, false);
        String configParms = configuration.getAttribute(StartTab.PROJECT_START_PARM, (String) null);
        String javaHomePath = JRETab.resolveJavaHome(configuration);

        // Process the action.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        if (runInContainer) {
            devModeOps.startInContainer(targetProjectModel, configParms, javaHomePath, launch, mode, runProjectClean);
        } else {
            devModeOps.start(targetProjectModel, configParms, javaHomePath, launch, mode, runProjectClean);
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }
}
