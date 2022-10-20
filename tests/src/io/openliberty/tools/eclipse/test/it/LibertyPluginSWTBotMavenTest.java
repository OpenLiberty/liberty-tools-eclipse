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
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginSWTBotMavenTest {

    /**
     * Wokbench bot instance.
     */
    static SWTWorkbenchBot bot;

    /**
     * Dashboard instance.
     */
    static SWTBotView dashboard;

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
        bot = new SWTWorkbenchBot();
        SWTBotPluginOperations.closeWelcomePage(bot);

        // Import the required applications into the Eclipse workspace.
        importMavenApplications();

        // Update browser preferences.
        if (isInternalBrowserSupportAvailable()) {
            boolean success = LibertyPluginTestUtils.updateBrowserPreferences(true);
            Assertions.assertTrue(success, () -> "Unable to update browser preferences.");
        }

        // Check basic plugin artifacts are functioning before running tests.
        checkBasics();
    }

    /**
     * Cleanup.
     */
    @AfterAll
    public static void cleanup() {
        bot.closeAllEditors();
        bot.closeAllShells();
        bot.resetWorkbench();
    }

    @BeforeEach
    public void beforeEach(TestInfo info) {
        System.out.println("INFO: Test " + info.getDisplayName() + " entry: " + java.time.LocalDateTime.now());
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        System.out.println("INFO: Test " + info.getDisplayName() + " exit: " + java.time.LocalDateTime.now());
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
    public static final void checkBasics() {
        dashboard = SWTBotPluginOperations.openDashboardUsingToolbar(bot);

        // Check that the dashboard can be opened and its content retrieved.
        List<String> projetList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);

        // Check that dashboard contains the expected applications.
        boolean foundApp = false;
        for (String project : projetList) {
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
        SWTBotPluginOperations.launchRunConfigurationsDialog(bot, MVN_APP_NAME, "run");
        SWTBotTreeItem runAslibertyToolsEntry = SWTBotPluginOperations.getLibertyToolsConfigMenuItem(bot);
        Assertions.assertTrue(runAslibertyToolsEntry != null, "Liberty entry in Run Configurations view was not found.");
        bot.button("Close").click();

        // Commented out pending design discussions.
        // Check that the Debug As -> Debug Configurations... contains the Liberty entry in the menu.
        // SWTBotPluginOperations.launchRunConfigurationsDialog(bot, MVN_APP_NAME, "debug");
        // SWTBotTreeItem debugAslibertyToolsEntry = SWTBotPluginOperations.getLibertyToolsConfigMenuItem(bot);
        // Assertions.assertTrue(debugAslibertyToolsEntry != null, "Liberty entry in Debug Configurations view was not found.");
        // bot.button("Close").click();
    }

    @Test
    public void testMavenCommandAssembly() throws IOException, InterruptedException {

        DevModeOperations devMode = DevModeOperations.getInstance();
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot root = workspace.getRoot();
        String projectName = MVN_APP_NAME;
        IProject proj = root.getProject(projectName);

        String projPath = proj.getLocation().toOSString();

        String localMvnCmd = LibertyPluginTestUtils.onWindows() ? "mvn.cmd" : "mvn";
        String opaqueMvnCmd = devMode.getMavenCommand(projPath, "io.openliberty.tools:liberty-maven-plugin:dev -f " + projPath);
        Assertions.assertTrue(opaqueMvnCmd.contains(localMvnCmd + " io.openliberty.tools:liberty-maven-plugin:dev"),
                "Expected cmd to contain 'mvn io.openliberty.tools...' but cmd = " + opaqueMvnCmd);
    }

    @Test
    public void testMavenWrapperCommandAssembly() throws IOException, InterruptedException {
        DevModeOperations devMode = DevModeOperations.getInstance();
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot root = workspace.getRoot();
        String projectName = MVN_WRAPPER_APP_NAME;
        IProject proj = root.getProject(projectName);

        String projPath = proj.getLocation().toOSString();

        String opaqueMvnwCmd = devMode.getMavenCommand(projPath, "io.openliberty.tools:liberty-maven-plugin:dev -f " + projPath);
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

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartWithCustomConfigAction() {
        String mode = "run";

        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, MVN_APP_NAME, mode);

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartConfigDialogWithDashboardAction(bot, dashboard, MVN_APP_NAME);
        SWTBotPluginOperations.createNewLibertyConfiguration(bot);
        SWTBotPluginOperations.updateLibertyConfigParms(bot, "-DhotTests=true");
        SWTBotPluginOperations.runLibertyConfiguration(bot, mode);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Validate that the test reports were generated.
        validateTestReportExists(pathToITReport);

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
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
        SWTBotPluginOperations.launchStartWithDashboardAction(bot, dashboard, MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Run Tests.
        SWTBotPluginOperations.launchRunTestsWithDashboardAction(bot, dashboard, MVN_APP_NAME);

        // Validate that the reports were generated and the the browser editor was launched.
        validateTestReportExists(pathToITReport);
        if (isInternalBrowserSupportAvailable()) {
            SWTBotPluginOperations.launchViewITReportWithDashboardAction(bot, dashboard, MVN_APP_NAME);
        }

        validateTestReportExists(pathToUTReport);
        if (isInternalBrowserSupportAvailable()) {
            SWTBotPluginOperations.launchViewUTReportWithDashboardAction(bot, dashboard, MVN_APP_NAME);
        }

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithDashboardAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
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
        // Get the list of entries on the dashboard and verify the expected number
        // found.
        List<String> projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
        boolean mavenAppFound = false;
        for (String project : projectList) {
            if (MVN_APP_NAME.equals(project)) {
                mavenAppFound = true;
            }
        }
        Assertions.assertTrue(mavenAppFound, () -> "The maven test app was not found.");

        Path original = Paths.get("resources", "applications", "maven", "liberty-maven-test-app", "pom.xml").toAbsolutePath();
        Path original_backup = Paths.get("resources", "applications", "maven", "liberty-maven-test-app", "pom.xml_backup").toAbsolutePath();
        Path updated = Paths.get("resources", "files", "apps", "maven", "liberty-maven-test-app", "pom.xml").toAbsolutePath();

        try {
            // Move pom.xml files
            Files.copy(original, original_backup, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(updated, original, StandardCopyOption.REPLACE_EXISTING);

            // Refresh
            SWTBotPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is
            // found.
            projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
            mavenAppFound = false;
            for (String project : projectList) {
                if (MVN_APP_NAME.equals(project)) {
                    mavenAppFound = true;
                }
            }
            Assertions.assertFalse(mavenAppFound, () -> "The maven test app was found.");

        } finally {
            // Reset pom.xml files
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

            // Validate that the pom.xml was correctly updated.
            Assertions.assertTrue(isTextInFile(original.toString(), "liberty-maven-plugin"),
                    "The pom.xml file does not contain the Liberty Maven plugin");

            // Refresh
            SWTBotPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is
            // found.
            projectList = SWTBotPluginOperations.getDashboardContent(bot, dashboard);
            mavenAppFound = false;
            for (String project : projectList) {
                if (MVN_APP_NAME.equals(project)) {
                    mavenAppFound = true;
                }
            }
            Assertions.assertTrue(mavenAppFound, () -> "The maven test app was not found.");
        }
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (default) ->
     * Run.
     */
    @Test
    public void tesStartWithDefaultRunAsConfig() {
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, MVN_APP_NAME, "run");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithDefaultConfig(bot, MVN_APP_NAME, "run");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, MVN_APP_NAME, "run");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (customized)
     * -> Run.
     */
    @Test
    public void tesStartWithCustomRunAsConfig() {
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, MVN_APP_NAME, "run");

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        SWTBotPluginOperations.launchStartWithCustomConfig(bot, MVN_APP_NAME, "run", "-DhotTests=true");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Validate that the test reports were generated.
        validateTestReportExists(pathToITReport);

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, MVN_APP_NAME, "run");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start, run tests, view IT report, view UT report, and stop run as shortcut actions.
     */
    @Test
    public void testRunAsShortcutActions() {
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, MVN_APP_NAME, "run");

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean itReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(itReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        Path pathToUTReport = Paths.get(projectPath.toString(), "target", "site", "surefire-report.html");
        boolean utReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(utReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithRunDebugAsShortcut(bot, MVN_APP_NAME, "run");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Run Tests.
        SWTBotPluginOperations.launchRunTestspWithRunDebugAsShortcut(bot, MVN_APP_NAME, "run");

        // Validate that the reports were generated and the the browser editor was launched.
        validateTestReportExists(pathToITReport);
        if (isInternalBrowserSupportAvailable()) {
            SWTBotPluginOperations.launchViewITReportWithRunDebugAsShortcut(bot, MVN_APP_NAME);
        }

        validateTestReportExists(pathToUTReport);
        if (isInternalBrowserSupportAvailable()) {
            SWTBotPluginOperations.launchViewUTReportWithRunDebugAsShortcut(bot, MVN_APP_NAME);
        }

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, MVN_APP_NAME, "run");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start action initiated through: project -> Debug As -> Debug Configurations -> Liberty -> New configuration
     * (customized) -> Run.
     */
    @Disabled("Disabled pending design discussions")
    @Test
    public void tesStartWithCustomDebugAsConfig() {
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, MVN_APP_NAME, "debug");

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

        // Validate that the test reports were generated.
        validateTestReportExists(pathToITReport);

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, MVN_APP_NAME, "debug");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start/stop debug as shortcut actions.
     */
    @Disabled("Disabled pending design discussions")
    @Test
    public void testStartWithDebugAsShortcut() {
        // Delete any previously created configs.
        SWTBotPluginOperations.deleteLibertyToolsConfigEntries(bot, MVN_APP_NAME, "debug");

        // Start dev mode.
        SWTBotPluginOperations.launchStartWithRunDebugAsShortcut(bot, MVN_APP_NAME, "debug");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        SWTBotPluginOperations.launchStopWithRunDebugAsShortcut(bot, MVN_APP_NAME, "debug");
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Imports existing Maven application projects into the workspace.
     */
    public static void importMavenApplications() {
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
                ArrayList<String> projectPaths = new ArrayList<String>();
                projectPaths.add(projectPath.toString());
                projectPaths.add(wrapperProjectPath.toString());

                try {
                    importProjects(workspaceRoot, projectPaths);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Imports the specified list of projects.
     *
     * @param workspaceRoot The workspace root location.
     * @param folders The list of folders containing the projects to install.
     *
     * @throws InterruptedException
     * @throws CoreException
     */
    public static void importProjects(File workspaceRoot, List<String> folders) throws InterruptedException, CoreException {
        // Get the list of projects to install.
        MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
        LocalProjectScanner lps = new LocalProjectScanner(folders, false, modelManager);
        lps.run(new NullProgressMonitor());
        List<MavenProjectInfo> projects = lps.getProjects();

        // Import the projects.
        ProjectImportConfiguration projectImportConfig = new ProjectImportConfiguration();
        IProjectConfigurationManager projectConfigurationManager = MavenPlugin.getProjectConfigurationManager();
        projectConfigurationManager.importProjects(projects, projectImportConfig, new NullProgressMonitor());
    }

    /**
     * Copies maven build wrapper artifacts to the project.
     */
    public void copyWrapperArtifactsToProject() {
        Path sourceDirPath = Paths.get(Paths.get("").toAbsolutePath().toString(), "resources", "files", "apps", "maven",
                "liberty-maven-test-app", "wrapper");
        try {
            Stream<Path> sourceFiles = Files.walk(sourceDirPath);
            Iterator<Path> iterator = sourceFiles.iterator();
            while (iterator.hasNext()) {
                Path sourceFile = iterator.next();
                Path destination = Paths.get(projectPath.toString(), sourceFile.toString().substring(sourceDirPath.toString().length()));
                try {
                    Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);
                } catch (FileAlreadyExistsException | DirectoryNotEmptyException ee) {
                    // Ignore.
                }
            }
            sourceFiles.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes all wrapper related artifacts.
     */
    public void removeWrapperArtifactsFromProject() {
        Path mvnw = Paths.get(projectPath.toString(), "mvnw");
        boolean mvnwDeleted = deleteFile(mvnw.toFile());
        Assertions.assertTrue(mvnwDeleted, () -> "File: " + mvnw + " was not be deleted.");

        Path mvncmd = Paths.get(projectPath.toString(), "mvnw.cmd");
        boolean mvncmdDeleted = deleteFile(mvncmd.toFile());
        Assertions.assertTrue(mvncmdDeleted, () -> "File: " + mvncmd + " was not be deleted.");

        Path mvnDir = Paths.get(projectPath.toString(), ".mvn");
        boolean mvnDirDeleted = deleteFile(mvnDir.toFile());
        Assertions.assertTrue(mvnDirDeleted, () -> "File: " + mvnDir + " was not be deleted.");
    }
}
