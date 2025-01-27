/*******************************************************************************
* Copyright (c) 2022, 2024 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.test.ut;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.openliberty.tools.eclipse.debug.DebugModeHandler;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher.RuntimeEnv;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationHelper;
import io.openliberty.tools.eclipse.ui.launch.StartTab;

/**
 * Unit tests.
 */
public class LibertyPluginUnitTest {

    /** Temporary directory name. */
    public static String TEMP_DIR_NAME = "tempDir";

    /** Temporary directory File object. */
    public static File tempDir = new File(TEMP_DIR_NAME);

    /**
     * Runs before all tests.
     */
    @BeforeAll
    public static void beforeAll() {
        tempDir.mkdir();
    }

    /**
     * Runs after all tests.
     */
    @AfterAll
    public static void afterAll() {
        File[] files = tempDir.listFiles();
        for (File file : files) {
            file.delete();
        }
        tempDir.delete();
    }

    /**
     * Runs before each test.
     */
    @BeforeEach
    public void beforeEach(TestInfo info) {
        System.out.println("INFO: Test " + info.getDisplayName() + " entry: " + java.time.LocalDateTime.now());
    }

    /**
     * Runs after each test.
     */
    @AfterEach
    public void afterEach(TestInfo info) {
        System.out.println("INFO: Test " + info.getDisplayName() + " exit: " + java.time.LocalDateTime.now());
    }

    /**
     * Tests that the debug port is properly read from server.env
     */
    @Test
    public void testReadingDebugPortFromServerEnv() throws Exception {

        DebugModeHandler debugModeHelper = new DebugModeHandler(null);
        try {
            // Test1 . No debug port entry.
            File serverEnv1 = createFile("server.env1", "");
            String port = debugModeHelper.readDebugPortFromServerEnv(serverEnv1.toPath());
            Assertions.assertTrue(port == null, "The resulting list should have returned null as there is debug port entry in file.");

            // Test2 . Single port entry.
            File serverEnv2 = createFile("server.env2", "WLP_DEBUG_ADDRESS=1111");
            String port2 = debugModeHelper.readDebugPortFromServerEnv(serverEnv2.toPath());
            Assertions.assertTrue(port2.equals("1111"), "The resulting list should have returned port 1111. Instead it returned: " + port2);

            // Test1 . Multiple port entries.
            File serverEnv3 = createFile("server.env3", "WLP_DEBUG_ADDRESS=1111", "WLP_DEBUG_ADDRESS=2222", "WLP_DEBUG_ADDRESS=3333");
            String port3 = debugModeHelper.readDebugPortFromServerEnv(serverEnv3.toPath());
            Assertions.assertTrue(port3.equals("3333"), "The resulting list should have returned port 3333. Instead it returned: " + port3);
        } finally {

        }
    }

    /**
     * Tests that run configurations are filtered correctly based on the project, run environment.
     * 
     * @throws Exception
     */
    @Test
    public void testConfigFiltering() throws Exception {
        LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
        List<ILaunchConfiguration> rawCfgList = getDefaultConfigurationList();

        // Test 1. Normal run.
        List<ILaunchConfiguration> filteredListDev = launchConfigHelper
                .filterLaunchConfigurations(rawCfgList.toArray(new ILaunchConfiguration[rawCfgList.size()]), "project1", RuntimeEnv.LOCAL);
        Assertions.assertTrue(filteredListDev.size() == 3,
                "The resulting list should have contained 3 entries. List size: " + filteredListDev.size());
        Assertions.assertTrue(filteredListDev.get(0).getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, true) == false,
                "The run in container value associated with config entry[0] was not false.");
        Assertions.assertTrue(filteredListDev.get(1).getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, true) == false,
                "The run in container value associated with config entry[1] was not false.");
        Assertions.assertTrue(filteredListDev.get(2).getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, true) == false,
                "The run in container value associated with config entry[2] was not false.");

        // test 2. Container run.
        List<ILaunchConfiguration> filteredListDevc = launchConfigHelper.filterLaunchConfigurations(
                rawCfgList.toArray(new ILaunchConfiguration[rawCfgList.size()]), "project1", RuntimeEnv.CONTAINER);
        Assertions.assertTrue(filteredListDevc.size() == 3,
                "The resulting list should have contained 3 entries. Found: " + filteredListDevc.size() + ". List: " + filteredListDevc);
        Assertions.assertTrue(filteredListDevc.get(0).getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, false) == true,
                "The run in container value associated with config entry[0] was not true.");
        Assertions.assertTrue(filteredListDevc.get(1).getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, false) == true,
                "The run in container value associated with config entry[1] was not true.");
        Assertions.assertTrue(filteredListDevc.get(2).getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, false) == true,
                "The run in container value associated with config entry[2] was not true.");
    }

    /**
     * Tests that the run configuration that ran last is returned.
     * 
     * @throws Exception
     */
    @Test
    public void testRetrieveLastRunConfig() throws Exception {
        LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
        List<ILaunchConfiguration> rawCfgList = getDefaultConfigurationList();
        List<ILaunchConfiguration> filteredListDev = launchConfigHelper
                .filterLaunchConfigurations(rawCfgList.toArray(new ILaunchConfiguration[rawCfgList.size()]), "project1", RuntimeEnv.LOCAL);
        Assertions.assertTrue(filteredListDev.size() == 3,
                "The resulting list should have contained 3 entries. List size: " + filteredListDev.size());

        // Test 1. Normal run.
        ILaunchConfiguration lastRunConfigDev = launchConfigHelper.getLastRunConfiguration(filteredListDev);

        String cfgNameFoundDev = lastRunConfigDev.getName();
        String expectedCfgNameDev = "test2";
        Assertions.assertTrue(expectedCfgNameDev.equals(cfgNameFoundDev),
                "The expected configuration of " + expectedCfgNameDev + " was not returned. Configuration returned:: " + cfgNameFoundDev);

        long expectedTimeDev = 1000000000003L;
        long timeFoundDev = Long.valueOf(lastRunConfigDev.getAttribute(StartTab.PROJECT_RUN_TIME, "0"));
        Assertions.assertTrue(timeFoundDev == expectedTimeDev,
                "The configuration found does not contain the expected value of " + expectedTimeDev + ". Time found: " + timeFoundDev);

        // Test 2. Container run.
        List<ILaunchConfiguration> filteredListDevc = launchConfigHelper.filterLaunchConfigurations(
                rawCfgList.toArray(new ILaunchConfiguration[rawCfgList.size()]), "project1", RuntimeEnv.CONTAINER);
        Assertions.assertTrue(filteredListDevc.size() == 3,
                "The resulting list should have contained 3 entries. List size: " + filteredListDevc.size());

        ILaunchConfiguration lastRunConfigDevc = launchConfigHelper.getLastRunConfiguration(filteredListDevc);

        String cfgNameFoundDevc = lastRunConfigDevc.getName();
        String expectedCfgNameDevc = "test6";
        Assertions.assertTrue(expectedCfgNameDevc.equals(cfgNameFoundDevc),
                "The expected configuration of " + expectedCfgNameDevc + " was not returned. Configuration returned:: " + cfgNameFoundDevc);

        long expectedTimeDevc = 1000000000006L;
        long timeFoundDevc = Long.valueOf(lastRunConfigDevc.getAttribute(StartTab.PROJECT_RUN_TIME, "0"));
        Assertions.assertTrue(timeFoundDevc == expectedTimeDevc,
                "The configuration found does not contain the expected value of " + expectedTimeDevc + ". Time found: " + timeFoundDevc);

        // Test 3: Normal run. Configurations with equal minimum time. Configuration with max time expected.
        List<ILaunchConfiguration> filteredListT3Dev = launchConfigHelper
                .filterLaunchConfigurations(rawCfgList.toArray(new ILaunchConfiguration[rawCfgList.size()]), "project2", RuntimeEnv.LOCAL);
        Assertions.assertTrue(filteredListT3Dev.size() == 3,
                "The resulting list should have contained 3 entries. List size: " + filteredListT3Dev.size());

        ILaunchConfiguration lastRunConfigT3Dev = launchConfigHelper.getLastRunConfiguration(filteredListT3Dev);

        String cfgNameFoundT3Dev = lastRunConfigT3Dev.getName();
        String expectedCfgNameT3Dev = "test11";
        Assertions.assertTrue(expectedCfgNameT3Dev.equals(cfgNameFoundT3Dev), "The expected configuration of " + expectedCfgNameT3Dev
                + " was not returned. Configuration returned:: " + cfgNameFoundT3Dev);

        // Test 4: Container run. Configurations with equal max time. One of the max times is returned.
        // In this particular case, is the second entry after the entries are sorted.
        List<ILaunchConfiguration> filteredListT4Devc = launchConfigHelper.filterLaunchConfigurations(
                rawCfgList.toArray(new ILaunchConfiguration[rawCfgList.size()]), "project3", RuntimeEnv.CONTAINER);
        Assertions.assertTrue(filteredListT4Devc.size() == 3,
                "The resulting list should have contained 3 entries. List size: " + filteredListT4Devc.size());

        ILaunchConfiguration lastRunConfigT4Devc = launchConfigHelper.getLastRunConfiguration(filteredListT4Devc);

        String cfgNameFoundT4Devc = lastRunConfigT4Devc.getName();
        String expectedCfgNameT4Devc = "test16";
        Assertions.assertTrue(expectedCfgNameT4Devc.equals(cfgNameFoundT4Devc), "The expected configuration of " + expectedCfgNameT4Devc
                + " was not returned. Configuration returned:: " + cfgNameFoundT4Devc);

        // Test 5: This is the start... case where we do not really know the runtime environment to be used to run dev mode.
        // In this case, it is expected that the configuration that ran last irrespective of runtime environment should be returned.
        List<ILaunchConfiguration> filteredListT5Dev = launchConfigHelper.filterLaunchConfigurations(
                rawCfgList.toArray(new ILaunchConfiguration[rawCfgList.size()]), "project1", RuntimeEnv.UNKNOWN);
        Assertions.assertTrue(filteredListT5Dev.size() == 6,
                "The resulting list should have contained 6 entries. List size: " + filteredListT5Dev.size());

        ILaunchConfiguration lastRunConfigT5Dev = launchConfigHelper.getLastRunConfiguration(filteredListT5Dev);

        String cfgNameFoundT5Dev = lastRunConfigT5Dev.getName();
        String expectedCfgNameT5Dev = "test6";
        Assertions.assertTrue(expectedCfgNameT5Dev.equals(cfgNameFoundT5Dev), "The expected configuration of " + expectedCfgNameT5Dev
                + " was not returned. Configuration returned:: " + cfgNameFoundT5Dev);
    }

    /**
     * Returns a list of launch configurations.
     * 
     * @return a list of launch configurations.
     * 
     * @throws CoreException
     */
    private List<ILaunchConfiguration> getDefaultConfigurationList() throws CoreException {
        ArrayList<ILaunchConfiguration> configList = new ArrayList<ILaunchConfiguration>();
        configList.add(mockLaunchConfiguration(Map.of("name", "test1", StartTab.PROJECT_NAME, "project1", StartTab.PROJECT_RUN_TIME,
                "1000000000001", StartTab.PROJECT_RUN_IN_CONTAINER, false)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test2", StartTab.PROJECT_NAME, "project1", StartTab.PROJECT_RUN_TIME,
                "1000000000003", StartTab.PROJECT_RUN_IN_CONTAINER, false)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test3", StartTab.PROJECT_NAME, "project1", StartTab.PROJECT_RUN_TIME,
                "1000000000002", StartTab.PROJECT_RUN_IN_CONTAINER, false)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test4", StartTab.PROJECT_NAME, "project1", StartTab.PROJECT_RUN_TIME,
                "1000000000004", StartTab.PROJECT_RUN_IN_CONTAINER, true)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test5", StartTab.PROJECT_NAME, "project1", StartTab.PROJECT_RUN_TIME,
                "1000000000005", StartTab.PROJECT_RUN_IN_CONTAINER, true)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test6", StartTab.PROJECT_NAME, "project1", StartTab.PROJECT_RUN_TIME,
                "1000000000006", StartTab.PROJECT_RUN_IN_CONTAINER, true)));

        configList.add(mockLaunchConfiguration(Map.of("name", "test10", StartTab.PROJECT_NAME, "project2", StartTab.PROJECT_RUN_TIME,
                "1000000000010", StartTab.PROJECT_RUN_IN_CONTAINER, false)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test11", StartTab.PROJECT_NAME, "project2", StartTab.PROJECT_RUN_TIME,
                "1000000000011", StartTab.PROJECT_RUN_IN_CONTAINER, false)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test12", StartTab.PROJECT_NAME, "project2", StartTab.PROJECT_RUN_TIME,
                "1000000000010", StartTab.PROJECT_RUN_IN_CONTAINER, false)));

        configList.add(mockLaunchConfiguration(Map.of("name", "test15", StartTab.PROJECT_NAME, "project3", StartTab.PROJECT_RUN_TIME,
                "1000000000011", StartTab.PROJECT_RUN_IN_CONTAINER, true)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test16", StartTab.PROJECT_NAME, "project3", StartTab.PROJECT_RUN_TIME,
                "1000000000011", StartTab.PROJECT_RUN_IN_CONTAINER, true)));
        configList.add(mockLaunchConfiguration(Map.of("name", "test17", StartTab.PROJECT_NAME, "project3", StartTab.PROJECT_RUN_TIME,
                "1000000000010", StartTab.PROJECT_RUN_IN_CONTAINER, true)));

        return configList;
    }

    /**
     * Returns a mocked launch configuration.
     * 
     * @param attributes The attributes that the configuration is to return.
     * 
     * @return A mocked launch configuration.
     * 
     * @throws CoreException
     */
    public static ILaunchConfiguration mockLaunchConfiguration(Map<String, Object> attributes) throws CoreException {
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        when(config.getName()).thenReturn((String) attributes.get("name"));
        when(config.getAttribute(eq(StartTab.PROJECT_NAME), anyString())).thenReturn(((String) attributes.get(StartTab.PROJECT_NAME)));
        when(config.getAttribute(eq(StartTab.PROJECT_RUN_TIME), anyString()))
                .thenReturn(((String) attributes.get(StartTab.PROJECT_RUN_TIME)));
        when(config.getAttribute(eq(StartTab.PROJECT_RUN_IN_CONTAINER), anyBoolean()))
                .thenReturn(((Boolean) attributes.get(StartTab.PROJECT_RUN_IN_CONTAINER)).booleanValue());

        return config;
    }

    /**
     * Creates a temporary file of the specified name in a temp dir location.
     * 
     * @param tempDir The temporary directory where the file is to be created.
     * @param content The array of entries the file is to contain.
     * 
     * @return The server.env File object with the input content.
     * 
     * @throws Exception
     */
    public File createFile(String fileName, String... content) throws Exception {
        File file = null;
        Path filePath = tempDir.toPath().resolve(fileName);

        if (content != null) {
            List<String> lines = Arrays.asList(content);
            Path path = Files.write(filePath, lines);
            file = path.toFile();
        }

        return file;
    }
}
