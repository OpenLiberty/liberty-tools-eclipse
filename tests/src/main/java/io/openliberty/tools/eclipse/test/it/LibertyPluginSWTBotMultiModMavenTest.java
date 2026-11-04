/*******************************************************************************
 * Copyright (c) 2022, 2026 IBM Corporation and others.
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
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDashboardContent;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDashboardItemMenuActions;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDefaultSourceLookupTreeItemNoBot;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getLibertyTreeItem;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getLibertyTreeItemNoBot;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDebugConfigurationsDialogFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchRunConfigurationsDialogFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStartWithDefaultRunConfigFromAppRunAs;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchStopWithRunAsShortcut;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openSourceTab;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.MethodName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotTestCondition;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.dashboard.OpenLogsMenuContributor;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
@TestMethodOrder(MethodName.class)
public class LibertyPluginSWTBotMultiModMavenTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Application name.
     */
    static final String MVN_APP_NAME = "guide-maven-multimodules-pom";

    /**
     * Parent name.
     */
    static final String MVN_PARENT_NAME = "guide-maven-multimodules";

    /**
     * Jar sub-module name.
     */
    static final String MVN_JAR_NAME = "guide-maven-multimodules-jar";

    /**
     * War sub-module name
     */
    static final String MVN_WAR_NAME = "guide-maven-multimodules-war1";

    /**
     * Path to import from, in this case the multi-module root
     */
    static final Path project1Path = Paths.get("resources", "applications", "maven", "maven-multi-module", "typeJ");
    static final Path serverModule1Path = project1Path.resolve("pom");

    /**
     * Expected menu items.
     */
    static String[] mvnMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START,
                                                  DashboardView.APP_MENU_ACTION_START_CONFIG,
                                                  DashboardView.APP_MENU_ACTION_START_IN_CONTAINER,
                                                  DashboardView.APP_MENU_ACTION_DEBUG,
                                                  DashboardView.APP_MENU_ACTION_DEBUG_CONFIG,
                                                  DashboardView.APP_MENU_ACTION_DEBUG_IN_CONTAINER,
                                                  DashboardView.APP_MENU_ACTION_STOP,
                                                  DashboardView.APP_MENU_ACTION_RUN_TESTS,
                                                  DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT,
                                                  DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT,
                                                  OpenLogsMenuContributor.MENU_ACTION_OPENLOGS };

    /** Number of separators added to the dashboard context menu alongside the named menu items. */
    static final int MENU_SEPARATOR_COUNT = 1;

    /**
     * Run As configuration menu items with the project inactive. Actions such
     * as "Stop" and "Run Tests" are only present when the project is active.
     */
    static String[] runAsShortcuts = new String[] { LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
                                                    LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG,
                                                    LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER,
                                                    LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT,
                                                    LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_UT_REPORT, };

    static File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();

    static List<String> projectPaths = new ArrayList<String>();

    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() throws Exception {

        commonSetup();

        projectPaths.add(project1Path.resolve("pom").toString());
        projectPaths.add(project1Path.resolve("jar").toString());
        projectPaths.add(project1Path.resolve("war1").toString());
        projectPaths.add(project1Path.resolve("war2").toString());

        // Could be an interesting variation to stop with the above, which would cause the execution to run
        // in a single module context, since the aggregator POM wouldn't be present. The rest of the test class
        // isn't factored now to complete this idea, so leaving this as an idea if we want to expand in this direction later.
        projectPaths.add(project1Path.toString());

        // Maybe redundant but we really want to cleanup. We really want to
        // avoid wasting time debugging tricky differences in behavior because of a dirty re-run
        for (String p : projectPaths) {
            cleanupProject(p);
        }
        importMavenProjects(workspaceRoot, projectPaths);

        // Check basic plugin artifacts are functioning before running tests.
        validateBeforeTestRun();
    }

    @AfterAll
    public static void cleanup() {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
    }

    /**
     * Makes sure that some basic actions can be performed before running the tests:
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

        // Wait until the dashboard contains the expected application.
        SWTBotTestCondition.waitFor(
                                    () -> getDashboardContent().contains(MVN_APP_NAME), SWTBotTestCondition.LARGE_WAIT_MS);

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
        Assertions.assertTrue(menuItems.size() == mvnMenuItems.length + MENU_SEPARATOR_COUNT,
                              () -> "Maven application " + MVN_APP_NAME + " does not contain the expected number of menu items: " + (mvnMenuItems.length + MENU_SEPARATOR_COUNT)
                                    + ". Instead found: " + menuItems.size());
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
                                                                   + ". Found menu entries count: " + foundItems + ". Found menu entries: " + runAsMenuItems);

        // Check that the Run As -> Run Configurations... contains the Liberty entry in the menu.
        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(MVN_APP_NAME);
        SWTBotTreeItem runAslibertyToolsEntry = getLibertyTreeItem(configShell);
        Assertions.assertTrue(runAslibertyToolsEntry != null, "Liberty entry in Run Configurations view was not found.");
        go("Close", configShell);

        // Check that the Debug As -> Debug Configurations... contains the Liberty entry in the menu.
        Shell debugShell = launchDebugConfigurationsDialogFromAppRunAs(MVN_APP_NAME);
        SWTBotTreeItem debugAslibertyToolsEntry = getLibertyTreeItem(debugShell);
        Assertions.assertTrue(debugAslibertyToolsEntry != null, "Liberty entry in Debug Configurations view was not found.");
        go("Close", debugShell);
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartAction() {

        // set the preferences
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Maven");

        // Start dev mode.
        SWTBotPluginOperations.launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        LibertyPluginTestUtils.validateApplicationOutcomeCustom("http://localhost:9080/converter1/heights.jsp?heightCm=10", true,
                                                                "Height in feet and inches", serverModule1Path.resolve(DEFAULT_MAVEN_WLP_PARENT_DIR).toString(), MVN_APP_NAME);
        LibertyPluginTestUtils.validateApplicationOutcomeCustom("http://localhost:9080/converter2/heights.jsp?heightCm=20", true,
                                                                "Height in feet and inches", serverModule1Path.resolve(DEFAULT_MAVEN_WLP_PARENT_DIR).toString(), MVN_APP_NAME);

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        SWTBotPluginOperations.launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(serverModule1Path.resolve(DEFAULT_MAVEN_WLP_PARENT_DIR).toString());

        // unset the preferences
        SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Maven");
    }

    /**
     * Tests the start action initiated through: project -> Run As -> Run Configurations -> Liberty -> New configuration (default) ->
     * Run.
     */
    @Test
    public void testStartWithDefaultRunAsConfig() {

        // set the preferences
        setBuildCmdPathInPreferences(bot, "Maven");

        // Delete any previously created configs.
        deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_APP_NAME);

        // Start dev mode.
        launchStartWithDefaultRunConfigFromAppRunAs(MVN_APP_NAME);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcomeCustom("http://localhost:9080/converter1/heights.jsp?heightCm=30", true,
                                                                "Height in feet and inches", serverModule1Path.resolve(DEFAULT_MAVEN_WLP_PARENT_DIR).toString(), MVN_APP_NAME);

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchStopWithRunAsShortcut(MVN_APP_NAME);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(serverModule1Path.resolve(DEFAULT_MAVEN_WLP_PARENT_DIR).toString());

        // unset the preferences
        unsetBuildCmdPathInPreferences(bot, "Maven");
    }

    /**
     * Tests that the correct dependency projects are added to the debug source lookup list
     */
    @Test
    public void testDebugSourceLookupContentSiblingModule() {

        Shell configShell = launchDebugConfigurationsDialogFromAppRunAs(MVN_APP_NAME);

        boolean jarEntryFound = false;
        boolean warEntryFound = false;
        SWTBotTreeItem defaultSourceLookupTree = null;
        String foundNodesSnapshot = "";

        try {
            Object libertyConfigTree = getLibertyTreeItemNoBot(configShell);

            context(libertyConfigTree, "New Configuration");

            openSourceTab(configShell);

            defaultSourceLookupTree = new SWTBotTreeItem((TreeItem) getDefaultSourceLookupTreeItemNoBot(configShell));
            foundNodesSnapshot = SWTBotPluginOperations.getTreeItemChildrenAsString(defaultSourceLookupTree);

            try {
                defaultSourceLookupTree.getNode(MVN_JAR_NAME);
                jarEntryFound = true;
            } catch (WidgetNotFoundException wnfe) {
                wnfe.printStackTrace();
            }

            // Lookup war project
            try {
                defaultSourceLookupTree.getNode(MVN_WAR_NAME);
                warEntryFound = true;
            } catch (WidgetNotFoundException wnfe) {
                wnfe.printStackTrace();
            }

        } finally {
            go("Close", configShell);
        }

        // Validate dependency projects are in source lookup list
        Assertions.assertTrue(jarEntryFound,
                              "The sibling module project, " + MVN_JAR_NAME + ", was not listed in the source lookup list for project " + MVN_APP_NAME
                                             + ". Nodes found: " + foundNodesSnapshot);
        Assertions.assertTrue(warEntryFound,
                              "The sibling module project, " + MVN_WAR_NAME + ", was not listed in the source lookup list for project " + MVN_APP_NAME
                                             + ". Nodes found: " + foundNodesSnapshot);

    }

    /**
     * Tests that the correct dependency projects are added to the debug source lookup list when starting the parent module
     */
    @Test
    public void testDebugSourceLookupContentParentModule() {

        Shell configShell = launchDebugConfigurationsDialogFromAppRunAs(MVN_PARENT_NAME);

        boolean jarEntryFound = false;
        boolean warEntryFound = false;
        String foundNodesSnapshot = "";

        try {
            Object libertyConfigTree = getLibertyTreeItemNoBot(configShell);

            context(libertyConfigTree, "New Configuration");

            openSourceTab(configShell);

            SWTBotTreeItem defaultSourceLookupTree = new SWTBotTreeItem((TreeItem) getDefaultSourceLookupTreeItemNoBot(configShell));
            foundNodesSnapshot = SWTBotPluginOperations.getTreeItemChildrenAsString(defaultSourceLookupTree);
            System.out.println("@ed: Source lookup list. Nodes found: " + foundNodesSnapshot);

            // Lookup jar project
            try {
                defaultSourceLookupTree.getNode(MVN_JAR_NAME);
                jarEntryFound = true;
            } catch (WidgetNotFoundException wnfe) {
                wnfe.printStackTrace();

            }

            // Lookup war project
            try {
                defaultSourceLookupTree.getNode(MVN_WAR_NAME);
                warEntryFound = true;
            } catch (WidgetNotFoundException wnfe) {
                wnfe.printStackTrace();
            }

        } finally {
            go("Close", configShell);
        }

        // Validate dependency projects are in source lookup list
        Assertions.assertTrue(jarEntryFound,
                              "The child module project, " + MVN_JAR_NAME + ", was not listed in the source lookup list for project " + MVN_PARENT_NAME
                                             + ". Nodes found: " + foundNodesSnapshot);
        Assertions.assertTrue(warEntryFound,
                              "The child module project, " + MVN_WAR_NAME + ", was not listed in the source lookup list for project " + MVN_PARENT_NAME
                                             + ". Nodes found: " + foundNodesSnapshot);

    }

    /**
     * Tests the full Open logs context menu entry behavior.
     *
     * @throws Exception If an error occurs.
     */
    @Test
    public void testOpenLogs() throws Exception {
        String serverOutputPath = serverModule1Path.resolve(DEFAULT_MAVEN_WLP_PARENT_DIR).toString();

        // Remove the logs directory so the entry starts in the disabled state.
        SWTBotPluginOperations.deleteLogsDir(SWTBotPluginOperations.getDefaultLogsDir(serverModule1Path, true));

        // The Open logs entry must be disabled. The logs directory does not exist.
        boolean disabledBefore = SWTBotPluginOperations.waitForOpenLogsEntryState(
                                                                                  MVN_APP_NAME, false, SWTBotTestCondition.SHORT_WAIT_MS);
        Assertions.assertTrue(disabledBefore,
                              "Open logs entry should be disabled when the logs directory does not exist.");

        // Set the preferences.
        SWTBotPluginOperations.setBuildCmdPathInPreferences(bot, "Maven");

        // Start dev mode.
        SWTBotPluginOperations.launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        try {
            LibertyPluginTestUtils.validateApplicationOutcomeCustom("http://localhost:9080/converter1/heights.jsp?heightCm=10", true,
                                                                    "Height in feet and inches", serverOutputPath, MVN_APP_NAME);
            SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

            // After the server starts, the Open logs entry should become an enabled submenu.
            boolean enabledAfterStart = SWTBotPluginOperations.waitForOpenLogsEntryState(
                                                                                         MVN_APP_NAME, true, SWTBotTestCondition.SHORT_WAIT_MS);
            Assertions.assertTrue(enabledAfterStart,
                                  "Open logs entry should be enabled after the server created the logs directory.");

            // Show the Open logs submenu, check that all four entries are present, and verify that the Open logs folder entry is enabled.
            SWTBotMenu logsMenu = SWTBotPluginOperations.getDashboardOpenLogsSubmenu(MVN_APP_NAME, SWTBotTestCondition.SHORT_WAIT_MS);
            Assertions.assertNotNull(logsMenu, "Open logs submenu could not be obtained for: " + MVN_APP_NAME);
            try {
                List<String> items = logsMenu.menuItems();
                Assertions.assertTrue(items.contains(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_MSGS_TYPE),
                                      "Messages entry missing from Open logs submenu. Items found: " + items);
                Assertions.assertTrue(items.contains(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_TRACE_TYPE),
                                      "Trace entry missing from Open logs submenu. Items found: " + items);
                Assertions.assertTrue(items.contains(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_FFDC_TYPE),
                                      "FFDC entry missing from Open logs submenu. Items found: " + items);
                Assertions.assertTrue(items.contains(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_FOLDER),
                                      "Open logs folder entry missing from Open logs submenu. Items found: " + items);
                SWTBotMenu folderEntry = logsMenu.menu(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_FOLDER);
                Assertions.assertTrue(folderEntry.isEnabled(),
                                      "Open logs folder entry should be enabled when the logs directory exists.");
            } finally {
                logsMenu.hide();
            }

            // Click the messages.log entry. The file should open in an editor.
            boolean clicked = SWTBotPluginOperations.clickOpenLogsFileEntry(
                                                                            MVN_APP_NAME, DEFAULT_MESSAGES_LOG_FILE_NAME, SWTBotTestCondition.SHORT_WAIT_MS);
            Assertions.assertTrue(clicked, "Failed to click messages.log entry in the Open logs submenu.");

            boolean editorOpened = SWTBotTestCondition.waitFor(
                                                               () -> SWTBotPluginOperations.findEditorByTitle(bot, DEFAULT_MESSAGES_LOG_FILE_NAME) != null,
                                                               SWTBotTestCondition.SHORT_WAIT_MS);
            Assertions.assertTrue(editorOpened, "An editor for messages.log did not open after clicking the entry.");
        } finally {
            // Stop dev mode, close the opened editor, and unset the preferences.
            SWTBotPluginOperations.launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
            LibertyPluginTestUtils.validateLibertyServerStopped(serverOutputPath);
            SWTBotPluginOperations.closeEditorByTitle(bot, DEFAULT_MESSAGES_LOG_FILE_NAME);
            SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Maven");
        }
    }

    /**
     * Tests that the Open logs entry is not shown for the parent of the Liberty server module.
     * The parent is a multi-module aggregator and must not contribute an Open logs entry.
     */
    @Test
    public void testOpenLogsEntryNotShownOnParent() {
        List<String> menuItems = SWTBotPluginOperations.getDashboardItemMenuActions(MVN_PARENT_NAME);
        Assertions.assertFalse(menuItems.contains(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS),
                               "Open logs entry must not appear for the multi-module parent: " + MVN_PARENT_NAME
                                                                                                 + ". Items found: " + menuItems);
    }
}