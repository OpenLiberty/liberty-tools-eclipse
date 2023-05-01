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
package io.openliberty.tools.eclipse.test.it;

import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.*;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.LibertyNature;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.Option;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
@Disabled
public class dLibertyPluginSWTBotGradleTest extends AbstractLibertyPluginSWTBotTest {

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

    static ArrayList<File> projectsToInstall = new ArrayList<File>();

    /**
     * Expected menu items.
     */
    static String[] gradleMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START, DashboardView.APP_MENU_ACTION_START_CONFIG,
            DashboardView.APP_MENU_ACTION_START_IN_CONTAINER, DashboardView.APP_MENU_ACTION_DEBUG,
            DashboardView.APP_MENU_ACTION_DEBUG_CONFIG, DashboardView.APP_MENU_ACTION_DEBUG_IN_CONTAINER,
            DashboardView.APP_MENU_ACTION_STOP, DashboardView.APP_MENU_ACTION_RUN_TESTS,
            DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT };

    /**
     * Run As configuration menu items.
     */
    static String[] runAsShortcuts = new String[] { LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER, LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT };

    /**
     * Debug As configuration menu items.
     */
    static String[] debugAsShortcuts = new String[] { LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER };

    /**
     * Setup.
     * 
     * @throws CoreException
     * @throws InterruptedException
     */
    @BeforeAll
    public static void setup() {

        commonSetup();

        File mainProject = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME).toFile();
        File wrapperProject = Paths.get("resources", "applications", "gradle", GRADLE_WRAPPER_APP_NAME).toFile();
        projectsToInstall.add(mainProject);
        projectsToInstall.add(wrapperProject);

        // Maybe redundant but we really want to cleanup. We really want to
        // avoid wasting time debugging tricky differences in behavior because of a dirty re-run
        for (File p : projectsToInstall) {
            cleanupProject(p.toString());
        }

        importGradleApplications(projectsToInstall);

        // Check basic plugin artifacts are functioning before running tests.
        validateBeforeTestRun();

        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Gradle");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();

    }

    @AfterAll
    public static void cleanup() {
        for (File p : projectsToInstall) {
            cleanupProject(p.toString());
        }
        SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Gradle");
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
        File projectFile = projPath.toFile();

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
        int foundRunAsItems = 0;

        for (String expectedItem : runAsShortcuts) {
            for (String item : runAsMenuItems) {
                if (item.contains(expectedItem)) {
                    foundRunAsItems++;
                    break;
                }
            }
        }

        Assertions.assertTrue(foundRunAsItems == runAsShortcuts.length,
                "The runAs menu associated with project: " + GRADLE_APP_NAME
                        + " does not contain one or more expected entries. Expected number of entries: " + runAsShortcuts.length
                        + "Found entry count: " + foundRunAsItems + ". Found menu entries: " + runAsMenuItems);

        // Check that the Debug As menu contains the expected shortcut
        SWTBotMenu debugAsMenu = SWTBotPluginOperations.getAppDebugAsMenu(bot, GRADLE_APP_NAME);
        Assertions.assertTrue(debugAsMenu != null, "The debugAs menu associated with project: " + GRADLE_APP_NAME + " is null.");
        List<String> debugAsMenuItems = debugAsMenu.menuItems();
        Assertions.assertTrue(debugAsMenuItems != null && !debugAsMenuItems.isEmpty(),
                "The debugAs menu associated with project: " + GRADLE_APP_NAME + " is null or empty.");
        int foundDebugAsItems = 0;

        for (String expectedItem : debugAsShortcuts) {
            for (String item : debugAsMenuItems) {
                if (item.contains(expectedItem)) {
                    foundDebugAsItems++;
                    break;
                }
            }
        }

        Assertions.assertTrue(foundDebugAsItems == debugAsShortcuts.length,
                "The debugAs menu associated with project: " + GRADLE_APP_NAME
                        + " does not contain one or more expected entries. Expected number of entries: " + debugAsShortcuts.length
                        + "Found entry count: " + foundDebugAsItems + ". Found menu entries: " + debugAsMenuItems);

        // Check that the Run As -> Run Configurations ... contains the Liberty entry in the menu.
        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(GRADLE_APP_NAME);
        try {
            SWTBotTreeItem runAslibertyToolsEntry = getLibertyTreeItem(configShell);
            Assertions.assertTrue(runAslibertyToolsEntry != null, "Liberty entry in Run Configurations view was not found.");
        } finally {
            go("Close", configShell);
        }

        // Check that the Debug As -> Debug Configurations... contains the Liberty entry in the menu.
        Shell debugShell = launchDebugConfigurationsDialogFromMenu();
        try {
            SWTBotTreeItem debugAslibertyToolsEntry = getLibertyTreeItem(debugShell);
            Assertions.assertTrue(debugAslibertyToolsEntry != null, "Liberty entry in Debug Configurations view was not found.");
        } finally {
            go("Close", debugShell);
        }
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartAction() {

        // Start dev mode.
        SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
        terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the debug menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardDebugAction() {

        // Start dev mode.
        SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
        terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartWithCustomConfigAction() {

        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START_CONFIG);

        Shell configShell = (Shell) findGlobal("Run Configurations", Option.factory().widgetClass(Shell.class).build());
        TreeItem libertyConfigTree = (TreeItem) find(SWTBotPluginOperations.LAUNCH_CONFIG_LIBERTY_MENU_NAME, configShell);

        context(libertyConfigTree, "New Configuration");
        set("Start parameters:", "--hotTests");
        go("Run", configShell);

        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
            terminal.show();

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the debug with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardDebugWithCustomConfigAction() {
        String mode = "debug";

        // Delete any previously created configs.
    	deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG_CONFIG);

        Shell configShell = (Shell) findGlobal("Debug Configurations", Option.factory().widgetClass(Shell.class).build());
        TreeItem libertyConfigTree = (TreeItem) find(SWTBotPluginOperations.LAUNCH_CONFIG_LIBERTY_MENU_NAME, configShell);

        context(libertyConfigTree, "New Configuration");
        set("Start parameters:", "--hotTests");
        go("Debug", configShell);

        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
            terminal.show();

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the start, run tests, view test report, and stopdashboard actions.
     */
    @Test
    public void testDashboardActions() {

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "Test report file: " + pathToTestReport + " was not be deleted.");

        // Start dev mode.
        SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Run Tests.
            SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_RUN_TESTS);

            // Validate that the reports were generated and the the browser editor was launched.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
            if (LibertyPluginTestUtils.isInternalBrowserSupportAvailable()) {
                SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT);
            }
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchDashboardAction(bot, GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
//            terminal.show();

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

            // Close the terminal.
 //           terminal.close();
        }
    }

    /**
     * Tests that a non-Liberty project can be manually be categorized to be Liberty project. This test also tests the refresh
     * function.
     * 
     * @throws Exception
     */
    @Test
    @Disabled("Issue 232")
    public void testAddingProjectToDashboardManually() throws Exception {
        // Update the application .project file to remove the liberty nature if it exists. and rename the server.xml
        IProject iProject = LibertyPluginTestUtils.getProject(GRADLE_APP_NAME);
        String projectName = iProject.getName();

        Project.removeNature(iProject, LibertyNature.NATURE_ID);

        // Rename the server.xml file.
        Path originalPath = Paths
                .get("resources", "applications", "gradle", "liberty-gradle-test-app", "src", "main", "liberty", "config", "server.xml")
                .toAbsolutePath();
        Path renamedPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app", "src", "main", "liberty", "config",
                "server.xml.renamed").toAbsolutePath();

        File originalFile = originalPath.toFile();
        Assertions.assertTrue(originalFile.exists(), () -> "The server.xml for project " + projectName
                + " should exist, but it could not be found at this location: " + originalPath);

        Files.copy(originalPath, renamedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.delete(originalPath);

        File renamedFile = renamedPath.toFile();
        Assertions.assertTrue(renamedFile.exists(), () -> "The server.xml for project " + projectName
                + " should have been renamed to server.xml.renamed. The renamed file does not exist at this location: " + renamedPath);

        Assertions.assertTrue(!originalFile.exists(), () -> "The server.xml for project " + projectName
                + " should no longer exist because it was renamed. File still exists at this location: " + originalPath);

        Assertions.assertTrue(iProject.getDescription().hasNature(LibertyNature.NATURE_ID) == false,
                () -> "The nature ID should have been removed, but it is still present.");

        try {
            // Refresh the project through the explorer view to pick up the nature removal.
            SWTBotPluginOperations.refreshProjectUsingExplorerView(GRADLE_APP_NAME);

            // Refresh the dashboard.
            SWTBotPluginOperations.refreshDashboard(bot);

            // Make sure the application is no longer listed in the dashboard.
            List<String> projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
            boolean gradleAppFound = false;
            for (String project : projectList) {
                if (GRADLE_APP_NAME.equals(project)) {
                    gradleAppFound = true;
                    break;
                }
            }

            Assertions.assertTrue(!gradleAppFound, () -> "Project " + projectName + " should not be listed in the dashboard.");

            // Add the project nature manually.
            SWTBotPluginOperations.enableLibertyTools(GRADLE_APP_NAME);

            // Refresh the project through the explorer view to pick up the nature removal.
            SWTBotPluginOperations.refreshProjectUsingExplorerView(GRADLE_APP_NAME);

            // Refresh the dashboard.
            SWTBotPluginOperations.refreshDashboard(bot);

            // Make sure the application is listed in the dashboard.
            List<String> newProjectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
            boolean newGradleAppFound = false;
            for (String project : newProjectList) {
                if (GRADLE_APP_NAME.equals(project)) {
                    newGradleAppFound = true;
                    break;
                }
            }
            Assertions.assertTrue(newGradleAppFound, () -> "Project " + projectName + " should be listed in the dashboard.");

        } finally {
            // Rename server.xml.renamed to server.xml. The nature should have already been added.
            Files.copy(renamedPath, originalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.delete(renamedPath);
            // Files.move(renamedPath, originalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            Assertions.assertTrue(!renamedFile.exists(), () -> "File server.xml.renamed for project " + projectName
                    + " should have been renamed to server.xml, but it was found at this location: " + renamedPath);
            Assertions.assertTrue(originalFile.exists(), () -> "The server.xml for project " + projectName
                    + " should exist, but it could not be found at this location: " + originalPath);
        }
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (default) ->
     * Run.
     */
    @Test
    public void testStartWithDefaultRunAsConfig() {

        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDefaultRunConfigFromAppRunAs(GRADLE_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithRunAsShortcut(GRADLE_APP_NAME);
        terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (customized)
     * -> Run.
     */
    @Test
    public void testStartWithCustomRunAsConfig() {

        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartWithNewCustomRunConfig(GRADLE_APP_NAME, "--hotTests");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithRunAsShortcut(GRADLE_APP_NAME);
            terminal.show();

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the start, run tests, view test report, and stop run as shortcut actions.
     */
    @Test
    public void testRunAsShortcutActions() {

        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "Test report file: " + pathToTestReport + " was not be deleted.");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithRunAsShortcut(GRADLE_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Run Tests.
            SWTBotPluginOperations.launchRunTestsWithRunAsShortcut(GRADLE_APP_NAME);

            // Validate that the reports were generated and the the browser editor was launched.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
            if (LibertyPluginTestUtils.isInternalBrowserSupportAvailable()) {
                SWTBotPluginOperations.launchViewTestReportWithRunDebugAsShortcut(bot, GRADLE_APP_NAME);
            }
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithRunAsShortcut(GRADLE_APP_NAME);
            terminal.show();

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

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
        // Delete any previously created configs.
    	deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartWithNewCustomDebugConfig(GRADLE_APP_NAME, "--hotTests");
        goGlobal("Terminal");
        //SWTBotView terminal = bot.viewByTitle("Terminal");
        //terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);

            // Validate that a remote java application configuration was created and is named after the application.
            validateRemoteJavaAppCreation(GRADLE_APP_NAME);
        } finally {
            // Stop dev mode using the Run As stop command.
            SWTBotPluginOperations.launchStopWithRunAsShortcut(GRADLE_APP_NAME);
            //terminal.show();

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

            // Close the terminal.
            //terminal.close();
        }
    }

    /**
     * Tests the start/stop debug as shortcut actions.
     */
    @Test
    public void testStartWithDebugAsShortcut() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDebugAsShortcut(GRADLE_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        // Validate that a remote java application configuration was created and is named after the application.
        validateRemoteJavaAppCreation(GRADLE_APP_NAME);

        // Stop dev mode using the Run As stop command.
        SWTBotPluginOperations.launchStopWithRunAsShortcut(GRADLE_APP_NAME);
        terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests that the default JRE set in the project's java build path matches what is marked as default in the Liberty Tools
     * configuration JRE tab.
     */
    @Test
    public void testDefaultJRECompliance() {
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(GRADLE_APP_NAME);

        try {
            TreeItem libertyConfigTree = (TreeItem) find(SWTBotPluginOperations.LAUNCH_CONFIG_LIBERTY_MENU_NAME,
                    configShell);

            context(libertyConfigTree, "New Configuration");

            SWTBotPluginOperations.openJRETab(bot);
            String buildPathJRE = LibertyPluginTestUtils.getJREFromBuildpath(testAppPath);

            Assertions.assertTrue(buildPathJRE != null,
                    () -> "Unable to find the JRE configured in the project's build path (.classpath).");

            SWTBotCombo comboJREBox = SWTBotPluginOperations.getComboTextBoxWithTextPrefix(bot, buildPathJRE);

            Assertions.assertTrue(comboJREBox != null,
                    () -> "The java installation shown on the Liberty run configurations JRE tab does not contain the Java installation configured on project's the build path (claspath):"
                            + buildPathJRE);
            Assertions.assertTrue(comboJREBox.isEnabled(),
                    () -> "The JRE tab box showing Java installation \" + buildPathJRE + \" is not selected.");
        } finally {
            go("Apply", configShell);
            go("Close", configShell);
        }
    }
}
