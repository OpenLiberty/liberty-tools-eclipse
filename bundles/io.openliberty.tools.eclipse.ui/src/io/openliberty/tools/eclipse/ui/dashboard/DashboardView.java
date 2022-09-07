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
package io.openliberty.tools.eclipse.ui.dashboard;

import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.Dialog;

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
     * Table viewer that holds the entries in the dashboard.
     */
    private TableViewer viewer;

    /**
     * Listener object that updates the dashboard content as actions take place on the projects it contains.
     */
    private IResourceChangeListener projectStateListener;

    /**
     * DevModeOperations reference.
     */
    private DevModeOperations devModeOps;

    /**
     * Constructor.
     */
    public DashboardView() {
        devModeOps = DevModeOperations.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createPartControl(Composite parent) {
        viewer = new TableViewer(parent, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new DashboardEntryLabelProvider(devModeOps));

        try {
            viewer.setInput(devModeOps.getSupportedProjects());
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
        registerResourceListener();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        super.dispose();
        if (viewer == null) {
            return;
        }
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(projectStateListener);
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
        IProject iProject = devModeOps.getSelectedDashboardProject();
        String projectName = iProject.getName();
        Project project = devModeOps.getSupportedProject(projectName);

        if (project != null) {
            mgr.add(startAction);
            mgr.add(startWithParmAction);
            mgr.add(startInContainerAction);
            mgr.add(stopAction);
            mgr.add(runTestAction);

            if (project.getBuildType() == Project.BuildType.MAVEN) {
                mgr.add(viewMavenITestReportsAction);
                mgr.add(viewMavenUTestReportsAction);
            } else if (project.getBuildType() == Project.BuildType.GRADLE) {
                mgr.add(viewGradleTestReportsAction);
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");
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
                devModeOps.start();
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
                devModeOps.startWithParms();
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
                devModeOps.startInContainer();
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
                devModeOps.stop();
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
                devModeOps.runTests();
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
                devModeOps.openMavenIntegrationTestReport();
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
                devModeOps.openMavenUnitTestReport();
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
                devModeOps.openGradleTestReport();
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
                    viewer.setInput(devModeOps.getSupportedProjects());
                } catch (Exception e) {
                    Dialog.displayErrorMessageWithDetails("An error was detected while retrieving Liberty projects.", e);
                    return;
                }
            }
        };
        refreshAction.setImageDescriptor(refreshImg);
    }

    /**
     * Registers a resource change listener to process project open/close and project create/import/delete.
     */
    private void registerResourceListener() {
        projectStateListener = new IResourceChangeListener() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void resourceChanged(IResourceChangeEvent event) {
                Display.getDefault().syncExec(new Runnable() {

                    @Override
                    public void run() {
                        IResourceDelta delta = event.getDelta();
                        if (delta == null) {
                            return;
                        }

                        // On entry the resource type is the root workspace. Find the child resources affected.
                        IResourceDelta[] resourcesChanged = delta.getAffectedChildren();

                        // Iterate over the affected resources.
                        for (IResourceDelta resourceChanged : resourcesChanged) {
                            IResource iResource = resourceChanged.getResource();
                            if (iResource.getType() != IResource.PROJECT) {
                                continue;
                            }

                            boolean updateCompleted = false;
                            IProject iProject = (IProject) iResource;
                            Project project = devModeOps.getSupportedProject(iProject.getName());
                            int updateFlag = resourceChanged.getFlags();

                            try {
                                switch (resourceChanged.getKind()) {
                                // Project opened/closed.
                                // Flag OPEN (16384): "Change constant (bit mask) indicating that the resource was opened or closed"
                                // Flag 147456: Although IResourceDelta does not have a predefined constant, this flag value is used to
                                // denote open/close actions.
                                case IResourceDelta.CHANGED:
                                    if (updateFlag == IResourceDelta.OPEN || updateFlag == 147456) {
                                        viewer.setInput(devModeOps.getSupportedProjects());
                                        updateCompleted = true;
                                    }
                                    break;
                                // Project created/imported.
                                // Flag OPEN (16384): "This flag is ... set when the project did not exist in the "before" state."
                                // Flag 147456: Although IResourceDelta does not have a predefined constant, this flag
                                // value is set when a project, that previously did not exist, is created.
                                case IResourceDelta.ADDED:
                                    if (project == null && (updateFlag == IResourceDelta.OPEN || updateFlag == 147456)) {
                                        viewer.setInput(devModeOps.getSupportedProjects());
                                        updateCompleted = true;
                                    }
                                    break;
                                // Project deleted.
                                // Flag NO_CHANGE (0).
                                // Flag MARKERS (130172).
                                case IResourceDelta.REMOVED:
                                    if (project != null
                                            && (updateFlag == IResourceDelta.NO_CHANGE || updateFlag == IResourceDelta.MARKERS)) {
                                        viewer.setInput(devModeOps.getSupportedProjects());
                                        updateCompleted = true;
                                    }
                                    break;
                                default:
                                    break;
                                }
                            } catch (Exception e) {
                                Dialog.displayErrorMessageWithDetails(
                                        "An error was detected while auto-refreshing the Liberty dashboard content.", e);
                                return;
                            }

                            // If the update completed for the project we are done.
                            if (updateCompleted) {
                                break;
                            }
                        }
                    }
                });
            }
        };

        // Register the resource listener to be invoked on PRE-BUILD events only. The event is triggered during resource create, modify,
        // and delete actions. This event type allows for workspace/resource modification while the registered listener is running.
        // The listener is registered when the the dashboard is opened and unregistered when the dashboard is closed/disposed.
        ResourcesPlugin.getWorkspace().addResourceChangeListener(projectStateListener, IResourceChangeEvent.PRE_BUILD);
    }
}