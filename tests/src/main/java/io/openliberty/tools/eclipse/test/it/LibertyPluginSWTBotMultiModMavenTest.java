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
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
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
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
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
    static String[] mvnMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START, DashboardView.APP_MENU_ACTION_START_CONFIG,
            DashboardView.APP_MENU_ACTION_START_IN_CONTAINER, DashboardView.APP_MENU_ACTION_DEBUG,
            DashboardView.APP_MENU_ACTION_DEBUG_CONFIG, DashboardView.APP_MENU_ACTION_DEBUG_IN_CONTAINER,
            DashboardView.APP_MENU_ACTION_STOP, DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT,
            DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT };

    /**
     * Run As configuration menu items.
     */
    static String[] runAsShortcuts = new String[] { LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONTAINER, LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP,
            LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS,
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
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        LibertyPluginTestUtils.validateApplicationOutcomeCustom("http://localhost:9080/converter1/heights.jsp?heightCm=10", true,
                "Height in feet and inches", serverModule1Path + "/target/liberty");
        LibertyPluginTestUtils.validateApplicationOutcomeCustom("http://localhost:9080/converter2/heights.jsp?heightCm=20", true,
                "Height in feet and inches", serverModule1Path + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        SWTBotPluginOperations.launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);
        terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(serverModule1Path + "/target/liberty");

        // unset the preferences
        SWTBotPluginOperations.unsetBuildCmdPathInPreferences(bot, "Maven");

        // Close the terminal.
        terminal.close();
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
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcomeCustom("http://localhost:9080/converter1/heights.jsp?heightCm=30", true,
                "Height in feet and inches", serverModule1Path + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchStopWithRunAsShortcut(MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(serverModule1Path + "/target/liberty");

        // unset the preferences
        unsetBuildCmdPathInPreferences(bot, "Maven");

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests that the correct dependency projects are added to the debug source lookup list
     */
    @Test
    public void testDebugSourceLookupContent() {

        Shell configShell = launchDebugConfigurationsDialogFromAppRunAs(MVN_WAR_NAME);

        boolean entryFound = false;

        try {
            Object libertyConfigTree = getLibertyTreeItemNoBot(configShell);

            context(libertyConfigTree, "New Configuration");

            openSourceTab(bot);

            SWTBotTreeItem defaultSourceLookupTree = new SWTBotTreeItem((TreeItem) getDefaultSourceLookupTreeItemNoBot(configShell));

            try {
                defaultSourceLookupTree.getNode(MVN_JAR_NAME);
                entryFound = true;
            } catch (WidgetNotFoundException wnfe) {
                // Jar project was not found in source lookup list.
            }

        } finally {
            go("Close", configShell);
            deleteLibertyToolsRunConfigEntriesFromAppRunAs(MVN_WAR_NAME);
        }

        // Validate dependency project is in source lookup list
        Assertions.assertTrue(entryFound,
                "The " + MVN_JAR_NAME + " project was not listed in the source lookup list for project " + MVN_WAR_NAME);

    }

    /**
     * Tests that the correct dependency projects are added to the debug source lookup list when starting the parent module
     */
    @Test
    public void testDebugSourceLookupContentParentModule() {

        Shell configShell = launchDebugConfigurationsDialogFromAppRunAs(MVN_APP_NAME);

        boolean jarEntryFound = false;
        boolean warEntryFound = false;

        try {
            Object libertyConfigTree = getLibertyTreeItemNoBot(configShell);

            context(libertyConfigTree, "New Configuration");

            openSourceTab(bot);

            SWTBotTreeItem defaultSourceLookupTree = new SWTBotTreeItem((TreeItem) getDefaultSourceLookupTreeItemNoBot(configShell));

            // Lookup jar project
            try {
                defaultSourceLookupTree.getNode(MVN_JAR_NAME);
                jarEntryFound = true;
            } catch (WidgetNotFoundException wnfe) {
                // Jar project was not found in source lookup list.
            }

            // Lookup war project
            try {
                defaultSourceLookupTree.getNode(MVN_WAR_NAME);
                warEntryFound = true;
            } catch (WidgetNotFoundException wnfe) {
                // War project was not found in source lookup list.
            }

        } finally {
            go("Close", configShell);
        }

        // Validate dependency projects are in source lookup list
        Assertions.assertTrue(jarEntryFound,
                "The child module projects, " + MVN_JAR_NAME + ", was not listed in the source lookup list for project " + MVN_APP_NAME);
        Assertions.assertTrue(warEntryFound,
                "The child module projects, " + MVN_WAR_NAME + ", was not listed in the source lookup list for project " + MVN_APP_NAME);

    }
}
