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
package io.openliberty.tools.eclipse.ui.launch.shortcuts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.ui.launch.MainTab;
import io.openliberty.tools.eclipse.utils.Dialog;
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
        try {
            IProject iProject = Utils.getProjectFromSelection(selection);
            run(iProject, null, mode);
        } catch (Exception e) {
            String msg = "An error was detected while processing the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START
                    + "\" launch shortcut.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(IEditorPart part, String mode) {
        try {
            IProject selectedProject = Utils.getProjectFromPart(part);
            run(selectedProject, null, mode);
        } catch (Exception e) {
            String msg = "An error was detected while processing the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START
                    + "\" launch shortcut.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
        }
    }

    /**
     * Processes the start shortcut action.
     * 
     * @param iProject The project to process.
     * @param mode The operation mode type. Run or debug.
     * 
     * @throws Exception
     */
    public static void run(IProject iProject, ILaunchConfiguration iConfiguration, String mode) throws Exception {
        if (iProject == null) {
            throw new Exception("Invalid project. Be sure to select a project first.");
        }

        // Validate that the project is supported.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        devModeOps.verifyProjectSupport(iProject);

        // If the configuration was not provided by the caller, determine what configuration to use.
        ILaunchConfiguration configuration = (iConfiguration != null) ? iConfiguration : getLaunchConfiguration(iProject, mode, false);

        // Save the time when this configuration was processed.
        saveConfigProcessingTime(configuration);

        // Retrieve configuration data.
        boolean runInContainer = configuration.getAttribute(MainTab.PROJECT_RUN_IN_CONTAINER, false);
        String startParms = configuration.getAttribute(MainTab.PROJECT_START_PARM, (String) null);

        // Process the action.
        if (runInContainer) {
            devModeOps.startInContainer(iProject, startParms);
        } else {
            devModeOps.start(iProject, startParms);
        }
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
    protected static ILaunchConfiguration getLaunchConfiguration(IProject iProject, String mode, boolean container) throws Exception {
        ILaunchConfiguration configuration = null;
        ILaunchManager iLaunchMgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType iLaunchConfigType = iLaunchMgr
                .getLaunchConfigurationType(LaunchConfigurationDelegateLauncher.LAUNCH_CONFIG_TYPE_ID);

        // Find the set of configurations that were used by the currently active project last.
        ILaunchConfiguration[] existingConfigs = iLaunchMgr.getLaunchConfigurations(iLaunchConfigType);

        List<ILaunchConfiguration> matchingConfigList = filterLaunchConfigurations(existingConfigs, iProject.getName(), container);

        switch (matchingConfigList.size()) {
        case 0:
            // Create a new configuration.
            String newName = iLaunchMgr.generateLaunchConfigurationName(iProject.getName());
            ILaunchConfigurationWorkingCopy workingCopy = iLaunchConfigType.newInstance(null, newName);
            workingCopy.setAttribute(MainTab.PROJECT_NAME, iProject.getName());
            workingCopy.setAttribute(MainTab.PROJECT_START_PARM, "");
            workingCopy.setAttribute(MainTab.PROJECT_RUN_IN_CONTAINER, false);
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
    public static List<ILaunchConfiguration> filterLaunchConfigurations(ILaunchConfiguration[] rawConfigList, String projectName,
            boolean container) throws Exception {
        ArrayList<ILaunchConfiguration> matchingConfigList = new ArrayList<>();
        for (ILaunchConfiguration existingConfig : rawConfigList) {
            String configProjName = existingConfig.getAttribute(MainTab.PROJECT_NAME, "");
            if (configProjName.isEmpty()) {
                continue;
            }

            if (projectName.equals(configProjName)) {
                boolean configRanInContainer = existingConfig.getAttribute(MainTab.PROJECT_RUN_IN_CONTAINER, false);
                if (container) {
                    if (configRanInContainer) {
                        matchingConfigList.add(existingConfig);
                    }
                } else {
                    if (!configRanInContainer) {
                        matchingConfigList.add(existingConfig);
                    }
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
    protected static void saveConfigProcessingTime(ILaunchConfiguration configuration) {
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