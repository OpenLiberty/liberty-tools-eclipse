/*******************************************************************************
* Copyright (c) 2022, 2023 IBM Corporation and others.
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
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher.RuntimeEnv;

public class LaunchConfigurationHelper {

    /** This class instance. */
    private static LaunchConfigurationHelper instance;

    /**
     * Returns the instance of this class.
     * 
     * @return The instance of this class.
     */
    public static LaunchConfigurationHelper getInstance() {
        return (instance == null) ? new LaunchConfigurationHelper() : instance;
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
    public ILaunchConfiguration getLaunchConfiguration(IProject iProject, String mode, RuntimeEnv runtimeEnv) throws Exception {
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        ILaunchConfiguration configuration = null;
        ILaunchManager iLaunchMgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType iLaunchConfigType = iLaunchMgr
                .getLaunchConfigurationType(LaunchConfigurationDelegateLauncher.LAUNCH_CONFIG_TYPE_ID);

        // Find the set of configurations that were used by the currently active project last.
        ILaunchConfiguration[] existingConfigs = iLaunchMgr.getLaunchConfigurations(iLaunchConfigType);

        List<ILaunchConfiguration> matchingConfigList = filterLaunchConfigurations(existingConfigs, iProject.getName(), runtimeEnv);

        switch (matchingConfigList.size()) {
            case 0:
                // Create a new configuration.
                String newName = iLaunchMgr.generateLaunchConfigurationName(iProject.getName());
                ILaunchConfigurationWorkingCopy workingCopy = iLaunchConfigType.newInstance(null, newName);
                workingCopy.setAttribute(StartTab.PROJECT_NAME, iProject.getName());
                workingCopy.setAttribute(StartTab.PROJECT_START_PARM, devModeOps.getProjectModel().getDefaultStartParameters(iProject));
                workingCopy.setAttribute(StartTab.PROJECT_PRE_START_GOALS, (String) null);

                workingCopy.setAttribute(StartTab.PROJECT_LAUNCH_COMMAND,
                        devModeOps.getProjectModel().getDefaultStartCommand(iProject, runtimeEnv) + " "
                                + devModeOps.getProjectModel().getDefaultStartParameters(iProject));

                // default to 'false', no container
                boolean runInContainer = runtimeEnv.equals(RuntimeEnv.CONTAINER);
                workingCopy.setAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, runInContainer);

                String defaultJavaDef = JRETab.getDefaultJavaFromBuildPath(iProject);
                if (defaultJavaDef != null) {
                    workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH, defaultJavaDef);
                }

                configuration = workingCopy.doSave();
                break;
            case 1:
                // Return the found configuration.
                configuration = matchingConfigList.get(0);
                break;
            default:
                // Return the configuration that was run last.
                configuration = getLastRunConfiguration(matchingConfigList);
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
    public List<ILaunchConfiguration> filterLaunchConfigurations(ILaunchConfiguration[] rawConfigList, String projectName,
            RuntimeEnv runtimeEnv) throws Exception {
        ArrayList<ILaunchConfiguration> matchingConfigList = new ArrayList<>();
        for (ILaunchConfiguration existingConfig : rawConfigList) {
            String configProjName = existingConfig.getAttribute(StartTab.PROJECT_NAME, "");
            if (configProjName.isEmpty()) {
                continue;
            }

            if (projectName.equals(configProjName)) {
                boolean configRanInContainer = existingConfig.getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, false);
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
    public ILaunchConfiguration getLastRunConfiguration(List<ILaunchConfiguration> launchConfigList) {
        launchConfigList.sort(new Comparator<ILaunchConfiguration>() {

            /**
             * Organizes the items in the descending order. If there are more than one entries with equal value that are considered greater
             * than the others, the one in the first position after the sort is returned in no particular order.
             */
            @Override
            public int compare(ILaunchConfiguration lc1, ILaunchConfiguration lc2) {
                int rc = 0;
                try {
                    long time1 = Long.valueOf(lc1.getAttribute(StartTab.PROJECT_RUN_TIME, "0"));
                    long time2 = Long.valueOf(lc2.getAttribute(StartTab.PROJECT_RUN_TIME, "0"));
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
    public void saveConfigProcessingTime(ILaunchConfiguration configuration) {
        try {
            ILaunchConfigurationWorkingCopy configWorkingCopy = configuration.getWorkingCopy();
            configWorkingCopy.setAttribute(StartTab.PROJECT_RUN_TIME, String.valueOf(System.currentTimeMillis()));
            configWorkingCopy.doSave();
        } catch (Exception e) {
            // Log it and move on.
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Unable to set time for configuration " + configuration.getName(), e);
            }
        }
    }

    /**
     * Returns the configuration name based on the input name. If the input base name matches an existing configuration name, the base
     * name is appended with a space and a decimal counter surrounded by parenthesis. If a decimal counter enclosed by parenthesis
     * already exists, the counter is incremented; however, if the counter is not a decimal number, the base name is appended with
     * (1).
     * 
     * @param baseName The base configuration name.
     * 
     * @return The configuration name based on the input name.
     * 
     * @throws CoreException
     */
    public String buildConfigurationName(String baseName) throws CoreException {
        String configName = baseName.trim();
        ILaunchManager iLaunchManager = DebugPlugin.getDefault().getLaunchManager();
        if (iLaunchManager.isExistingLaunchConfigurationName(configName)) {
            if (configName.contains(" (") && configName.contains(")")) {
                int start = configName.indexOf("(") + 1;
                int end = configName.indexOf(")");

                String count = configName.substring(start, end);
                if (count.matches("\\d+")) {
                    int newCount = Integer.valueOf(count).intValue() + 1;
                    configName = configName.replace(count, String.valueOf(newCount));
                } else {
                    configName += " (1)";
                }

            } else {
                configName += " (1)";
            }
        }

        return configName;
    }
}
