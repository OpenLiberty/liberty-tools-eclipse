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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
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
        // Processing paths:
        // - Explorer-> Run As-> Run Configurations
        // - Dashboard-> project -> Start...
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        IWorkbench workbench = PlatformUI.getWorkbench();
        Display display = workbench.getDisplay();
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
    }

    /**
     * Returns the configuration to be used by the project associated with the action being processed.
     * 
     * @param iProject The project for which the configuration will be returned. It must not be null.
     * @param mode The launch mode.
     * @param container The indicator of whether or not the caller is running in a container. If true, It allows multiple
     *        configurations associated to a single project to be filtered based on whether or not the configuration was previously
     *        used to run the project in a container.
     * 
     * @return The configuration to be used by the project associated with the action being processed.
     * 
     * @throws Exception
     */
    public static ILaunchConfiguration getLaunchConfiguration(IProject iProject, String mode, RuntimeEnv runtimeEnv) throws Exception {
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        ILaunchConfiguration configuration = null;
        ILaunchManager iLaunchMgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType iLaunchConfigType = iLaunchMgr
                .getLaunchConfigurationType(LaunchConfigurationDelegateLauncher.LAUNCH_CONFIG_TYPE_ID);

        // Find the set of configurations that were used by the currently active project last.
        ILaunchConfiguration[] existingConfigs = iLaunchMgr.getLaunchConfigurations(iLaunchConfigType);

        List<ILaunchConfiguration> matchingConfigList = LaunchConfigurationDelegateLauncher.filterLaunchConfigurations(existingConfigs,
                iProject.getName(), runtimeEnv);

        switch (matchingConfigList.size()) {
            case 0:
                // Create a new configuration.
                String newName = iLaunchMgr.generateLaunchConfigurationName(iProject.getName());
                ILaunchConfigurationWorkingCopy workingCopy = iLaunchConfigType.newInstance(null, newName);
                workingCopy.setAttribute(MainTab.PROJECT_NAME, iProject.getName());
                workingCopy.setAttribute(MainTab.PROJECT_START_PARM, devModeOps.getDashboard().getDefaultStartParameters(iProject));
                workingCopy.setAttribute(MainTab.PROJECT_RUN_IN_CONTAINER, false);
                configuration = workingCopy.doSave();
                break;

            case 1:
                // Return the found configuration.
                configuration = matchingConfigList.get(0);
                break;
            default:
                // Return the configuration that was run last.
                configuration = LaunchConfigurationDelegateLauncher.getLastRunConfiguration(matchingConfigList);
                break;
        }

        return configuration;
    }

    /**
     * Returns a filtered list of launch configurations.
     * 
     * @param rawConfigList The raw list of Liberty associated launch configurations to be filtered.
     * @param projectName The project name to be used as filter.
     * @param container The processing type to be used as filter.
     * 
     * @return A filtered list of launch configurations.
     * 
     * @throws Exception
     */
    public static List<ILaunchConfiguration> filterLaunchConfigurations(ILaunchConfiguration[] rawConfigList, String projectName,
            RuntimeEnv runtimeEnv) throws Exception {
        ArrayList<ILaunchConfiguration> matchingConfigList = new ArrayList<>();
        for (ILaunchConfiguration existingConfig : rawConfigList) {
            String configProjName = existingConfig.getAttribute(MainTab.PROJECT_NAME, "");
            if (configProjName.isEmpty()) {
                continue;
            }

            if (projectName.equals(configProjName)) {
                boolean configRanInContainer = existingConfig.getAttribute(MainTab.PROJECT_RUN_IN_CONTAINER, false);
                if (runtimeEnv == RuntimeEnv.CONTAINER) {
                    if (configRanInContainer) {
                        matchingConfigList.add(existingConfig);
                    }
                } else if ((runtimeEnv == RuntimeEnv.LOCAL)) {
                    if (!configRanInContainer) {
                        matchingConfigList.add(existingConfig);
                    }
                } else if ((runtimeEnv == RuntimeEnv.UNKNOWN)) {
                    matchingConfigList.add(existingConfig);
                } else {
                    String msg = "Invalid Runtime Environment option: " + runtimeEnv;
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg);
                    }
                    throw new Exception(msg);
                }
            }
        }

        return matchingConfigList;

    }

    /**
     * Returns the last run configuration found in the input list of launch configurations.
     * 
     * @param launchConfigList The list of launch configurations.
     * 
     * @return The last run configuration found in the input list of launch configurations.
     */
    public static ILaunchConfiguration getLastRunConfiguration(List<ILaunchConfiguration> launchConfigList) {
        launchConfigList.sort(new Comparator<ILaunchConfiguration>() {

            /**
             * Organizes the items in the descending order. If there are more than one entries with equal value that are considered greater
             * than the others, the one in the first position after the sort is returned in no particular order.
             */
            @Override
            public int compare(ILaunchConfiguration lc1, ILaunchConfiguration lc2) {
                int rc = 0;
                try {
                    long time1 = Long.valueOf(lc1.getAttribute(MainTab.PROJECT_RUN_TIME, "0"));
                    long time2 = Long.valueOf(lc2.getAttribute(MainTab.PROJECT_RUN_TIME, "0"));
                    rc = (time2 > time1) ? 1 : -1;
                } catch (Exception e) {
                    String msg = "An error occurred while trying to determine which configuration ran last. Configuration list: "
                            + launchConfigList;
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                }

                return rc;
            }
        });

        return launchConfigList.get(0);
    }

    /**
     * Persists the configuration processing time in the configuration itself.
     * 
     * @param configuration The configuration being processed.
     */
    public static void saveConfigProcessingTime(ILaunchConfiguration configuration) {
        try {
            ILaunchConfigurationWorkingCopy configWorkingCopy = configuration.getWorkingCopy();
            configWorkingCopy.setAttribute(MainTab.PROJECT_RUN_TIME, String.valueOf(System.currentTimeMillis()));
            configWorkingCopy.doSave();
        } catch (Exception e) {
            // Log it and move on.
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Unable to set time for configuration " + configuration.getName(), e);
            }
        }
    }
}
