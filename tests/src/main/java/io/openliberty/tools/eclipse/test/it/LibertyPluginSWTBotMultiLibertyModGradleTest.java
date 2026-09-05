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
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.clickDeselectAllInModuleSelectionDialog;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.clickSelectAllInModuleSelectionDialog;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.collapseDashboard;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.deleteLibertyToolsRunConfigEntriesFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.expandDashboard;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.filterDashboardByText;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDashboardContent;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDashboardItemMenuActions;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getLibertyTreeItem;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getModuleSelectionDialogCheckedItemCount;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getModuleSelectionDialogItemCount;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getModuleSelectionDialogItems;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getRunConfigurationsShell;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDashboardAction;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDebugConfigurationsDialogFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchRunConfigurationsDialogFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchRunTestsWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStartWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStopWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.selectAllModulesAndConfirmInDialog;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.selectModuleInDialog;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.typeInModuleSelectionDialogFilter;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.waitForAndClickButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.waitForModuleSelectionDialog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotTestCondition;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;

/**
 * Tests Open Liberty Eclipse plugin functions for Gradle multi-module projects
 * that contain multiple Liberty modules. The tests verify that the module
 * selection dialog opens correctly for every supported action, that the dialog
 * lists the expected Liberty modules, and that start and stop operations
 * complete successfully when a module is chosen.
 *
 * NOTE: The multi-module selection support for actions such as Start,
 * Start in container, Run tests, and Stop, will remember a prior selection.
 * This means that tests that require specific modules to be selected must
 * deselect all modules first.
 */
public class LibertyPluginSWTBotMultiLibertyModGradleTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * The Eclipse project name of the root aggregator project as registered by Buildship.
     * This matches the rootProject.name declared in settings.gradle.
     */
    static final String GRADLE_APP_NAME = "guide-gradle-multimodules-custmm";

    /**
     * Eclipse project name of the root aggregator project.
     */
    static final String PACKAGE_EXPLORER_PROJECT_NAME = "multi-liberty-module-gradle-app";

    /**
     * Name of the first Liberty ear module.
     */
    static final String GRADLE_EAR1_MODULE_NAME = "ear1";

    /**
     * Name of the second Liberty ear module.
     */
    static final String GRADLE_EAR2_MODULE_NAME = "ear2";

    /**
     * Name of the third Liberty ear module that uses skinny modules.
     */
    static final String GRADLE_EAR_SKINNY_MODULE_NAME = "ear-skinny-modules";

    /**
     * Expected number of Liberty child modules exposed by the selection dialog.
     */
    static final int EXPECTED_LIBERTY_MODULE_COUNT_ALL = 3;

    /**
     * Path to the root project directory imported into the workspace.
     */
    static final Path rootProjectPath = Paths.get("resources", "applications", "gradle", "multi-liberty-module-gradle-app");

    /**
     * Path to the Liberty server output directory for the ear1 module. Used to
     * confirm that the server started or stopped.
     */
    static final Path ear1ServerPath = rootProjectPath.resolve("ear1").resolve("build");

    /**
     * Path to the Liberty server output directory for the ear2 module. Used to
     * confirm that the server started or stopped.
     */
    static final Path ear2ServerPath = rootProjectPath.resolve("ear2").resolve("build");

    /**
     * Path to the Liberty server output directory for the ear-skinny-modules module. Used to
     * confirm that the server started or stopped.
     */
    static final Path earSkinnyServerPath = rootProjectPath.resolve("ear-skinny-modules").resolve("build");

    /**
     * Expected dashboard menu items for the root aggregator project.
     */
    static final String[] gradleMenuItems = new String[] {
                                                           DashboardView.APP_MENU_ACTION_START,
                                                           DashboardView.APP_MENU_ACTION_START_CONFIG,
                                                           DashboardView.APP_MENU_ACTION_START_IN_CONTAINER,
                                                           DashboardView.APP_MENU_ACTION_DEBUG,
                                                           DashboardView.APP_MENU_ACTION_DEBUG_CONFIG,
                                                           DashboardView.APP_MENU_ACTION_DEBUG_IN_CONTAINER,
                                                           DashboardView.APP_MENU_ACTION_STOP,
                                                           DashboardView.APP_MENU_ACTION_RUN_TESTS,
                                                           DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT
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
                                                          LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT
    };

    /** Projects imported into the workspace during setup. */
    static ArrayList<File> projectsToInstall = new ArrayList<File>();

    /**
     * Imports the multi-liberty-module-gradle-app projects and validates that the
     * plugin is in a ready state before any test runs.
     *
     * @throws Exception If setup fails.
     */
    @BeforeAll
    public static void setup() throws Exception {
        commonSetup();

        File rootProject = rootProjectPath.toFile();
        projectsToInstall.add(rootProject);

        for (File p : projectsToInstall) {
            cleanupProject(p.toString());
        }

        importGradleApplications(projectsToInstall);

        setBuildCmdPathInPreferences(bot, "Gradle");
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
     * Removes imported projects and resets the Gradle preference after all tests complete.
     */
    @AfterAll
    public static void cleanup() {
        for (File p : projectsToInstall) {
            cleanupProject(p.toString());
        }
        unsetBuildCmdPathInPreferences(bot, "Gradle");
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
                                    () -> getDashboardContent().contains(GRADLE_APP_NAME), SWTBotTestCondition.LARGE_WAIT_MS);

        List<String> projectList = getDashboardContent();
        boolean foundApp = false;
        for (String project : projectList) {
            if (GRADLE_APP_NAME.equals(project)) {
                foundApp = true;
                break;
            }
        }
        Assertions.assertTrue(foundApp, () -> "The dashboard does not contain expected application: " + GRADLE_APP_NAME);

        List<String> menuItems = getDashboardItemMenuActions(GRADLE_APP_NAME);
        Assertions.assertTrue(menuItems.size() == gradleMenuItems.length,
                              () -> "Gradle application " + GRADLE_APP_NAME + " does not contain the expected number of menu items: " + gradleMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(gradleMenuItems)),
                              () -> "Gradle application " + GRADLE_APP_NAME + " does not contain the expected menu items: " + Arrays.toString(gradleMenuItems));

        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, PACKAGE_EXPLORER_PROJECT_NAME);
        Assertions.assertNotNull(runAsMenu, "The Run As menu associated with project " + GRADLE_APP_NAME + " is null.");
        List<String> runAsMenuItems = runAsMenu.menuItems();
        Assertions.assertTrue(runAsMenuItems != null && !runAsMenuItems.isEmpty(),
                              "The Run As menu associated with project " + GRADLE_APP_NAME + " is null or empty.");

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
                              "The Run As menu associated with project " + GRADLE_APP_NAME
                                                                   + " does not contain one or more expected entries. Expected: " + runAsShortcuts.length
                                                                   + ", found: " + foundItems + ". Items: " + runAsMenuItems);

        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);
        try {
            SWTBotTreeItem runAsLibertyEntry = getLibertyTreeItem(configShell);
            Assertions.assertNotNull(runAsLibertyEntry, "Liberty entry in Run Configurations view was not found.");
        } finally {
            go("Close", configShell);
        }

        Shell debugShell = launchDebugConfigurationsDialogFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);
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
            launchDashboardAction(GRADLE_APP_NAME, action);

            Shell dialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
            Assertions.assertNotNull(dialog,
                                     "Module selection dialog did not open for dashboard action: " + action);

            int count = getModuleSelectionDialogItemCount(dialog);
            List<String> items = getModuleSelectionDialogItems(dialog);
            Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, count,
                                    "Dashboard action '" + action + "' should list " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                              + " Liberty modules in the selection dialog, but found " + count + ". Items: " + items);

            Assertions.assertTrue(items.contains(GRADLE_EAR1_MODULE_NAME),
                                  "Selection dialog for action '" + action + "' is missing module: " + GRADLE_EAR1_MODULE_NAME);
            Assertions.assertTrue(items.contains(GRADLE_EAR2_MODULE_NAME),
                                  "Selection dialog for action '" + action + "' is missing module: " + GRADLE_EAR2_MODULE_NAME);
            Assertions.assertTrue(items.contains(GRADLE_EAR_SKINNY_MODULE_NAME),
                                  "Selection dialog for action '" + action + "' is missing module: " + GRADLE_EAR_SKINNY_MODULE_NAME);

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
        launchStartWithRunAsShortcut(PACKAGE_EXPLORER_PROJECT_NAME);
        Shell startDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog,
                                 "Module selection dialog did not open for the Run As Start shortcut.");
        int startCount = getModuleSelectionDialogItemCount(startDialog);
        List<String> startItems = getModuleSelectionDialogItems(startDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, startCount,
                                "Run As Start shortcut should list " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                               + " Liberty modules, but found " + startCount + ". Items: " + startItems);
        cancelModuleSelectionDialog(startDialog);

        // Verify the Start in Container shortcut.
        SWTBotMenu containerMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, PACKAGE_EXPLORER_PROJECT_NAME).menu(WidgetMatcherFactory.withRegex(
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
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, containerCount,
                                "Run As Start in Container shortcut should list " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                                   + " Liberty modules, but found " + containerCount + ". Items: " + containerItems);
        cancelModuleSelectionDialog(containerDialog);
    }

    /**
     * Tests the Start/Stop dashboard actions. More precisely, it tests that the module
     * selection dialog opens when the start action is selected.
     * After selecting the module, the Liberty server starts for the selected module.
     * Last, when the stop action is issued through the dashboard, the server is stopped.
     */
    @Test
    public void testDashboardStartActionSelectsModuleAndStops() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);

        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        Shell startDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog, "Module selection dialog did not open for the Start dashboard action.");

        int count = getModuleSelectionDialogItemCount(startDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, count,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT_ALL + " Liberty modules in the selection dialog, but found " + count + ".");

        clickDeselectAllInModuleSelectionDialog(startDialog);
        int startAfterDeselectAll = getModuleSelectionDialogCheckedItemCount(startDialog);
        Assertions.assertEquals(0, startAfterDeselectAll,
                                "After clicking Deselect All in the Start dialog 0 items should be checked, but "
                                                          + startAfterDeselectAll + " were still checked.");

        selectModuleInDialog(startDialog, GRADLE_EAR1_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9090/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
    }

    /**
     * Tests the Debug dashboard action. More precisely, test that the module selection dialog
     * opens, a module is selected, the Liberty server starts in debug mode for that
     * module, and when the stop action is issued from the dashboard the server is stopped.
     */
    @Test
    public void testDashboardDebugActionSelectsModuleAndStops() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);

        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

        Shell debugDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(debugDialog, "Module selection dialog did not open for the Debug dashboard action.");

        int count = getModuleSelectionDialogItemCount(debugDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, count,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT_ALL + " Liberty modules in the selection dialog, but found " + count + ".");

        selectModuleInDialog(debugDialog, GRADLE_EAR1_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9090/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

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

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);

        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START_CONFIG);

        Shell dialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(dialog,
                                 "Module selection dialog did not open after pressing Run in the Run Configurations dialog.");

        int count = getModuleSelectionDialogItemCount(dialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, count,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT_ALL + " Liberty modules in the selection dialog, but found " + count + ".");

        selectModuleInDialog(dialog, GRADLE_EAR1_MODULE_NAME);

        Shell configShell = getRunConfigurationsShell();
        Assertions.assertNotNull(configShell, "Run Configurations dialog did not open.");

        go("Run", configShell);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9090/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
    }

    /**
     * Tests the Run As Start shortcut end-to-end. More precisely, test that the module
     * selection dialog opens, a module is selected, the Liberty server starts, and
     * when the stop action is issued from the Run As context menu, the server
     * is stopped.
     */
    @Test
    public void testRunAsStartShortcutSelectsModuleAndStops() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);

        launchStartWithRunAsShortcut(PACKAGE_EXPLORER_PROJECT_NAME);

        Shell startDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog,
                                 "Module selection dialog did not open for the Run As Start shortcut.");

        int count = getModuleSelectionDialogItemCount(startDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, count,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT_ALL + " Liberty modules in the selection dialog, but found " + count + ".");

        clickDeselectAllInModuleSelectionDialog(startDialog);
        int startAfterDeselectAll = getModuleSelectionDialogCheckedItemCount(startDialog);
        Assertions.assertEquals(0, startAfterDeselectAll,
                                "After clicking Deselect All in the Start dialog 0 items should be checked, but "
                                                          + startAfterDeselectAll + " were still checked.");

        selectModuleInDialog(startDialog, GRADLE_EAR1_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9090/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        launchStopWithRunAsShortcut(PACKAGE_EXPLORER_PROJECT_NAME);

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

        launchStartWithRunAsShortcut(PACKAGE_EXPLORER_PROJECT_NAME);

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

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);

        // Step 1: Start ear1. All 3 modules should be inactive.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        Shell startDialog1 = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog1, "Module selection dialog did not open when starting ear1.");
        int countBeforeFirstStart = getModuleSelectionDialogItemCount(startDialog1);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, countBeforeFirstStart,
                                "Before any module is started, the dialog should show " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                                          + " inactive modules, but found " + countBeforeFirstStart + ".");

        clickDeselectAllInModuleSelectionDialog(startDialog1);
        int startAfterDeselectAll1 = getModuleSelectionDialogCheckedItemCount(startDialog1);
        Assertions.assertEquals(0, startAfterDeselectAll1,
                                "After clicking Deselect All in the Start dialog 0 items should be checked, but "
                                                           + startAfterDeselectAll1 + " were still checked.");

        selectModuleInDialog(startDialog1, GRADLE_EAR1_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9090/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());
        pressWorkspaceErrorDialogProceedButton(bot);

        // Step 2: Start ear2. Only 2 inactive modules should remain (ear2 and ear-skinny-modules).
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);
        Shell startDialog2 = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog2, "Module selection dialog did not open when starting ear2.");
        int countAfterFirstStart = getModuleSelectionDialogItemCount(startDialog2);
        Assertions.assertEquals(2, countAfterFirstStart,
                                "After ear1 is running, the dialog should show 2 inactive modules, but found " + countAfterFirstStart + ".");

        clickDeselectAllInModuleSelectionDialog(startDialog2);
        int startAfterDeselectAll2 = getModuleSelectionDialogCheckedItemCount(startDialog2);
        Assertions.assertEquals(0, startAfterDeselectAll1,
                                "After clicking Deselect All in the Start dialog 0 items should be checked, but "
                                                           + startAfterDeselectAll2 + " were still checked.");

        selectModuleInDialog(startDialog2, GRADLE_EAR2_MODULE_NAME);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9091/converter/heights.jsp?heightCm=20", true,
                                                                "Height in feet and inches", ear2ServerPath.toString());
        pressWorkspaceErrorDialogProceedButton(bot);

        // Step 3: Stop from the parent. Both ear1 and ear2 are active so the dialog should
        // show 2 active modules. Select ear2 to stop it.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
        Shell stopDialog1 = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(stopDialog1,
                                 "Module selection dialog did not open for Stop when both ear1 and ear2 are active.");
        int activeCount = getModuleSelectionDialogItemCount(stopDialog1);
        List<String> activeItems = getModuleSelectionDialogItems(stopDialog1);
        Assertions.assertEquals(2, activeCount,
                                "With ear1 and ear2 running, the Stop dialog should show 2 active modules, but found "
                                                + activeCount + ". Items: " + activeItems);
        Assertions.assertTrue(activeItems.contains(GRADLE_EAR1_MODULE_NAME),
                              "Stop dialog should list " + GRADLE_EAR1_MODULE_NAME + " as active.");
        Assertions.assertTrue(activeItems.contains(GRADLE_EAR2_MODULE_NAME),
                              "Stop dialog should list " + GRADLE_EAR2_MODULE_NAME + " as active.");

        clickDeselectAllInModuleSelectionDialog(stopDialog1);
        int stopAfterDeselectAll1 = getModuleSelectionDialogCheckedItemCount(stopDialog1);
        Assertions.assertEquals(0, stopAfterDeselectAll1,
                                "After clicking Deselect All in the Start dialog 0 items should be checked, but "
                                                          + stopAfterDeselectAll1 + " were still checked.");

        selectModuleInDialog(stopDialog1, GRADLE_EAR2_MODULE_NAME);

        LibertyPluginTestUtils.validateLibertyServerStopped(ear2ServerPath.toString());

        // Step 4: Stop from the parent again. Only ear1 is active so no selection dialog
        // should appear and ear1 should stop directly.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
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

        try {
            Assertions.assertTrue(filteredContent.contains(GRADLE_APP_NAME),
                                  "The parent aggregator project " + GRADLE_APP_NAME + " should be visible after filtering by 'ear2'.");
            Assertions.assertTrue(filteredContent.contains(GRADLE_EAR2_MODULE_NAME),
                                  "Module " + GRADLE_EAR2_MODULE_NAME + " should be visible after filtering by 'ear2'.");
            Assertions.assertFalse(filteredContent.contains(GRADLE_EAR1_MODULE_NAME),
                                   "Module " + GRADLE_EAR1_MODULE_NAME + " should not be visible after filtering by 'ear2'.");
            Assertions.assertFalse(filteredContent.contains(GRADLE_EAR_SKINNY_MODULE_NAME),
                                   "Module " + GRADLE_EAR_SKINNY_MODULE_NAME + " should not be visible after filtering by 'ear2'.");
        } finally {
            // Clear the filter so the full dashboard is restored for subsequent tests.
            clearDashboardFilter(bot);

            SWTBotTestCondition.waitFor(() -> {
                List<String> content = getDashboardContent();
                return content.contains(GRADLE_EAR1_MODULE_NAME) && content.contains(GRADLE_EAR_SKINNY_MODULE_NAME);
            }, SWTBotTestCondition.MIN_WAIT_MS);
        }
    }

    /**
     * Tests the Collapse All toolbar button in the Liberty dashboard. After collapsing,
     * none of the root-level tree items should have expanded children visible in the tree.
     */
    @Test
    public void testDashboardCollapseAll() {

        // Expand the tree first so there is something to collapse, then collapse it.
        Assertions.assertTrue(expandDashboard(bot, GRADLE_APP_NAME),
                              "Timed out waiting for the dashboard tree to expand before Collapse All.");
        Assertions.assertTrue(collapseDashboard(bot, GRADLE_APP_NAME),
                              "Timed out waiting for all dashboard tree items to collapse after Collapse All.");
    }

    /**
     * Tests the Expand All toolbar button in the Liberty dashboard. After expanding,
     * the parent aggregator tree item should be expanded and its Liberty module children
     * should be visible.
     */
    @Test
    public void testDashboardExpandAll() {

        // Collapse the tree first so there is something to expand, then expand it.
        Assertions.assertTrue(collapseDashboard(bot, GRADLE_APP_NAME),
                              "Timed out waiting for all dashboard tree items to collapse before Expand All.");
        Assertions.assertTrue(expandDashboard(bot, GRADLE_APP_NAME),
                              "Timed out waiting for " + GRADLE_APP_NAME + " to expand after Expand All.");

        // Collect the visible children of the parent item.
        List<String> visibleChildren = new ArrayList<>();
        for (org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem item : SWTBotPluginOperations.getDashboardTree().getAllItems()) {
            if (GRADLE_APP_NAME.equals(SWTBotPluginOperations.getTreeItemNameText(item))) {
                for (org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem child : item.getItems()) {
                    visibleChildren.add(SWTBotPluginOperations.getTreeItemNameText(child));
                }
                break;
            }
        }

        Assertions.assertTrue(visibleChildren.contains(GRADLE_EAR1_MODULE_NAME),
                              "After Expand All, " + GRADLE_EAR1_MODULE_NAME + " should be visible as a child of " + GRADLE_APP_NAME + ".");
        Assertions.assertTrue(visibleChildren.contains(GRADLE_EAR2_MODULE_NAME),
                              "After Expand All, " + GRADLE_EAR2_MODULE_NAME + " should be visible as a child of " + GRADLE_APP_NAME + ".");
        Assertions.assertTrue(visibleChildren.contains(GRADLE_EAR_SKINNY_MODULE_NAME),
                              "After Expand All, " + GRADLE_EAR_SKINNY_MODULE_NAME + " should be visible as a child of " + GRADLE_APP_NAME + ".");

        // Verify that non-Liberty sub-modules are absent from the expanded child list.
        // The DashboardContentProvider filters them out; they must not appear as tree children.
        String[] nonLibertyChildren = new String[] { "jar", "ejb", "war", "war2", "rar" };
        for (String nonLibertyChild : nonLibertyChildren) {
            Assertions.assertFalse(visibleChildren.contains(nonLibertyChild),
                                   "After Expand All, non-Liberty module " + nonLibertyChild
                                                                              + " should not be visible as a child of " + GRADLE_APP_NAME + ".");
        }

        // Only the three Liberty EAR modules should be present.
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, visibleChildren.size(),
                                "After Expand All, the parent " + GRADLE_APP_NAME + " should have exactly "
                                                                                           + EXPECTED_LIBERTY_MODULE_COUNT_ALL + " children, but found: " + visibleChildren);
    }

    /**
     * Tests the restart of an externally started dev mode process for a single Liberty
     * module in a multi-module Gradle project. The scenario is:
     *
     * 1. Start ear1 externally using the Gradle wrapper so that Liberty Tools
     * has no knowledge of the running process.
     * 2. Trigger the dashboard Start action from the parent aggregator project. The module
     * selection dialog appears because all modules are in scope. Select ear1.
     * 3. Liberty Tools detects that ear1 is already running and opens a Yes/No dialog.
     * Click Yes to confirm the stop and restart.
     * 4. Validate that the server was genuinely restarted by checking that the application
     * is reachable.
     * 5. Stop ear1 from the parent using the dashboard Stop action. Since only one module
     * is active, no module selection dialog is expected. Validate that dev mode stopped.
     *
     * @throws IOException          If the external process cannot be started.
     * @throws InterruptedException If the process wait is interrupted.
     */
    @Test
    public void testRestartOfExternallyStartedDevMode() throws IOException, InterruptedException {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);

        Path rootPath = rootProjectPath.toAbsolutePath();

        // Start ear1 externally using the Gradle wrapper so that Liberty Tools has no
        // knowledge of the running process.
        String gradlewCmd;
        if (LibertyPluginTestUtils.onWindows()) {
            gradlewCmd = "cmd.exe /c gradlew.bat :ear1:libertyDev -DskipTests=true";
        } else {
            gradlewCmd = "./gradlew :ear1:libertyDev -DskipTests=true";
        }

        String[] startDMCmdParts = gradlewCmd.split(" ");
        ProcessBuilder starDMPB = new ProcessBuilder(startDMCmdParts).inheritIO().directory(rootPath.toFile()).redirectErrorStream(true);
        starDMPB.environment().put("JAVA_HOME", JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath());

        Process startDMProcess = starDMPB.start();
        startDMProcess.waitFor(3, TimeUnit.SECONDS);

        // Validate that ear1 is up and running outside of Liberty Tools.
        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9090/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());

        boolean devModeStopped = false;
        try {
            // Trigger the Start action from the parent dashboard. A module selection dialog
            // appears showing all inactive modules. Select ear1.
            launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);

            Shell startDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
            Assertions.assertNotNull(startDialog,
                                     "Module selection dialog did not open when triggering Start with ear1 running externally.");

            clickDeselectAllInModuleSelectionDialog(startDialog);
            int startAfterDeselectAll = getModuleSelectionDialogCheckedItemCount(startDialog);
            Assertions.assertEquals(0, startAfterDeselectAll,
                                    "After clicking Deselect All in the Start dialog 0 items should be checked, but "
                                                              + startAfterDeselectAll + " were still checked.");

            selectModuleInDialog(startDialog, GRADLE_EAR1_MODULE_NAME);

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
                                                                    "http://localhost:9090/converter/heights.jsp?heightCm=10", true,
                                                                    "Height in feet and inches", ear1ServerPath.toString());

            pressWorkspaceErrorDialogProceedButton(bot);

            // Stop ear1 from the parent dashboard Stop action. Only one module is active so
            // no module selection dialog is expected and ear1 stops directly.
            launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

            LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
            devModeStopped = true;
        } finally {
            if (!devModeStopped) {
                String stopCmd;
                if (LibertyPluginTestUtils.onWindows()) {
                    stopCmd = "cmd.exe /c gradlew.bat :ear1:libertyStop";
                } else {
                    stopCmd = "./gradlew :ear1:libertyStop";
                }

                String[] stopDMCmdParts = stopCmd.split(" ");
                ProcessBuilder stopDMPB = new ProcessBuilder(stopDMCmdParts).inheritIO().directory(rootPath.toFile()).redirectErrorStream(true);
                stopDMPB.environment().put("JAVA_HOME", JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath());

                Process stopDMProcess = stopDMPB.start();
                stopDMProcess.waitFor(3, TimeUnit.SECONDS);

                LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
            }
        }
    }

    /**
     * Tests that the multi-module selection is enabled for the following actions:
     * Start, and Start in Container actions.
     * Note: Run Tests and Stop are not tested here because they require dev mode to have been started
     * in order for the actions to be enabled.
     */
    @Test
    public void testModuleSelectionDialogSelectAllAndDeselectAllButtons() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);

        // Start action.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        Shell startDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog,
                                 "Module selection dialog did not open for the Start dashboard action.");

        try {
            int startTotal = getModuleSelectionDialogItemCount(startDialog);
            Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, startTotal,
                                    "Start dialog should list " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                                   + " modules, but found " + startTotal + ".");

            clickSelectAllInModuleSelectionDialog(startDialog);
            int startAfterSelectAll = getModuleSelectionDialogCheckedItemCount(startDialog);
            Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, startAfterSelectAll,
                                    "After clicking Select All in the Start dialog all " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                                            + " items should be checked, but " + startAfterSelectAll + " were checked.");

            clickDeselectAllInModuleSelectionDialog(startDialog);
            int startAfterDeselectAll = getModuleSelectionDialogCheckedItemCount(startDialog);
            Assertions.assertEquals(0, startAfterDeselectAll,
                                    "After clicking Deselect All in the Start dialog 0 items should be checked, but "
                                                              + startAfterDeselectAll + " were still checked.");
        } finally {
            cancelModuleSelectionDialog(startDialog);
        }

        // Start in Container action.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START_IN_CONTAINER);

        Shell startCtrDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startCtrDialog,
                                 "Module selection dialog did not open for the Start in Container dashboard action.");

        try {
            int startCtrTotal = getModuleSelectionDialogItemCount(startCtrDialog);
            Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, startCtrTotal,
                                    "Start in Container dialog should list " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                                      + " modules, but found " + startCtrTotal + ".");

            clickSelectAllInModuleSelectionDialog(startCtrDialog);
            int startCtrAfterSelectAll = getModuleSelectionDialogCheckedItemCount(startCtrDialog);
            Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, startCtrAfterSelectAll,
                                    "After clicking Select All in the Start in Container dialog all " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                                               + " items should be checked, but " + startCtrAfterSelectAll + " were checked.");

            clickDeselectAllInModuleSelectionDialog(startCtrDialog);
            int startCtrAfterDeselectAll = getModuleSelectionDialogCheckedItemCount(startCtrDialog);
            Assertions.assertEquals(0, startCtrAfterDeselectAll,
                                    "After clicking Deselect All in the Start dialog 0 items should be checked, but "
                                                                 + startCtrAfterDeselectAll + " were still checked.");

        } finally {
            cancelModuleSelectionDialog(startCtrDialog);
        }
    }

    /**
     * Tests starting and stopping multiple child modules at once.
     */
    @Test
    public void testStartAllModulesWithSelectAllAndStopAll() {

        deleteLibertyToolsRunConfigEntriesFromAppRunAs(PACKAGE_EXPLORER_PROJECT_NAME);

        // Trigger the Start action from the parent project.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        Shell startDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(startDialog,
                                 "Module selection dialog did not open for the Start dashboard action.");

        int startCount = getModuleSelectionDialogItemCount(startDialog);
        Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, startCount,
                                "Expected " + EXPECTED_LIBERTY_MODULE_COUNT_ALL
                                                                               + " Liberty modules in the Start dialog, but found " + startCount + ".");

        // Select all modules and confirm to start them all at once.
        selectAllModulesAndConfirmInDialog(startDialog);

        // Validate that ear1, ear2, and ear-skinny-modules are serving the expected response.
        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9090/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", ear1ServerPath.toString());
        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9091/converter/heights.jsp?heightCm=20", true,
                                                                "Height in feet and inches", ear2ServerPath.toString());
        LibertyPluginTestUtils.validateApplicationOutcomeCustom(
                                                                "http://localhost:9093/converter/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", earSkinnyServerPath.toString());

        pressWorkspaceErrorDialogProceedButton(bot);

        // Run Tests action should show the Select All and Deselect All buttons.
        launchRunTestsWithRunAsShortcut(PACKAGE_EXPLORER_PROJECT_NAME);

        Shell runTestsDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(runTestsDialog,
                                 "Module selection dialog did not open for the Run Tests shortcut.");

        try {
            int runTestsTotal = getModuleSelectionDialogItemCount(runTestsDialog);
            Assertions.assertEquals(EXPECTED_LIBERTY_MODULE_COUNT_ALL, runTestsTotal,
                                    "Run Tests dialog should list " + EXPECTED_LIBERTY_MODULE_COUNT_ALL + " active modules, but found " + runTestsTotal + ".");

            clickDeselectAllInModuleSelectionDialog(runTestsDialog);
            int runTestsAfterDeselectAll = getModuleSelectionDialogCheckedItemCount(runTestsDialog);
            Assertions.assertEquals(0, runTestsAfterDeselectAll,
                                    "After clicking Deselect All in the Run Tests dialog 0 modules should be checked, but "
                                                                 + runTestsAfterDeselectAll + " were still checked.");
        } finally {
            cancelModuleSelectionDialog(runTestsDialog);
        }

        // Trigger the Stop action.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        Shell stopDialog = waitForModuleSelectionDialog(SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertNotNull(stopDialog,
                                 "Module selection dialog did not open for the Stop dashboard action.");

        int stopCount = getModuleSelectionDialogItemCount(stopDialog);
        Assertions.assertTrue(stopCount == EXPECTED_LIBERTY_MODULE_COUNT_ALL,
                              "Stop dialog should list at least " + EXPECTED_LIBERTY_MODULE_COUNT_ALL + " active modules (ear1, ear2, and ear-skinny-modules), but found "
                                                                              + stopCount + ".");

        // Select all active modules and confirm the stop.
        selectAllModulesAndConfirmInDialog(stopDialog);

        // Validate that ear1, ear2, and ear-skinny-modules have stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(ear1ServerPath.toString());
        LibertyPluginTestUtils.validateLibertyServerStopped(ear2ServerPath.toString());
        LibertyPluginTestUtils.validateLibertyServerStopped(earSkinnyServerPath.toString());
    }
}
