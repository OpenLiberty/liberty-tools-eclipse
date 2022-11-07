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
                    IProject iProject = Utils.getActiveProject();
                    if (iProject == null) {
                        iProject = devModeOps.getSelectedDashboardProject();
                    }

                    StartAction.run(iProject, configuration, mode);
                } catch (Exception e) {
                    String msg = "An error was detected while launching configuration " + configuration.getName() + ".";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(msg, e, true);
                    return;
                }
            }
        });

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }
}
