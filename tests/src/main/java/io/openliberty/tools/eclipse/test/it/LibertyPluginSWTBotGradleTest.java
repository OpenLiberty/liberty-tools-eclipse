/*******************************************************************************
* Copyright (c) 2022, 2025 IBM Corporation and others.
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

import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.context;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.go;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.deleteLibertyToolsRunConfigEntriesFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.enableLibertyTools;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getAppDebugAsMenu;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getAppRunAsMenu;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getComboTextBoxWithTextPrefix;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDashboardContent;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDashboardItemMenuActions;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDefaultSourceLookupTreeItemNoBot;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getLibertyTreeItem;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getLibertyTreeItemNoBot;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchCustomDebugFromDashboard;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchCustomRunFromDashboard;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDashboardAction;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDebugConfigurationsDialogFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchRunConfigurationsDialogFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchRunTestsWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStartWithDebugAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStartWithDefaultRunConfigFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStartWithNewCustomDebugConfig;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStartWithNewCustomRunConfig;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStartWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStopWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchViewTestReportWithRunDebugAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openJRETab;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openSourceTab;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.refreshDashboard;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.refreshProjectUsingExplorerView;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.CommandBuilder;
import io.openliberty.tools.eclipse.CommandBuilder.CommandNotFoundException;
import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.LibertyNature;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
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

    /**
     * Shared lib jar project name.
     */
    static final String MVN_SHARED_LIB_NAME = "shared-lib";

    static String testAppPath;
    static String testWrapperAppPath;

    static ArrayList<File> projectsToInstall = new ArrayList<File>();

    static ArrayList<String> mavenProjectToInstall = new ArrayList<String>();

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
     * @throws IOException
     * @throws CoreException
     * @throws InterruptedException
     */
    @BeforeAll
    public static void setup() throws Exception {

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

        // Install Maven shared lib project
        Path sharedLibProjectPath = Paths.get("resources", "applications", "maven", "shared-lib");
        mavenProjectToInstall.add(sharedLibProjectPath.toString());
        for (String p : mavenProjectToInstall) {
            cleanupProject(p);
        }
        importMavenProjects(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile(), mavenProjectToInstall);

        // Build shared lib project
        Process process = new ProcessBuilder(getMvnCmd(), "clean", "install").directory(sharedLibProjectPath.toFile()).start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "Building of shared lib jar project failed with RC " + exitCode);

        // Check basic plugin artifacts are functioning before running tests.
        validateBeforeTestRun();

        // set the preferences
        setBuildCmdPathInPreferences(bot, "Gradle");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();

    }

    @AfterAll
    public static void cleanup() {
        for (File p : projectsToInstall) {
            cleanupProject(p.toString());
        }

        for (String p : mavenProjectToInstall) {
            cleanupProject(p);
        }

        unsetBuildCmdPathInPreferences(bot, "Gradle");
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

        // Though supposedly we use blocking methods to do the import, it seems Eclipse has the ability to break out of a deadlock
        // by interrupting our thread, and we also seem to be causing one due to changing compiler settings. Since we haven't debugged
        // the latter, we'll introduce this wait.
        try {
            Thread.sleep(Integer.parseInt(System.getProperty("io.liberty.tools.eclipse.tests.app.import.wait", "0")));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Path projPath = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME);
        File projectFile = projPath.toFile();

        testAppPath = Paths.get(projectFile.getPath()).toAbsolutePath().toString();
        testWrapperAppPath = Paths.get(projectFile.getPath()).toAbsolutePath().toString();

        // Check that the dashboard can be opened and its content retrieved.
        List<String> projectList = getDashboardContent();

        // Check that dashboard contains the expected applications.
        boolean foundApp = false;
        boolean foundWrapperApp = false;
        for (String project : projectList) {
            if (GRADLE_APP_NAME.equals(project)) {
                foundApp = true;
            } else if (GRADLE_WRAPPER_APP_NAME.equals(project)) {
                foundWrapperApp = true;
            }
        }
        Assertions.assertTrue(foundApp, () -> "The dashboard does not contain expected application: " + GRADLE_APP_NAME);
        Assertions.assertTrue(foundWrapperApp, () -> "The dashboard does not contain expected application: " + GRADLE_WRAPPER_APP_NAME);

        // Check that the menu for the expected application contains the required actions.
        List<String> menuItems = getDashboardItemMenuActions(GRADLE_APP_NAME);
        Assertions.assertTrue(menuItems.size() == gradleMenuItems.length, () -> "Gradle application " + GRADLE_APP_NAME
                + " does not contain the expected number of menu items: " + gradleMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(gradleMenuItems)),
                () -> "Gradle application " + GRADLE_APP_NAME + " does not contain the expected menu items: " + gradleMenuItems);

        // Check that the Run As menu contains the expected shortcut
        SWTBotMenu runAsMenu = getAppRunAsMenu(bot, GRADLE_APP_NAME);
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
        SWTBotMenu debugAsMenu = getAppDebugAsMenu(bot, GRADLE_APP_NAME);
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
        Shell debugShell = launchDebugConfigurationsDialogFromAppRunAs(GRADLE_APP_NAME);
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
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");
    }

    /**
     * Tests the debug menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardDebugAction() {

        // Start dev mode.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");
    }

    /**
     * Tests stop of a server started outside of the current Liberty Tools Eclipse session
     * 
     * @throws CommandNotFoundException
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void testDashboardStopExternalServer() throws CommandNotFoundException, IOException, InterruptedException {

        Path wrapperProject = Paths.get("resources", "applications", "gradle", GRADLE_WRAPPER_APP_NAME).toAbsolutePath();

        // Doing a 'clean' first in case server was started previously and terminated abruptly
        String cmd = CommandBuilder.getGradleCommandLine(wrapperProject.toString(), "clean libertyDev", null);
        String[] cmdParts = cmd.split(" ");
        ProcessBuilder pb = new ProcessBuilder(cmdParts).inheritIO().directory(wrapperProject.toFile()).redirectErrorStream(true);
        pb.environment().put("JAVA_HOME", JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath());

        Process p = pb.start();
        p.waitFor(3, TimeUnit.SECONDS);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_WRAPPER_APP_NAME, true, wrapperProject.toString() + "/target/liberty");

        // Stop dev mode.
        launchDashboardAction(GRADLE_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        bot.button("Yes").click();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(wrapperProject.toString() + "/build");

    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartWithCustomConfigAction() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        launchCustomRunFromDashboard(GRADLE_APP_NAME, "--hotTests");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
        } finally {
            // Stop dev mode.
            launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");
        }
    }

    /**
     * Tests the debug with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardDebugWithCustomConfigAction() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        launchCustomDebugFromDashboard(GRADLE_APP_NAME, "--hotTests");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
        } finally {
            // Stop dev mode.
            launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");
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
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Run Tests.
            launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_RUN_TESTS);

            // Validate that the reports were generated and the the browser editor was launched.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
            if (LibertyPluginTestUtils.isInternalBrowserSupportAvailable()) {
                launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT);
            }
        } finally {
            // Stop dev mode.
            launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

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
            refreshProjectUsingExplorerView(GRADLE_APP_NAME);

            // Refresh the dashboard.
            refreshDashboard();

            // Make sure the application is no longer listed in the dashboard.
            List<String> projectList = getDashboardContent();
            boolean gradleAppFound = false;
            for (String project : projectList) {
                if (GRADLE_APP_NAME.equals(project)) {
                    gradleAppFound = true;
                    break;
                }
            }

            Assertions.assertTrue(!gradleAppFound, () -> "Project " + projectName + " should not be listed in the dashboard.");

            // Add the project nature manually.
            enableLibertyTools(GRADLE_APP_NAME);

            // Refresh the project through the explorer view to pick up the nature removal.
            refreshProjectUsingExplorerView(GRADLE_APP_NAME);

            // Refresh the dashboard.
            refreshDashboard();

            // Make sure the application is listed in the dashboard.
            List<String> newProjectList = getDashboardContent();
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
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Start dev mode.
        launchStartWithDefaultRunConfigFromAppRunAs(GRADLE_APP_NAME);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchStopWithRunAsShortcut(GRADLE_APP_NAME);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (customized)
     * -> Run.
     */
    @Test
    public void testStartWithCustomRunAsConfig() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        launchStartWithNewCustomRunConfig(GRADLE_APP_NAME, "--hotTests");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
        } finally {
            // Stop dev mode.
            launchStopWithRunAsShortcut(GRADLE_APP_NAME);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");
        }
    }

    /**
     * Tests the start, run tests, view test report, and stop run as shortcut actions.
     */
    @Test
    public void testRunAsShortcutActions() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "Test report file: " + pathToTestReport + " was not be deleted.");

        // Start dev mode.
        launchStartWithRunAsShortcut(GRADLE_APP_NAME);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Run Tests.
            launchRunTestsWithRunAsShortcut(GRADLE_APP_NAME);

            // Validate that the reports were generated and the the browser editor was launched.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);
            if (LibertyPluginTestUtils.isInternalBrowserSupportAvailable()) {
                launchViewTestReportWithRunDebugAsShortcut(bot, GRADLE_APP_NAME);
            }
        } finally {
            // Stop dev mode.
            launchStopWithRunAsShortcut(GRADLE_APP_NAME);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");
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
        launchStartWithNewCustomDebugConfig(GRADLE_APP_NAME, "--hotTests");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToTestReport);

            // Validate that a debug configuration was created
            validateDebugConfigCreation(GRADLE_APP_NAME, SWTBotPluginOperations.NEW_CONFIGURATION);
        } finally {
            // Stop dev mode using the Run As stop command.
            launchStopWithRunAsShortcut(GRADLE_APP_NAME);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");

        }
    }

    /**
     * Tests the start/stop debug as shortcut actions.
     */
    @Test
    public void testStartWithDebugAsShortcut() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        // Start dev mode.
        launchStartWithDebugAsShortcut(GRADLE_APP_NAME);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Validate that a debug configuration was created
        validateDebugConfigCreation(GRADLE_APP_NAME, GRADLE_APP_NAME);

        // Stop dev mode using the Run As stop command.
        launchStopWithRunAsShortcut(GRADLE_APP_NAME);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(testAppPath + "/build");
    }

    /**
     * Tests that the default JRE set in the project's java build path matches what is marked as default in the Liberty Tools
     * configuration JRE tab.
     */
    @Test
    public void testDefaultJRECompliance() {
        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(GRADLE_APP_NAME);

        try {
            Object libertyConfigTree = getLibertyTreeItemNoBot(configShell);

            context(libertyConfigTree, "New Configuration");

            openJRETab(bot);
            String buildPathJRE = LibertyPluginTestUtils.getJREFromBuildpath(testAppPath);

            Assertions.assertTrue(buildPathJRE != null,
                    () -> "Unable to find the JRE configured in the project's build path (.classpath).");

            SWTBotCombo comboJREBox = getComboTextBoxWithTextPrefix(bot, buildPathJRE);

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

    /**
     * Tests that the correct dependency projects are added to the debug source lookup list NOTE: At the moment we only support Maven
     * dependency projects which is why we are using a Maven jar project to test
     */
    @Test
    public void testDebugSourceLookupContent() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);

        Shell configShell = launchDebugConfigurationsDialogFromAppRunAs(GRADLE_APP_NAME);

        boolean jarEntryFound = false;

        try {
            Object libertyConfigTree = getLibertyTreeItemNoBot(configShell);

            context(libertyConfigTree, "New Configuration");

            openSourceTab(bot);

            SWTBotTreeItem defaultSourceLookupTree = new SWTBotTreeItem((TreeItem) getDefaultSourceLookupTreeItemNoBot(configShell));

            // Lookup shared-lib project
            try {
                defaultSourceLookupTree.getNode(MVN_SHARED_LIB_NAME);
                jarEntryFound = true;
            } catch (WidgetNotFoundException wnfe) {
                // Jar project was not found in source lookup list.
            }

        } finally {
            go("Close", configShell);
            deleteLibertyToolsRunConfigEntriesFromAppRunAs(GRADLE_APP_NAME);
        }

        // Validate dependency projects are in source lookup list
        Assertions.assertTrue(jarEntryFound, "The dependency project, " + MVN_SHARED_LIB_NAME
                + ", was not listed in the source lookup list for project " + GRADLE_APP_NAME);

    }
}
