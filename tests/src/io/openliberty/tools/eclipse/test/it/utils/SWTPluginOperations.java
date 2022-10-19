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
package io.openliberty.tools.eclipse.test.it.utils;

import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.allOf;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
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
public class SWTPluginOperations {

    /**
     * Constants.
     */
    public static final String MENU_NAME = "Liberty";
    public static final String MENU_OPEN_DASHBOARD_ACTION = "Open Dashboard";
    public static final String TOOLBAR_OPEN_DASHBOARD_TIP = "Open Liberty Dashboard View";
    public static final String DASHBOARD_TOOLBAR_REFRESH_TIP = "refresh";
    public static final String DASHBOARD_VIEW_TITLE = "Liberty Dashboard";

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
            SWTPluginOperations.openDashboardUsingToolbar(bot);
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
            SWTPluginOperations.openDashboardUsingToolbar(bot);
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
        openDashboardUsingToolbar(bot);
        bot.toolbarButtonWithTooltip(DASHBOARD_TOOLBAR_REFRESH_TIP).click();
    }

    /**
     * Launches the start menu action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchAppMenuStartAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu startAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_START);
        startAction.click();
    }

    /**
     * Launches the start with parameters menu action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchAppMenuStartWithParmsAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item, String parms) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu startAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_START_PARMS);
        startAction.click();
        SWTBotShell inputParmDialog = bot.activeShell();
        bot.waitUntil(SWTTestCondition.isDialogActive(inputParmDialog), 5000);

        SWTBotText textDialog = bot.textWithLabel(DevModeOperations.DEVMODE_START_PARMS_DIALOG_MSG);
        textDialog = textDialog.setText(parms);
        bot.button("OK").click();
    }

    /**
     * Launches the run test menu action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchAppMenuRunTestsAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu runTestsAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_RUN_TESTS);
        runTestsAction.click();
    }

    /**
     * Launches the stop menu action associated with the input application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The application name to select.
     */
    public static void launchAppMenuStopAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu stopAction = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_STOP);
        stopAction.click();
    }

    /**
     * Launches the menu action to view the integration test report associated with the input Maven application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The Maven application name to select.
     */
    public static void launchAppMenuViewMavenITReportAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu itReport = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT);
        itReport.click();

        bot.waitUntil(SWTTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_MVN_IT_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Launches the menu action to view the unit test report associated with the input Maven application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The Maven application name to select.
     */
    public static void launchAppMenuViewMavenUTReportAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu utReport = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT);
        utReport.click();

        bot.waitUntil(SWTTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_MVN_UT_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Launches the menu action to view the test report associated with the input Gradle application item.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item The Gradle application name to select.
     */
    public static void launchAppMenuViewGradleTestReportAction(SWTWorkbenchBot bot, SWTBotView dashboard, String item) {
        SWTBotRootMenu appCtxMenu = getAppContextMenu(bot, dashboard, item);
        SWTBotMenu testReport = appCtxMenu.contextMenu(DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT);
        testReport.click();

        bot.waitUntil(SWTTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX), 5000);
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
        SWTBotView peView = bot.viewByTitle("Project Explorer");
        peView.show();
        SWTBotTree projectExplorerContent = peView.bot().tree();
        SWTBotTreeItem project = null;
        for (SWTBotTreeItem projectFromTree : projectExplorerContent.getAllItems()) {
            if (projectFromTree.getText().contains(item)) {
                project = projectFromTree;
                break;
            }
        }

        return project;
    }

    /**
     * Returns the object representing the Run As menu.
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

        project.setFocus();
        runAsMenu = project.contextMenu("Run As").click();

        return runAsMenu;
    }

    /**
     * Returns the object representing the Debug As menu.
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

        project.setFocus();
        runAsMenu = project.contextMenu("Debug As").click();

        return runAsMenu;
    }

    /**
     * Returns the object representing the Run/Debug As->Run/Debug Configuration... menu entry.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     * 
     * @return The object representing the Run/Debug As->Run/Debug Configuration... menu entry.
     */
    public static SWTBotTreeItem getLibertyToolsConfigMenuEntry(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotTreeItem libertyToolsEntry = null;
        SWTBotMenu modeAsMenu = ("run".equals(mode)) ? getAppRunAsMenu(bot, item) : getAppDebugAsMenu(bot, item);
        String configMenuText = ("run".equals(mode)) ? "Run Configurations..." : "Debug Configurations...";

        SWTBotMenu runConfigMenu = modeAsMenu.menu(configMenuText);
        runConfigMenu.setFocus();
        runConfigMenu.click();

        libertyToolsEntry = bot.tree().getTreeItem("Liberty Tools");
        libertyToolsEntry.setFocus();

        return libertyToolsEntry;
    }

    /**
     * Deletes Liberty Tools configuration entries based on the supplied mode.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void deleteLibertyToolsConfigEntries(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotTreeItem libertyToolsEntry = getLibertyToolsConfigMenuEntry(bot, item, mode);
        libertyToolsEntry.setFocus();
        List<String> configs = libertyToolsEntry.getNodes();

        for (String config : configs) {
            SWTBotTreeItem configEntry = libertyToolsEntry.getNode(config);
            configEntry.select().setFocus();
            bot.toolbarButtonWithTooltip("Delete selected launch configuration(s)").click();
            SWTBotButton deleteButton = bot.button("Delete");
            deleteButton.setFocus();
            deleteButton.click();
        }

        SWTBotButton closeButton = bot.button("Close");
        closeButton.setFocus();
        closeButton.click();

    }

    /**
     * Launches dev mode start using a new Liberty tools configuration (Run/Debug As -> Run/Debug Configurations -> Liberty Tools ->
     * New config -> Run).
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchAppConfigStart(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotTreeItem libertyToolsEntry = getLibertyToolsConfigMenuEntry(bot, item, mode);
        libertyToolsEntry.doubleClick();

        SWTBotButton runButton = bot.button(("run".equals(mode)) ? "Run" : "Debug");
        runButton.setFocus();
        runButton.click();
    }

    /**
     * Launches dev mode with parms using a new Liberty tools configuration (Run/Debug As -> Run/Debug Configurations -> Liberty Tools
     * -> New config -> update parms -> Run). Note that the changes are not saved.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     * @param parms The parameter(s) to pass to the dev mode start action.
     */
    public static void launchAppConfigStartWithParms(SWTWorkbenchBot bot, String item, String mode, String parms) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotTreeItem libertyToolsEntry = getLibertyToolsConfigMenuEntry(bot, item, mode);
        libertyToolsEntry.doubleClick();

        SWTBotText parmTextBox = bot.textWithLabel("Start parameter:");
        parmTextBox.setFocus();
        parmTextBox.setText(parms);
        bot.waitUntil(SWTTestCondition.isTextPresent(parmTextBox, parms), 5000);

        SWTBotButton runButton = bot.button(("run".equals(mode)) ? "Run" : "Debug");
        runButton.setFocus();
        runButton.click();
    }

    /**
     * Launches dev mode using the run as configuration start shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchAppConfigShortcutStart(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotMenu modeAsMenu = ("run".equals(mode)) ? getAppRunAsMenu(bot, item) : getAppDebugAsMenu(bot, item);

        SWTBotMenu stopShortcut = modeAsMenu
                .menu(WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START + ".*"), false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();
    }

    /**
     * Stops dev mode using the run as configuration stop shortcut
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchAppConfigShortcutStop(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotMenu modeAsMenu = ("run".equals(mode)) ? getAppRunAsMenu(bot, item) : getAppDebugAsMenu(bot, item);
        SWTBotMenu stopShortcut = modeAsMenu
                .menu(WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP + ".*"), false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();

    }

    /**
     * Launches the run tests action using the run as configuration run tests shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     * @param mode The operating mode. It can be either \"run\" or \"debug\".
     */
    public static void launchAppRunAsShortcutRunTests(SWTWorkbenchBot bot, String item, String mode) {
        Assertions.assertTrue(("run".equals(mode) || "debug".equals(mode)),
                () -> "Invalid configration mode: " + mode + ". Accepted values: run, debug.");

        SWTBotMenu modeAsMenu = ("run".equals(mode)) ? getAppRunAsMenu(bot, item) : getAppDebugAsMenu(bot, item);
        SWTBotMenu stopShortcut = modeAsMenu.menu(
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS + ".*"), false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();
    }

    /**
     * Launches the action that displays the maven IT test report using the run as configuration view IT test report shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchAppRunAsShortcutViewMavenITReport(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT + ".*"), false,
                0);
        stopShortcut.setFocus();
        stopShortcut.click();

        bot.waitUntil(SWTTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_MVN_IT_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Launches the action that displays the maven UT test report using the run as configuration view UT test report shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchAppRunAsShortcutViewMavenUTReport(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_UT_REPORT + ".*"), false,
                0);
        stopShortcut.setFocus();
        stopShortcut.click();

        bot.waitUntil(SWTTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_MVN_UT_REPORT_NAME_SUFFIX), 5000);
    }

    /**
     * Launches the action that displays the gradle test report using the run as configuration view test report shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchAppRunAsShortcutViewGradleTestReport(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT + ".*"),
                false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();

        bot.waitUntil(SWTTestCondition.isEditorActive(bot, item + " " + DevModeOperations.BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX), 5000);
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

        appProj.select();
        appProj.expand();
        SWTBotTreeItem file = appProj.getNode(fileName);
        file.select();
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
            SWTPluginOperations.openDashboardUsingToolbar(bot);
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
        SWTBotToolbarButton toolbarButton = getToolbarButtonWithToolTipPrefix(bot, TOOLBAR_OPEN_DASHBOARD_TIP);
        toolbarButton.click();
        SWTBotView dashboard = bot.viewByTitle(DASHBOARD_VIEW_TITLE);
        dashboard.show();
        bot.waitUntil(SWTTestCondition.isViewActive(dashboard, DASHBOARD_VIEW_TITLE), 5000);
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
     * Returns a SWTBotToolbarButton instance representing the toolbar button with the input tooltip prefix.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param toolTipPrefix The tooltip prefix.
     *
     * @return A SWTBotToolbarButton instance representing the toolbar button with the input tooltip prefix.
     */
    @SuppressWarnings("unchecked")
    public static SWTBotToolbarButton getToolbarButtonWithToolTipPrefix(SWTWorkbenchBot bot, String toolTipPrefix) {
        Matcher<Item> matcher = allOf(widgetOfType(ToolItem.class), new ToolTipPrefixMatcher<Item>(toolTipPrefix));
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

    /**
     * Toolbar button tip prefix matcher.
     */
    public static class ToolTipPrefixMatcher<T> extends BaseMatcher<T> {

        final String toolTipPrefix;

        /**
         * Constructor.
         *
         * @param toolTipPrefix The tooltip prefix to match.
         */
        public ToolTipPrefixMatcher(String toolTipPrefix) {
            this.toolTipPrefix = toolTipPrefix;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void describeTo(Description description) {
            description.appendText("with tooltip prefix '").appendText(toolTipPrefix).appendText("'");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(Object object) {
            boolean matchFound = false;

            try {
                Object tooltipText = SWTUtils.invokeMethod(object, "getToolTipText");
                if (tooltipText instanceof String) {
                    matchFound = ((String) tooltipText).startsWith(toolTipPrefix);
                }
            } catch (Exception e) {
                System.out.println("INFO: Unabled to find tooltip with prefix: " + toolTipPrefix + ". Error: " + e.getMessage());
            }

            return matchFound;
        }
    }
}
