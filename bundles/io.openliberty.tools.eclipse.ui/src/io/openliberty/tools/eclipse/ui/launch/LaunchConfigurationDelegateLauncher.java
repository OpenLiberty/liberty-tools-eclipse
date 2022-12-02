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
package io.openliberty.tools.eclipse.ui.launch;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
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
import io.openliberty.tools.eclipse.ui.launch.shortcuts.StartAction;
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
     * {@inheritDocs}
     */
    @Override
    public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { configuration, mode, launch, monitor });
        }

        // Processing paths:
        // - Explorer-> Run As-> Run Configurations
        // - Dashboard-> project -> Start...
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        IWorkbench workbench = PlatformUI.getWorkbench();
        Display display = workbench.getDisplay();

        // Launch dev mode.
        display.syncExec(new Runnable() {
            public void run() {
                try {
                    validateProjectsMatch(configuration);

                    String configProjectName = configuration.getAttribute(StartTab.PROJECT_NAME, (String) null);
                    IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
                    IProject configProject = root.getProject(configProjectName);

                    StartAction.run(configProject, configuration, mode);

                } catch (Exception e) {
                    String msg = "An error was detected while launching configuration " + configuration.getName() + ".";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(msg, e, true);
                    return;
                }
            }

            private void validateProjectsMatch(ILaunchConfiguration configuration) throws CoreException {
                IProject activeProject = Utils.getActiveProject();
                if (activeProject != null) {
                    assertProjectsMatch(configuration, activeProject);
                }
            }

            private void assertProjectsMatch(ILaunchConfiguration configuration, IProject selectedProject) throws CoreException {
                String configProjectName = configuration.getAttribute(StartTab.PROJECT_NAME, (String) null);

                if (!configProjectName.equals(selectedProject.getName())) {
                    String configurationName = configuration.getName();
                    String msg = "The selected  Run/Debug configuration '" + configurationName
                            + "' cannot be used to run selected project '" + selectedProject.getName()
                            + ", because the configuration is associated with project '" + configProjectName
                            + "'. You can create a new configuration, or pick an existing configuration associated with the selected project.";
                    throw new IllegalStateException(msg);
                }
            }

        });

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }
}
