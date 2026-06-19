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
package io.openliberty.tools.eclipse.ui.dashboard;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.WorkspaceModel;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.OpenGradleTestReportAction;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.OpenMavenITestReportAction;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.OpenMavenUTestReportAction;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.RunTestsAction;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.StartAction;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.StartConfigurationDialogAction;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.StartInContainerAction;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.StopAction;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * View of Liberty application projects and dev mode actions to be processed on the selected projects.
 */
public class DashboardView extends ViewPart {

    /** Dashboard view ID. */
    public static final String ID = "io.openliberty.tools.eclipse.views.liberty.devmode.dashboard";

    /** Liberty logo path. */
    public static final String LIBERTY_LOGO_PATH = "icons/openLibertyLogo.png";

    /** Maven image tag path. */
    public static final String MAVEN_IMG_TAG_PATH = "icons/mavenTag.png";

    /** Gradle image tag path. */
    public static final String GRADLE_IMG_TAG_PATH = "icons/gradleTag.png";

    /** Menu Constants. */
    public static final String APP_MENU_ACTION_START = Messages.getMessage("dashboard_action_start");
    public static final String APP_MENU_ACTION_START_CONFIG = Messages.getMessage("dashboard_action_start_config");
    public static final String APP_MENU_ACTION_START_IN_CONTAINER = Messages.getMessage("dashboard_action_start_in_container");
    public static final String APP_MENU_ACTION_DEBUG = Messages.getMessage("dashboard_action_debug");
    public static final String APP_MENU_ACTION_DEBUG_CONFIG = Messages.getMessage("dashboard_action_debug_config");
    public static final String APP_MENU_ACTION_DEBUG_IN_CONTAINER = Messages.getMessage("dashboard_action_debug_in_container");
    public static final String APP_MENU_ACTION_STOP = Messages.getMessage("dashboard_action_stop");
    public static final String APP_MENU_ACTION_RUN_TESTS = Messages.getMessage("dashboard_action_run_tests");
    public static final String APP_MENU_ACTION_VIEW_MVN_IT_REPORT = Messages.getMessage("dashboard_action_view_mvn_it_report");
    public static final String APP_MENU_ACTION_VIEW_MVN_UT_REPORT = Messages.getMessage("dashboard_action_view_mvn_ut_report");
    public static final String APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT = Messages.getMessage("dashboard_action_view_gradle_test_report");
    public static final String DASHBORD_TOOLBAR_REFRESH = Messages.getMessage("dashboard_toolbar_refresh");
    public static final String DASHBORD_TOOLBAR_EXPAND_ALL = Messages.getMessage("dashboard_toolbar_expand_all");
    public static final String DASHBORD_TOOLBAR_COLLAPSE_ALL = Messages.getMessage("dashboard_toolbar_collapse_all");
    public static final String DASHBORD_TOOLBAR_FILTER = Messages.getMessage("dashboard_toolbar_filter");

    

    /** view actions. */
    private Action startAction;
    private Action startConfigDialogAction;
    private Action startInContainerAction;
    private Action debugAction;
    private Action debugConfigDialogAction;
    private Action debugInContainerAction;
    private Action stopAction;
    private Action runTestAction;
    private Action viewMavenITestReportsAction;
    private Action viewMavenUTestReportsAction;
    private Action viewGradleTestReportsAction;
    private Action refreshAction;
    private Action filterAction;
    private Action expandAllAction;
    private Action collapseAllAction;

    /** Tree viewer that holds the entries in the dashboard. */
    TreeViewer viewer;

    /** Search text widget */
    private Text searchText;

    /** Search filter */
    private ViewerFilter searchFilter;

    /** Search bar visibility */
    private boolean searchBarVisible = false;

    /** DevModeOperations reference. */
    DevModeOperations devModeOps;

    DashboardContentProvider contentProvider;

    /** Parent composite for stack layout */
    private Composite stackComposite;

    /** Stack layout to switch between tree and empty message */
    private StackLayout stackLayout;

    /** Composite containing the tree viewer */
    private Composite treeComposite;

    /** Composite containing the empty state message */
    private Composite emptyComposite;

    /**
     * Constructor.
     */
    public DashboardView() {
        devModeOps = DevModeOperations.getInstance();
        devModeOps.setDashboardView(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createPartControl(Composite parent) {
        // Create stack composite with stack layout
        stackComposite = new Composite(parent, SWT.NONE);
        stackLayout = new StackLayout();
        stackComposite.setLayout(stackLayout);

        // Create tree composite
        treeComposite = new Composite(stackComposite, SWT.NONE);
        treeComposite.setLayout(new GridLayout(1, false));

        // Create search text box (initially hidden) - with cancel icon only
        searchText = new Text(treeComposite, SWT.SEARCH | SWT.ICON_CANCEL);
        searchText.setMessage("Filter projects...");
        GridData searchData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        searchData.exclude = true; // Initially hidden
        searchText.setLayoutData(searchData);
        searchText.setVisible(false);

        // Add search text listener
        searchText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                showSearchFilter(searchText.getText());
            }
        });

        viewer = new TreeViewer(treeComposite, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        viewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        // Create search filter
        searchFilter = new ViewerFilter() {
            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element) {
                if (element instanceof ProjectModel) {
                    ProjectModel project = (ProjectModel) element;
                    String searchPattern = searchText.getText().toLowerCase();
                    if (searchPattern.isEmpty()) {
                        return true;
                    }
                    // Check if project name matches
                    if (project.getName().toLowerCase().contains(searchPattern)) {
                        return true;
                    }
                    // Check if any child matches (show parent if child matches)
                    return hasMatchingChild(project, searchPattern);
                }
                return true;
            }
        };
        contentProvider = new DashboardContentProvider(devModeOps.getWorkspaceModel(), this);
        viewer.setContentProvider(contentProvider);
        viewer.setLabelProvider(new DashboardEntryLabelProvider(devModeOps, this));

        // Create empty state composite
        createEmptyStateComposite();

        // Show tree by default
        stackLayout.topControl = treeComposite;

        createActions();
        createContextMenu();
        addToolbarActions();

        devModeOps.refreshDashboardView(false);
        getSite().setSelectionProvider(viewer);
    }

    /**
     * Creates the composite shown when there are no Liberty projects.
     */
    private void createEmptyStateComposite() {
        emptyComposite = new Composite(stackComposite, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 20;
        layout.marginHeight = 20;
        emptyComposite.setLayout(layout);

        // Combined message and link
        Link messageLink = new Link(emptyComposite, SWT.WRAP);
        messageLink.setText("No Liberty projects found in the current workspace. <a>Import</a> a Liberty project to get started.");
        messageLink.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        messageLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                try {
                    // Open the Eclipse import wizard using the workbench window's handler service
                    IHandlerService handlerService = getSite().getService(IHandlerService.class);
                    if (handlerService != null) {
                        handlerService.executeCommand("org.eclipse.ui.file.import", null);
                    } else {
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_UI, "Handler service is null");
                        }
                    }
                } catch (Exception ex) {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, "Error opening import wizard", ex);
                    }
                    ErrorHandler.processErrorMessage("Failed to open import wizard: " + ex.getMessage(), ex, true);
                }
            }
        });
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
        // null out viewer so we don't try to update upon a resource change listener notification
        viewer = null;
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
        tbMgr.add(expandAllAction);
        tbMgr.add(collapseAllAction);
        tbMgr.add(filterAction);
        tbMgr.add(refreshAction);
    }

    /**
     * Populates the context menu.
     *
     * @param mgr The menu manager.
     */
    private void addActionsToContextMenu(IMenuManager mgr) {
        IProject iProject = Utils.getActiveProject();

        // If no project is selected (e.g., selection was cleared for non-Liberty project), don't show menu
        if (iProject == null) {
            return;
        }

        String projectLocation = iProject.getLocation().toOSString();
        ProjectModel projectModel = devModeOps.getWorkspaceModel().getProjectByLocation(projectLocation);
        String projectName = projectModel.getName();

        // Only show the context menu if the project has been configured to run on Liberty.
        if (projectModel != null && projectModel.hasLibertyNature()) {
            mgr.add(startAction);
            mgr.add(startInContainerAction);
            mgr.add(startConfigDialogAction);
            mgr.add(debugAction);
            mgr.add(debugInContainerAction);
            mgr.add(debugConfigDialogAction);
            mgr.add(stopAction);
            mgr.add(runTestAction);

            if (projectModel.getBuildType() == ProjectModel.BuildType.MAVEN) {
                mgr.add(viewMavenITestReportsAction);
                mgr.add(viewMavenUTestReportsAction);
            } else if (projectModel.getBuildType() == ProjectModel.BuildType.GRADLE) {
                mgr.add(viewGradleTestReportsAction);
            } else {
                String msg = "Project" + projectName + "is not a Gradle or Maven project.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI, msg);
                }
                ErrorHandler.processErrorMessage(Messages.getMessage("project_not_gradle_or_maven", projectName), true);
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
            ActionImg = ImageDescriptor.createFromURL(URI.create("platform:/plugin/org.eclipse.jdt.debug.ui/icons/full/elcl16/thread_view.gif").toURL());
        } catch (Exception e) {
            String msg = "An error was detected while retrieving image descriptions.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processWarningMessage(Messages.getMessage("image_descriptions_error"), e, true);
        }

        // Activate the Liberty tools context.
        IContextService contextService = getSite().getService(IContextService.class);
        contextService.activateContext("io.openliberty.tools.eclipse.context");

        // Menu: Start.
        startAction = new Action(APP_MENU_ACTION_START) {
            @Override
            public void run() {
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    StartAction.run(iProject, ILaunchManager.RUN_MODE);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_START + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_START), e, true);
                }
            }
        };

        startAction.setImageDescriptor(ActionImg);
        startAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.start.command");
        IHandlerService handlerService = getSite().getService(IHandlerService.class);
        ActionHandler startHandler = new ActionHandler(startAction);
        handlerService.activateHandler(startAction.getActionDefinitionId(), startHandler);

        // Menu: Start with parameters.
        startConfigDialogAction = new Action(APP_MENU_ACTION_START_CONFIG) {
            @Override
            public void run() {
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    StartConfigurationDialogAction.run(iProject, ILaunchManager.RUN_MODE);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_START_CONFIG + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_START_CONFIG), e,
                                                     true);
                }
            }
        };
        startConfigDialogAction.setImageDescriptor(ActionImg);
        startConfigDialogAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.startConfigDialog.command");
        ActionHandler startConfigDialogHandler = new ActionHandler(startConfigDialogAction);
        handlerService.activateHandler(startConfigDialogAction.getActionDefinitionId(), startConfigDialogHandler);

        // Menu: Start in container.
        startInContainerAction = new Action(APP_MENU_ACTION_START_IN_CONTAINER) {
            @Override
            public void run() {
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    StartInContainerAction.run(iProject, ILaunchManager.RUN_MODE);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_START_IN_CONTAINER
                                 + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_START_IN_CONTAINER), e, true);
                }
            }
        };
        startInContainerAction.setImageDescriptor(ActionImg);
        startInContainerAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.startInContainer.command");
        ActionHandler startWithContainerHandler = new ActionHandler(startInContainerAction);
        handlerService.activateHandler(startInContainerAction.getActionDefinitionId(), startWithContainerHandler);

        // Menu: Debug in container.
        debugInContainerAction = new Action(APP_MENU_ACTION_DEBUG_IN_CONTAINER) {
            @Override
            public void run() {
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    StartInContainerAction.run(iProject, ILaunchManager.DEBUG_MODE);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_DEBUG_IN_CONTAINER
                                 + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_DEBUG_IN_CONTAINER), e, true);
                }
            }
        };
        debugInContainerAction.setImageDescriptor(ActionImg);
        debugInContainerAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.debugInContainer.command");
        ActionHandler debugWithContainerHandler = new ActionHandler(debugInContainerAction);
        handlerService.activateHandler(debugInContainerAction.getActionDefinitionId(), debugWithContainerHandler);

        // Menu: Debug.
        debugAction = new Action(APP_MENU_ACTION_DEBUG) {
            @Override
            public void run() {
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    StartAction.run(iProject, ILaunchManager.DEBUG_MODE);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_DEBUG + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_DEBUG), e, true);
                }
            }
        };

        debugAction.setImageDescriptor(ActionImg);
        debugAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.debug.command");
        ActionHandler debugHandler = new ActionHandler(debugAction);
        handlerService.activateHandler(debugAction.getActionDefinitionId(), debugHandler);

        // Menu: Debug with parameters.
        debugConfigDialogAction = new Action(APP_MENU_ACTION_DEBUG_CONFIG) {
            @Override
            public void run() {
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    StartConfigurationDialogAction.run(iProject, ILaunchManager.DEBUG_MODE);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_DEBUG_CONFIG + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_DEBUG_CONFIG), e, true);
                }
            }
        };
        debugConfigDialogAction.setImageDescriptor(ActionImg);
        debugConfigDialogAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.debugConfigDialog.command");
        ActionHandler debugConfigDialogHandler = new ActionHandler(debugConfigDialogAction);
        handlerService.activateHandler(debugConfigDialogAction.getActionDefinitionId(), debugConfigDialogHandler);

        // Menu: Stop.
        stopAction = new Action(APP_MENU_ACTION_STOP) {
            @Override
            public void run() {
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    StopAction.run(iProject);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_STOP + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_STOP), e, true);
                }
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
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    RunTestsAction.run(iProject);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_RUN_TESTS + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_RUN_TESTS), e, true);
                }
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
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    OpenMavenITestReportAction.run(iProject);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_VIEW_MVN_IT_REPORT
                                 + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_VIEW_MVN_IT_REPORT), e, true);
                }
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
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    OpenMavenUTestReportAction.run(iProject);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_VIEW_MVN_UT_REPORT
                                 + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_VIEW_MVN_UT_REPORT), e, true);
                }
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
                IProject iProject = devModeOps.getSelectedDashboardProject();
                try {
                    OpenGradleTestReportAction.run(iProject);
                } catch (Exception e) {
                    String msg = "An error was detected during the " + APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT
                                 + " action.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                    }
                    ErrorHandler.processErrorMessage(Messages.getMessage("action_general_error", APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT), e,
                                                     true);
                }
            }
        };
        viewGradleTestReportsAction.setImageDescriptor(ActionImg);
        viewGradleTestReportsAction.setActionDefinitionId("io.openliberty.tools.eclipse.project.viewGradleTestReport.command");
        ActionHandler gradleTestReportsHandler = new ActionHandler(viewGradleTestReportsAction);
        handlerService.activateHandler(viewGradleTestReportsAction.getActionDefinitionId(), gradleTestReportsHandler);

        // Toolbar: Refresh the project list.
        refreshAction = new Action(DASHBORD_TOOLBAR_REFRESH) {
            @Override
            public void run() {
                devModeOps.refreshDashboardView(true);
            }
        };
        refreshAction.setToolTipText(DASHBORD_TOOLBAR_REFRESH);

        try {
            refreshImg = ImageDescriptor.createFromURL(URI.create("platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_refresh.png").toURL());
            refreshAction.setImageDescriptor(refreshImg);
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Refresh icon not found, using text label", e);
            }
        }
        
        // Toolbar: Search/Filter projects
        filterAction = new Action(DASHBORD_TOOLBAR_FILTER, Action.AS_CHECK_BOX) {
            @Override
            public void run() {
                toggleSearchBar();
            }
        };
        filterAction.setToolTipText(DASHBORD_TOOLBAR_FILTER);
        try {
            ImageDescriptor searchImg = ImageDescriptor.createFromURL(
                                                                      URI.create("platform:/plugin/org.eclipse.ui.ide/icons/full/elcl16/filter_ps.png").toURL());
            filterAction.setImageDescriptor(searchImg);
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Filter icon not found, using text label", e);
            }
        }

        // Toolbar: Expand all tree nodes
        expandAllAction = new Action(DASHBORD_TOOLBAR_EXPAND_ALL) {
            @Override
            public void run() {
                if (viewer != null) {
                    viewer.expandAll();
                }
            }
        };
        expandAllAction.setToolTipText(DASHBORD_TOOLBAR_EXPAND_ALL);
        try {
            ImageDescriptor expandImg = ImageDescriptor.createFromURL(
                                                                      URI.create("platform:/plugin/org.eclipse.ui/icons/full/elcl16/expandall.png").toURL());
            expandAllAction.setImageDescriptor(expandImg);
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Expand all icon not found, using text label", e);
            }
        }

        // Toolbar: Collapse all tree nodes
        collapseAllAction = new Action(DASHBORD_TOOLBAR_COLLAPSE_ALL) {
            @Override
            public void run() {
                if (viewer != null) {
                    viewer.collapseAll();
                }
            }
        };
        collapseAllAction.setToolTipText(DASHBORD_TOOLBAR_COLLAPSE_ALL);
        try {
            ImageDescriptor collapseImg = ImageDescriptor.createFromURL(
                                                                        URI.create("platform:/plugin/org.eclipse.ui/icons/full/elcl16/collapseall.png").toURL());
            collapseAllAction.setImageDescriptor(collapseImg);
        } catch (Exception e) {
            // If icon not found, the action will just show as text
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Collapse all icon not found, using text label", e);
            }
        }
    }

    /**
     * Updates toolbar action enablement based on whether the dashboard has content.
     *
     * @param hasContent true if the dashboard contains Liberty projects; otherwise false
     */
    private void updateToolbarActionEnablement(boolean hasContent) {
        if (filterAction != null) {
            filterAction.setEnabled(hasContent);
            if (!hasContent && filterAction.isChecked()) {
                toggleSearchBar();
            }
        }
        if (expandAllAction != null) {
            expandAllAction.setEnabled(hasContent);
        }
        if (collapseAllAction != null) {
            collapseAllAction.setEnabled(hasContent);
        }
    }

    /**
     * Sets the input data for the dashboard tree viewer.
     *
     * @param rootProjects The list of root projects to display.
     */
    public void setInput(List<ProjectModel> rootProjects) {
        // Early return if viewer not initialized
        if (viewer == null) {
            return;
        }

        // Save the current expansion state by project names before any changes
        Object[] expandedElements = viewer.getExpandedElements();
        Set<String> expandedProjectNames = new HashSet<>();
        if (expandedElements != null && expandedElements.length > 0) {
            for (Object element : expandedElements) {
                if (element instanceof ProjectModel) {
                    expandedProjectNames.add(((ProjectModel) element).getName());
                }
            }
        }

        // Update the viewer's input
        viewer.setInput(rootProjects);

        // Restore the expansion state by matching project names
        // Only proceed if we have both: saved expansion state AND new projects to display
        if (!expandedProjectNames.isEmpty() && !rootProjects.isEmpty()) {
            List<ProjectModel> toExpand = new ArrayList<>();

            // Find matching projects in the new tree
            for (ProjectModel project : rootProjects) {
                if (expandedProjectNames.contains(project.getName())) {
                    toExpand.add(project);
                }
                // Recursively check children
                addExpandedChildren(project, expandedProjectNames, toExpand);
            }

            // Restore expansion if we found matching projects
            if (!toExpand.isEmpty()) {
                viewer.setExpandedElements(toExpand.toArray());
            }
        }

        // Switch between tree and empty state based on whether there are Liberty projects
        boolean isEmpty = getTree().getItemCount() == 0;
        if (!isEmpty) {
            stackLayout.topControl = treeComposite;
        } else {
            stackLayout.topControl = emptyComposite;
        }
        updateToolbarActionEnablement(!isEmpty);
        stackComposite.layout();
    }

    /**
     * Recursively find and add children that should be expanded.
     */
    private void addExpandedChildren(ProjectModel parentProjectModel, Set<String> expandedNames, List<ProjectModel> toExpand) {
        for (ProjectModel child : parentProjectModel.getChildProjects()) {
            if (expandedNames.contains(child.getName())) {
                toExpand.add(child);
            }
            addExpandedChildren(child, expandedNames, toExpand);
        }
    }

    /**
     * Toggles the visibility of the search bar.
     */
    private void toggleSearchBar() {
        searchBarVisible = !searchBarVisible;
        GridData searchData = (GridData) searchText.getLayoutData();
        searchData.exclude = !searchBarVisible;
        searchText.setVisible(searchBarVisible);
        filterAction.setChecked(searchBarVisible);

        if (searchBarVisible) {
            searchText.setFocus();
        } else {
            // Clear search when hiding
            searchText.setText("");
            viewer.removeFilter(searchFilter);
        }

        treeComposite.layout(true, true);
    }

    /**
     * Applies the search filter based on the search text.
     */
    private void showSearchFilter(String searchPattern) {
        if (searchPattern == null || searchPattern.trim().isEmpty()) {
            viewer.removeFilter(searchFilter);
        } else {
            viewer.removeFilter(searchFilter);
            viewer.addFilter(searchFilter);
        }
        viewer.expandAll();
    }

    /**
     * Checks if a project or any of its children match the search pattern.
     */
    private boolean hasMatchingChild(ProjectModel projectModel, String searchPattern) {
        for (ProjectModel child : projectModel.getChildProjects()) {
            if (child.getName().toLowerCase().contains(searchPattern)) {
                return true;
            }
            if (hasMatchingChild(child, searchPattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Refreshes the dashboard view.
     */
    public void refreshDashboardView(WorkspaceModel workspaceModel, boolean reportError) {
        try {
            workspaceModel.createNewCompleteWorkspaceModelWithClassify();
            setInput(contentProvider.getRootDashboardProjects());
        } catch (Exception e) {
            String msg = "An error was detected when the Liberty dashboard content was refreshed.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("dashboard_refresh_error"), e, reportError);
            return;
        }
    }

    /**
     * Returns the tree widget from the viewer.
     *
     * @return The tree widget.
     */
    public Tree getTree() {
        return viewer.getTree();
    }
}