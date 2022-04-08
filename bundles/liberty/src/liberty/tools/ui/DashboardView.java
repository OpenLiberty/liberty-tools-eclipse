package liberty.tools.ui;

import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.part.ViewPart;

import liberty.tools.DevModeOperations;
import liberty.tools.utils.Dialog;
import liberty.tools.utils.Project;

/**
 * View of Liberty application projects and development mode actions to be processed on the selected projects.
 */
public class DashboardView extends ViewPart {

    public static final DevModeOperations devMode = new DevModeOperations();

    public static final String APP_MENU_ACTION_START = "Start";
    public static final String APP_MENU_ACTION_START_PARMS = "Start...";
    public static final String APP_MENU_ACTION_START_IN_CONTAINER = "Start in container";
    public static final String APP_MENU_ACTION_STOP = "Stop";
    public static final String APP_MENU_ACTION_RUN_TESTS = "Run tests";
    public static final String APP_MENU_ACTION_VIEW_MVN_IT_REPORT = "View integration test report";
    public static final String APP_MENU_ACTION_VIEW_MVN_UT_REPORT = "View unit test report";
    public static final String APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT = "View test report";
    public static final String DASHBORD_TOOLBAR_ACTION_REFRESH = "refresh";

    Action startAction;
    Action startWithParmAction;
    Action startInContanerAction;
    Action stopAction;
    Action runTestAction;
    Action viewMavenITestReportsAction;
    Action viewMavenUTestReportsAction;
    Action viewGradleTestReportsAction;
    Action refreshAction;

    ListViewer viewer;

    @Override
    public void createPartControl(Composite parent) {
        viewer = new ListViewer(parent);
        viewer.setContentProvider(new ArrayContentProvider());
        viewer.setLabelProvider(new LabelProvider());

        try {
            viewer.setInput(Project.getLibertyProjects(false));
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while retrieving Liberty projects.", e);
            return;
        }

        createActions();
        createContextMenu();
        addToolbarActions();
        getSite().setSelectionProvider(viewer);
    }

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

        getSite().registerContextMenu("liberty.views.liberty.devmode.dashboard", menuMgr, viewer);
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
        IProject project = Project.getSelected();

        if (project != null) {
            mgr.add(startAction);
            mgr.add(startWithParmAction);
            mgr.add(startInContanerAction);
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
    public void createActions() {
        ImageDescriptor ActionImg = null;
        ImageDescriptor refreshImg = null;

        // Get the image descriptors for the menu actions and toolbar.
        // If there is a failure, display the error and proceed without the icons.
        try {
            ActionImg = ImageDescriptor.createFromURL(
                    new URL("platform:/plugin/org.eclipse.jst.jsf.standard.tagsupport/icons/palette/Composite/small/ACTIONSOURCE.gif"));
            refreshImg = ImageDescriptor.createFromURL(new URL("platform:/plugin/org.eclipse.ui.browser/icons/clcl16/nav_refresh.png"));
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while retrieving Imade descriptions.", e);
        }

        // Menu: Start.
        startAction = new Action(APP_MENU_ACTION_START) {
            @Override
            public void run() {
                devMode.start();
            }
        };
        startAction.setImageDescriptor(ActionImg);

        // Menu: Start with parameters.
        startWithParmAction = new Action(APP_MENU_ACTION_START_PARMS) {
            @Override
            public void run() {
                devMode.startWithParms();
            }
        };
        startWithParmAction.setImageDescriptor(ActionImg);

        // Menu: Start in container.
        startInContanerAction = new Action(APP_MENU_ACTION_START_IN_CONTAINER) {
            @Override
            public void run() {
                devMode.startInContainer();
            }
        };
        startInContanerAction.setImageDescriptor(ActionImg);

        // Menu: Stop.
        stopAction = new Action(APP_MENU_ACTION_STOP) {
            @Override
            public void run() {
                devMode.stop();
            }
        };
        stopAction.setImageDescriptor(ActionImg);

        // Menu: Run tests.
        runTestAction = new Action(APP_MENU_ACTION_RUN_TESTS) {
            @Override
            public void run() {
                devMode.runTests();
            }
        };
        runTestAction.setImageDescriptor(ActionImg);

        // Menu: View integration test report. Maven project specific.
        viewMavenITestReportsAction = new Action(APP_MENU_ACTION_VIEW_MVN_IT_REPORT) {
            @Override
            public void run() {
                devMode.openMavenIntegrationTestReport();
            }
        };
        viewMavenITestReportsAction.setImageDescriptor(ActionImg);

        // Menu: View unit test report. Maven project specific.
        viewMavenUTestReportsAction = new Action(APP_MENU_ACTION_VIEW_MVN_UT_REPORT) {
            @Override
            public void run() {
                devMode.openMavenUnitTestReport();
            }
        };
        viewMavenUTestReportsAction.setImageDescriptor(ActionImg);

        // Menu: View test report. Gradle project specific.
        viewGradleTestReportsAction = new Action(APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT) {
            @Override
            public void run() {
                devMode.openGradleTestReport();
            }
        };
        viewGradleTestReportsAction.setImageDescriptor(ActionImg);

        // Toolbar: Refresh the project list.
        refreshAction = new Action(DASHBORD_TOOLBAR_ACTION_REFRESH) {
            @Override
            public void run() {
                try {
                    viewer.setInput(Project.getLibertyProjects(true));
                } catch (Exception e) {
                    Dialog.displayErrorMessageWithDetails("An error was detected while retrieving Liberty projects.", e);
                    return;
                }
            }
        };
        refreshAction.setImageDescriptor(refreshImg);
    }
}
