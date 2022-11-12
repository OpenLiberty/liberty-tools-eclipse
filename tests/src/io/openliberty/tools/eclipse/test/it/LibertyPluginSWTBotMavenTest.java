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
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.validateApplicationOutcome;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.validateTestReportExists;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.validateWrapperInProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.CommandBuilder;
import io.openliberty.tools.eclipse.LibertyNature;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.test.it.utils.DisabledOnMac;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginSWTBotMavenTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Application name.
     */
    static final String MVN_APP_NAME = "liberty.maven.test.app";

    /**
     * Wrapper Application name.
     */
    static final String MVN_WRAPPER_APP_NAME = "liberty.maven.test.wrapper.app";

    /**
     * Test app relative path.
     */
    static final Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");

    /**
     * Test app relative path.
     */
    static final Path wrapperProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-wrapper-app");

    /**
     * Expected menu items.
     */
    static String[] mvnMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START, DashboardView.APP_MENU_ACTION_START_CONFIG,
            DashboardView.APP_MENU_ACTION_START_IN_CONTAINER, DashboardView.APP_MENU_ACTION_STOP, DashboardView.APP_MENU_ACTION_RUN_TESTS,
            DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT, DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT };

    /**
     * Run As configuration menu items.
     */
    static String[] runAsShortcuts = new String[] { LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER, LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_UT_REPORT, };

    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() {

        commonSetup();

        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
        ArrayList<String> projectPaths = new ArrayList<String>();
        projectPaths.add(projectPath.toString());
        projectPaths.add(wrapperProjectPath.toString());
        importMavenProjects(workspaceRoot, projectPaths);

        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Maven");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();

        // Check basic plugin artifacts are functioning before running tests.
        validateBeforeTestRun();
    }

    @AfterAll
    public static void cleanup() {
        SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Maven");
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
        dashboard = SWTBotPluginOperations.openDashboardUsingToolbar(bot);

        // Check that the dashboard can be opened and its content retrieved.
        List<String> projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);

        // Check that dashboard contains the expected applications.
        boolean foundApp = false;
        for (String project : projectList) {
            if (MVN_APP_NAME.equals(project)) {
                foundApp = true;
                break;
            }
        }
        Assertions.assertTrue(foundApp, () -> "The dashboard does not contain expected application: " + MVN_APP_NAME);

        // Check that the menu that the application in the dashboard contains the required actions.
        List<String> menuItems = SWTBotPluginOperations.getDashboardItemMenuActions(bot, dashboard, MVN_APP_NAME);
        Assertions.assertTrue(menuItems.size() == mvnMenuItems.length,
                () -> "Maven application " + MVN_APP_NAME + " does not contain the expected number of menu items: " + mvnMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(mvnMenuItems)),
                () -> "Maven application " + MVN_APP_NAME + " does not contain the expected menu items: " + mvnMenuItems);

        // Check that the Run As menu contains the expected shortcut
        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, MVN_APP_NAME);
        Assertions.assertTrue(runAsMenu != null, "The runAs menu associated with project: " + MVN_APP_NAME + " is null.");
        List<String> runAsMenuItems = runAsMenu.menuItems();
        Assertions.assertTrue(runAsMenuItems != null && !runAsMenuItems.isEmpty(),
                "The runAs menu associated with project: " + MVN_APP_NAME + " is null or empty.");
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
                "The runAs menu associated with project: " + MVN_APP_NAME
                        + " does not contain one or more expected entries. Expected number of entries: " + runAsShortcuts.length
                        + "Found entry count: " + foundItems + ". Found menu entries: " + runAsMenuItems);

        // Check that the Run As -> Run Configurations... contains the Liberty entry in the menu.
        SWTBotPluginOperations.launchConfigurationsDialog(bot, MVN_APP_NAME, "run");
        SWTBotTreeItem runAslibertyToolsEntry = SWTBotPluginOperations.getLibertyToolsConfigMenuItem(bot);
        Assertions.assertTrue(runAslibertyToolsEntry != null, "Liberty entry in Run Configurations view was not found.");
        bot.button("Close").click();

        // Check that the Debug As -> Debug Configurations... contains the Liberty entry in the menu.
        SWTBotPluginOperations.launchConfigurationsDialog(bot, MVN_APP_NAME, "debug");
        SWTBotTreeItem debugAslibertyToolsEntry = SWTBotPluginOperations.getLibertyToolsConfigMenuItem(bot);
        Assertions.assertTrue(debugAslibertyToolsEntry != null, "Liberty entry in Debug Configurations view was not found.");
        bot.button("Close").click();
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testLibertyConfigurationTabsExist() {
        SWTBotPluginOperations.launchConfigurationsDialog(bot, MVN_APP_NAME, "run");
        try {
            SWTBotPluginOperations.createNewLibertyConfiguration(bot);
            Assertions.assertTrue(bot.cTabItem("Start").isVisible(), "Liberty Start tab not visible.");
            Assertions.assertTrue(bot.cTabItem("JRE").isVisible(), "Liberty JRE tab not visible.");
        } finally {
            bot.button("Close").click();
        }
    }

    @Test
    @DisabledOnMac
    public void testMavenCommandAssembly() throws IOException, InterruptedException {

        IProject iProject = LibertyPluginTestUtils.getProject(MVN_APP_NAME);
        String projPath = iProject.getLocation().toOSString();

        String localMvnCmd = LibertyPluginTestUtils.onWindows() ? "mvn.cmd" : "mvn";
        String opaqueMvnCmd = CommandBuilder.getMavenCommandLine(projPath, "io.openliberty.tools:liberty-maven-plugin:dev -f " + projPath,
                System.getenv("PATH"));
        Assertions.assertTrue(opaqueMvnCmd.contains(localMvnCmd + " io.openliberty.tools:liberty-maven-plugin:dev"),
                "Expected cmd to contain 'mvn io.openliberty.tools...' but cmd = " + opaqueMvnCmd);
    }

    @Test
    public void testMavenWrapperCommandAssembly() throws IOException, InterruptedException {
        IProject iProject = LibertyPluginTestUtils.getProject(MVN_WRAPPER_APP_NAME);
        String projPath = iProject.getLocation().toOSString();

        String opaqueMvnwCmd = CommandBuilder.getMavenCommandLine(projPath, "io.openliberty.tools:liberty-maven-plugin:dev -f " + projPath,
                System.getenv("PATH"));
        Assertions.assertTrue(opaqueMvnwCmd.contains("mvnw"), "Expected cmd to contain 'mvnw' but cmd = " + opaqueMvnwCmd);
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartActionWithWrapper() {

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard, MVN_WRAPPER_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_WRAPPER_APP_NAME, true, wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard, MVN_WRAPPER_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_WRAPPER_APP_NAME, false, wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartAction() {
        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, true,
                projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, false,
                projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartWithCustomConfigAction() {
        String mode = "run";

        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, mode);

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartConfigDialogWithDashboardAction(bot, dashboard,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
        SWTBotPluginOperations.createNewLibertyConfiguration(bot);
        SWTBotPluginOperations.setLibertyConfigParms(bot, "-DhotTests=true");
        SWTBotPluginOperations.runLibertyConfiguration(bot, mode);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, true,
                projectPath.toAbsolutePath().toString() + "/target/liberty");

        try {
            // Validate that the test reports were generated.
            validateTestReportExists(pathToITReport);
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard,
                    Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, false,
                    projectPath.toAbsolutePath().toString() + "/target/liberty");

            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the start, run tests, view test report, and stop dashboard actions.
     */
    @Test
    public void testDashboardActions() {

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean itReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(itReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        Path pathToUTReport = Paths.get(projectPath.toString(), "target", "site", "surefire-report.html");
        boolean utReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(utReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, true,
                projectPath.toAbsolutePath().toString() + "/target/liberty");

        try {
            // Run Tests.
            SWTBotPluginOperations.launchRunTestsWithDashboardAction(bot, dashboard,
                    Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);

            // Validate that the reports were generated and the the browser editor was launched.
            validateTestReportExists(pathToITReport);
            if (isInternalBrowserSupportAvailable()) {
                SWTBotPluginOperations.launchViewITReportWithDashboardAction(bot, dashboard,
                        Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
            }

            validateTestReportExists(pathToUTReport);
            if (isInternalBrowserSupportAvailable()) {
                SWTBotPluginOperations.launchViewUTReportWithDashboardAction(bot, dashboard,
                        Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
            }
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard,
                    Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, false,
                    projectPath.toAbsolutePath().toString() + "/target/liberty");

            // Close the terminal.
            terminal.close();
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
        IProject iProject = LibertyPluginTestUtils.getProject(MVN_APP_NAME);
        String projectName = iProject.getName();

        Project.removeLibertyNature(iProject);

        // Rename the server.xml file.
        Path originalPath = Paths
                .get("resources", "applications", "maven", "liberty-maven-test-app", "src", "main", "liberty", "config", "server.xml")
                .toAbsolutePath();
        Path renamedPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app", "src", "main", "liberty", "config",
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
            SWTBotPluginOperations.refreshProjectUsingExplorerView(bot, MVN_APP_NAME);

            // Refresh the dashboard.
            SWTBotPluginOperations.refreshDashboard(bot);

            // Make sure the application is no longer listed in the dashboard.
            List<String> projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
            boolean mavenAppFound = false;
            for (String project : projectList) {
                if (MVN_APP_NAME.equals(project)) {
                    mavenAppFound = true;
                    break;
                }
            }

            Assertions.assertTrue(!mavenAppFound, () -> "Project " + projectName + " should not be listed in the dashboard.");

            // Add the project nature manually.
            SWTBotPluginOperations.enableLibertyTools(bot, MVN_APP_NAME);

            // Refresh the project through the explorer view to pick up the nature removal.
            SWTBotPluginOperations.refreshProjectUsingExplorerView(bot, MVN_APP_NAME);

            // Refresh the dashboard.
            SWTBotPluginOperations.refreshDashboard(bot);

            // Make sure the application is listed in the dashboard.
            List<String> newProjectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
            boolean newMavenAppFound = false;
            for (String project : newProjectList) {
                if (MVN_APP_NAME.equals(project)) {
                    newMavenAppFound = true;
                    break;
                }
            }
            Assertions.assertTrue(newMavenAppFound, () -> "The Maven project should be listed in the dashboard.");

        } finally {
            // Rename server.xml.renamed to server.xml. The nature should have already been added.
            Files.copy(renamedPath, originalPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.delete(renamedPath);

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
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDefaultConfig(bot,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, true,
                projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, false,
                projectPath.toAbsolutePath().toString() + "/target/liberty");

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
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartWithCustomConfig(bot,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run", "-DhotTests=true");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, true,
                projectPath.toAbsolutePath().toString() + "/target/liberty");

        try {
            // Validate that the test reports were generated.
            validateTestReportExists(pathToITReport);
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot,
                    Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, false,
                    projectPath.toAbsolutePath().toString() + "/target/liberty");

            // Close the terminal.
            terminal.close();
        }
    }

    /**
     * Tests the start, run tests, view IT report, view UT report, and stop run as shortcut actions.
     */
    @Test
    public void testRunAsShortcutActions() {

        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean itReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(itReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        Path pathToUTReport = Paths.get(projectPath.toString(), "target", "site", "surefire-report.html");
        boolean utReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(utReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithRunDebugAsShortcut(bot,
                Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, true,
                projectPath.toAbsolutePath().toString() + "/target/liberty");

        try {
            // Run Tests.
            SWTBotPluginOperations.launchRunTestspWithRunDebugAsShortcut(bot,
                    Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");

            // Validate that the reports were generated and the the browser editor was launched.
            validateTestReportExists(pathToITReport);
            if (isInternalBrowserSupportAvailable()) {
                SWTBotPluginOperations.launchViewITReportWithRunDebugAsShortcut(bot,
                        Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
            }

            validateTestReportExists(pathToUTReport);
            if (isInternalBrowserSupportAvailable()) {
                SWTBotPluginOperations.launchViewUTReportWithRunDebugAsShortcut(bot,
                        Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME);
            }
        } finally {
            // Stop dev mode.
            SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot,
                    Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, "run");
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(Platform.getOS().equals(Platform.OS_MACOSX) ? MVN_WRAPPER_APP_NAME : MVN_APP_NAME, false,
                    projectPath.toAbsolutePath().toString() + "/target/liberty");

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
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartWithCustomConfig(bot, MVN_APP_NAME, "debug", "-DhotTests=true");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        try {
            // Validate that the test reports were generated.
            validateTestReportExists(pathToITReport);

            // At the moment this check is just too unreliable because the configuration dialog contains different
            // sections that maybe in focus at different times. This makes it difficult to predictably get the panel
            // we are after. We need to find a way to focus on the main dialog. We should also cleanup the configurations.
            // Commenting this code out for now.
            //
            // Validate that a remote java application configuration was created and is named after the application.
            // validateRemoteJavaAppCreation(MVN_APP_NAME);

        } finally {
            // Stop dev mode using the Run As stop command.
            SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, MVN_APP_NAME, "run");
            terminal.show();

            // Validate application stopped.
            validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

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
        SWTBotPluginOperations.launchStartWithRunDebugAsShortcut(bot, MVN_APP_NAME, "debug");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // At the moment this check is just too unreliable because the configuration dialog contains different
        // sections that maybe in focus at different times. This makes it difficult to predictably get the panel
        // we are after. We need to find a way to focus on the main dialog. We should also cleanup the configurations.
        // Commenting this code out for now.
        //
        // Validate that a remote java application configuration was created and is named after the application.
        // validateRemoteJavaAppCreation(MVN_APP_NAME);

        // Stop dev mode using the Run As stop command.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, MVN_APP_NAME, "run");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

    @Test
    @Disabled
    public void testStartWithNoWrapperAndNoPreferencesSet() {

        // verify no wrapper present

        String localMvnwCmd = LibertyPluginTestUtils.onWindows() ? "mvnw.cmd" : "mvnw";
        String absoluteMvnwCmd = projectPath.toAbsolutePath().toString() + localMvnwCmd;
        validateWrapperInProject(false, absoluteMvnwCmd);

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard, MVN_APP_NAME);

        // Validate application is not up and not running.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");
    }

    @Test
    @DisabledOnMac
    public void testStartWithWrapperAndNoPreferencesSet() {

        SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Maven");

        // verify wrapper present
        String localMvnwCmd = LibertyPluginTestUtils.onWindows() ? "mvnw.cmd" : "mvnw";
        String absoluteMvnwCmd = wrapperProjectPath + File.separator + localMvnwCmd;
        validateWrapperInProject(true, absoluteMvnwCmd);

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard, MVN_WRAPPER_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_WRAPPER_APP_NAME, true, wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard, MVN_WRAPPER_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_WRAPPER_APP_NAME, false, wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();

        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Maven");
    }

    @Test
    @DisabledOnMac
    public void testStartWithNoWrapperAndPreferencesSet() {

        // verify no wrapper present
        String localMvnwCmd = LibertyPluginTestUtils.onWindows() ? "mvnw.cmd" : "mvnw";
        String absoluteMvnwCmd = projectPath.toAbsolutePath().toString() + localMvnwCmd;
        validateWrapperInProject(false, absoluteMvnwCmd);

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard, MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

}
