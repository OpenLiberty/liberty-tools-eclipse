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
package io.openliberty.tools.eclipse.test.it.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.allOf;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotRootMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotStyledText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarPushButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Assertions;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;

/**
 * Provides a set of SWTBot wrapper functions.
 */
public class SWTBotPluginOperations {

    /**
     * Constants.
     */
    public static final String MENU_NAME = "Liberty";
    public static final String MENU_OPEN_DASHBOARD_ACTION = "Open Dashboard";
    public static final String TOOLBAR_OPEN_DASHBOARD_TIP = "Open Liberty Dashboard View";
    public static final String DASHBOARD_TOOLBAR_REFRESH_TIP = "refresh";
    public static final String DASHBOARD_VIEW_TITLE = "Liberty Dashboard";
    public static final String LAUNCH_CONFIG_LIBERTY_MENU_NAME = "Liberty";
    public static final String LAUNCH_CONFIG_REMOTE_JAVA_APP = "Remote Java Application";
    public static final String EXPLORER_CONFIGURE_MENU_ENABLE_LIBERTY_TOOLS = "Enable Liberty";

    /**
     * Close the welcome page if active.
     */
    public static void closeWelcomePage(SWTWorkbenchBot bot) {
        for (SWTBotView v : bot.views()) {
            if (v.getTitle().equals("Welcome")) {
                v.close();
            }
        }
    }

    /**
     * Open the Eclipse java perspective.
     */
    public static void openJavaPerspective() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    IWorkbench wb = PlatformUI.getWorkbench();
                    wb.showPerspective("org.eclipse.jdt.ui.JavaPerspective", wb.getActiveWorkbenchWindow());
                } catch (WorkbenchException we) {
                    // Print a message. Lighter environments may not support this perspective.
                    System.out.println("INFO: Java perspective was not opened: " + we.getMessage());
                }
            }
        };

        Display.getDefault().syncExec(runnable);
    }

    /**
     * Returns a list of entries on the Open Liberty dashboard.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     *
     * @return A list of entries on the Open Liberty dashboard.
     */
    public static List<String> getDashboardContent(SWTWorkbenchBot bot, SWTBotView dashboard) {
        if (dashboard == null) {
            SWTBotPluginOperations.openDashboardUsingToolbar(bot);
        } else {
            dashboard.show();
        }

        SWTBotTable dashboardTable = bot.table();
        ArrayList<String> contentList = new ArrayList<String>();
        for (int i = 0; i < dashboardTable.rowCount(); i++) {
            contentList.add(dashboardTable.getTableItem(i).getText());
        }

        return contentList;
    }

    /**
     * Returns a list of menu actions associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     *
     * @return A list of menu actions for the input application item.
     */
    public static List<String> getDashboardItemMenuActions(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        if (dashboard == null) {
            SWTBotPluginOperations.openDashboardUsingToolbar(bot);
        } else {
            dashboard.show();
        }

        SWTBotTable dashboardTable = bot.table();
        dashboardTable.select(item);
        SWTBotRootMenu appCtxMenu = dashboardTable.contextMenu();
        return appCtxMenu.menuItems();
    }

    /**
     * Clicks on the refresh icon on the Open Liberty dashboard.
     *
     * @param bot
     */
    public static void refreshDashboard(SWTWorkbenchBot bot) {
        SWTBotView dashboardView = openDashboardUsingToolbar(bot);
        dashboardView.setFocus();
        SWTBotToolbarButton refreshButton = bot.toolbarButtonWithTooltip(DASHBOARD_TOOLBAR_REFRESH_TIP);
        refreshButton.setFocus();
        refreshButton.click();
    }

    /**
     * Refreshes the application project through the explorer view (explorer-> right click on project -> refresh).
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name to select.
     */
    public static void refreshProjectUsingExplorerView(SWTWorkbenchBot bot, String item) {
        SWTBotTreeItem projectInExplorer = SWTBotPluginOperations.getInstalledProjectItem(bot, item);
        Assertions.assertTrue(projectInExplorer != null, () -> "Could not find project " + item + " in the explorer view.");
        bot.waitUntil(SWTBotTestCondition.isTreeItemEnabled(projectInExplorer), 5000);
        projectInExplorer.select().setFocus();
        SWTBotMenu refresh = projectInExplorer.contextMenu("Refresh");
        bot.waitUntil(SWTBotTestCondition.isMenuEnabled(refresh), 5000);
        refresh.setFocus();
        refresh.click();
    }

    /**
     * Launches the dashboard's start action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchStartWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu startAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_START);
        startAction.click();
    }

    /**
     * Launches the dashboard's debug action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchDebugWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu debugAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_DEBUG);
        debugAction.click();
    }

    /**
     * Launches the dashboard's start with configuration dialog associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchStartConfigDialogWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu startAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_START_CONFIG);
        startAction.click();
    }

    /**
     * Launches the dashboard's debug with configuration dialog associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchDebugConfigDialogWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu debugAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_DEBUG_CONFIG);
        debugAction.click();
    }

    /**
     * Launches the dashboard's run test action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchRunTestsWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu runTestsAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_RUN_TESTS);
        runTestsAction.click();
    }

    /**
     * Launches the dashboard's stop action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchStopWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        System.out.println("INFO: Launching stop with dashboard action");
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu stopAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_STOP);
        stopAction.click();
    }

    /**
     * Launches dashboard's view (Maven) integration test report action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The Maven application name to select.
     */
    public static void launchViewITReportWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu itReport = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT);
        itReport.click();

        bot.waitUntil(SWTBotTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_MVN_IT_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Launches the dashboard's view (Maven) unit test report action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The Maven application name to select.
     */
    public static void launchViewUTReportWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu utReport = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT);
        utReport.click();

        bot.waitUntil(SWTBotTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_MVN_UT_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Launches the dashboard's view (Gradle) the test report action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The Gradle application name to select.
     */
    public static void launchViewTestReportWithDashboardAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu testReport = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT);
        testReport.click();

        bot.waitUntil(SWTBotTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Returns the object representing the active project matching the input project name.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * 
     * @return The object representing the active project matching the input project name.
     */
    public static SWTBotTreeItem getInstalledProjectItem(SWTWorkbenchBot bot, String item) {
        openJavaPerspective();
        SWTBotView peView = bot.viewByTitle("Package Explorer");
        peView.show();
        SWTBotTree packageExplorerContent = peView.bot().tree();
        SWTBotTreeItem project = null;
        for (SWTBotTreeItem projectFromTree : packageExplorerContent.getAllItems()) {
            if (projectFromTree.getText().contains(item)) {
                project = projectFromTree;
                break;
            }
        }

        return project;
    }

    /**
     * Returns the object representing the explorer->project->right-click->Run As menu.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * 
     * @return The object representing the Run As menu.
     */
    public static SWTBotMenu getAppRunAsMenu(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = null;
        SWTBotTreeItem project = getInstalledProjectItem(bot, item);
        Assertions.assertTrue(project != null, () -> "Could not find active project.");
        bot.waitUntil(SWTBotTestCondition.isTreeItemEnabled(project), 5000);
        project.select().setFocus();

        runAsMenu = project.contextMenu("Run As");
        bot.waitUntil(SWTBotTestCondition.isMenuEnabled(runAsMenu), 5000);
        runAsMenu.click();

        return runAsMenu;
    }

    /**
     * Returns the object representing the explorer->project->right-click->Debug As menu.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * 
     * @return The object representing the Debug As menu.
     */
    public static SWTBotMenu getAppDebugAsMenu(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = null;

        SWTBotTreeItem project = getInstalledProjectItem(bot, item);
        Assertions.assertTrue(project != null, () -> "Could not find active project.");
        bot.waitUntil(SWTBotTestCondition.isTreeItemEnabled(project), 5000);
        project.select().setFocus();

        runAsMenu = project.contextMenu("Debug As");
        bot.waitUntil(SWTBotTestCondition.isMenuEnabled(runAsMenu), 5000);
        runAsMenu.click();

        return runAsMenu;
    }

    /**
     * Sets the absolute path to the maven and gradle executables that should be used for build into the Liberty Tools Plugin
     * Preferences page
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param buildTool the build tool to be used (Maven or Gradle)
     */
    public static void setBuildCmdPathInPreferences(SWTWorkbenchBot bot, String buildTool) {

        /* Preferences are accessed from a different menu on macOS than on Windows and Linux */
        /* Currently not possible to access the Preferences dialog panel on macOS so we */
        /* will return and just use an app configured with a wrapper */
        if (Platform.getOS().equals(Platform.OS_MACOSX)) {
            return;
        }

        String finalMvnExecutableLoc = null;
        String finalGradleExecutableLoc = null;

        finalMvnExecutableLoc = System.getProperty("io.liberty.tools.eclipse.tests.mvnexecutable.path");
        finalGradleExecutableLoc = System.getProperty("io.liberty.tools.eclipse.tests.gradleexecutable.path");

        bot.menu("Window").menu("Preferences").click();
        bot.tree().getTreeItem("Liberty").select().setFocus();
        if (buildTool == "Maven") {
            bot.textWithLabel("&Maven Install Location:").setText(finalMvnExecutableLoc);
        } else if (buildTool == "Gradle") {
            bot.textWithLabel("&Gradle Install Location:").setText(finalGradleExecutableLoc);
        }
        bot.button("Apply and Close").click();
    }

    public static void unsetBuildCmdPathInPreferences(SWTWorkbenchBot bot, String buildTool) {

        /* Preferences are accessed from a different menu on macOS than on Windows and Linux */
        /* Currently not possible to access the Preferences dialog panel on macOS so we */
        /* will return and just use an app configured with a wrapper */
        if (Platform.getOS().equals(Platform.OS_MACOSX)) {
            return;
        }

        bot.menu("Window").menu("Preferences").click();
        bot.tree().getTreeItem("Liberty").select().setFocus();
        bot.button("Restore Defaults").click();
        bot.button("Apply and Close").click();
    }

    /**
     * Launches the Run/Debug configuration dialog.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchConfigurationsDialog(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configuration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotMenu modeAsMenu = ("run".equals(mode)) ? getAppRunAsMenu(bot, item) : getAppDebugAsMenu(bot, item);
        String configMenuText = ("run".equals(mode)) ? "Run Configurations..." : "Debug Configurations...";

        SWTBotMenu runConfigMenu = modeAsMenu.menu(configMenuText);
        runConfigMenu.setFocus();
        runConfigMenu.click();

        bot.waitUntil(SWTBotTestCondition.isTreeWidgetEnabled(bot, LAUNCH_CONFIG_LIBERTY_MENU_NAME), 5000);

        if ("debug".equals(mode)) {
            bot.waitUntil(SWTBotTestCondition.isTreeWidgetEnabled(bot, LAUNCH_CONFIG_REMOTE_JAVA_APP), 5000);
        }

    }

    /**
     * Returns the object that represents the Run/Debug As->Run/Debug Configuration...->Liberty menu entry.
     * 
     * @param bot The SWTWorkbenchBot instance..
     * 
     * @return The object that represents the Run/Debug As->Run/Debug Configuration...->Liberty menu entry.
     */
    public static SWTBotTreeItem getLibertyToolsConfigMenuItem(SWTWorkbenchBot bot) {
        SWTBotTreeItem libertyToolsEntry = bot.tree().getTreeItem(LAUNCH_CONFIG_LIBERTY_MENU_NAME);
        bot.waitUntil(SWTBotTestCondition.isTreeItemEnabled(libertyToolsEntry), 10000);
        libertyToolsEntry.select().setFocus();

        return libertyToolsEntry;
    }

    /**
     * Returns the object that represents the Debug As->Debug Configuration...->Remote Java Application menu entry.
     * 
     * @param bot The SWTWorkbenchBot instance..
     * 
     * @return The object that represents the Run/Debug As->Run/Debug Configuration...->Liberty menu entry.
     */
    public static SWTBotTreeItem getRemoteJavaAppConfigMenuItem(SWTWorkbenchBot bot) {
        SWTBotTreeItem remoteJavaApp = null;

        SWTBotTreeItem[] treeItems = bot.tree().getAllItems();
        for (SWTBotTreeItem treeItem : treeItems) {
            if (treeItem.getText().equals(LAUNCH_CONFIG_REMOTE_JAVA_APP)) {
                remoteJavaApp = treeItem;

                bot.waitUntil(SWTBotTestCondition.isTreeItemEnabled(remoteJavaApp), 10000);
                remoteJavaApp.select().setFocus();
                break;
            }
        }

        return remoteJavaApp;
    }

    /**
     * Deletes Liberty configuration entries based on the supplied mode.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void deleteLibertyToolsConfigEntries(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotPluginOperations.launchConfigurationsDialog(bot, item, mode);
        try {
            SWTBotTreeItem libertyToolsEntry = getLibertyToolsConfigMenuItem(bot);
            Assertions.assertTrue((libertyToolsEntry != null), () -> "The Liberty entry was not found in run Configurations dialog.");

            List<String> configs = libertyToolsEntry.getNodes();

            for (String config : configs) {
                SWTBotTreeItem configEntry = libertyToolsEntry.getNode(config);
                bot.waitUntil(SWTBotTestCondition.isTreeItemEnabled(configEntry), 10000);
                configEntry.select().setFocus();

                SWTBotToolbarButton deleteButon = bot.toolbarButtonWithTooltip("Delete selected launch configuration(s)");
                deleteButon.setFocus();
                deleteButon.click();

                SWTBotButton deleteButton = bot.button("Delete");
                bot.waitUntil(SWTBotTestCondition.isButtonEnabled(deleteButton), 5000);
                deleteButton.setFocus();
                deleteButton.click();
            }

            // Delete debug mode Remote Java Application configurations
            if ("debug".equals(mode)) {
                SWTBotTreeItem remoteJavaAppEntry = getRemoteJavaAppConfigMenuItem(bot);
                Assertions.assertTrue((remoteJavaAppEntry != null),
                        () -> "The " + LAUNCH_CONFIG_REMOTE_JAVA_APP + " entry was not found in run Configurations dialog.");

                List<String> rjaConfigs = remoteJavaAppEntry.getNodes();
                for (String rjaConfig : rjaConfigs) {
                    SWTBotTreeItem configEntry = remoteJavaAppEntry.getNode(rjaConfig);
                    bot.waitUntil(SWTBotTestCondition.isTreeItemEnabled(configEntry), 10000);
                    configEntry.select().setFocus();

                    SWTBotToolbarButton deleteButon = bot.toolbarButtonWithTooltip("Delete selected launch configuration(s)");
                    deleteButon.setFocus();
                    deleteButon.click();

                    SWTBotButton deleteButton = bot.button("Delete");
                    bot.waitUntil(SWTBotTestCondition.isButtonEnabled(deleteButton), 5000);
                    deleteButton.setFocus();
                    deleteButton.click();
                }
            }
        } finally {
            // Close the configuration dialog.
            closeDialog(bot);
        }
    }

    /**
     * Launches dev mode start using a new Liberty configuration: project -> Run/Debug As -> Run/Debug Configurations -> Liberty ->
     * New configuration (default) -> Run.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchStartWithDefaultConfig(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotPluginOperations.launchConfigurationsDialog(bot, item, mode);
        SWTBotTreeItem libertyToolsEntry = getLibertyToolsConfigMenuItem(bot);
        libertyToolsEntry.doubleClick();

        runLibertyConfiguration(bot, mode);
    }

    /**
     * Launches dev mode with parms using a new Liberty configuration: project -> Run/Debug As -> Run/Debug Configurations -> Liberty
     * -> New configuration (default) -> update parms -> Run. Note that the changes are not saved.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     * @param parms The parameter(s) to pass to the dev mode start action.
     */
    public static void launchStartWithCustomConfig(SWTWorkbenchBot bot, String item, String mode, String parms) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotPluginOperations.launchConfigurationsDialog(bot, item, mode);
        createNewLibertyConfiguration(bot);

        setLibertyConfigParms(bot, parms);

        runLibertyConfiguration(bot, mode);
    }

    /**
     * Creates a new Liberty configuration: <Run/Debug configurations dialog> -> Liberty -> New configuration. The Run/Debug
     * configurations dialog containing the Liberty entry must be the active view when this method is called.
     * 
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void createNewLibertyConfiguration(SWTWorkbenchBot bot) {
        SWTBotTreeItem libertyToolsEntry = getLibertyToolsConfigMenuItem(bot);
        libertyToolsEntry.doubleClick();
    }

    /**
     * Updates the Start parameters entry on the Main tab of the Liberty configuration. The Liberty Main tab on the Run Configurations
     * dialog must be the active view when this method is called.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param parms The parameter(s) to pass to the dev mode start action.
     */
    public static void setLibertyConfigParms(SWTWorkbenchBot bot, String parms) {
        SWTBotText parmTextBox = bot.textWithLabel("Start parameters:");
        parmTextBox.setFocus();
        parmTextBox.setText(parms);
        bot.waitUntil(SWTBotTestCondition.isTextPresent(parmTextBox, parms), 5000);
    }

    /**
     * Clicks the Run/Debug button on the Main tab of the Liberty configuration. The Liberty Main tab on the Run Configurations dialog
     * must be the active view when this method is called.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void runLibertyConfiguration(SWTWorkbenchBot bot, String mode) {
        SWTBotButton button = bot.button(("run".equals(mode)) ? "Run" : "Debug");
        bot.waitUntil(SWTBotTestCondition.isButtonEnabled(button), 5000);
        button.setFocus();
        button.click();
    }

    /**
     * Launches the start action using the run/debug as configuration shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchStartWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotMenu modeAsMenu = ("run".equals(mode)) ? getAppRunAsMenu(bot, item) : getAppDebugAsMenu(bot, item);

        SWTBotMenu stopShortcut = modeAsMenu
                .menu(WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START + ".*"), false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();
    }

    /**
     * Launches the stop action using the run/debug as configuration shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchStopWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotMenu modeAsMenu = ("run".equals(mode)) ? getAppRunAsMenu(bot, item) : getAppDebugAsMenu(bot, item);
        SWTBotMenu stopShortcut = modeAsMenu
                .menu(WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP + ".*"), false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();
    }

    /**
     * Launches the run tests action using the run/debug as configuration shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchRunTestspWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotMenu modeAsMenu = ("run".equals(mode)) ? getAppRunAsMenu(bot, item) : getAppDebugAsMenu(bot, item);
        SWTBotMenu stopShortcut = modeAsMenu.menu(
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS + ".*"), false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();
    }

    /**
     * Launches the view (Maven) integration test report action using the run/debug as configuration shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchViewITReportWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT + ".*"), false,
                0);
        stopShortcut.setFocus();
        stopShortcut.click();

        bot.waitUntil(SWTBotTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_MVN_IT_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Launches the view (Maven) unit test report action using the run/debug as configuration shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchViewUTReportWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_UT_REPORT + ".*"), false,
                0);
        stopShortcut.setFocus();
        stopShortcut.click();

        bot.waitUntil(SWTBotTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_MVN_UT_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Launches the view (Gradle) test report action using the run/debug as configuration shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchViewTestReportWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT + ".*"),
                false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();

        bot.waitUntil(SWTBotTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Returns the object representing the explorer->project->right-click->Configure.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * 
     * @return The object representing the explorer->project->right-click->Configure.
     */
    public static SWTBotMenu getExplorerConfigurationMenu(SWTWorkbenchBot bot, String item) {
        SWTBotMenu configurationMenu = null;
        SWTBotTreeItem project = getInstalledProjectItem(bot, item);
        Assertions.assertTrue(project != null, () -> "Could not find active project.");

        project.setFocus();
        configurationMenu = project.contextMenu("Configure").click();

        return configurationMenu;
    }

    /**
     * Enables Liberty tools on the input project by clicking on explorer->project->right-click->Configure->Enable Liberty.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void enableLibertyTools(SWTWorkbenchBot bot, String item) {
        SWTBotMenu configureMenu = getExplorerConfigurationMenu(bot, item);
        SWTBotMenu runConfigMenu = configureMenu.menu(EXPLORER_CONFIGURE_MENU_ENABLE_LIBERTY_TOOLS);
        runConfigMenu.setFocus();
        runConfigMenu.click();
    }

    /**
     * Get the content of a text editor view obtained associated with the input title name.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param title The complete title view name.
     *
     * @return The content of a text editor view obtained associated with the input title name.
     */
    public static String getTextEditorContentByTitle(SWTWorkbenchBot bot, String title) {
        SWTBotEditor editor = bot.editorByTitle(title);
        editor.show();

        return bot.text().getText();
    }

    /**
     * Searches for the text editor that contains the complete or partial input title name.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param titleContent The complete or partial title name.
     *
     * @return The text editor object associated with input title name.
     */
    public static SWTBotEditor searchForEditor(SWTWorkbenchBot bot, String titleContent) {
        Iterator<? extends SWTBotEditor> editors = bot.editors().iterator();
        SWTBotEditor editor = null;
        while (editors.hasNext()) {
            editor = editors.next();
            if (editor.getTitle().contains(titleContent)) {
                editor.show();
                break;
            }
        }
        return editor;
    }

    /**
     * Returns the content of the file associated with the the input file name under the input application name.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param appViewTitle The title of the view (i.e. Project Explorer) where to look.
     * @param appName The application where to find the file.
     * @param fileName The name of the file from which to retrieve content.
     *
     * @return The content of the file associated with the the input file name under the input application name.
     */
    public static String getAppFileContent(SWTWorkbenchBot bot, String appViewTitle, String appName, String fileName) {
        SWTBotTreeItem appProj = null;
        bot.viewByTitle(appViewTitle).show();
        SWTBotTreeItem[] appProjects = bot.tree().getAllItems();
        for (int i = 0; i < appProjects.length; i++) {
            if (appProjects[i].getText().contains(appName)) {
                appProj = appProjects[i];
                break;
            }
        }

        appProj.select().setFocus();
        appProj.expand();
        SWTBotTreeItem file = appProj.getNode(fileName);
        file.select().setFocus();
        file.doubleClick();

        SWTBotEditor editor = searchForEditor(bot, fileName);
        editor.show();

        return bot.styledText().getText();
    }

    /**
     * Writes the input content to a text editor view.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param titleContent The title or part of the title of the text editor view to update.
     * @param content The content with which the text editor view is updated.
     */
    public static void setEditorText(SWTWorkbenchBot bot, String titleContent, String content) {
        SWTBotEditor editor = searchForEditor(bot, titleContent);
        editor.show();
        SWTBotStyledText styledText = bot.styledText();
        styledText.setText(content);
        editor.save();
    }

    /**
     * Returns the content to a text editor view.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param titleContent The title or part of the title of the text editor view.
     *
     * @return The content to a text editor view.
     */
    public static String getEditorText(SWTWorkbenchBot bot, String titleContent) {
        SWTBotEditor editor = searchForEditor(bot, titleContent);
        editor.show();
        SWTBotStyledText styledText = bot.styledText();
        return styledText.getText();
    }

    /**
     * Returns the context menu object associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     *
     * @return The context menu object associated with the input application item.
     */
    public static SWTBotRootMenu getAppContextMenu(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        if (dashboard == null) {
            SWTBotPluginOperations.openDashboardUsingToolbar(bot);
        } else {
            dashboard.show();
            dashboard.setFocus();
        }

        SWTBotTable dashboardTable = bot.table();

        dashboardTable.select(item);
        return dashboardTable.contextMenu();
    }

    /**
     * Returns the Open Liberty dashboard view obtained by pressing on the Open Liberty icon located on the main tool bar.
     *
     * @param bot The SWTWorkbenchBot instance.
     *
     * @return The Open Liberty dashboard view obtained by pressing on the Open Liberty icon located on the main tool bar.
     */
    public static SWTBotView openDashboardUsingToolbar(SWTWorkbenchBot bot) {
        bot.shell("data").activate().setFocus();

        SWTBotToolbarButton toolbarButton = getToolbarButtonWithToolTipPrefix(bot, TOOLBAR_OPEN_DASHBOARD_TIP);
        toolbarButton.click();
        SWTBotView dashboard = bot.viewByTitle(DASHBOARD_VIEW_TITLE);
        dashboard.show();
        bot.waitUntil(SWTBotTestCondition.isViewActive(dashboard, DASHBOARD_VIEW_TITLE), 5000);
        return dashboard;
    }

    /**
     * Closes the Open Liberty dashboard view.
     *
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void closeDashboardView(SWTWorkbenchBot bot) {
        SWTBotView dashboard = bot.viewByTitle(DASHBOARD_VIEW_TITLE);
        if (dashboard.isActive()) {
            dashboard.close();
        }
    }

    /**
     * Switches the Liberty run configuration main tab to the JRE Tab. A Liberty configuration must be opened prior to calling this
     * method.
     * 
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void openJRETab(SWTWorkbenchBot bot) {
        SWTBotShell shell = bot.shell("Run Configurations");
        shell.activate().setFocus();
        SWTBot shellBot = shell.bot();
        SWTBotCTabItem tabItem = shellBot.cTabItem("JRE");
        tabItem.activate().setFocus();
    }

    /**
     * Switches the perspective to Project Explorer.
     *
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void switchToProjectExplorerView(SWTWorkbenchBot bot) {

        bot.shell("data").activate().setFocus();

        String projExpViewName = "Project Explorer";
        bot.menu("Window").menu("Show View").menu(projExpViewName).click();
        int maxRetries = 10;
        String activeViewTitle = null;

        for (int i = 0; i < maxRetries; i++) {
            SWTBotView activeView = bot.activeView();
            activeViewTitle = activeView.getTitle();

            if (projExpViewName.equals(activeViewTitle)) {
                bot.waitUntil(SWTBotTestCondition.isViewActive(activeView, activeView.getTitle()), 10000);
                return;
            } else {
                try {
                    TimeUnit.MILLISECONDS.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                continue;
            }
        }

        Assertions.fail("Found Invalid view: " + activeViewTitle + ". Expected view: " + projExpViewName);
    }

    /**
     * Presses the Proceed button if it exists on the error in workspace dialog.
     * 
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void pressWorkspaceErrorDialogProceedButton(SWTWorkbenchBot bot) {
        try {
            bot.button("Proceed").click();
        } catch (Exception e) {
            // Best effort approach.
        }
    }

    /**
     * Clicks the Apply button on run configuration dialog tab. A dialog containing this button must be active before making this
     * call.
     * 
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void applyDialogChanges(SWTWorkbenchBot bot) {
        SWTBotButton applyButton = bot.button("Apply");
        if (applyButton.isEnabled()) {
            applyButton.setFocus();
            applyButton.click();
        }
    }

    /**
     * Clicks the Close button on run configuration dialog tab. A dialog containing this button must be active before making this
     * call.
     * 
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void closeDialog(SWTWorkbenchBot bot) {
        SWTBotButton closeButton = bot.button("Close");
        if (closeButton.isEnabled()) {
            closeButton.setFocus();
            closeButton.click();
        }
    }

    /**
     * Returns a SWTBotToolbarButton instance representing the toolbar button with the input tooltip prefix.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param toolTipPrefix The tooltip prefix.
     *
     * @return A SWTBotToolbarButton instance representing the toolbar button with the input tooltip prefix.
     */
    @SuppressWarnings("unchecked")
    public static SWTBotToolbarButton getToolbarButtonWithToolTipPrefix(SWTWorkbenchBot bot, String toolTipPrefix) {
        Matcher<Item> matcher = allOf(widgetOfType(ToolItem.class), new TextPrefixMatcher<Item>(toolTipPrefix, "getToolTipText"));
        Item item = bot.widget(matcher, 0);
        if (item instanceof ToolItem) {
            ToolItem toolItem = (ToolItem) item;
            if (SWTUtils.hasStyle(toolItem, SWT.PUSH)) {
                return new SWTBotToolbarPushButton(toolItem, matcher);
            }
        }

        throw new RuntimeException(
                "toolbar button of type ToolItem, with style push, and tooltip prefix of " + toolTipPrefix + " was not found.");
    }

    @SuppressWarnings("unchecked")
    public static SWTBotCombo getComboTextBoxWithTextPrefix(SWTWorkbenchBot bot, String textPrefix) {
        Matcher<Combo> matcher = allOf(widgetOfType(Combo.class), new TextPrefixMatcher<Combo>(textPrefix, "getText"));
        Combo comboBox = bot.widget(matcher, 0);
        if (comboBox instanceof Combo) {
            Combo combo = (Combo) comboBox;
            if (SWTUtils.hasStyle(comboBox, SWT.DROP_DOWN)) {
                return new SWTBotCombo(combo, matcher);
            }
        }

        throw new RuntimeException("Combo box of type Combo, with style drop down, and text prefix of " + textPrefix + " was not found.");
    }

    /**
     * Text prefix matcher.
     */
    public static class TextPrefixMatcher<T> extends BaseMatcher<T> {

        String prefix;
        String method;

        /**
         * Constructor.
         *
         * @param toolTipPrefix The tooltip prefix to match.
         */
        public TextPrefixMatcher(String prefix, String method) {
            this.prefix = prefix;
            this.method = method;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void describeTo(Description description) {
            description.appendText("with prefix '").appendText(prefix).appendText("'");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(Object object) {
            boolean matchFound = false;

            try {
                Object text = SWTUtils.invokeMethod(object, method);
                if (text instanceof String) {
                    matchFound = ((String) text).startsWith(prefix);
                }
            } catch (Exception e) {
                System.out.println("INFO: Unabled to find text with prefix: " + prefix + ". Error: " + e.getMessage());
            }

            return matchFound;
        }
    }
}
