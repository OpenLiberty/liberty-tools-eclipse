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

import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.context;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.expandTreeItem;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.find;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.findGlobal;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.go;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.goGlobal;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.goMenuItem;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.set;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.allOf;
import static org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory.widgetOfType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCTabItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotRootMenu;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotStyledText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarPushButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.part.ViewPart;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Assertions;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.test.it.AbstractLibertyPluginSWTBotTest;
import io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.ControlFinder;
import io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.ControlFinder.Direction;
import io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.Option;
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
    public static final String EXPLORER_CONFIGURE_MENU_ENABLE_LIBERTY_TOOLS = "Enable Liberty";
    public static final String NEW_CONFIGURATION = "New_configuration";

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
     * Gets the context menu item labeled "Connect Liberty Debugger" for the given
     * debug object. The debug object can either be a launch, a debug target, or a
     * process in the Debug View.
     * 
     * @param debugObject - The debug object in the Debug View
     * 
     * @return
     */
    public static SWTBotMenu getDebuggerConnectMenuForDebugObject(Object debugObject) {
        openDebugPerspective();
        Object windowMenu = findGlobal("Window", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Show View", "Debug");

        SWTBotTreeItem obj = new SWTBotTreeItem((TreeItem) debugObject);

        return obj.contextMenu("Connect Liberty Debugger");
    }

    /**
     * Disconnects the given debug target (debugger) in the Debug View
     * 
     * @param debugTarget - The debug target object in the Debug View
     * 
     * @return
     */
    public static void disconnectDebugTarget(Object debugTarget) {
        openDebugPerspective();
        Object windowMenu = findGlobal("Window", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Show View", "Debug");

        MagicWidgetFinder.context(debugTarget, "Disconnect");

        MagicWidgetFinder.pause(3000);
    }

    /**
     * Terminate the launch
     */
    public static void terminateLaunch() {
        openDebugPerspective();
        Object windowMenu = findGlobal("Window", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Show View", "Debug");

        Object debugView = MagicWidgetFinder.findGlobal("Debug");

        Object launch = MagicWidgetFinder.find("[Liberty]", debugView,
                Option.factory().useContains(true).setThrowExceptionOnNotFound(false).build());

        MagicWidgetFinder.context(launch, "Terminate and Remove");

        try {
            Shell confirm = (Shell) findGlobal("Terminate and Remove", Option.factory().widgetClass(Shell.class).build());

            MagicWidgetFinder.go("Yes", confirm);
            MagicWidgetFinder.pause(3000);
        } catch (Exception e) {
            // The configrmation pop up window only shows if the launch has not yet been terminated.
            // If it has been terminated (or stopped), there is no confirmation.
        }

    }

    /**
     * Returns the debug object item in the Debug View with the given name.
     * The debug object can either be a launch, a debug target, or a process in the Debug View.
     * 
     * @param objectName - The name of the object in the Debug View.
     * 
     * @return
     */
    public static Object getObjectInDebugView(String objectName) {
        openDebugPerspective();
        Object windowMenu = findGlobal("Window", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Show View", "Debug");

        Object debugView = MagicWidgetFinder.findGlobal("Debug");

        return MagicWidgetFinder.find(objectName, debugView,
                Option.factory().useContains(true).setThrowExceptionOnNotFound(false).widgetClass(TreeItem.class).build());
    }

    /**
     * Open the Eclipse debug perspective.
     */
    public static void openDebugPerspective() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    IWorkbench wb = PlatformUI.getWorkbench();
                    wb.showPerspective("org.eclipse.debug.ui.DebugPerspective", wb.getActiveWorkbenchWindow());
                } catch (WorkbenchException we) {
                    // Print a message. Lighter environments may not support this perspective.
                    System.out.println("INFO: Debug perspective was not opened: " + we.getMessage());
                }
            }
        };

        Display.getDefault().syncExec(runnable);
    }

    public static void openJavaPerspectiveViaMenu() {
        Object windowMenu = findGlobal("Window", Option.factory().widgetClass(MenuItem.class).build());

        if (new SWTWorkbenchBot().activePerspective().getLabel().equals("Java")) {
            // Won't be an option to switch to if already active
            return;
        } else {
            goMenuItem(windowMenu, "Perspective", "Open Perspective", "Java");
        }
    }

    public static SWTBotTable getDashboardTable() {
        openDashboardUsingToolbar();
        Object dashboardView = findGlobal(DASHBOARD_VIEW_TITLE, Option.factory().widgetClass(ViewPart.class).build());
        Table table = ((DashboardView) dashboardView).getTable();
        return new SWTBotTable(table);
    }

    /**
     * Returns a list of entries on the Open Liberty dashboard.
     * 
     * @return A list of entries on the Open Liberty dashboard.
     */
    public static List<String> getDashboardContent() {
        SWTBotTable dashboardTable = getDashboardTable();

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
    public static List<String> getDashboardItemMenuActions(String item) {

        SWTBotTable dashboardTable = getDashboardTable();
        dashboardTable.select(item);
        SWTBotRootMenu appCtxMenu = dashboardTable.contextMenu();
        return appCtxMenu.menuItems();
    }

    /**
     * Clicks on the refresh icon on the Open Liberty dashboard.
     */
    public static void refreshDashboard() {
        Object dashboardView = findGlobal(DASHBOARD_VIEW_TITLE);
        go(DASHBOARD_TOOLBAR_REFRESH_TIP, dashboardView);
    }

    /**
     * Refreshes the application project through the explorer view (explorer-> right click on project -> refresh).
     * 
     * @param appName The application name to select.
     */
    public static void refreshProjectUsingExplorerView(String appName) {
        Object peView = MagicWidgetFinder.findGlobal("Project Explorer");
        Object project = MagicWidgetFinder.find(appName, peView);

        MagicWidgetFinder.context(project, "Refresh");
    }

    /**
     * Launches a dashboard action for the specified application name.
     * 
     * @param appName The application name to select.
     * @param action The action to select
     */
    public static void launchDashboardAction(String appName, String action) {
        openDashboardUsingToolbar();

        Object dashboardView = MagicWidgetFinder.findGlobal(DASHBOARD_VIEW_TITLE);
        Object project = MagicWidgetFinder.find(appName, dashboardView, Option.factory().widgetClass(TableItem.class).build());
        MagicWidgetFinder.context(project, action);
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
        Object locationLabel = null;
        Object locationText = null;

        finalMvnExecutableLoc = AbstractLibertyPluginSWTBotTest.getMvnCmdPath();
        finalGradleExecutableLoc = AbstractLibertyPluginSWTBotTest.getGradleCmdPath();

        Object windowMenu = findGlobal("Window", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Preferences");

        TreeItem liberty = (TreeItem) findGlobal("Liberty", Option.factory().widgetClass(TreeItem.class).build());
        go(liberty);
        if (buildTool == "Maven") {
            locationLabel = findGlobal("Maven Install Location:", Option.factory().widgetClass(Label.class).build());
            locationText = ControlFinder.findControlInRange(locationLabel, Text.class, Direction.EAST);
            set(locationText, finalMvnExecutableLoc);
        } else if (buildTool == "Gradle") {
            locationLabel = findGlobal("Gradle Install Location:", Option.factory().widgetClass(Label.class).build());
            locationText = ControlFinder.findControlInRange(locationLabel, Text.class, Direction.EAST);
            set(locationText, finalGradleExecutableLoc);
        }

        goGlobal("Apply and Close");
    }

    public static void unsetBuildCmdPathInPreferences(SWTWorkbenchBot bot, String buildTool) {

        /* Preferences are accessed from a different menu on macOS than on Windows and Linux */
        /* Currently not possible to access the Preferences dialog panel on macOS so we */
        /* will return and just use an app configured with a wrapper */
        if (Platform.getOS().equals(Platform.OS_MACOSX)) {
            return;
        }

        Object windowMenu = findGlobal("Window", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Preferences");

        findGlobal("Liberty", Option.factory().widgetClass(TreeItem.class).build());

        goGlobal("Restore Defaults");
        goGlobal("Apply and Close");
    }

    /**
     * Launches the run configuration dialog.
     * 
     * @param appName The application name.
     */
    public static Shell launchRunConfigurationsDialogFromAppRunAs(String appName) {

        Object project = getAppInPackageExplorerTree(appName);

        MagicWidgetFinder.context(project, "Run As", "Run Configurations...");

        // Return the newly launched configurations shell
        return (Shell) findGlobal("Run Configurations", Option.factory().widgetClass(Shell.class).build());
    }

    /**
     * Launches the run configuration dialog.
     * 
     * @param appName The application name.
     */
    public static Shell launchDebugConfigurationsDialogFromAppRunAs(String appName) {

        Object project = getAppInPackageExplorerTree(appName);

        MagicWidgetFinder.context(project, "Debug As", "Debug Configurations...");

        // Return the newly launched configurations shell
        return (Shell) findGlobal("Debug Configurations", Option.factory().widgetClass(Shell.class).build());
    }

    /**
     * Launches the debug configuration dialog.
     */
    public static Shell launchDebugConfigurationsDialogFromMenu() {
        Object windowMenu = findGlobal("Run", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Debug Configurations...");
        return (Shell) findGlobal("Debug Configurations", Option.factory().widgetClass(Shell.class).build());
    }

    /**
     * Launches the debug configuration dialog.
     */
    public static Shell launchRunConfigurationsDialogFromMenu() {
        Object windowMenu = findGlobal("Run", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Run Configurations...");
        return (Shell) findGlobal("Run Configurations", Option.factory().widgetClass(Shell.class).build());
    }

    public static SWTBotTreeItem getLibertyTreeItem(Shell shell) {
        return new SWTBotTreeItem(getLibertyTreeItemNoBot(shell));
    }

    public static TreeItem getLibertyTreeItemNoBot(Shell shell) {
        TreeItem ti = (TreeItem) find(LAUNCH_CONFIG_LIBERTY_MENU_NAME, shell);
        expandTreeItem(ti);
        return ti;
    }

    public static TreeItem getDefaultSourceLookupTreeItemNoBot(Shell shell) {
        TreeItem ti = (TreeItem) find("Default", shell);
        expandTreeItem(ti);
        return ti;
    }

    public static SWTBotTreeItem getLibertyToolsConfigMenuItem(Shell shell) {
        return new SWTBotTreeItem((TreeItem) find(LAUNCH_CONFIG_LIBERTY_MENU_NAME, shell));
    }

    /**
     * Deletes Liberty run configuration entries.
     * 
     * @param bot The SWTWorkbenchBot instance..
     * @param appName The application name.
     */
    public static void deleteLibertyToolsRunConfigEntriesFromAppRunAs(String appName) {

        Shell configShell = launchRunConfigurationsDialogFromAppRunAs(appName);

        try {
            SWTBotTreeItem libertyToolsEntry = getLibertyTreeItem(configShell);

            Assertions.assertTrue((libertyToolsEntry != null), () -> "The Liberty entry was not found in run Configurations dialog.");

            List<String> configs = libertyToolsEntry.getNodes();

            for (String config : configs) {
                deleteRunDebugConfigEntry(libertyToolsEntry, config);
            }
        } finally {
            // Close the configuration dialog.
            MagicWidgetFinder.go("Close", configShell);
        }
    }

    /**
     * Deletes Liberty debug configuration entries.
     * 
     * @param bot The SWTWorkbenchBot instance..
     * @param appName The application name.
     */
    public static void deleteLibertyToolsDebugConfigEntriesFromMenu() {

        Shell configShell = launchDebugConfigurationsDialogFromMenu();

        try {
            SWTBotTreeItem libertyToolsEntry = getLibertyTreeItem(configShell);
            Assertions.assertTrue((libertyToolsEntry != null), () -> "The Liberty entry was not found in run Configurations dialog.");

            for (String config : libertyToolsEntry.getNodes()) {
                deleteRunDebugConfigEntry(libertyToolsEntry, config);
            }

        } finally {
            // Close the configuration dialog.
            MagicWidgetFinder.go("Close", configShell);
        }
    }

    private static void deleteRunDebugConfigEntry(SWTBotTreeItem parentTree, String configName) {
        go(configName, parentTree);
        goGlobal("Delete selected launch configuration(s)", Option.factory().widgetClass(ToolItem.class).useContains(true).build());
        go("Delete", parentTree);
    }

    /**
     * Launches dev mode start using a new Liberty configuration: project -> Run As -> Liberty Start
     * 
     * @param item The application name.
     */
    public static void launchStartWithDefaultRunConfigFromAppRunAs(String appName) {

        Object project = getAppInPackageExplorerTree(appName);
        context(project, "Run As", WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START + ".*"));

    }

    /**
     * Launches dev mode with parms using a new Liberty configuration: project -> Run As -> Run Configurations -> Liberty -> New
     * configuration (default) -> update parms -> Run. Note that the changes are not saved.
     * 
     * @param appName The application name.
     * @param customParms The parameter(s) to pass to the dev mode start action.
     */
    public static void launchStartWithNewCustomRunConfig(String appName, String customParms) {
        Shell shell = launchRunConfigurationsDialogFromAppRunAs(appName);
        createAndSetNewCustomConfig(shell, customParms);
        go("Run", shell);
    }

    /**
     * Launches dev mode with parms using a new Liberty configuration: project -> Debug As -> Debug Configurations -> Liberty -> New
     * configuration (default) -> update parms -> Debug. Note that the changes are not saved.
     * 
     * @param appName The application name.
     * @param customParms The parameter(s) to pass to the dev mode start action.
     */
    public static void launchStartWithNewCustomDebugConfig(String appName, String customParms) {
        Shell shell = launchDebugConfigurationsDialogFromAppRunAs(appName);
        createAndSetNewCustomConfig(shell, customParms);
        go("Debug", shell);
    }

    public static void createAndSetNewCustomConfig(Shell shell, String customParms) {

        Object libertyConfigTree = find(LAUNCH_CONFIG_LIBERTY_MENU_NAME, shell);

        context(libertyConfigTree, "New Configuration");
        Object parmLabel = find("Start parameters:", shell, Option.factory().widgetClass(Label.class).build());
        Control parmText = ControlFinder.findControlInRange(parmLabel, Text.class, Direction.EAST);
        set(parmText, customParms);
    }

    public static Shell getDebugConfigurationsShell() {
        return (Shell) findGlobal("Debug Configurations", Option.factory().widgetClass(Shell.class).build());
    }

    public static Shell getRunConfigurationsShell() {
        return (Shell) findGlobal("Run Configurations", Option.factory().widgetClass(Shell.class).build());
    }

    public static void launchCustomDebugFromDashboard(String appName, String customParms) {
        launchDashboardAction(appName, DashboardView.APP_MENU_ACTION_DEBUG_CONFIG);
        Shell shell = getDebugConfigurationsShell();
        setCustomStartParmsFromShell(shell, appName, customParms);
        go("Debug", shell);
    }

    public static void launchCustomRunFromDashboard(String appName, String customParms) {
        launchDashboardAction(appName, DashboardView.APP_MENU_ACTION_START_CONFIG);
        Shell shell = getRunConfigurationsShell();
        setCustomStartParmsFromShell(shell, appName, customParms);
        go("Run", shell);
    }

    public static void setCustomStartParmsFromShell(Shell shell, String runDebugConfigName, String customParms) {

        Object libertyConfigTree = getLibertyTreeItem(shell);

        Object appConfigEntry = find(runDebugConfigName, libertyConfigTree,
                Option.factory().useContains(true).widgetClass(TreeItem.class).build());
        go(appConfigEntry);
        Object parmLabel = find("Start parameters:", appConfigEntry, Option.factory().widgetClass(Label.class).build());

        Control parmText = ControlFinder.findControlInRange(parmLabel, Text.class, Direction.EAST);
        set(parmText, customParms);
    }

    public static void checkRunInContainerCheckBox(Shell shell, String runDebugConfigName) {

        Object libertyConfigTree = getLibertyTreeItem(shell);

        Object appConfigEntry = find(runDebugConfigName, libertyConfigTree,
                Option.factory().useContains(true).widgetClass(TreeItem.class).build());
        go(appConfigEntry);
        Object button = find("Run in Container", appConfigEntry, Option.factory().widgetClass(Button.class).build());

        go(button);
    }

    public static Object getAppInPackageExplorerTree(String appName) {
        openJavaPerspectiveViaMenu();
        Object windowMenu = findGlobal("Window", Option.factory().widgetClass(MenuItem.class).build());
        goMenuItem(windowMenu, "Show View", "Package Explorer");
        Object peView = MagicWidgetFinder.findGlobal("Package Explorer");

        Object project = MagicWidgetFinder.find(appName, peView, Option.factory().useContains(true).widgetClass(TreeItem.class).build());
        go(project);
        return project;
    }

    /**
     * Launches the start action using the debug as configuration shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param appName The application name.
     */
    public static void launchStartWithDebugAsShortcut(String appName) {
        Object project = getAppInPackageExplorerTree(appName);
        MagicWidgetFinder.context(project, "Debug As",
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START + ".*"));
    }

    /**
     * Launches the start action using the run as configuration shortcut.
     * 
     * @param bot The SWTWorkbenchBot instance.
     * @param appName The application name.
     */
    public static void launchStartWithRunAsShortcut(String appName) {
        Object project = getAppInPackageExplorerTree(appName);
        MagicWidgetFinder.context(project, "Run As",
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START + ".*"));
    }

    /**
     * Launches the stop action using the run as configuration shortcut.
     * 
     * @param appName The application name.
     */
    public static void launchStopWithRunAsShortcut(String appName) {
        Object project = getAppInPackageExplorerTree(appName);
        MagicWidgetFinder.context(project, "Run As",
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_STOP + ".*"));
    }

    /**
     * Launches the run tests action using the run as configuration shortcut.
     * 
     * @param appName The application name.
     */
    public static void launchRunTestsWithRunAsShortcut(String appName) {
        Object project = getAppInPackageExplorerTree(appName);
        MagicWidgetFinder.context(project, "Run As",
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS + ".*"));
    }

    /**
     * Launches the run tests action using the run as configuration shortcut.
     * 
     * @param appName The application name.
     */
    public static void launchRunTestsWithDebugAsShortcut(String appName) {
        Object project = getAppInPackageExplorerTree(appName);
        MagicWidgetFinder.context(project, "Debug As",
                WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_RUN_TESTS + ".*"));
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
     * Enables Liberty tools on the input project by clicking on explorer->project->right-click->Configure->Enable Liberty.
     * 
     * @param appName The application name.
     */
    public static void enableLibertyTools(String appName) {

        Object project = getAppInPackageExplorerTree(appName);

        context(project, "Configure", WidgetMatcherFactory.withRegex(".*" + EXPLORER_CONFIGURE_MENU_ENABLE_LIBERTY_TOOLS + ".*"));
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
     * @param item The application name to select.
     *
     * @return The context menu object associated with the input application item.
     */
    public static SWTBotRootMenu getAppContextMenu(String item) {

        SWTBotTable dashboardTable = getDashboardTable();
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
    public static void openDashboardUsingToolbar() {
        goGlobal(TOOLBAR_OPEN_DASHBOARD_TIP, Option.factory().widgetClass(ToolItem.class).useContains(true).build());
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
     * Switches the Liberty run configuration main tab to the Source Tab. A Liberty configuration must be opened prior to calling this
     * method.
     * 
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void openSourceTab(SWTWorkbenchBot bot) {
        SWTBotShell shell = bot.shell("Debug Configurations");
        shell.activate().setFocus();
        SWTBot shellBot = shell.bot();
        SWTBotCTabItem tabItem = shellBot.cTabItem("Source");
        tabItem.activate().setFocus();
    }

    /**
     * Switches the Liberty run configuration main tab to the Common Tab. This operation will fail if tab is not
     * successfully launched or switched to
     * 
     * @param bot The SWTWorkbenchBot instance.
     */
    public static void openCommonTab(SWTWorkbenchBot bot) {
        SWTBotShell shell = bot.shell("Run Configurations");
        shell.activate().setFocus();
        SWTBot shellBot = shell.bot();
        SWTBotCTabItem tabItem = shellBot.cTabItem("Common");
        tabItem.activate().setFocus();
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
            // Not a problem if error wasn't generated. Continue...
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
