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

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
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
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotToolbarPushButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
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
        // Open Debug view using Eclipse API instead of menu navigation.
        // This is more reliable in headless CI environments.
        showDebugView();

        SWTBotTreeItem obj = new SWTBotTreeItem((TreeItem) debugObject);

        // Select and focus the item so the workbench selection service has it as the
        // active selection. LibertyDebugReconnectHandler.isEnabled() reads the selection
        // service, not the object passed here, so the selection must be current before
        // the context menu is opened.
        // Note: do NOT wait for isEnabled() here — callers may legitimately be checking
        // that the menu is *disabled*, and waiting for enabled would mask that state.
        obj.select();
        obj.setFocus();

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
        // Open Debug view using Eclipse API instead of menu navigation
        // This is more reliable in headless CI environments
        showDebugView();

        // Wait until the item is enabled before accessing its context menu.
        SWTBotTreeItem obj = new SWTBotTreeItem((TreeItem) debugTarget);
        obj.select();
        obj.setFocus();
        SWTBotTestCondition.waitFor(obj::isEnabled, SWTBotTestCondition.VALIDATION_WAIT_MS);

        MagicWidgetFinder.context(debugTarget, "Disconnect");

        // Wait for disconnect to complete by polling the item's disposed/enabled state.
        SWTBotTestCondition.waitFor(() -> !isTreeItemEnabled(obj), SWTBotTestCondition.MIN_WAIT_MS);
    }

    /**
     * Returns true if the input tree item is enabled, false otherwise.
     * 
     * @param item The tree item.
     * 
     * @return True if the input tree item is enabled, false otherwise.
     */
    private static boolean isTreeItemEnabled(SWTBotTreeItem item) {
        try {
            return item.isEnabled();
        } catch (Exception e) {
            // Item disposed or no longer accessible — disconnect completed.
            return false;
        }
    }

    /**
     * Terminates the Liberty launch if one is present in the Debug view, then verifies
     * it is gone using a single instant check (no polling).
     *
     * <p>Callers do not need to do any follow-up lookup — the assertion is done here.
     *
     * @throws AssertionError if a launch was found and terminated but the Debug view
     *                            still shows it immediately after termination.
     */
    public static void terminateLaunch() {
        String searchObjectName = "[Liberty]";
        Object launch = null;
        if (isObjectInDebugView(searchObjectName)) {
            launch = getObjectInDebugView(searchObjectName);
        }

        if (launch != null) {
            System.out.println("Found Liberty launch, attempting to terminate");
            MagicWidgetFinder.context(launch, "Terminate and Remove");

            // The confirmation shell only appears when the process is still running.
            if (isShellVisible("Terminate and Remove")) {
                Shell confirm = (Shell) findGlobal("Terminate and Remove", Option.factory().widgetClass(Shell.class).build());
                MagicWidgetFinder.go("Yes", confirm);
                SWTBotTestCondition.waitFor(() -> !isShellVisible("Terminate and Remove"), SWTBotTestCondition.MIN_WAIT_MS);
            }

            boolean stillPresent = !SWTBotTestCondition.waitFor(
                                                                 () -> !isObjectInDebugView(searchObjectName), SWTBotTestCondition.MIN_WAIT_MS);
            org.junit.jupiter.api.Assertions.assertFalse(stillPresent,
                                                         "Liberty launch was not removed from the Debug view after termination.");
        } else {
            System.out.println("No Liberty launch found in Debug view to terminate");
        }
    }

    /**
     * Returns true while a shell with the given title is still open and visible.
     * 
     * @param title The shell title.
     */
    private static boolean isShellVisible(String title) {
        final boolean[] found = { false };
        Display.getDefault().syncExec(() -> {
            for (org.eclipse.swt.widgets.Shell s : Display.getDefault().getShells()) {
                if (!s.isDisposed() && s.isVisible() && title.equals(s.getText())) {
                    found[0] = true;
                    return;
                }
            }
        });
        return found[0];
    }

    /**
     * Returns true if an object containing the input object name currently exists in the
     * Debug view, false otherwise.
     */
    public static boolean isObjectInDebugView(final String objectName) {
        final boolean[] found = { false };
        Display.getDefault().syncExec(() -> {
            try {
                IWorkbench wb = PlatformUI.getWorkbench();
                IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
                if (window == null || window.getActivePage() == null) {
                    return;
                }
                ViewPart debugView = (ViewPart) window.getActivePage().findView("org.eclipse.debug.ui.DebugView");
                if (debugView == null) {
                    return;
                }
                Object result = MagicWidgetFinder.find(objectName, debugView,
                                                       Option.factory().useContains(true).setThrowExceptionOnNotFound(false).setRetryAttempts(0).widgetClass(TreeItem.class).build());
                found[0] = (result != null);
            } catch (Exception ignored) {
                // View not ready. Treat as not found.
            }
        });
        return found[0];
    }

    /**
     * Returns the debug object item in the Debug View with the given name.
     * The debug object can either be a launch, a debug target, or a process in the Debug View.
     *
     * @param objectName - The name of the object in the Debug View.
     *
     * @return
     */
    public static Object getObjectInDebugView(final String objectName) {
        // Don't wrap in syncExec - MagicWidgetFinder methods already handle thread synchronization
        // Nested syncExec calls can cause deadlocks in headless CI environments
        openDebugPerspective();
        showDebugView();

        // Get the Debug view directly using Eclipse API instead of text search
        // This is more reliable than findGlobal("Debug") which could find the wrong view
        final Object[] debugViewHolder = new Object[1];
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    IWorkbench wb = PlatformUI.getWorkbench();
                    IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
                    if (window != null && window.getActivePage() != null) {
                        // Get the Debug view by its ID
                        ViewPart debugView = (ViewPart) window.getActivePage().findView("org.eclipse.debug.ui.DebugView");
                        if (debugView != null) {
                            // Activate it to ensure widgets are rendered
                            window.getActivePage().activate(debugView);
                            debugViewHolder[0] = debugView;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to get Debug view: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        // Wait for the Debug view to activate before searching for items in it.
        SWTBotTestCondition.waitFor(
                                    () -> isDebugViewPresent(), SWTBotTestCondition.VALIDATION_WAIT_MS);

        Object debugView = debugViewHolder[0];
        if (debugView == null) {
            System.err.println("Debug view not found, cannot find object: " + objectName);
            return null;
        }

        // Try multiple times to find the object, as it may take time to appear in headless CI.
        final Object debugViewFinal = debugView;
        final Object[] resultHolder = { null };
        final Option singleShotOption = Option.factory().useContains(true).setThrowExceptionOnNotFound(false).setRetryAttempts(0).widgetClass(TreeItem.class).build();
        SWTBotTestCondition.waitFor(() -> {
            resultHolder[0] = MagicWidgetFinder.find(objectName, debugViewFinal, singleShotOption);
            return resultHolder[0] != null;
        }, SWTBotTestCondition.MIN_WAIT_MS);
        Object result = resultHolder[0];

        if (result == null) {
            System.out.println("Object not found in Debug view: " + objectName);
        }

        return result;
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

    /**
     * Opens the Debug view using Eclipse API directly.
     * This is more reliable than menu navigation in headless CI environments.
     */
    private static void showDebugView() {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    IWorkbench wb = PlatformUI.getWorkbench();
                    IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
                    if (window != null && window.getActivePage() != null) {
                        // Show the Debug view using its ID
                        window.getActivePage().showView("org.eclipse.debug.ui.DebugView");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to open Debug view: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        // Wait for the Debug view to open.
        SWTBotTestCondition.waitFor(
                                    () -> isDebugViewPresent(), SWTBotTestCondition.MIN_WAIT_MS);
    }

    /**
     * Returns true once the Debug view becomes visible on the active page.
     * 
     * @return True once the Debug view becomes visible on the active page.
     */
    private static boolean isDebugViewPresent() {
        final boolean[] visible = { false };
        Display.getDefault().syncExec(() -> {
            try {
                org.eclipse.ui.IWorkbenchWindow window = org.eclipse.ui.PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window != null && window.getActivePage() != null) {
                    org.eclipse.ui.IViewPart view = window.getActivePage().findView("org.eclipse.debug.ui.DebugView");
                    visible[0] = (view != null);
                }
            } catch (Exception ignored) {
                // View not ready yet; waitFor will retry.
            }
        });
        return visible[0];
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

    /**
     * Returns the dashboard tree widget.
     *
     * @return The dashboard tree widget.
     */
    public static SWTBotTree getDashboardTree() {
        openDashboardUsingToolbar();

        // Ensure the dashboard view is actually shown and has focus.
        // This prevents finding the wrong view (like ConsoleView) when the console takes focus after server start.
        Object dashboardView = findGlobal(DASHBOARD_VIEW_TITLE, Option.factory().widgetClass(ViewPart.class).build());

        // Explicitly show and activate the dashboard view to ensure it has focus.
        if (dashboardView instanceof ViewPart) {
            final ViewPart vp = (ViewPart) dashboardView;
            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run() {
                    try {
                        IWorkbench wb = PlatformUI.getWorkbench();
                        IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
                        if (window != null && window.getActivePage() != null) {
                            window.getActivePage().activate(vp);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to activate dashboard view: " + e.getMessage());
                    }
                }
            });

        }

        Tree tree = ((DashboardView) dashboardView).getTree();
        return new SWTBotTree(tree);
    }

    /**
     * Returns a list of entries on the Open Liberty dashboard.
     * This includes both root level projects and their children in a hierarchical tree.
     * Tree items are expanded before collecting to ensure all children are visible.
     *
     * @return A list of entries on the Open Liberty dashboard.
     */
    public static List<String> getDashboardContent() {
        SWTBotTree dashboardTree = getDashboardTree();

        ArrayList<String> contentList = new ArrayList<String>();
        // Get all tree items (root level projects)
        SWTBotTreeItem[] items = dashboardTree.getAllItems();
        for (SWTBotTreeItem item : items) {
            contentList.add(item.getText());
            // Expand the item to make children visible.
            item.expand();
            // Recursively add child items.
            addChildItems(item, contentList);
        }

        return contentList;
    }

    /**
     * Recursively adds child tree items to the content list.
     * Expands each item before processing its children.
     *
     * @param parent      The parent tree item
     * @param contentList The list to add items to
     */
    private static void addChildItems(SWTBotTreeItem parent, ArrayList<String> contentList) {
        SWTBotTreeItem[] children = parent.getItems();
        for (SWTBotTreeItem child : children) {
            contentList.add(child.getText());
            // Expand child to make grandchildren visible
            child.expand();
            // Recursively process grandchildren
            addChildItems(child, contentList);
        }
    }

    /**
     * Returns a list of menu actions associated with the input application item.
     *
     * @param bot       The SWTWorkbenchBot instance.
     * @param dashboard An instance representing the Open Liberty dashboard view.
     * @param item      The application name to select.
     *
     * @return A list of menu actions for the input application item.
     */
    public static List<String> getDashboardItemMenuActions(String item) {

        SWTBotTree dashboardTree = getDashboardTree();
        SWTBotTreeItem treeItem = findTreeItem(dashboardTree, item);
        if (treeItem == null) {
            throw new org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException("Tree item not found: " + item);
        }
        treeItem.select();
        SWTBotRootMenu appCtxMenu = treeItem.contextMenu();
        return appCtxMenu.menuItems();
    }

    /**
     * Recursively searches for a tree item by name in the dashboard tree.
     * Searches both root items and nested children.
     *
     * @param tree     The dashboard tree
     * @param itemName The name of the item to find
     * @return The tree item if found, null otherwise
     */
    private static SWTBotTreeItem findTreeItem(SWTBotTree tree, String itemName) {
        // First try root level items
        SWTBotTreeItem[] rootItems = tree.getAllItems();
        for (SWTBotTreeItem rootItem : rootItems) {
            if (rootItem.getText().equals(itemName)) {
                return rootItem;
            }
            // Expand and search children recursively
            rootItem.expand();
            SWTBotTreeItem found = findTreeItemInChildren(rootItem, itemName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Recursively searches for a tree item by name within the children of a parent item.
     *
     * @param parent   The parent tree item
     * @param itemName The name of the item to find
     * @return The tree item if found, null otherwise
     */
    private static SWTBotTreeItem findTreeItemInChildren(SWTBotTreeItem parent, String itemName) {
        SWTBotTreeItem[] children = parent.getItems();
        for (SWTBotTreeItem child : children) {
            if (child.getText().equals(itemName)) {
                return child;
            }
            // Expand and search grandchildren recursively
            child.expand();
            SWTBotTreeItem found = findTreeItemInChildren(child, itemName);
            if (found != null) {
                return found;
            }
        }
        return null;
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
     * @param action  The action to select
     */
    public static void launchDashboardAction(String appName, String action) {
        SWTBotTree dashboardTree = getDashboardTree();
        SWTBotTreeItem treeItem = findTreeItem(dashboardTree, appName);
        if (treeItem == null) {
            throw new org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException("Tree item not found: " + appName);
        }
        treeItem.select();
        SWTBotRootMenu appCtxMenu = treeItem.contextMenu();
        appCtxMenu.menu(action).click();
    }

    /**
     * Returns the object representing the active project matching the input project name.
     * 
     * @param bot  The SWTWorkbenchBot instance.
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
     * @param bot  The SWTWorkbenchBot instance.
     * @param item The application name.
     * 
     * @return The object representing the Run As menu.
     */
    public static SWTBotMenu getAppRunAsMenu(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = null;
        SWTBotTreeItem project = getInstalledProjectItem(bot, item);
        Assertions.assertTrue(project != null, () -> "Could not find active project.");
        SWTBotTestCondition.waitFor(project::isEnabled, SWTBotTestCondition.MIN_WAIT_MS);
        project.select().setFocus();

        runAsMenu = project.contextMenu("Run As");
        SWTBotTestCondition.waitFor(runAsMenu::isEnabled, SWTBotTestCondition.MIN_WAIT_MS);
        runAsMenu.click();

        return runAsMenu;
    }

    /**
     * Returns the object representing the explorer->project->right-click->Debug As menu.
     * 
     * @param bot  The SWTWorkbenchBot instance.
     * @param item The application name.
     * 
     * @return The object representing the Debug As menu.
     */
    public static SWTBotMenu getAppDebugAsMenu(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = null;

        SWTBotTreeItem project = getInstalledProjectItem(bot, item);
        Assertions.assertTrue(project != null, () -> "Could not find active project.");
        SWTBotTestCondition.waitFor(project::isEnabled, SWTBotTestCondition.MIN_WAIT_MS);
        project.select().setFocus();

        runAsMenu = project.contextMenu("Debug As");
        SWTBotTestCondition.waitFor(runAsMenu::isEnabled, SWTBotTestCondition.MIN_WAIT_MS);
        runAsMenu.click();

        return runAsMenu;
    }

    /**
     * Sets the absolute path to the maven and gradle executables that should be used for build into the Liberty Tools Plugin
     * Preferences page
     * 
     * @param bot       The SWTWorkbenchBot instance.
     * @param buildTool the build tool to be used (Maven or Gradle)
     */
    public static void setBuildCmdPathInPreferences(SWTWorkbenchBot bot, String buildTool) {
        // Use Eclipse preference store API directly instead of UI navigation
        // This avoids issues with menu accessibility in headless CI environments

        String finalMvnExecutableLoc = AbstractLibertyPluginSWTBotTest.getMvnCmdPath();
        String finalGradleExecutableLoc = AbstractLibertyPluginSWTBotTest.getGradleCmdPath();

        // Get the preference store for the Liberty Tools plugin
        org.eclipse.jface.preference.IPreferenceStore prefStore = new org.eclipse.ui.preferences.ScopedPreferenceStore(org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE, "io.openliberty.tools.eclipse.ui");

        if ("Maven".equals(buildTool)) {
            prefStore.setValue("MVNPATH", finalMvnExecutableLoc);
        } else if ("Gradle".equals(buildTool)) {
            prefStore.setValue("GRADLEPATH", finalGradleExecutableLoc);
        }

        // Save the preference store
        if (prefStore instanceof org.eclipse.ui.preferences.ScopedPreferenceStore) {
            try {
                ((org.eclipse.ui.preferences.ScopedPreferenceStore) prefStore).save();
            } catch (java.io.IOException e) {
                System.err.println("Failed to save preferences: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void unsetBuildCmdPathInPreferences(SWTWorkbenchBot bot, String buildTool) {
        // Use Eclipse preference store API directly instead of UI navigation
        // This avoids issues with menu accessibility in headless CI environments

        // Get the preference store for the Liberty Tools plugin
        org.eclipse.jface.preference.IPreferenceStore prefStore = new org.eclipse.ui.preferences.ScopedPreferenceStore(org.eclipse.core.runtime.preferences.InstanceScope.INSTANCE, "io.openliberty.tools.eclipse.ui");

        // Reset to default values (empty strings)
        if ("Maven".equals(buildTool)) {
            prefStore.setToDefault("MVNPATH");
        } else if ("Gradle".equals(buildTool)) {
            prefStore.setToDefault("GRADLEPATH");
        }

        // Save the preference store
        if (prefStore instanceof org.eclipse.ui.preferences.ScopedPreferenceStore) {
            try {
                ((org.eclipse.ui.preferences.ScopedPreferenceStore) prefStore).save();
            } catch (java.io.IOException e) {
                System.err.println("Failed to save preferences: " + e.getMessage());
                e.printStackTrace();
            }
        }
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
     * @param bot     The SWTWorkbenchBot instance..
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
     * @param bot     The SWTWorkbenchBot instance..
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
     * @param appName     The application name.
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
     * @param appName     The application name.
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

    /**
     * Selects the project clean option under liberty in run configurations
     * 
     * @param shell
     * @param runDebugConfigName
     */
    public static void checkRunCleanProjectCheckBox(Shell shell, String runDebugConfigName) {

        Object libertyConfigTree = getLibertyTreeItem(shell);

        Object appConfigEntry = find(runDebugConfigName, libertyConfigTree,
                                     Option.factory().useContains(true).widgetClass(TreeItem.class).build());
        go(appConfigEntry);
        Object button = find("Clean project", appConfigEntry, Option.factory().widgetClass(Button.class).build());

        go(button);
    }

    public static Object getAppInPackageExplorerTree(String appName) {
        openJavaPerspectiveViaMenu();
        // Open Package Explorer view using Eclipse API instead of menu navigation
        // This is more reliable in headless CI environments
        showPackageExplorerView();
        Object peView = MagicWidgetFinder.findGlobal("Package Explorer");

        Object project = MagicWidgetFinder.find(appName, peView, Option.factory().useContains(true).widgetClass(TreeItem.class).build());
        go(project);

        // Wait until the tree item is enabled before returning.
        SWTBotTreeItem botItem = new SWTBotTreeItem((TreeItem) project);
        SWTBotTestCondition.waitFor(botItem::isEnabled, SWTBotTestCondition.SHORT_WAIT_MS);
        botItem.select();
        botItem.setFocus();
        System.out.println("Explorer item selected: " + botItem.contextMenu().menuItems());

        return project;
    }

    /**
     * Launches the start action using the debug as configuration shortcut.
     * 
     * @param bot     The SWTWorkbenchBot instance.
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
     * @param bot     The SWTWorkbenchBot instance.
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
     * @param bot  The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchViewITReportWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                                                 WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_IT_REPORT + ".*"), false,
                                                 0);
        stopShortcut.setFocus();
        stopShortcut.click();

        SWTBotTestCondition.waitFor(
                                    () -> isEditorActiveFor(bot, item + " " + DevModeOperations.BROWSER_MVN_IT_REPORT_NAME_SUFFIX), SWTBotTestCondition.MIN_WAIT_MS);
    }

    /**
     * Launches the view (Maven) unit test report action using the run/debug as configuration shortcut.
     * 
     * @param bot  The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchViewUTReportWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                                                 WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_MVN_VIEW_UT_REPORT + ".*"), false,
                                                 0);
        stopShortcut.setFocus();
        stopShortcut.click();

        SWTBotTestCondition.waitFor(
                                    () -> isEditorActiveFor(bot, item + " " + DevModeOperations.BROWSER_MVN_UT_REPORT_NAME_SUFFIX), SWTBotTestCondition.MIN_WAIT_MS);
    }

    /**
     * Launches the view (Gradle) test report action using the run/debug as configuration shortcut.
     * 
     * @param bot  The SWTWorkbenchBot instance.
     * @param item The application name.
     */
    public static void launchViewTestReportWithRunDebugAsShortcut(SWTWorkbenchBot bot, String item) {
        SWTBotMenu runAsMenu = SWTBotPluginOperations.getAppRunAsMenu(bot, item);
        SWTBotMenu stopShortcut = runAsMenu.menu(
                                                 WidgetMatcherFactory.withRegex(".*" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_GRADLE_VIEW_TEST_REPORT + ".*"),
                                                 false, 0);
        stopShortcut.setFocus();
        stopShortcut.click();

        SWTBotTestCondition.waitFor(
                                    () -> isEditorActiveFor(bot, item + " " + DevModeOperations.BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX), SWTBotTestCondition.MIN_WAIT_MS);
    }

    /**
     * returns true if the editor containing the input title is active, false otherwise.
     * 
     * @param wbbot        The SWTWorkbenchBot instance.
     * @param titleContent The editor title.
     * 
     * @return True if the editor containing the input title is active, false otherwise.
     */
    private static boolean isEditorActiveFor(SWTWorkbenchBot wbbot, String titleContent) {
        SWTBotEditor editor = searchForEditor(wbbot, titleContent);
        return editor != null && editor.isActive();
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
     * @param bot   The SWTWorkbenchBot instance.
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
     * @param bot          The SWTWorkbenchBot instance.
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
     * @param bot          The SWTWorkbenchBot instance.
     * @param appViewTitle The title of the view (i.e. Project Explorer) where to look.
     * @param appName      The application where to find the file.
     * @param fileName     The name of the file from which to retrieve content.
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
     * @param bot          The SWTWorkbenchBot instance.
     * @param titleContent The title or part of the title of the text editor view to update.
     * @param content      The content with which the text editor view is updated.
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
     * @param bot          The SWTWorkbenchBot instance.
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

        SWTBotTree dashboardTree = getDashboardTree();
        SWTBotTreeItem treeItem = dashboardTree.getTreeItem(item);
        treeItem.select();
        return treeItem.contextMenu();
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
     * Opens the Package Explorer view using Eclipse API directly.
     * This is more reliable than menu navigation in headless CI environments.
     */
    private static void showPackageExplorerView() {
        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    IWorkbench wb = PlatformUI.getWorkbench();
                    IWorkbenchWindow window = wb.getActiveWorkbenchWindow();
                    if (window != null && window.getActivePage() != null) {
                        // Show the Package Explorer view using its ID
                        window.getActivePage().showView("org.eclipse.jdt.ui.PackageExplorer");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to open Package Explorer view: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        // Wait for the Package Explorer view to open.
        SWTBotTestCondition.waitFor(
                                    () -> isPackageExplorerViewPresent(), SWTBotTestCondition.VALIDATION_WAIT_MS);
    }

    /**
     * Returns true once the Package Explorer is visible on the active page.
     * 
     * @return True once the Package Explorer is visible on the active page.
     */
    private static boolean isPackageExplorerViewPresent() {
        final boolean[] visible = { false };
        Display.getDefault().syncExec(() -> {
            try {
                org.eclipse.ui.IWorkbenchWindow window = org.eclipse.ui.PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window != null && window.getActivePage() != null) {
                    org.eclipse.ui.IViewPart view = window.getActivePage().findView("org.eclipse.jdt.ui.PackageExplorer");
                    visible[0] = (view != null);
                }
            } catch (Exception ignored) {
                // View not ready yet; waitFor will retry.
            }
        });
        return visible[0];
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
     * @param shell The Debug Configurations shell already obtained by the caller.
     */
    public static void openSourceTab(Shell shell) {
        SWTBotShell botShell = new SWTBotShell(shell);
        botShell.setFocus();
        SWTBot shellBot = botShell.bot();
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
     * @param bot           The SWTWorkbenchBot instance.
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

        throw new RuntimeException("toolbar button of type ToolItem, with style push, and tooltip prefix of " + toolTipPrefix + " was not found.");
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
