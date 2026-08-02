/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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

import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.go;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.cancelModuleSelectionDialog;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.clearDashboardFilter;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.collapseDashboard;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.deleteLibertyToolsRunConfigEntriesFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.expandDashboard;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.filterDashboardByText;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDashboardContent;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDashboardItemMenuActions;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getLibertyTreeItem;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getModuleSelectionDialogItemCount;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getModuleSelectionDialogItems;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getRunConfigurationsShell;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDashboardAction;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.waitForAndClickButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDebugConfigurationsDialogFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchRunConfigurationsDialogFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStartWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStopWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.selectModuleInDialog;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.typeInModuleSelectionDialogFilter;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.waitForModuleSelectionDialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.openliberty.tools.eclipse.CommandBuilder.CommandNotFoundException;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotTestCondition;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;

/**
 * Tests Open Liberty Eclipse plugin functions for Maven multi-module projects
 * that contain multiple Liberty modules. The tests verify that the module
 * selection dialog opens correctly for every supported action, that the dialog
 * lists the expected Liberty modules, and that start and stop operations
 * complete successfully when a module is chosen.
 */
public class LibertyPluginSWTBotMultiLibertyModMavenTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * The artifact ID of the root aggregator project as declared in its pom.xml.
     */
    static final String MVN_APP_NAME = "guide-maven-multimodules-custmm";

    /**
     * Name of the first Liberty ear module.
     */
    static final String MVN_EAR1_MODULE_NAME = "guide-maven-multimodules-custmm-ear1";

    /**
     * Name of the second Liberty ear module.
     */
    static final String MVN_EAR2_MODULE_NAME = "guide-maven-multimodules-custmm-ear2";

    /**
     * Name of the third Liberty ear module that uses skinny modules.
     */
    static final String MVN_EAR_SKINNY_MODULE_NAME = "guide-maven-multimodules-custmm-ear-skinny-modules";

    /**
     * Expected number of Liberty child modules exposed by the selection dialog.
     */
    static final int EXPECTED_LIBERTY_MODULE_COUNT = 3;

    /**
     * Path to the root project directory imported into the workspace.
     */
    static final Path rootProjectPath = Paths.get("resources", "applications", "maven", "multi-liberty-module");

    /**
     * Path to the Liberty server output directory for the ear1 module. Used to
     * confirm that the server started or stopped.
     */
    static final Path ear1ServerPath = rootProjectPath.resolve("ear1").resolve("target").resolve("liberty");

    /**
     * Path to the Liberty server output directory for the ear2 module. Used to
     * confirm that the server started or stopped.
     */
    static final Path ear2ServerPath = rootProjectPath.resolve("ear2").resolve("target").resolve("liberty");

    /**
     * Expected dashboard menu items for the root aggregator project.
     */
    static final String[] mvnMenuItems = new String[] {
                                                        DashboardView.APP_MENU_ACTION_START,
                                                        DashboardView.APP_MENU_ACTION_START_CONFIG,
                                                        DashboardView.APP_MENU_ACTION_START_IN_CONTAINER,
                                                        DashboardView.APP_MENU_ACTION_DEBUG,
                                                        DashboardView.APP_MENU_ACTION_DEBUG_CONFIG,
                                                        DashboardView.APP_MENU_ACTION_DEBUG_IN_CONTAINER,
                                                        DashboardView.APP_MENU_ACTION_STOP,
                                                        DashboardView.APP_MENU_ACTION_RUN_TESTS,
                                                        DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT,
                                                        DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT
    };

    /**
     * Expected Run As shortcut menu items visible for an inactive project. Stop
     * and Run Tests are only present while the project is active and are therefore
     * not listed here.
     */
    static final String[] runAsShortcuts = new String[] {
                                                          LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
                                                          LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG,
                                                          LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER,
                                                          LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT,
                                                          LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_UT_REPORT
    };

    static File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();

    static List<String> projectPaths = new ArrayList<String>();

    /**
     * Imports the multi-liberty-module application projects and validates that the
     * plugin is in a ready state before any test runs.
     *
     * @throws Exception if setup fails.
     */
    @BeforeAll
    public static void setup() throws Exception {
        commonSetup();

        // Add each sub-module project path first so M2E discovers them individually.
        projectPaths.add(rootProjectPath.resolve("jar").toString());
        projectPaths.add(rootProjectPath.resolve("ejb").toString());
        projectPaths.add(rootProjectPath.resolve("war").toString());
        projectPaths.add(rootProjectPath.resolve("war2").toString());
        projectPaths.add(rootProjectPath.resolve("ear1").toString());
        projectPaths.add(rootProjectPath.resolve("ear2").toString());
        projectPaths.add(rootProjectPath.resolve("rar").toString());
        projectPaths.add(rootProjectPath.resolve("ear-skinny-modules").toString());
        // Add the aggregator root last so M2E can resolve the complete module graph.
        projectPaths.add(rootProjectPath.toString());

        for (String p : projectPaths) {
            cleanupProject(p);
        }

        importMavenProjects(workspaceRoot, projectPaths);

        setBuildCmdPathInPreferences(bot, "Maven");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();

        validateBeforeTestRun();
    }

    /**
     * Terminates any lingering launch after each test to keep subsequent tests isolated.
     *
     * @param info Test information provided by JUnit.
     */
    @AfterEach
    public void afterEach(TestInfo info) {
        SWTBotPluginOperations.terminateLaunch();
        super.afterEach(info);
    }

    /**
     * Removes imported projects and resets the Maven preference after all tests complete.
     */
    @AfterAll
    public static void cleanup() {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
        unsetBuildCmdPathInPreferences(bot, "Maven");
    }

    /**
     * Verifies preconditions before the test suite executes.
     * Checks that:
     * 1. The dashboard can be opened and its content retrieved.
     * 2. The dashboard contains the aggregator project.
     * 3. The dashboard menu for the project contains all required actions.
     * 4. The Run As menu contains all required shortcut actions.
     * 5. The Run As configuration view contains the Liberty entry.
     * 6. The Debug As configuration view contains the Liberty entry.
     */
    public static final void validateBeforeTestRun() {

        SWTBotTestCondition.waitFor(
                                    () -> getDashboardContent().contains(MVN_APP_NAME), SWTBotTestCondition.LARGE_WAIT_MS);

        List<String> projectList = getDashboardContent();
        boolean foundApp = false;
        for (String project : projectList) {
            if (MVN_APP_NAME.equals(project)) {
                foundApp = true;
                break;
            }
        }
        Assertions.assertTrue(foundApp, () -> "The dashboard does not contain expected application: " + MVN_APP_NAME);

        List<String> menuItems = getDashboardItemMenuActions(MVN_APP_NAME);
        Assertions.assertTrue(menuItems.size() == mvnMenuItems.length,
                              () -> "Maven application " + MVN_APP_NAME + " does not contain the expected number of menu items: " + mvnMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(mvnMenuItems)),
                              () -> "Maven application " + MVN_APP_NAME + " does not contain the expected menu items: " + Arrays.toString(mvnMenuItems));

        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, MVN_APP_NAME);
        Assertions.assertNotNull(runAsMenu, "The Run As menu associated with project " + MVN_APP_NAME + " is null.");
        List<String> runAsMenuItems = runAsMenu.menuItems();
        Assertions.assertTrue(runAsMenuItems != null && !runAsMenuItems.isEmpty(),
                              "The Run As menu associated with project " + MVN_APP_NAME + " is null or empty.");

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
                              "The Run As menu associated with project " + MVN_APP_NAME
                                                                   + " does not contain one or more expected entries. Expected: " + runAsShortcuts.length
                                                                   + ", found: " + foundItems + ". Items: " + runAsMenuItems);

        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(MVN_APP_NAME);
        try {
            SWTBotTreeItem runAsLibertyEntry = getLibertyTreeItem(configShell);
            Assertions.assertNotNull(runAsLibertyEntry, "Liberty entry in Run Configurations view was not found.");
        } finally {
            go("Close", configShell);
        }

        Shell debugShell = launchDebugConfigurationsDialogFromAppRunAs(MVN_APP_NAME);
        try {
            SWTBotTreeItem debugAsLibertyEntry = getLibertyTreeItem(debugShell);
            Assertions.assertNotNull(debugAsLibertyEntry, "Liberty entry in Debug Configurations view was not found.");
        } finally {
            go("Close", debugShell);
        }
    }

    /**
     * Tests that every dashboard action that targets a module opens the module
     * selection dialog with the correct number of Liberty modules listed.
     */
    @Test
    public void testDashboardActionsOpenModuleSelectionDialogWithCorrectCount() {

        String[] actionsWithInactiveModuleFilter = new String[] {
                                                                  DashboardView.APP_MENU_ACTION_START,
                                                                  DashboardView.APP_MENU_ACTION_DEBUG,
                                                                  DashboardView.APP_MENU_ACTION_START_CONFIG,
                                                                  DashboardView.APP_MENU_ACTION_DEBUG_CONFIG,
                                                                  DashboardView.APP_MENU_ACTION_START_IN_CONTAINER,
                                                                  DashboardView.APP_MENU_ACTION_DEBUG_IN_CONTAINER
        };

        for (String action : actionsWithInactiveModuleFilter) {
            launchDashboardAction(MVN_APP_NAME, action);

            Shell dialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
            Assertions.assertNotNull(dialog,
                                     "Module selection dialog did not open for dashboard action: " + action);

            int count = getModuleSelectionDialogItemCount(dialog);
            List<String> items = getModuleSelectionDialogItems(dialog);
            Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT, count,
                                    "Dashboard action '" + action + "' should list " + EXPECTED_LIBERTY_MODULE_COUNT
                                                                          + " Liberty modules in the selection dialog, but found " + count + ". Items: " + items);

            Assertions.assertTrue(items.contains(MVN_EAR1_MODULE_NAME),
                                  "Selection dialog for action '" + action + "' is missing module: " + MVN_EAR1_MODULE_NAME);
            Assertions.assertTrue(items.contains(MVN_EAR2_MODULE_NAME),
                                  "Selection dialog for action '" + action + "' is missing module: " + MVN_EAR2_MODULE_NAME);
            Assertions.assertTrue(items.contains(MVN_EAR_SKINNY_MODULE_NAME),
                                  "Selection dialog for action '" + action + "' is missing module: " + MVN_EAR_SKINNY_MODULE_NAME);

            cancelModuleSelectionDialog(dialog);
        }
    }

    /**
     * Tests that the Run As Start shortcut and the Run As Start in Container
     * shortcut each open the module selection dialog with the correct number of
     * Liberty modules.
     */
    @Test
    public void testRunAsShortcutActionsOpenModuleSelectionDialogWithCorrectCount() {

        // Verify the Start shortcut.
        launchStartWithRunAsShortcut(MVN_APP_NAME);
        Shell startDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog,
                                 "Module selection dialog did not open for the Run As Start shortcut.");
        int startCount = getModuleSelectionDialogItemCount(startDialog);
        List<String> startItems = getModuleSelectionDialogItems(startDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT, startCount,
                                "Run As Start shortcut should list " + EXPECTED_LIBERTY_MODULE_COUNT
                                                                           + " Liberty modules, but found " + startCount + ". Items: " + startItems);
        cancelModuleSelectionDialog(startDialog);

        // Verify the Start in Container shortcut.
        SWTBotMenu containerMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, MVN_APP_NAME).menu(WidgetMatcherFactory.withRegex(
                                                                                                                                 ".*"
                                                                                                                                 + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER
                                                                                                                                 + ".*"),
                                                                                                  false, 0);
        containerMenu.click();

        Shell containerDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(containerDialog,
                                 "Module selection dialog did not open for the Run As Start in Container shortcut.");
        int containerCount = getModuleSelectionDialogItemCount(containerDialog);
        List<String> containerItems = getModuleSelectionDialogItems(containerDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT, containerCount,
                                "Run As Start in Container shortcut should list " + EXPECTED_LIBERTY_MODULE_COUNT
                                                                               + " Liberty modules, but found " + containerCount + ". Items: " + containerItems);
        cancelModuleSelectionDialog(containerDialog);
    }

    /**
     * Tests the Start/Stop dashboard actions. More precisely, it tests that the module
     * selection dialog opens when the start action is selected.
     * After selecting the the module, the Liberty server starts for the selected module.
     * Last, when the stop actions through the dashboard, the server is stopped.
     */
    @Test
    public void testDashboardStartActionSelectsModuleAndStops() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        Shell startDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog, "Module selection dialog did not open for the Start dashboard action.");

        int count = getModuleSelectionDialogItemCount(startDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT, count,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT + " Liberty modules in the selection dialog, but found " + count + ".");

        selectModuleInDialog(startDialog, MVN_EAR1_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9080/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
    }

    /**
     * Tests the Debug dashboard action. More precisely, test that the module selection dialog
     * opens, a module is selected, the Liberty server starts in debug mode for that
     * module, and when the stop action is issued from the dashboard the server is stopped.
     */
    @Test
    public void testDashboardDebugActionSelectsModuleAndStops() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

        Shell debugDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(debugDialog, "Module selection dialog did not open for the Debug dashboard action.");

        int count = getModuleSelectionDialogItemCount(debugDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT, count,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT + " Liberty modules in the selection dialog, but found " + count + ".");

        selectModuleInDialog(debugDialog, MVN_EAR1_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9080/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
    }

    /**
     * Tests the Start with configuration dashboard action. More precisely, test that
     * the Run Configurations dialog opens, the user presses Run, the module selection
     * dialog appears with the expected modules, a module is selected, the server starts,
     * and when the stop action is issued from the dashboard, the server is stopped.
     */
    @Test
    public void testDashboardStartWithConfigActionSelectsModuleAndStops() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START_CONFIG);

        Shell dialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(dialog,
                                 "Module selection dialog did not open after pressing Run in the Run Configurations dialog.");

        int count = getModuleSelectionDialogItemCount(dialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT, count,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT + " Liberty modules in the selection dialog, but found " + count + ".");

        selectModuleInDialog(dialog, MVN_EAR1_MODULE_NAME);

        Shell configShell = getRunConfigurationsShell();
        Assertions.assertNotNull(configShell, "Run Configurations dialog did not open.");

        go("Run", configShell);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9080/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
    }

    /**
     * Tests the Run As Start shortcut end-to-end. More precisely, test that the module
     * selection dialog opens, a module is selected, the Liberty server starts, and
     * when the stop action is issued from the the Run As context menu, the server
     * is stopped.
     */
    @Test
    public void testRunAsStartShortcutSelectsModuleAndStops() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        launchStartWithRunAsShortcut(MVN_APP_NAME);

        Shell dialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(dialog,
                                 "Module selection dialog did not open for the Run As Start shortcut.");

        int count = getModuleSelectionDialogItemCount(dialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT, count,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT + " Liberty modules in the selection dialog, but found " + count + ".");

        selectModuleInDialog(dialog, MVN_EAR1_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9080/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        launchStopWithRunAsShortcut(MVN_APP_NAME);

        LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
    }

    /**
     * Tests that the filter text field inside the module selection dialog works correctly.
     * The Run As Start shortcut is triggered from the parent project. When the dialog
     * opens, "ear1" is typed into the filter field. The test verifies that only the
     * module whose name contains "ear1" remains visible in the list, then cancels the
     * dialog without starting the server.
     */
    @Test
    public void testModuleSelectionDialogSearchFilter() {

        launchStartWithRunAsShortcut(MVN_APP_NAME);

        Shell dialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(dialog,
                                 "Module selection dialog did not open for the Run As Start shortcut.");

        // Type "ear1" into the filter field. The dialog should reduce the visible list
        // to only items whose name contains "ear1".
        typeInModuleSelectionDialogFilter(dialog, "ear1");

        // Wait briefly for the filter to apply.
        SWTBotTestCondition.waitFor(() -> {
            List<String> filtered = getModuleSelectionDialogItems(dialog);
            return filtered.size() == 1;
        }, SWTBotTestCondition.MIN_WAIT_MS);

        List<String> filteredItems = getModuleSelectionDialogItems(dialog);
        Assertions.assertEquals(1, filteredItems.size(),
                                "After typing 'ear1', the selection dialog should show exactly 1 item, but found: " + filteredItems);
        Assertions.assertTrue(filteredItems.get(0).contains("ear1"),
                              "The remaining item after filtering by 'ear1' should contain 'ear1' in its name, but was: " + filteredItems.get(0));

        cancelModuleSelectionDialog(dialog);
    }

    /**
     * Tests the module selection dialog item count as active modules accumulate and then
     * decrease. The scenario is:
     * 1. Start ear1 from the parent. The selection dialog shows 3 inactive modules. Select ear1.
     * 2. Start ear2 from the parent. The selection dialog now shows 2 inactive modules (ear2 and
     * ear-skinny-modules). Select ear2.
     * 3. Stop from the parent. The selection dialog shows 2 active modules (ear1 and ear2).
     * Select ear2. Verify ear2 stops.
     * 4. Stop from the parent again. Because only one active module (ear1) remains, no
     * selection dialog is shown and ear1 stops directly.
     */
    @Test
    public void testStopDialogCountReducesAsModulesStop() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Step 1: Start ear1. All 3 modules should be inactive.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        Shell startDialog1 = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog1, "Module selection dialog did not open when starting ear1.");
        int countBeforeFirstStart = getModuleSelectionDialogItemCount(startDialog1);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT, countBeforeFirstStart,
                                "Before any module is started, the dialog should show " + EXPECTED_LIBERTY_MODULE_COUNT
                                                                                      + " inactive modules, but found " + countBeforeFirstStart + ".");
        selectModuleInDialog(startDialog1, MVN_EAR1_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9080/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());
        pressWorkspaceErrorDialogProceedButton(bot);

        // Step 2: Start ear2. Only 2 inactive modules should remain (ear2 and ear-skinny-modules).
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        Shell startDialog2 = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog2, "Module selection dialog did not open when starting ear2.");
        int countAfterFirstStart = getModuleSelectionDialogItemCount(startDialog2);
        Assertions.assertEquals(2, countAfterFirstStart,
                                "After ear1 is running, the dialog should show 2 inactive modules, but found " + countAfterFirstStart + ".");
        selectModuleInDialog(startDialog2, MVN_EAR2_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9081/converter/heights.jsp?heightCm=20", true,
                                                                "Height in feet and inches", ear2ServerPath.toString());
        pressWorkspaceErrorDialogProceedButton(bot);

        // Step 3: Stop from the parent. Both ear1 and ear2 are active so the dialog should
        // show 2 active modules. Select ear2 to stop it.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
        Shell stopDialog1 = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(stopDialog1,
                                 "Module selection dialog did not open for Stop when both ear1 and ear2 are active.");
        int activeCount = getModuleSelectionDialogItemCount(stopDialog1);
        List<String> activeItems = getModuleSelectionDialogItems(stopDialog1);
        Assertions.assertEquals(2, activeCount,
                                "With ear1 and ear2 running, the Stop dialog should show 2 active modules, but found "
                                                + activeCount + ". Items: " + activeItems);
        Assertions.assertTrue(activeItems.contains(MVN_EAR1_MODULE_NAME),
                              "Stop dialog should list " + MVN_EAR1_MODULE_NAME + " as active.");
        Assertions.assertTrue(activeItems.contains(MVN_EAR2_MODULE_NAME),
                              "Stop dialog should list " + MVN_EAR2_MODULE_NAME + " as active.");
        selectModuleInDialog(stopDialog1, MVN_EAR2_MODULE_NAME);

        LibertyPluginTestUtils.validateLibertyServerStopped(ear2ServerPath.toString());

        // Step 4: Stop from the parent again. Only ear1 is active so no selection dialog
        // should appear and ear1 should stop directly.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
        Shell stopDialog2 = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNull(stopDialog2,
                              "With only ear1 active, the Stop action should not show a selection dialog, but one appeared.");

        LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
    }

    /**
     * Tests the dashboard toolbar search filter. The filter icon is clicked, "ear2" is
     * typed into the search box, and the dashboard tree is verified to contain only the
     * parent aggregator project and the ear2 Liberty module. All other modules should be
     * hidden. The filter is cleared at the end so subsequent tests see the full dashboard.
     */
    @Test
    public void testDashboardToolbarSearchFilter() {

        // Apply filter.
        filterDashboardByText(bot, "ear2");

        // Wait for the dashboard to refresh its visible items.
        SWTBotTestCondition.waitFor(() -> {
            List<String> content = getDashboardContent();
            // Expect: parent project + ear2 child module only.
            return content.size() == 2;
        }, SWTBotTestCondition.MIN_WAIT_MS);

        List<String> filteredContent = getDashboardContent();

        Assertions.assertTrue(filteredContent.contains(MVN_APP_NAME),
                              "The parent aggregator project " + MVN_APP_NAME + " should be visible after filtering by 'ear2'.");
        Assertions.assertTrue(filteredContent.contains(MVN_EAR2_MODULE_NAME),
                              "Module " + MVN_EAR2_MODULE_NAME + " should be visible after filtering by 'ear2'.");
        Assertions.assertFalse(filteredContent.contains(MVN_EAR1_MODULE_NAME),
                               "Module " + MVN_EAR1_MODULE_NAME + " should not be visible after filtering by 'ear2'.");
        Assertions.assertFalse(filteredContent.contains(MVN_EAR_SKINNY_MODULE_NAME),
                               "Module " + MVN_EAR_SKINNY_MODULE_NAME + " should not be visible after filtering by 'ear2'.");
        Assertions.assertEquals(2, filteredContent.size(),
                                "Dashboard should show exactly 2 items (parent + ear2) after filtering, but found: " + filteredContent);

        // Clear the filter so the full dashboard is restored for subsequent tests.
        clearDashboardFilter(bot);

        SWTBotTestCondition.waitFor(() -> {
            List<String> content = getDashboardContent();
            return content.contains(MVN_EAR1_MODULE_NAME) && content.contains(MVN_EAR_SKINNY_MODULE_NAME);
        }, SWTBotTestCondition.MIN_WAIT_MS);
    }

    /**
     * Tests the Collapse All toolbar button in the Liberty dashboard. After collapsing,
     * none of the root-level tree items should have expanded children visible in the tree.
     */
    @Test
    public void testDashboardCollapseAll() {

        // Ensure the tree is expanded first so there is something to collapse.
        expandDashboard(bot);
        SWTBotTestCondition.waitFor(() -> {
            SWTBotPluginOperations.getDashboardTree().getAllItems()[0].isExpanded();
            return SWTBotPluginOperations.getDashboardTree().getAllItems()[0].isExpanded();
        }, SWTBotTestCondition.MIN_WAIT_MS);

        // Collapse all.
        collapseDashboard(bot);

        // Wait for the collapse to take effect.
        SWTBotTestCondition.waitFor(() -> {
            for (org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem item : SWTBotPluginOperations.getDashboardTree().getAllItems()) {
                if (item.isExpanded()) {
                    return false;
                }
            }
            return true;
        }, SWTBotTestCondition.MIN_WAIT_MS);

        // Verify that no root-level item is expanded.
        for (org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem item : SWTBotPluginOperations.getDashboardTree().getAllItems()) {
            Assertions.assertFalse(item.isExpanded(),
                                   "Dashboard tree item '" + item.getText() + "' should be collapsed after Collapse All, but it is expanded.");
        }
    }

    /**
     * Tests the Expand All toolbar button in the Liberty dashboard. After expanding,
     * the parent aggregator tree item should be expanded and its Liberty module children
     * should be visible.
     */
    @Test
    public void testDashboardExpandAll() {

        // Ensure the tree is collapsed first so there is something to expand.
        collapseDashboard(bot);
        SWTBotTestCondition.waitFor(() -> {
            for (org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem item : SWTBotPluginOperations.getDashboardTree().getAllItems()) {
                if (item.isExpanded()) {
                    return false;
                }
            }
            return true;
        }, SWTBotTestCondition.MIN_WAIT_MS);

        // Expand all.
        expandDashboard(bot);

        // Wait for the parent item to become expanded.
        SWTBotTestCondition.waitFor(() -> {
            for (org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem item : SWTBotPluginOperations.getDashboardTree().getAllItems()) {
                if (MVN_APP_NAME.equals(item.getText()) && item.isExpanded()) {
                    return true;
                }
            }
            return false;
        }, SWTBotTestCondition.MIN_WAIT_MS);

        // Verify that the parent item is expanded and its children are visible.
        boolean parentExpanded = false;
        List<String> visibleChildren = new ArrayList<>();
        for (org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem item : SWTBotPluginOperations.getDashboardTree().getAllItems()) {
            if (MVN_APP_NAME.equals(item.getText())) {
                parentExpanded = item.isExpanded();
                for (org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem child : item.getItems()) {
                    visibleChildren.add(child.getText());
                }
                break;
            }
        }

        Assertions.assertTrue(parentExpanded,
                              "The parent project " + MVN_APP_NAME + " should be expanded after Expand All.");
        Assertions.assertTrue(visibleChildren.contains(MVN_EAR1_MODULE_NAME),
                              "After Expand All, " + MVN_EAR1_MODULE_NAME + " should be visible as a child of " + MVN_APP_NAME + ".");
        Assertions.assertTrue(visibleChildren.contains(MVN_EAR2_MODULE_NAME),
                              "After Expand All, " + MVN_EAR2_MODULE_NAME + " should be visible as a child of " + MVN_APP_NAME + ".");
        Assertions.assertTrue(visibleChildren.contains(MVN_EAR_SKINNY_MODULE_NAME),
                              "After Expand All, " + MVN_EAR_SKINNY_MODULE_NAME + " should be visible as a child of " + MVN_APP_NAME + ".");
    }

    /**
     * Tests the restart of an externally started dev mode process for a single Liberty
     * module in a multi-module Maven project. The scenario is:
     *
     * 1. Start ear1 externally using the Maven -pl and -am flags so that Liberty Tools
     * has no knowledge of the running process.
     * 2. Trigger the dashboard Start action from the parent aggregator project. The module
     * selection dialog appears because all modules are in scope. Select ear1.
     * 3. Liberty Tools detects that ear1 is already running and opens a Yes/No dialog.
     * Click Yes to confirm the stop and restart.
     * 4. Validate that the server was genuinely restarted by checking that messages.log
     * is newer than the timestamp recorded before the restart was triggered.
     * 5. Stop ear1 from the parent using the dashboard Stop action. Since only one module
     * is active, no module selection dialog is expected. Validate that dev mode stopped.
     *
     * @throws CommandNotFoundException If the Maven command cannot be constructed.
     * @throws IOException              If the external process cannot be started.
     * @throws InterruptedException     If the process wait is interrupted.
     */
    @Test
    public void testRestartOfExternallyStartedDevMode() throws IOException, InterruptedException {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        Path rootPath = rootProjectPath.toAbsolutePath();

        // Start ear1 externally using -pl ear1 -am so that Liberty Tools has no
        // knowledge of the running process.
        String startDevModeCmd = "io.openliberty.tools:liberty-maven-plugin:dev -pl ear1 -am -DskipITs=true";
        if (LibertyPluginTestUtils.onWindows()) {
            startDevModeCmd = "cmd.exe /c mvn " + startDevModeCmd;
        } else {
            startDevModeCmd = "mvn " + startDevModeCmd;
        }

        String[] startDMCmdParts = startDevModeCmd.split(" ");
        ProcessBuilder starDMPB = new ProcessBuilder(startDMCmdParts).inheritIO().directory(rootPath.toFile()).redirectErrorStream(true);
        starDMPB.environment().put("JAVA_HOME", JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath());

        Process startDMProcess = starDMPB.start();
        startDMProcess.waitFor(3, TimeUnit.SECONDS);

        // Validate that ear1 is up and running outside of Liberty Tools.
        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9080/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        boolean devModeStopped = false;
        try {
            // Record the messages.log timestamp before triggering the restart. This is used
            // after the restart to prove that the server genuinely restarted and was not
            // simply left running from the original external process.
            long timestampBeforeRestart = new File(ear1ServerPath.toString()
                                                   + "/wlp/usr/servers/defaultServer/logs/messages.log").lastModified();

            // Trigger the Start action from the parent dashboard. A module selection dialog
            // appears showing all inactive modules. Select ear1.
            launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);

            Shell moduleDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
            Assertions.assertNotNull(moduleDialog,
                                     "Module selection dialog did not open when triggering Start with ear1 running externally.");
            selectModuleInDialog(moduleDialog, MVN_EAR1_MODULE_NAME);

            // Liberty Tools detects that ear1 is already running. Wait for the Yes/No dialog
            // and confirm the restart.
            try {
                waitForAndClickButton(bot, "Liberty Tools", "Yes", SWTBotTestCondition.SERVER_WAIT_MS);
            } catch (org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException e) {
                System.out.println("[testRestartOfExternallyStartedDevMode] Eclipse console output before dialog failure:\n"
                                   + LibertyPluginTestUtils.getConsoleOutput());
                throw e;
            }

            // Validate that the server came back up under Liberty Tools.
            LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                    "http://localhost:9080/converter/heights.jsp?heightCm=10", true,
                                                                    "Height in feet and inches", ear1ServerPath.toString());

            // Validate that messages.log is newer than before the restart was triggered.
            // A newer timestamp proves the server process was genuinely restarted and is not
            // the original externally started instance still running.
            LibertyPluginTestUtils.validateMessagesLogIsNewer(ear1ServerPath.toString(), timestampBeforeRestart);

            pressWorkspaceErrorDialogProceedButton(bot);

            // Stop ear1 from the parent dashboard Stop action. Only one module is active so
            // no module selection dialog is expected and ear1 stops directly.
            launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

            LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
            devModeStopped = true;
        } finally {
            if (!devModeStopped) {
                String stopDevModeCmd = "io.openliberty.tools:liberty-maven-plugin:stop -pl ear1 -am";
                if (LibertyPluginTestUtils.onWindows()) {
                    stopDevModeCmd = "cmd.exe /c mvn " + stopDevModeCmd;
                } else {
                    stopDevModeCmd = "mvn " + stopDevModeCmd;
                }

                String[] stopDMCmdParts = stopDevModeCmd.split(" ");
                ProcessBuilder stopDMPB = new ProcessBuilder(stopDMCmdParts).inheritIO().directory(rootPath.toFile()).redirectErrorStream(true);
                stopDMPB.environment().put("JAVA_HOME", JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath());

                Process stopDMProcess = stopDMPB.start();
                stopDMProcess.waitFor(3, TimeUnit.SECONDS);

                LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
            }

        }
    }
}
