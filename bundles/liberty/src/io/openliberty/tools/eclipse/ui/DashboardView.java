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
package io.openliberty.tools.eclipse.ui;

import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.Dialog;
import io.openliberty.tools.eclipse.utils.Project;
import io.openliberty.tools.eclipse.utils.Workspace;

/**
 * View of Liberty application projects and dev mode actions to be processed on the selected projects.
 */
public class DashboardView extends ViewPart {

    /**
     * Liberty logo path.
     */
    public static final String LIBERTY_LOGO_PATH = "icons/openLibertyLogo.png";

    /**
     * Menu Constants.
     */
    public static final String APP_MENU_ACTION_START = "Start";
    public static final String APP_MENU_ACTION_START_PARMS = "Start...";
    public static final String APP_MENU_ACTION_START_IN_CONTAINER = "Start in container";
    public static final String APP_MENU_ACTION_STOP = "Stop";
    public static final String APP_MENU_ACTION_RUN_TESTS = "Run tests";
    public static final String APP_MENU_ACTION_VIEW_MVN_IT_REPORT = "View integration test report";
    public static final String APP_MENU_ACTION_VIEW_MVN_UT_REPORT = "View unit test report";
    public static final String APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT = "View test report";
    public static final String DASHBORD_TOOLBAR_ACTION_REFRESH = "refresh";

    /**
     * view actions.
     */
    private Action startAction;
    private Action startWithParmAction;
    private Action startInContainerAction;
    private Action stopAction;
    private Action runTestAction;
    private Action viewMavenITestReportsAction;
    private Action viewMavenUTestReportsAction;
    private Action viewGradleTestReportsAction;
    private Action refreshAction;

    /**
     * Class instances.
     */
    private ListViewer viewer;
    public static DevModeOperations devMode = new DevModeOperations();

    /**
     * {@inheritDoc}
     */
    @Override
    public void createPartControl(Composite parent) {
        viewer = new ListViewer(parent);
        viewer.setContentProvider(new ArrayContentProvider());
        viewer.setLabelProvider(new LabelProvider());

        try {
            viewer.setInput(Workspace.getLibertyProjects(false));
        } catch (Exception e) {
            String msg = "An error was detected while retrieving Liberty projects.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
            return;
        }

        createActions();
        createContextMenu();
        addToolbarActions();
        getSite().setSelectionProvider(viewer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    /**
     * Creates a right-click menu.
     */
    private void createContextMenu() {
        MenuManager menuMgr = new MenuManager();
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager mgr) {
                addActionsToContextMenu(mgr);

            }
        });

        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);

        getSite().registerContextMenu("io.openliberty.tools.eclipse.views.liberty.devmode.dashboard", menuMgr, viewer);
    }

    /**
     * Populates the toolbar.
     */
    private void addToolbarActions() {
        IToolBarManager tbMgr = getViewSite().getActionBars().getToolBarManager();
        tbMgr.add(refreshAction);
    }

    /**
     * Populates the context menu.
     *
     * @param mgr The menu manager.
     */
    private void addActionsToContextMenu(IMenuManager mgr) {
        IProject project = devMode.getSelectedDashboardProject();

        if (project != null) {
            mgr.add(startAction);
            mgr.add(startWithParmAction);
            mgr.add(startInContainerAction);
            mgr.add(stopAction);
            mgr.add(runTestAction);

            if (Project.isMaven(project)) {
                mgr.add(viewMavenITestReportsAction);
                mgr.add(viewMavenUTestReportsAction);
            } else if (Project.isGradle(project)) {
                mgr.add(viewGradleTestReportsAction);
            } else {
                Dialog.displayErrorMessage("Project" + project.getName() + "is not a Gradle or Maven project.");
                return;
            }
        }
    }

    /**
     * Instantiates menu and toolbar actions.
     */
    private void createActions() {
        ImageDescriptor ActionImg = null;
        ImageDescriptor refreshImg = null;

        // Get the image descriptors for the menu actions and toolbar.
        // If there is a failure, display the error and proceed without the icons.
        try {
            ActionImg = ImageDescriptor
                    .createFromURL(new URL("platform:/plugin/org.eclipse.jdt.debug.ui/icons/full/elcl16/thread_view.gif"));
            refreshImg = ImageDescriptor.createFromURL(new URL("platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_refresh.png"));
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while retrieving Imade descriptions.", e);
        }

        // Activate the Liberty tools context.
        IContextService contextService = getSite().getService(IContextService.class);
        contextService.activateContext("io.openliberty.tools.eclipse.context");

        // Menu: Start.
        startAction = new Action(APP_MENU_ACTION_START) {
            @Override
            public void run() {
                devMode.start();
            }
        };
        startAction.setImageDescriptor(ActionImg);
        startAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.start.command");
        IHandlerService handlerService = getSite().getService(IHandlerService.class);
        ActionHandler startHandler = new ActionHandler(startAction);
        handlerService.activateHandler(startAction.getActionDefinitionId(), startHandler);

        // Menu: Start with parameters.
        startWithParmAction = new Action(APP_MENU_ACTION_START_PARMS) {
            @Override
            public void run() {
                devMode.startWithParms();
            }
        };
        startWithParmAction.setImageDescriptor(ActionImg);
        startWithParmAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.startWithParms.command");
        ActionHandler startWithParmsHandler = new ActionHandler(startWithParmAction);
        handlerService.activateHandler(startWithParmAction.getActionDefinitionId(), startWithParmsHandler);

        // Menu: Start in container.
        startInContainerAction = new Action(APP_MENU_ACTION_START_IN_CONTAINER) {
            @Override
            public void run() {
                devMode.startInContainer();
            }
        };
        startInContainerAction.setImageDescriptor(ActionImg);
        startInContainerAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.startInContainer.command");
        ActionHandler startWithContainerHandler = new ActionHandler(startInContainerAction);
        handlerService.activateHandler(startInContainerAction.getActionDefinitionId(), startWithContainerHandler);

        // Menu: Stop.
        stopAction = new Action(APP_MENU_ACTION_STOP) {
            @Override
            public void run() {
                devMode.stop();
            }
        };
        stopAction.setImageDescriptor(ActionImg);
        stopAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.stop.command");
        ActionHandler stopHandler = new ActionHandler(stopAction);
        handlerService.activateHandler(stopAction.getActionDefinitionId(), stopHandler);

        // Menu: Run tests.
        runTestAction = new Action(APP_MENU_ACTION_RUN_TESTS) {
            @Override
            public void run() {
                devMode.runTests();
            }
        };
        runTestAction.setImageDescriptor(ActionImg);
        runTestAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.runTests.command");
        ActionHandler runTestsHandler = new ActionHandler(runTestAction);
        handlerService.activateHandler(runTestAction.getActionDefinitionId(), runTestsHandler);

        // Menu: View integration test report. Maven project specific.
        viewMavenITestReportsAction = new Action(APP_MENU_ACTION_VIEW_MVN_IT_REPORT) {
            @Override
            public void run() {
                devMode.openMavenIntegrationTestReport();
            }
        };
        viewMavenITestReportsAction.setImageDescriptor(ActionImg);
        viewMavenITestReportsAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.viewMvnIntegrationTestReport.command");
        ActionHandler mvnITTestReportHandler = new ActionHandler(viewMavenITestReportsAction);
        handlerService.activateHandler(viewMavenITestReportsAction.getActionDefinitionId(), mvnITTestReportHandler);

        // Menu: View unit test report. Maven project specific.
        viewMavenUTestReportsAction = new Action(APP_MENU_ACTION_VIEW_MVN_UT_REPORT) {
            @Override
            public void run() {
                devMode.openMavenUnitTestReport();
            }
        };
        viewMavenUTestReportsAction.setImageDescriptor(ActionImg);
        viewMavenUTestReportsAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.viewMvnUnitTestReport.command");
        ActionHandler mvnUTTestReportsHandler = new ActionHandler(viewMavenUTestReportsAction);
        handlerService.activateHandler(viewMavenUTestReportsAction.getActionDefinitionId(), mvnUTTestReportsHandler);

        // Menu: View test report. Gradle project specific.
        viewGradleTestReportsAction = new Action(APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT) {
            @Override
            public void run() {
                devMode.openGradleTestReport();
            }
        };
        viewGradleTestReportsAction.setImageDescriptor(ActionImg);
        viewGradleTestReportsAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.viewGradleTestReport.command");
        ActionHandler gradleTestReportsHandler = new ActionHandler(viewGradleTestReportsAction);
        handlerService.activateHandler(viewGradleTestReportsAction.getActionDefinitionId(), gradleTestReportsHandler);

        // Toolbar: Refresh the project list.
        refreshAction = new Action(DASHBORD_TOOLBAR_ACTION_REFRESH) {
            @Override
            public void run() {
                try {
                    viewer.setInput(Workspace.getLibertyProjects(true));
                } catch (Exception e) {
                    Dialog.displayErrorMessageWithDetails("An error was detected while retrieving Liberty projects.", e);
                    return;
                }
            }
        };
        refreshAction.setImageDescriptor(refreshImg);
    }
}
