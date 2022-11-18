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
package io.openliberty.tools.eclipse;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import io.openliberty.tools.eclipse.Project.BuildType;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTab;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTab.State;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTabController;
import io.openliberty.tools.eclipse.ui.terminal.TerminalListener;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

/**
 * Provides the implementation of all supported dev mode operations.
 */
public class DevModeOperations {

    /**
     * Constants.
     */
    public static final String DEVMODE_START_PARMS_DIALOG_TITLE = "Liberty Dev Mode";
    public static final String DEVMODE_START_PARMS_DIALOG_MSG = "Specify custom parameters for the liberty dev command.";
    public static final String BROWSER_MVN_IT_REPORT_NAME_SUFFIX = "failsafe report";
    public static final String BROWSER_MVN_UT_REPORT_NAME_SUFFIX = "surefire report";
    public static final String BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX = "test report";

    /**
     * Project terminal tab controller instance.
     */
    private ProjectTabController projectTabController;

    /**
     * Dashboard object reference.
     */
    private WorkspaceProjectsModel projectModel;

    /**
     * PATH environment variable.
     */
    private String pathEnv;

    private DebugModeHandler debugModeHandler;
    /**
     * The instance of this class.
     */
    private static DevModeOperations instance;

    /**
     * DashboardView
     */
    private DashboardView dashboardView;

    /**
     * Constructor.
     */
    public DevModeOperations() {
        projectTabController = ProjectTabController.getInstance();
        projectModel = new WorkspaceProjectsModel();
        pathEnv = System.getenv("PATH");
        debugModeHandler = new DebugModeHandler(this);
    }

    /**
     * Because the current class is used as a singleton this effectively provides a singleton for the model object returned
     * 
     * @return a complete model of the projects in the workspace
     */
    public WorkspaceProjectsModel getProjectModel() {
        return projectModel;
    }

    /**
     * Returns an instance of this class.
     * 
     * @return An instance of this class.
     */
    public static DevModeOperations getInstance() {
        if (instance == null) {
            instance = new DevModeOperations();
        }

        return instance;
    }

    /**
     * @param iProject The project instance to associate with this action.
     * @param parms The configuration parameters to be used when starting dev mode.
     * @param javaHomePath The configuration java installation home to be set in the terminal running dev mode.
     * @param mode The configuration mode.
     */
    public void start(IProject iProject, String parms, String javaHomePath, String mode) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { iProject, parms, javaHomePath, mode });
        }

        if (iProject == null) {
            String msg = "An error was detected while processing the start request. The object representing the selected project could not be found.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        // Check if the start action has already been issued.
        String projectName = iProject.getName();

        // Check if the start action has already been issued.
        State terminalState = projectTabController.getTerminalState(projectName);
        if (terminalState != null && terminalState == ProjectTab.State.STARTED) {
            // Check if the terminal tab associated with this call was marked as closed. This scenario may occur if a previous
            // attempt to start the server in dev mode was issued successfully, but there was a failure in the process or
            // there was an unexpected case that caused the terminal process to end. If that is the case, cleanup the objects
            // associated with the previous instance to allow users to restart dev mode.
            if (projectTabController.isProjectTabMarkedClosed(projectName)) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS,
                            "The start request was already processed on project " + projectName
                                    + ". The terminal tab for this project is marked as closed. Cleaning up. ProjectTabController: "
                                    + projectTabController);
                }
                projectTabController.cleanupTerminal(projectName);
            } else {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "The start request was already issued on project " + projectName
                            + ". No-op. ProjectTabController: " + projectTabController);
                }
                ErrorHandler.processErrorMessage("The start request was already issued on project " + projectName
                        + ". Use the stop action prior to selecting the start action.", true);
                return;
            }
        }

        Project project = null;

        try {
            project = projectModel.getProject(projectName);
            if (project == null) {
                throw new Exception("Unable to find internal instance of project " + projectName);
            }

            // Get the absolute path to the application project.
            String projectPath = project.getPath();
            if (projectPath == null) {
                throw new Exception("Unable to find the path to selected project " + projectName);
            }

            // If in debug mode, adjust the start parameters.
            String userParms = (parms == null) ? "" : parms.trim();
            String startParms = null;
            String debugPort = null;
            if (ILaunchManager.DEBUG_MODE.equals(mode)) {
                debugPort = debugModeHandler.calculateDebugPort(project, userParms);
                startParms = debugModeHandler.addDebugDataToStartParms(project, debugPort, userParms);
            } else {
                startParms = userParms;
            }

            // Prepare the Liberty plugin container dev mode command.
            String cmd = "";
            BuildType buildType = project.getBuildType();
            if (buildType == Project.BuildType.MAVEN) {
                try {
                    cmd = CommandBuilder.getMavenCommandLine(projectPath,
                        "io.openliberty.tools:liberty-maven-plugin:dev " + startParms + " -f " + projectPath, pathEnv);
                }
                catch (Exception e) {
                    String mvnCmdErrMsg = "Unable to get mvn command line";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_TOOLS, mvnCmdErrMsg, e);
                    }
                    ErrorHandler.processPreferenceWarningMessage(cmd, e, true);
                    return;
                }
            } else if (buildType == Project.BuildType.GRADLE) {
                try {
                    cmd = CommandBuilder.getGradleCommandLine(projectPath, "libertyDev " + startParms + " -p=" + projectPath, pathEnv);
                }
                catch (Exception e) {
                    String gradleCmdErrMsg = "Unable to get gradle command line";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_TOOLS, gradleCmdErrMsg, e);
                    }
                    ErrorHandler.processPreferenceWarningMessage(cmd, e, false);
                    return;
                }
            } else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project" + projectName
                        + "does not appear to be a Maven or Gradle built project.");
            }

            // If there is a debugPort, start the job to attach the debugger to the Liberty server JVM.
            if (debugPort != null) {
                debugModeHandler.startDebugAttacher(project, debugPort);
            }

            // Start a terminal and run the application in dev mode.
            startDevMode(cmd, projectName, projectPath, javaHomePath);
        } catch (Exception e) {
            String msg = "An error was detected while performing the start request on project " + projectName;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, project);
        }
    }

    /**
     * Starts the Liberty server in dev mode in a container.
     * 
     * @param iProject The project instance to associate with this action.
     * @param parms The configuration parameters to be used when starting dev mode.
     * @param javaHomePath The configuration java installation home to be set in the terminal running dev mode.
     * @param mode The configuration mode.
     */
    public void startInContainer(IProject iProject, String parms, String javaHomePath, String mode) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { iProject, parms, javaHomePath, mode });
        }

        if (iProject == null) {
            String msg = "An error was detected while processing the start in container request. The object representing the selected project could not be found.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        // Check if the start action has already been issued.
        String projectName = iProject.getName();

        // Check if the start action has already been issued.
        State terminalState = projectTabController.getTerminalState(projectName);
        if (terminalState != null && terminalState == ProjectTab.State.STARTED) {
            // Check if the terminal tab associated with this call was marked as closed. This scenario may occur if a previous
            // attempt to start the server in dev mode was issued successfully, but there was a failure in the process or
            // there was an unexpected case that caused the terminal process to end. If that is the case, cleanup the objects
            // associated with the previous instance to allow users to restart dev mode.
            if (projectTabController.isProjectTabMarkedClosed(projectName)) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS,
                            "The start in coontainer request was already processed on project " + projectName
                                    + ". The terminal tab for this project is marked as closed. Cleaning up. ProjectTabController: "
                                    + projectTabController);
                }
                projectTabController.cleanupTerminal(projectName);
            } else {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "The start in container request was already issued on project " + projectName
                            + ". No-op. ProjectTabController: " + projectTabController);
                }
                ErrorHandler.processErrorMessage("The start in container request was already issued on project " + projectName
                        + ". Use the stop action prior to selecting the start action.", true);
                return;
            }
        }

        Project project = null;

        try {
            project = projectModel.getProject(projectName);
            if (project == null) {
                throw new Exception("Unable to find internal instance of project " + projectName);
            }

            // Get the absolute path to the application project.
            String projectPath = project.getPath();
            if (projectPath == null) {
                throw new Exception("Unable to find the path to selected project " + projectName);
            }

            // If in debug mode, adjust the start parameters.
            String userParms = (parms == null) ? "" : parms.trim();
            String startParms = null;
            String debugPort = null;
            if (ILaunchManager.DEBUG_MODE.equals(mode)) {
                debugPort = debugModeHandler.calculateDebugPort(project, userParms);
                startParms = debugModeHandler.addDebugDataToStartParms(project, debugPort, userParms);
            } else {
                startParms = userParms;
            }

            // Prepare the Liberty plugin container dev mode command.
            String cmd = "";
            BuildType buildType = project.getBuildType();
            if (buildType == Project.BuildType.MAVEN) {
                cmd = CommandBuilder.getMavenCommandLine(projectPath,
                        "io.openliberty.tools:liberty-maven-plugin:devc " + startParms + " -f " + projectPath, pathEnv);
            } else if (buildType == Project.BuildType.GRADLE) {
                cmd = CommandBuilder.getGradleCommandLine(projectPath, "libertyDevc " + startParms + " -p=" + projectPath, pathEnv);
            } else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project" + projectName
                        + "does not appear to be a Maven or Gradle built project.");
            }

            // If there is a debugPort, start the job to attach the debugger to the Liberty server JVM.
            if (debugPort != null) {
                debugModeHandler.startDebugAttacher(project, debugPort);
            }

            // Start a terminal and run the application in dev mode.
            startDevMode(cmd, projectName, projectPath, javaHomePath);
        } catch (Exception e) {
            String msg = "An error was detected while performing the start in container request on project " + projectName;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, project);
        }
    }

    /**
     * Stops the Liberty server.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void stop(IProject inputProject) {
        // Get the object representing the selected application project. The returned project should never be null, but check it
        // just in case it is.
        IProject iProject = inputProject;
        if (iProject == null) {
            iProject = getSelectedDashboardProject();
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, iProject);
        }

        if (iProject == null) {
            String msg = "An error was detected while processing the stop request. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        String projectName = iProject.getName();

        // Check if the stop action has already been issued of if a start action was never issued before.
        if (projectTabController.getProjectConnector(projectName) == null) {
            String msg = "The start request was not issued first or the stop request has already been issued on project " + projectName
                    + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. ProjectTabController: " + projectTabController);
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        // Check if the terminal tab associated with this call was marked as closed. This scenario may occur if a previous
        // attempt to start the server in dev mode was issued successfully, but there was a failure in the process or
        // there was an unexpected case that caused the terminal process to end. Note that objects associated with the previous
        // start attempt will be cleaned up on the next restart attempt.
        if (projectTabController.isProjectTabMarkedClosed(projectName)) {
            String msg = "The terminal tab running project " + projectName
                    + " is not active due to an unexpected error or external action. Review the terminal output for more details. "
                    + "Once the circumstance that caused the terminal tab to be inactive is determined and resolved, "
                    + "issue a start request prior to issuing the stop request.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. ProjectTabController: " + projectTabController);
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        try {
            // Prepare the dev mode command to stop the server.
            String cmd = "exit" + System.lineSeparator();

            // Issue the command on the terminal.
            projectTabController.writeTerminalStream(projectName, cmd.getBytes());

            // The command to exit dev mode was issued. Set the internal project tab state to STOPPED as
            // indication that the stop command was issued. The project's terminal tab UI will be marked as closed (title and state
            // updates) when dev mode exits.
            projectTabController.setTerminalState(projectName, ProjectTab.State.STOPPED);

            // Cleanup internal objects. This maybe done a bit prematurely at this point because the operations triggered by
            // the action of writing to the terminal are asynchronous. However, there is no good way to listen for terminal tab
            // state changes (avoid loops or terminal internal class references). Furthermore, if errors are experienced during
            // dev mode exit, those errors may not be easily solved by re-trying the stop command.
            // If there are any errors during cleanup or if cleanup does not happen at all here, cleanup will be attempted
            // when the associated terminal view tab is closed/disposed.
            projectTabController.cleanupTerminal(projectName);

        } catch (Exception e) {
            String msg = "An error was detected while processing the stop request on project " + projectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectName);
        }
    }

    /**
     * Runs the tests provided by the application.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void runTests(IProject inputProject) {
        // Get the object representing the selected application project. The returned project should never be null, but check it
        // just in case it is.
        IProject iProject = inputProject;
        if (iProject == null) {
            iProject = getSelectedDashboardProject();
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, iProject);
        }

        if (iProject == null) {
            String msg = "An error was detected while processing the run tests request. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        String projectName = iProject.getName();

        // Check if the stop action has already been issued of if a start action was never issued before.
        if (projectTabController.getProjectConnector(projectName) == null) {
            String msg = "The start request was not issued first or the stop request has already been issued on project " + projectName
                    + ". Issue a start request prior to issuing the run tests request.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. ProjectTabController: " + projectTabController);
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        // Check if the terminal tab associated with this call was marked as closed. This scenario may occur if a previous
        // attempt to start the server in dev mode was issued successfully, but there was a failure in the process or
        // there was an unexpected case that caused the terminal process to end. Note that objects associated with the previous
        // start attempt will be cleaned up on the next restart attempt.
        if (projectTabController.isProjectTabMarkedClosed(projectName)) {
            String msg = "The terminal tab running project " + projectName
                    + " is not active due to an unexpected error or external action. Review the terminal output for more details. "
                    + "Once the circumstance that caused the terminal tab to be inactive is determined and resolved, "
                    + "issue a start request prior to issuing the run tests request.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. ProjectTabController: " + projectTabController);
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        try {
            // Prepare the dev mode command to run a test.
            String cmd = System.lineSeparator();

            // Issue the command on the terminal.
            projectTabController.writeTerminalStream(projectName, cmd.getBytes());
        } catch (Exception e) {
            String msg = "An error was detected while processing the run tests request on project " + projectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectName);
        }
    }

    /**
     * Open Maven integration test report.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void openMavenIntegrationTestReport(IProject inputProject) {
        // Get the object representing the selected application project. The returned project should never be null, but check it
        // just in case it is.
        IProject iProject = inputProject;
        if (iProject == null) {
            iProject = getSelectedDashboardProject();
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, iProject);
        }

        if (iProject == null) {
            String msg = "An error was detected while processing the view integration test report request. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        String projectName = iProject.getName();
        Project project = null;

        try {
            project = projectModel.getProject(projectName);
            if (project == null) {
                throw new Exception("Unable to find internal instance of project " + projectName);
            }

            // Get the absolute path to the application project.
            String projectPath = project.getPath();
            if (projectPath == null) {
                throw new Exception("Unable to find the path to selected project " + projectName);
            }

            // Get the path to the test report.
            Path path = getMavenIntegrationTestReportPath(projectPath);
            if (!path.toFile().exists()) {
                String msg = "Integration test results were not found for project " + projectName + ". Select \""
                        + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" prior to selecting \""
                        + DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Path: " + path);
                }
                ErrorHandler.processErrorMessage(msg, true);
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserTabTitle = projectName + " " + BROWSER_MVN_IT_REPORT_NAME_SUFFIX;
            openTestReport(projectName, path, path.toString(), browserTabTitle, browserTabTitle);
        } catch (Exception e) {
            String msg = "An error was detected while processing the view integration test report request on project " + projectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, project);
        }
    }

    /**
     * Open Maven unit test report.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void openMavenUnitTestReport(IProject inputProject) {
        // Get the object representing the selected application project. The returned project should never be null, but check it
        // just in case it is.
        IProject iProject = inputProject;
        if (iProject == null) {
            iProject = getSelectedDashboardProject();
        }

        if (Trace.isEnabled()) {
            if (Trace.isEnabled()) {
                Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, iProject);
            }
        }

        if (iProject == null) {
            String msg = "An error was detected while processing the view unit test report request. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(msg, true);
        }

        String projectName = iProject.getName();
        Project project = null;

        try {
            project = projectModel.getProject(projectName);
            if (project == null) {
                throw new Exception("Unable to find internal instance of project " + projectName);
            }

            // Get the absolute path to the application project.
            String projectPath = project.getPath();
            if (projectPath == null) {
                throw new Exception("Unable to find the path to selected project " + projectName);
            }

            // Get the path to the test report.
            Path path = getMavenUnitTestReportPath(projectPath);
            if (!path.toFile().exists()) {
                String msg = "Unit test results were not found for project " + projectName + ". Select \""
                        + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" prior to selecting \""
                        + DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Path: " + path);
                }
                ErrorHandler.processErrorMessage(msg, true);
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserTabTitle = projectName + " " + BROWSER_MVN_UT_REPORT_NAME_SUFFIX;
            openTestReport(projectName, path, path.toString(), browserTabTitle, browserTabTitle);
        } catch (Exception e) {
            String msg = "An error was detected while processing the view unit test report request on project " + projectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, project);
        }
    }

    /**
     * Open Gradle test report.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void openGradleTestReport(IProject inputProject) {
        // Get the object representing the selected application project. The returned project should never be null, but check it
        // just in case it is.
        IProject iProject = inputProject;
        if (iProject == null) {
            iProject = getSelectedDashboardProject();
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, iProject);
        }

        if (iProject == null) {
            String msg = "An error was detected while processing the view test report request. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        String projectName = iProject.getName();
        Project project = null;

        try {
            project = projectModel.getProject(projectName);
            if (project == null) {
                throw new Exception("Unable to find internal instance of project " + projectName);
            }

            // Get the absolute path to the application project.
            String projectPath = project.getPath();
            if (projectPath == null) {
                throw new Exception("Unable to find the path to selected project " + projectName);
            }

            // Get the path to the test report.
            Path path = getGradleTestReportPath(projectPath);
            if (!path.toFile().exists()) {
                String msg = "Test results were not found for project " + projectName + ". Select \""
                        + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" prior to selecting \""
                        + DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Path: " + path);
                }
                ErrorHandler.processErrorMessage(msg, true);
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserTabTitle = projectName + " " + BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX;
            openTestReport(projectName, path, path.toString(), browserTabTitle, browserTabTitle);
        } catch (Exception e) {
            String msg = "An error was detected while processing the view test report request on project " + projectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, project);
        }
    }

    /**
     * Opens the specified report in a browser.
     *
     * @param projectName The application project name.
     * @param path The path to the HTML report file.
     * @param browserId The Id to use for the browser display.
     * @param name The name to use for the browser display.
     * @param toolTip The tool tip to use for the browser display.
     *
     * @throws Exception If an error occurs while displaying the test report.
     */
    public void openTestReport(String projectName, Path path, String browserId, String name, String toolTip) throws Exception {
        URL url = path.toUri().toURL();
        IWorkbenchBrowserSupport bSupport = PlatformUI.getWorkbench().getBrowserSupport();
        IWebBrowser browser = null;
        if (bSupport.isInternalWebBrowserAvailable()) {
            browser = bSupport.createBrowser(IWorkbenchBrowserSupport.AS_EDITOR | IWorkbenchBrowserSupport.LOCATION_BAR
                    | IWorkbenchBrowserSupport.NAVIGATION_BAR | IWorkbenchBrowserSupport.STATUS, browserId, name, toolTip);
        } else {
            browser = bSupport.createBrowser(browserId);
        }

        browser.openURL(url);
    }

    /**
     * Runs the specified command on a terminal.
     *
     * @param cmd The command to run.
     * @param projectName The name of the project currently being processed.
     * @param projectPath The project's path.
     *
     * @throws Exception If an error occurs while running the specified command.
     */
    public void startDevMode(String cmd, String projectName, String projectPath, String javaInstallPath) throws Exception {
        // Determine the environment properties to be set in the terminal prior to running dev mode.
        List<String> envs = new ArrayList<String>(1);

        // The value for JAVA_HOME comes from the underlying configuration. The configuration allows
        // the java installation to be custom defined, execution environment defined, or workspace defined.
        envs.add("JAVA_HOME=" + javaInstallPath);

        projectTabController.runOnTerminal(projectName, projectPath, cmd, envs);
    }

    /**
     * Returns the path of the HTML file containing the integration test report.
     *
     * @param projectPath The project's path.
     *
     * @return The path of the HTML file containing the integration test report.
     */
    public static Path getMavenIntegrationTestReportPath(String projectPath) {
        Path path = Paths.get(projectPath, "target", "site", "failsafe-report.html");

        return path;
    }

    /**
     * Returns the path of the HTML file containing the unit test report.
     *
     * @param projectPath The project's path.
     *
     * @return The path of the HTML file containing the unit test report.
     */
    public static Path getMavenUnitTestReportPath(String projectPath) {
        Path path = Paths.get(projectPath, "target", "site", "surefire-report.html");

        return path;
    }

    /**
     * Returns the path of the HTML file containing the test report.
     *
     * @param projectPath The project's path.
     *
     * @return The custom path of the HTML file containing the or the default location.
     */
    public static Path getGradleTestReportPath(String projectPath) {
        // TODO: Look for custom dir entry in build.gradle:
        // "test.reports.html.destination". Need to handle a value like this:
        // reports.html.destination = file("$buildDir/edsTestReports/teststuff")
        // Notice the use of a variable: $buildDir.

        // If a custom path was not defined, use default value.
        Path path = Paths.get(projectPath, "build", "reports", "tests", "test", "index.html");

        return path;
    }

    /**
     * Returns the project instance associated with the currently selected view object in the workspace.
     *
     * @return The project currently selected or null if one was not found.
     */
    public IProject getSelectedDashboardProject() {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS);
        }

        IProject iProject = null;
        IWorkbenchWindow w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

        if (w != null) {
            ISelectionService selectionService = w.getSelectionService();
            ISelection selection = selectionService.getSelection();

            if (selection instanceof IStructuredSelection) {
                IStructuredSelection structuredSelection = (IStructuredSelection) selection;
                Object firstElement = structuredSelection.getFirstElement();
                if (firstElement instanceof String) {
                    Project project = projectModel.getProject((String) firstElement);
                    if (project != null) {
                        iProject = project.getIProject();
                    }
                }
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, iProject);
        }

        return iProject;
    }

    /**
     * Verifies that the input project is known to the plugin and that it is a supported project.
     * 
     * @param iProject The project to validate. If null, this operation is a no-op.
     * 
     * @throws Exception If the input project is not supported.
     */
    public void verifyProjectSupport(IProject iProject) throws Exception {
        if (iProject != null) {
            String projectName = iProject.getName();
            Project project = projectModel.getProject(projectName);
            if (project == null) {
                throw new Exception("Project " + projectName + " is not a supported project. Make sure the project is a Liberty project.");
            }
        }
    }

    public DashboardView getDashboardView() {
        return dashboardView;
    }

    public void setDashboardView(DashboardView dashboardView) {
        this.dashboardView = dashboardView;
    }

    /**
     * Returns true if the terminal tab associated with the input project was marked closed. False, otherwise.
     * 
     * @param projectName The name of the project.
     * 
     * @return true if the terminal tab associated with the input project was marked closed. False, otherwise.
     */
    public boolean isProjectTerminalTabMarkedClosed(String projectName) {
        return projectTabController.isProjectTabMarkedClosed(projectName);
    }

    /**
     * Registers the input terminal listener.
     * 
     * @param projectName The name of the project for which the listener is registered.
     * @param listener The listener implementation.
     */
    public void registerTerminalListener(String projectName, TerminalListener listener) {
        projectTabController.registerTerminalListener(projectName, listener);
    }

    /**
     * Deregisters the input terminal listener.
     * 
     * @param projectName The name of the project the input listener is registered for.
     * @param listener The listener implementation.
     */
    public void deregisterTerminalListener(String projectName, TerminalListener listener) {
        projectTabController.deregisterTerminalListener(projectName, listener);
    }

    /**
     * Refreshes the dashboard view.
     */
    public void refreshDashboardView(boolean reportError) {
        if (dashboardView != null) {
            dashboardView.refreshDashboardView(projectModel, reportError);
        }
    }
}
