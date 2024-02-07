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

import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.context;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.go;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.goGlobal;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.checkRunInContainerCheckBox;
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
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getRunConfigurationsShell;
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
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchViewITReportWithRunDebugAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchViewUTReportWithRunDebugAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openCommonTab;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openJRETab;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openJavaPerspectiveViaMenu;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openSourceTab;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
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
     * Wrapper Application name.
     */
    static final String NON_DFLT_NAME = "non.dflt.server.xml.path";

    /**
     * Shared lib jar project name.
     */
    static final String MVN_SHARED_LIB_NAME = "shared-lib";

    /**
     * Test app relative path.
     */
    static final Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");

    /**
     * Test app relative path.
     */
    static final Path wrapperProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-wrapper-app");

    /**
     * Test app relative path.
     */
    static final Path nonDfltProjectPath = Paths.get("resources", "applications", "maven", "non-dflt-server-xml-path");

    /**
     * Test shared lib relative path.
     */
    static final Path sharedLibProjectPath = Paths.get("resources", "applications", "maven", "shared-lib");

    static ArrayList<String> projectPaths = new ArrayList<String>();

    /**
     * Expected menu items.
     */
    static String[] mvnMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START, DashboardView.APP_MENU_ACTION_START_CONFIG,
            DashboardView.APP_MENU_ACTION_START_IN_CONTAINER, DashboardView.APP_MENU_ACTION_DEBUG,
            DashboardView.APP_MENU_ACTION_DEBUG_CONFIG, DashboardView.APP_MENU_ACTION_DEBUG_IN_CONTAINER,
            DashboardView.APP_MENU_ACTION_STOP, DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT,
            DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT };

    /**
     * Run As configuration menu items.
     */
    static String[] runAsShortcuts = new String[] { LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER, LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_UT_REPORT, };

    /**
     * Debug As configuration menu items.
     */
    static String[] debugAsShortcuts = new String[] { LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER };

    /**
     * Setup.
     * 
     * @throws Exception
     */
    @BeforeAll
    public static void setup() throws Exception {

        commonSetup();

        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
        projectPaths.add(projectPath.toString());
        projectPaths.add(wrapperProjectPath.toString());
        projectPaths.add(nonDfltProjectPath.toString());
        projectPaths.add(sharedLibProjectPath.toString());

        // Maybe redundant but we really want to cleanup. We really want to
        // avoid wasting time debugging tricky differences in behavior because of a dirty re-run
        for (String p : projectPaths) {
            cleanupProject(p);
        }

        importMavenProjects(workspaceRoot, projectPaths);

        // Build shared lib project
        Process process = new ProcessBuilder(getMvnCmd(), "clean", "install").directory(sharedLibProjectPath.toFile()).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "Building of shared lib jar project failed with RC " + exitCode);

        // Set the preferences
        setBuildCmdPathInPreferences(bot, "Maven");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();

        // Check basic plugin artifacts are functioning before running tests.
        validateBeforeTestRun();
    }

    @AfterAll
    public static void cleanup() {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
        unsetBuildCmdPathInPreferences(bot, "Maven");
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

        // Give the app some time to be imported (especially on Windows GHA runs)
        try {
            Thread.sleep(Integer.parseInt(System.getProperty("io.liberty.tools.eclipse.tests.app.import.wait", "0")));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Check that the dashboard can be opened and its content retrieved.
        List<String> projectList = getDashboardContent();

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
        List<String> menuItems = getDashboardItemMenuActions(MVN_APP_NAME);
        Assertions.assertTrue(menuItems.size() == mvnMenuItems.length,
                () -> "Maven application " + MVN_APP_NAME + " does not contain the expected number of menu items: " + mvnMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(mvnMenuItems)),
                () -> "Maven application " + MVN_APP_NAME + " does not contain the expected menu items: " + mvnMenuItems);

        // Check that the Run As menu contains the expected shortcut
        SWTBotMenu runAsMenu = getAppRunAsMenu(bot, MVN_APP_NAME);
        Assertions.assertTrue(runAsMenu != null, "The runAs menu associated with project: " + MVN_APP_NAME + " is null.");
        System.out.println("test!!!!! ----- " + runAsMenu.getText());
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

        // Check that the Debug As menu contains the expected shortcut
        SWTBotMenu debugAsMenu = getAppDebugAsMenu(bot, MVN_APP_NAME);
        Assertions.assertTrue(debugAsMenu != null, "The debugAs menu associated with project: " + MVN_APP_NAME + " is null.");
        List<String> debugAsMenuItems = debugAsMenu.menuItems();
        Assertions.assertTrue(debugAsMenuItems != null && !debugAsMenuItems.isEmpty(),
                "The debugAs menu associated with project: " + MVN_APP_NAME + " is null or empty.");
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
                "The debugAs menu associated with project: " + MVN_APP_NAME
                        + " does not contain one or more expected entries. Expected number of entries: " + debugAsShortcuts.length
                        + "Found entry count: " + foundDebugAsItems + ". Found menu entries: " + debugAsMenuItems);

        // Check that the Run As -> Run Configurations... contains the Liberty entry in the menu.
        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(MVN_APP_NAME);
        try {
            SWTBotTreeItem runAslibertyToolsEntry = getLibertyTreeItem(configShell);
            Assertions.assertTrue(runAslibertyToolsEntry != null, "Liberty entry in Run Configurations view was not found.");
        } finally {
            go("Close", configShell);

        }

        // Check that the Debug As -> Debug Configurations... contains the Liberty entry in the menu.
        Shell debugShell = launchDebugConfigurationsDialogFromAppRunAs(MVN_APP_NAME);
        try {
            SWTBotTreeItem debugAslibertyToolsEntry = getLibertyTreeItem(debugShell);
            Assertions.assertTrue(debugAslibertyToolsEntry != null, "Liberty entry in Debug Configurations view was not found.");
        } finally {
            go("Close", debugShell);
        }
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testLibertyConfigurationTabsExist() {

        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(MVN_APP_NAME);

        try {
            TreeItem libertyConfigTree = getLibertyTreeItemNoBot(configShell);
            context(libertyConfigTree, "New Configuration");

            Assertions.assertTrue(bot.cTabItem("Start").isVisible(), "Liberty Start tab not visible.");
            Assertions.assertTrue(bot.cTabItem("JRE").isVisible(), "Liberty JRE tab not visible.");
        } finally {
            go("Close", configShell);
        }
    }

    @Test
    @DisabledOnMac
    public void testMavenCommandAssembly() throws IOException, InterruptedException, CommandNotFoundException {

        IProject iProject = LibertyPluginTestUtils.getProject(MVN_APP_NAME);
        String projPath = iProject.getLocation().toOSString();

        String opaqueMvnCmd = CommandBuilder.getMavenCommandLine(projPath, "io.openliberty.tools:liberty-maven-plugin:dev -f " + projPath,
                System.getenv("PATH"), true);
        Assertions.assertTrue(opaqueMvnCmd.contains(getMvnCmdFilename() + " io.openliberty.tools:liberty-maven-plugin:dev"),
                "Expected cmd to contain 'mvn io.openliberty.tools...' but cmd = " + opaqueMvnCmd);
    }

    @Test
    public void testMavenWrapperCommandAssembly() throws IOException, InterruptedException, CommandNotFoundException {
        IProject iProject = LibertyPluginTestUtils.getProject(MVN_WRAPPER_APP_NAME);
        String projPath = iProject.getLocation().toOSString();

        String opaqueMvnwCmd = CommandBuilder.getMavenCommandLine(projPath, "io.openliberty.tools:liberty-maven-plugin:dev -f " + projPath,
                System.getenv("PATH"), true);
        Assertions.assertTrue(opaqueMvnwCmd.contains("mvnw"), "Expected cmd to contain 'mvnw' but cmd = " + opaqueMvnwCmd);
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartActionWithWrapper() {

        // Start dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_WRAPPER_APP_NAME, true,
                wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartAction() {
        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
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

        Path projAbsolutePath = wrapperProjectPath.toAbsolutePath();

        // Doing a 'clean' first in case server was started previously and terminated abruptly. App tests may fail,
        // making it look like an "outer", actual test is failing, so we skip the tests.
        String cmd = CommandBuilder.getMavenCommandLine(projAbsolutePath.toString(),
                "clean io.openliberty.tools:liberty-maven-plugin:dev -DskipITs=true", null, false);
        String[] cmdParts = cmd.split(" ");
        ProcessBuilder pb = new ProcessBuilder(cmdParts).inheritIO().directory(projAbsolutePath.toFile()).redirectErrorStream(true);
        pb.environment().put("JAVA_HOME", JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath());

        Process p = pb.start();
        p.waitFor(3, TimeUnit.SECONDS);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_WRAPPER_APP_NAME, true,
                wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        bot.button("Yes").click();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardDebugAction() {
        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);
        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartWithCustomConfigAction() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        launchCustomRunFromDashboard(MVN_APP_NAME, "-DhotTests=true");

        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToITReport);
        } finally {
            // Stop dev mode.
            launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
        }
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardDebugWithCustomConfigAction() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        launchCustomDebugFromDashboard(MVN_APP_NAME, "-DhotTests=true");

        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToITReport);
        } catch (Exception e) {
            System.out.println("Caught exception: " + e);
            throw new RuntimeException(e);
        } finally {
            // Stop dev mode.
            launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
        }
    }

    /**
     * Tests the start, run tests, view test report, and stop dashboard actions.
     */
    @Test
    public void testDashboardActions() {

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean itReportDeleted = LibertyPluginTestUtils.deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(itReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        Path pathToUTReport = Paths.get(projectPath.toString(), "target", "site", "surefire-report.html");
        boolean utReportDeleted = LibertyPluginTestUtils.deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(utReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Run Tests.
            launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_RUN_TESTS);

            // Validate that the reports were generated and the the browser editor was launched.
            LibertyPluginTestUtils.validateTestReportExists(pathToITReport);
            if (LibertyPluginTestUtils.isInternalBrowserSupportAvailable()) {
                launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT);
            }

            LibertyPluginTestUtils.validateTestReportExists(pathToUTReport);
            if (LibertyPluginTestUtils.isInternalBrowserSupportAvailable()) {
                launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT);
            }
        } finally {
            // Stop dev mode.
            launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
        }
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (default) ->
     * Run.
     */
    @Test
    public void testStartWithDefaultRunAsConfig() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Start dev mode.
        launchStartWithDefaultRunConfigFromAppRunAs(MVN_APP_NAME);

        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchStopWithRunAsShortcut(MVN_APP_NAME);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (customized)
     * -> Run.
     */
    @Test
    public void testStartWithCustomRunAsConfig() {
        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        launchStartWithNewCustomRunConfig(MVN_APP_NAME, "-DhotTests=true");
        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToITReport);
        } finally {
            // Stop dev mode.
            launchStopWithRunAsShortcut(MVN_APP_NAME);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
        }
    }

    /**
     * Tests the start, run tests, view IT report, view UT report, and stop run as shortcut actions.
     */
    @Test
    public void testRunAsShortcutActions() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean itReportDeleted = LibertyPluginTestUtils.deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(itReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        Path pathToUTReport = Paths.get(projectPath.toString(), "target", "site", "surefire-report.html");
        boolean utReportDeleted = LibertyPluginTestUtils.deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(utReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        // Start dev mode.
        launchStartWithRunAsShortcut(MVN_APP_NAME);
        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Run Tests.
            launchRunTestsWithRunAsShortcut(MVN_APP_NAME);

            // Validate that the reports were generated and the the browser editor was launched.
            LibertyPluginTestUtils.validateTestReportExists(pathToITReport);
            if (LibertyPluginTestUtils.isInternalBrowserSupportAvailable()) {
                launchViewITReportWithRunDebugAsShortcut(bot, MVN_APP_NAME);
            }

            LibertyPluginTestUtils.validateTestReportExists(pathToUTReport);
            if (LibertyPluginTestUtils.isInternalBrowserSupportAvailable()) {
                launchViewUTReportWithRunDebugAsShortcut(bot, MVN_APP_NAME);
            }
        } finally {
            // Stop dev mode.
            launchStopWithRunAsShortcut(MVN_APP_NAME);

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
        }
    }

    /**
     * Tests the start action initiated through: project -> Debug As -> Debug Configurations -> Liberty -> New configuration
     * (customized) -> Run.
     */
    @Test
    public void testStartWithCustomDebugAsConfig() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Delete the test report files before we start this test.
        Path pathToITReport = Paths.get(projectPath.toString(), "target", "site", "failsafe-report.html");
        boolean testReportDeleted = LibertyPluginTestUtils.deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        launchStartWithNewCustomDebugConfig(MVN_APP_NAME, "-DhotTests=true");
        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        try {
            // Validate that the test reports were generated.
            LibertyPluginTestUtils.validateTestReportExists(pathToITReport);

            // Validate that a debug configuration was created
            validateDebugConfigCreation(MVN_APP_NAME, SWTBotPluginOperations.NEW_CONFIGURATION);
        } catch (Exception e) {
            System.out.println("Caught exception: " + e);
            throw new RuntimeException(e);
        } finally {

            openJavaPerspectiveViaMenu();

            // Stop dev mode using the Run As stop command.
            launchStopWithRunAsShortcut(MVN_APP_NAME);
            // terminal.show();

            // Validate application stopped.
            LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");

            // Close the terminal.
            // terminal.close();
        }
    }

    /**
     * Tests the start/stop debug as shortcut actions.
     */
    @Test
    public void testStartWithDebugAsShortcut() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Start dev mode.
        launchStartWithDebugAsShortcut(MVN_APP_NAME);

        goGlobal("Terminal");

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Validate that a debug configuration was created
        validateDebugConfigCreation(MVN_APP_NAME, MVN_APP_NAME);

        // Switch back to Java so we can find Pkg Explorer
        openJavaPerspectiveViaMenu();

        // Stop dev mode using the Run As stop command.
        launchStopWithRunAsShortcut(MVN_APP_NAME);

        // terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        // terminal.close();
    }

    @Test
    @Disabled
    public void testStartWithNoWrapperAndNoPreferencesSet() {

        // verify no wrapper present
        String localMvnwCmd = LibertyPluginTestUtils.onWindows() ? "mvnw.cmd" : "mvnw";
        String absoluteMvnwCmd = projectPath.toAbsolutePath().toString() + localMvnwCmd;
        LibertyPluginTestUtils.validateWrapperInProject(false, absoluteMvnwCmd);

        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is not up and not running.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");
    }

    @Test
    @DisabledOnMac
    public void testStartWithWrapperAndNoPreferencesSet() {

        unsetBuildCmdPathInPreferences(bot, "Maven");

        // verify wrapper present
        String localMvnwCmd = LibertyPluginTestUtils.onWindows() ? "mvnw.cmd" : "mvnw";
        String absoluteMvnwCmd = wrapperProjectPath + File.separator + localMvnwCmd;
        LibertyPluginTestUtils.validateWrapperInProject(true, absoluteMvnwCmd);

        // Start dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_WRAPPER_APP_NAME, true,
                wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
        terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(wrapperProjectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();

        setBuildCmdPathInPreferences(bot, "Maven");
    }

    @Test
    @DisabledOnMac
    public void testStartWithNoWrapperAndPreferencesSet() {

        // verify no wrapper present
        String localMvnwCmd = LibertyPluginTestUtils.onWindows() ? "mvnw.cmd" : "mvnw";
        String absoluteMvnwCmd = projectPath.toAbsolutePath().toString() + localMvnwCmd;
        LibertyPluginTestUtils.validateWrapperInProject(false, absoluteMvnwCmd);

        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
        terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");

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
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(MVN_APP_NAME);
        try {
            TreeItem libertyConfigTree = getLibertyTreeItemNoBot(configShell);

            context(libertyConfigTree, "New Configuration");
            openJRETab(bot);
            String buildPathJRE = LibertyPluginTestUtils.getJREFromBuildpath(projectPath.toString());

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
     * Tests that the Common Tab is added and can be opened
     */
    @Test
    public void testDefaultCommonTab() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(MVN_APP_NAME);

        try {

            TreeItem libertyConfigTree = getLibertyTreeItemNoBot(configShell);

            context(libertyConfigTree, "New Configuration");

            openCommonTab(bot);

        } finally {

            go("Close", configShell);
        }

    }

    /**
     * Tests that a non-Liberty project can be manually be categorized to be Liberty project. This test also tests the refresh
     * function.
     * 
     * @throws Exception
     */
    @Test
    public void testAddingProjectToDashboardManually() throws Exception {

        IProject iProject = LibertyPluginTestUtils.getProject(NON_DFLT_NAME);
        String projectName = iProject.getName();

        // Make sure the application is no longer listed in the dashboard.
        List<String> projectList = getDashboardContent();
        boolean mavenAppFound = false;
        for (String project : projectList) {
            if (NON_DFLT_NAME.equals(project)) {
                mavenAppFound = true;
                break;
            }
        }

        Assertions.assertTrue(!mavenAppFound, () -> "Project " + projectName + " should not be listed in the dashboard.");

        // Add the project nature manually.
        enableLibertyTools(NON_DFLT_NAME);

        // Make sure the application is listed in the dashboard.
        List<String> newProjectList = getDashboardContent();
        boolean newMavenAppFound = false;
        for (String project : newProjectList) {
            if (NON_DFLT_NAME.equals(project)) {
                newMavenAppFound = true;
                break;
            }
        }

        Assertions.assertTrue(newMavenAppFound, () -> "The Maven project should be listed in the dashboard.");
    }

    /**
     * Tests that the correct launch configuration is chosen depending on "start" vs "start in container" This test will run
     * "Start..." and check "Run in container". A subsequent "Start" should run dev mode locally as the "Start" action should filter
     * out any existing "container" configurations.
     */
    @Test
    public void testLaunchConfigurationMatching() {

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Launch "Run Configurations" window and check "Run in container"
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START_CONFIG);
        Shell shell = getRunConfigurationsShell();
        checkRunInContainerCheckBox(shell, MVN_APP_NAME);

        // No need to run here. Just Apply and Close.
        go("Apply", shell);
        go("Close", shell);

        // Start dev mode. This should start locally.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        goGlobal("Terminal");

        // Since the app should be started locally, we should be able to validate that the app is up and running.
        // Since our tests cannot run docker, any "failed" start would indicate we did not start locally.
        // There is certainly room for improvement here like perhaps reading the Terminal window for "devc" vs "dev" commands, but this is
        // ok for now.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");

    }

    /**
     * Tests that the correct dependency projects are added to the debug source lookup list
     */
    @Test
    public void testDebugSourceLookupContent() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        Shell configShell = launchDebugConfigurationsDialogFromAppRunAs(MVN_APP_NAME);

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
            deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);
        }

        // Validate dependency projects are in source lookup list
        Assertions.assertTrue(jarEntryFound, "The dependency project, " + MVN_SHARED_LIB_NAME
                + ", was not listed in the source lookup list for project " + MVN_APP_NAME);

    }
}
