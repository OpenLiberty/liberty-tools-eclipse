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
package io.openliberty.tools.eclipse.test.it;

import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.deleteFile;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.isInternalBrowserSupportAvailable;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.isTextInFile;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.onWindows;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.validateApplicationOutcome;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.validateTestReportExists;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginSWTBotGradleTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Dashboard instance.
     */
    static SWTBotView dashboard;

    /**
     * Application name.
     */
    static final String GRADLE_APP_NAME = "liberty-gradle-test-app";

    /**
     * Application name.
     */
    static final String GRADLE_WRAPPER_APP_NAME = "liberty-gradle-test-wrapper-app";

    static String testAppPath;
    static String testWrapperAppPath;

    /**
     * Expected menu items.
     */
    static String[] gradleMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START, DashboardView.APP_MENU_ACTION_START_CONFIG,
            DashboardView.APP_MENU_ACTION_START_IN_CONTAINER, DashboardView.APP_MENU_ACTION_STOP, DashboardView.APP_MENU_ACTION_RUN_TESTS,
            DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT };

    /**
     * Run As configuration menu items.
     */
    static String[] runAsShortcuts = new String[] { LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER, LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT };

    /**
     * Setup.
     * 
     * @throws CoreException
     * @throws InterruptedException
     */
    @BeforeAll
    public static void setup() {

        commonSetup();

        ArrayList<File> projectsToInstall = new ArrayList<File>();
        File mainProject = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME).toFile();
        File wrapperProject = Paths.get("resources", "applications", "gradle", GRADLE_WRAPPER_APP_NAME).toFile();
        projectsToInstall.add(mainProject);
        projectsToInstall.add(wrapperProject);
        importGradleApplications(projectsToInstall);

        // Check basic plugin artifacts are functioning before running tests.
        validateBeforeTestRun();
    }

    /**
     * Makes sure that some basics actions can be performed before running the tests:
     * 
     * <pre>
     * 1. The dashboard can be opened and its content retrieved. 
     * 2. The dashboard contains the expected applications. 
     * 3. The dashboard menu associated with a selected application contains the required actions. 
     * 4. The Run As menu for the respective application contains the required shortcut actions. 
     * 5. The Run As configuration view contains the Liberty entry for creating a configuration.
     * 6. The Debug As configuration view contains the Liberty entry for creating a configuration.
     * </pre>
     */
    public static final void validateBeforeTestRun() {

        Path projPath = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME);
        Path wrapperProjPath = Paths.get("resources", "applications", "gradle", GRADLE_WRAPPER_APP_NAME);
        File projectFile = projPath.toFile();
        File wrapperProjectFile = wrapperProjPath.toFile();
        testAppPath = Paths.get(projectFile.getPath()).toAbsolutePath().toString();
        testWrapperAppPath = Paths.get(projectFile.getPath()).toAbsolutePath().toString();
        dashboard = SWTBotPluginOperations.openDashboardUsingToolbar(bot);

        // Check that the dashboard can be opened and its content retrieved.
        List<String> projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);

        // Check that dashboard contains the expected applications.
        boolean foundApp = false;
        for (String project : projectList) {
            if (GRADLE_APP_NAME.equals(project)) {
                foundApp = true;
                break;
            }
        }
        Assertions.assertTrue(foundApp, () -> "The dashboard does not contain expected application: " + GRADLE_APP_NAME);

        // Check that the menu for the expected application contains the required actions.
        List<String> menuItems = SWTBotPluginOperations.getDashboardItemMenuActions(bot, dashboard, GRADLE_APP_NAME);
        Assertions.assertTrue(menuItems.size() == gradleMenuItems.length, () -> "Gradle application " + GRADLE_APP_NAME
                + " does not contain the expected number of menu items: " + gradleMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(gradleMenuItems)),
                () -> "Gradle application " + GRADLE_APP_NAME + " does not contain the expected menu items: " + gradleMenuItems);

        // Check that the Run As menu contains the expected shortcut
        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, GRADLE_APP_NAME);
        Assertions.assertTrue(runAsMenu != null, "The runAs menu associated with project: " + GRADLE_APP_NAME + " is null.");
        List<String> runAsMenuItems = runAsMenu.menuItems();
        Assertions.assertTrue(runAsMenuItems != null && !runAsMenuItems.isEmpty(),
                "The runAs menu associated with project: " + GRADLE_APP_NAME + " is null or empty.");
        int foundItems = 0;

        for (String expectedItem : runAsShortcuts) {
            for (String item : runAsMenuItems) {
                if (item.contains(expectedItem)) {
                    foundItems++;
                    break;
                }
            }
        }

        Assertions.assertTrue(foundItems == runAsShortcuts.length,
                "The runAs menu associated with project: " + GRADLE_APP_NAME
                        + " does not contain one or more expected entries. Expected number of entries: " + runAsShortcuts.length
                        + "Found entry count: " + foundItems + ". Found menu entries: " + runAsMenuItems);

        // Check that the Run As -> Run Configurations ... contains the Liberty entry in the menu.
        SWTBotPluginOperations.launchConfigurationsDialog(bot, GRADLE_APP_NAME, "run");
        SWTBotTreeItem runAslibertyToolsEntry = SWTBotPluginOperations.getLibertyToolsConfigMenuItem(bot);
        Assertions.assertTrue(runAslibertyToolsEntry != null, "Liberty entry in Run Configurations view was not found.");
        bot.button("Close").click();

        // Check that the Debug As -> Debug Configurations... contains the Liberty entry in the menu.
        SWTBotPluginOperations.launchConfigurationsDialog(bot, GRADLE_APP_NAME, "debug");
        SWTBotTreeItem debugAslibertyToolsEntry = SWTBotPluginOperations.getLibertyToolsConfigMenuItem(bot);
        Assertions.assertTrue(debugAslibertyToolsEntry != null, "Liberty entry in Debug Configurations view was not found.");
        bot.button("Close").click();
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartAction() {
        
        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Gradle");
        
        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, true, testAppPath + "/build");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, false, testAppPath + "/build");

        // unset the preferences
        SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Gradle");
        
        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartWithCustomConfigAction() {
        String mode = "run";

        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Gradle");
        
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, mode);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartConfigDialogWithDashboardAction(bot, dashboard, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);
        SWTBotPluginOperations.createNewLibertyConfiguration(bot);
        SWTBotPluginOperations.setLibertyConfigParms(bot, "--hotTests");
        SWTBotPluginOperations.runLibertyConfiguration(bot, mode);

        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, true, testAppPath + "/build");

        try {
            // Validate that the test reports were generated.
            validateTestReportExists(pathToTestReport);
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, false, testAppPath + "/build");
            
            // unset the preferences
            SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Gradle");
            
            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the start, run tests, view test report, and stopdashboard actions.
     */
    @Test
    public void testDashboardActions() {
        
        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Gradle");
        
        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "Test report file: " + pathToTestReport + " was not be deleted.");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, true, testAppPath + "/build");

        try {
            // Run Tests.
            SWTBotPluginOperations.launchRunTestsWithDashboardAction(bot, dashboard, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);

            // Validate that the reports were generated and the the browser editor was launched.
            validateTestReportExists(pathToTestReport);
            if (isInternalBrowserSupportAvailable()) {
                SWTBotPluginOperations.launchViewTestReportWithDashboardAction(bot, dashboard, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);
            }
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, false, testAppPath + "/build");

            // set the preferences
            SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Gradle");
            
            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the refresh action on the dashboard's toolbar.
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    @Disabled("Issue 58")
    @Test
    public void testDashboardRefreshAction() throws IOException, InterruptedException {
        // Get the list of entries on the dashboard and verify the expected number found.
        List<String> projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
        boolean gradleAppFound = false;
        for (String project : projectList) {
            if (GRADLE_APP_NAME.equals(project)) {
                gradleAppFound = true;
            }
        }
        Assertions.assertTrue(gradleAppFound, () -> "The gradle test app was not found.");

        Path original = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME, "build.gradle").toAbsolutePath();
        Path original_backup = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME, "build.gradle_backup").toAbsolutePath();
        Path updated = Paths.get("resources", "files", "apps", "gradle", GRADLE_APP_NAME, "build.gradle").toAbsolutePath();

        try {
            // Move build.gradle files
            Files.copy(original, original_backup, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(updated, original, StandardCopyOption.REPLACE_EXISTING);

            // Refresh
            SWTBotPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is found.
            projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
            gradleAppFound = false;
            for (String project : projectList) {
                if (GRADLE_APP_NAME.equals(project)) {
                    gradleAppFound = true;
                }
            }
            Assertions.assertFalse(gradleAppFound, () -> "The gradle test app was found.");

        } finally {

            // Reset build.gradle files
            if (onWindows()) {
                // Windows may hold a lock on the file, so retry a few times
                int count = 0;
                while (true) {
                    try {
                        Files.copy(original_backup, original, StandardCopyOption.REPLACE_EXISTING);
                        Files.delete(original_backup);
                        break;
                    } catch (Exception e) {
                        System.out.println("Waiting for Windows file to delete.........");
                        Thread.sleep(3000);
                        if (++count == 50)
                            throw e;
                    }
                }
            } else {
                Files.copy(original_backup, original, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(original_backup);
            }
            Files.delete(original_backup);

            // Validate that the editor was correctly updated.
            Assertions.assertTrue(isTextInFile(original.toString(), "liberty-gradle-plugin"),
                    "The build.gradle file does not contain the Liberty Gradle plugin");

            // Refresh
            SWTBotPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is found.
            projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
            gradleAppFound = false;
            for (String project : projectList) {
                if (GRADLE_APP_NAME.equals(project)) {
                    gradleAppFound = true;
                }
            }
            Assertions.assertTrue(gradleAppFound, () -> "The gradle test app was not found.");

        }
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (default) ->
     * Run.
     */
    @Test
    public void testStartWithDefaultRunAsConfig() {

        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Gradle");
        
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDefaultConfig(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, true, testAppPath + "/build");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, false, testAppPath + "/build");

        // unset the preferences
        SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Gradle");
        
        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (customized)
     * -> Run.
     */
    @Test
    public void testStartWithCustomRunAsConfig() {

        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Gradle");
        
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartWithCustomConfig(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run", "--hotTests");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, true, testAppPath + "/build");

        try {
            // Validate that the test reports were generated.
            validateTestReportExists(pathToTestReport);
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, false, testAppPath + "/build");

            // unset the preferences
            SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Gradle");
            
            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the start, run tests, view test report, and stop run as shortcut actions.
     */
    @Test
    public void testRunAsShortcutActions() {

        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Gradle");
        
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "Test report file: " + pathToTestReport + " was not be deleted.");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithRunDebugAsShortcut(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, true, testAppPath + "/build");

        try {
            // Run Tests.
            SWTBotPluginOperations.launchRunTestspWithRunDebugAsShortcut(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");

            // Validate that the reports were generated and the the browser editor was launched.
            validateTestReportExists(pathToTestReport);
            if (isInternalBrowserSupportAvailable()) {
                SWTBotPluginOperations.launchViewTestReportWithRunDebugAsShortcut(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME);
            }
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, "run");
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? GRADLE_WRAPPER_APP_NAME : GRADLE_APP_NAME, false, testAppPath + "/build");
            
            // unset the preferences
            SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Gradle");
            
            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the start action initiated through: project -> Debug As -> Debug Configurations -> Liberty -> New configuration
     * (customized) -> Run.
     */
    @Test
    public void testStartWithCustomDebugAsConfig() {
        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartWithCustomConfig(bot, GRADLE_APP_NAME, "debug", "--hotTests");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        try {
            // Validate that the test reports were generated.
            validateTestReportExists(pathToTestReport);

            // At the moment this check is just too unreliable because the configuration dialog contains different
            // sections that maybe in focus at different times. This makes it difficult to predictably get the panel
            // we are after. We need to find a way to focus on the main dialog. We should also cleanup the configurations.
            // Commenting this code out for now.
            //
            // Validate that a remote java application configuration was created and is named after the application.
            // validateRemoteJavaAppCreation(GRADLE_APP_NAME);
        } finally {
            // Stop dev mode using the Run As stop command.
            SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, GRADLE_APP_NAME, "run");
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(GRADLE_APP_NAME, false, testAppPath + "/build");

            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the start/stop debug as shortcut actions.
     */
    @Test
    public void testStartWithDebugAsShortcut() {
        // Start dev mode.
        SWTBotPluginOperations.launchStartWithRunDebugAsShortcut(bot, GRADLE_APP_NAME, "debug");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // At the moment this check is just too unreliable because the configuration dialog contains different
        // sections that maybe in focus at different times. This makes it difficult to predictably get the panel
        // we are after. We need to find a way to focus on the main dialog. We should also cleanup the configurations.
        // Commenting this code out for now.
        //
        // Validate that a remote java application configuration was created and is named after the application.
        // validateRemoteJavaAppCreation(GRADLE_APP_NAME);

        // Stop dev mode using the Run As stop command.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, GRADLE_APP_NAME, "run");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(GRADLE_APP_NAME, false, testAppPath + "/build");

        // Close the terminal.
        terminal.close();
    }
}
