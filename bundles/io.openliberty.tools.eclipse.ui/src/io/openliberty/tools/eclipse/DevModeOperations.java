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
package io.openliberty.tools.eclipse;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import io.openliberty.tools.eclipse.CommandBuilder.CommandNotFoundException;
import io.openliberty.tools.eclipse.Project.BuildType;
import io.openliberty.tools.eclipse.debug.DebugModeHandler;
import io.openliberty.tools.eclipse.logging.Logger;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
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

    public static final String DEVMODE_COMMAND_EXIT = "exit" + System.lineSeparator();
    public static final String DEVMODE_COMMAND_RUN_TESTS = System.lineSeparator();

    public static final String BROWSER_MVN_IT_REPORT_NAME_SUFFIX = "failsafe report";
    public static final String BROWSER_MVN_UT_REPORT_NAME_SUFFIX = "surefire report";
    public static final String BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX = "test report";
    public static final String MVN_RUN_APP_LOG_FILE = "io.openliberty.tools.eclipse.mvnlogfilename";

    private static final int STOP_TIMEOUT_SECONDS = 60;
    protected static final QualifiedName STOP_JOB_COMPLETION_TIMEOUT = new QualifiedName("io.openliberty.tools.eclipse.ui",
            "stopJobCompletionTimeout");
    protected static final QualifiedName STOP_JOB_COMPLETION_EXIT_CODE = new QualifiedName("io.openliberty.tools.eclipse.ui",
            "stopJobCompletionExitCode");
    protected static final QualifiedName STOP_JOB_COMPLETION_OUTPUT = new QualifiedName("io.openliberty.tools.eclipse.ui",
            "stopJobCompletionOutput");
    private Map<Job, Boolean> runningJobs = new ConcurrentHashMap<Job, Boolean>();

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

    /**
     * Handles debug mode processing.
     */
    private DebugModeHandler debugModeHandler;

    /**
     * The instance of this class.
     */
    private static DevModeOperations instance;

    /**
     * Represents the liberty dashboard view.
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
    public void start(IProject iProject, String parms, String javaHomePath, ILaunch launch, String mode) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { iProject, parms, javaHomePath, mode });
        }

        if (iProject == null) {
            String msg = "An error was detected when the start request was processed. The object that represents the selected project was not found.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.start_no_project_found, null), true);
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
                projectTabController.processTerminalTabCleanup(projectName);
            } else {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "The start request was already issued on project " + projectName
                            + ". No-op. ProjectTabController: " + projectTabController);
                }
                ErrorHandler.processErrorMessage(NLS.bind(Messages.start_already_issued, projectName), true);
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
                cmd = CommandBuilder.getMavenCommandLine(projectPath, "io.openliberty.tools:liberty-maven-plugin:dev " + startParms,
                        pathEnv, true);
            } else if (buildType == Project.BuildType.GRADLE) {
                cmd = CommandBuilder.getGradleCommandLine(projectPath, "libertyDev " + startParms, pathEnv, true);
            } else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project " + projectName
                        + "does not appear to be a Maven or Gradle built project.");
            }

            // Start a terminal and run the application in dev mode.
            startDevMode(cmd, projectName, projectPath, javaHomePath);

            // If there is a debugPort, start the job to attach the debugger to the Liberty server JVM.
            if (debugPort != null) {
                debugModeHandler.startDebugAttacher(project, launch, debugPort);
            }
        } catch (CommandNotFoundException e) {
            String msg = "Maven or Gradle command not found for project " + projectName;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            return;
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "An error was detected during the start request on project " + projectName, e);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.start_general_error, projectName), e, true);
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
    public void startInContainer(IProject iProject, String parms, String javaHomePath, ILaunch launch, String mode) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { iProject, parms, javaHomePath, mode });
        }

        if (iProject == null) {
            String msg = "An error was detected when the start in container request was processed. The object that represents the selected project was not found.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.start_container_no_project_found, null), true);
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
                            "The start in container request was already processed on project " + projectName
                                    + ". The terminal tab for this project is marked as closed. Cleaning up. ProjectTabController: "
                                    + projectTabController);
                }
                projectTabController.processTerminalTabCleanup(projectName);
            } else {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "The start in container request was already issued on project " + projectName
                            + ". No-op. ProjectTabController: " + projectTabController);
                }
                ErrorHandler.processErrorMessage(NLS.bind(Messages.start_container_already_issued, projectName), true);
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
                cmd = CommandBuilder.getMavenCommandLine(projectPath, "io.openliberty.tools:liberty-maven-plugin:devc " + startParms,
                        pathEnv, true);
            } else if (buildType == Project.BuildType.GRADLE) {
                cmd = CommandBuilder.getGradleCommandLine(projectPath, "libertyDevc " + startParms, pathEnv, true);
            } else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project " + projectName
                        + "does not appear to be a Maven or Gradle built project.");
            }

            // Start a terminal and run the application in dev mode.
            startDevMode(cmd, projectName, projectPath, javaHomePath);

            // If there is a debugPort, start the job to attach the debugger to the Liberty server JVM.
            if (debugPort != null) {
                debugModeHandler.startDebugAttacher(project, launch, debugPort);
            }
        } catch (Exception e) {
            String msg = "An error was detected during the start in container request on project " + projectName;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.start_container_general_error, projectName), e, true);
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
            String msg = "An error was detected when the stop request was processed. The object that represents the selected project was not found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.stop_no_project_found, null), true);
            return;
        }

        String projectName = iProject.getName();

        // Check if the stop action has already been issued of if a start action was never issued before.
        if (projectTabController.getProjectConnector(projectName) == null) {
            String msg = NLS.bind(Messages.stop_already_issued, projectName);
            handleStopActionError(projectName, msg);

            return;
        }

        // Check if the terminal tab associated with this call was marked as closed. This scenario may occur if a previous
        // attempt to start the server in dev mode failed due to an invalid custom start parameter, dev mode was terminated manually,
        // dev mode is already running outside of the Liberty Tools session, or there was an unexpected case that caused
        // the terminal process to end. Note that objects associated with the previous start attempt will be cleaned up on
        // the next restart attempt.
        if (projectTabController.isProjectTabMarkedClosed(projectName)) {
            String msg = NLS.bind(Messages.stop_terminal_not_active, projectName);
            handleStopActionError(projectName, msg);

            return;
        }

        try {
            // Issue the command on the terminal.
            projectTabController.writeToTerminalStream(projectName, DEVMODE_COMMAND_EXIT.getBytes());

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
            projectTabController.processTerminalTabCleanup(projectName);

        } catch (Exception e) {
            String msg = NLS.bind(Messages.stop_general_error, projectName);
            handleStopActionError(projectName, msg);

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
            String msg = "An error was detected when the run tests request was processed. The object that represents the selected project was not found. When you use the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.run_tests_no_project_found, null), true);
            return;
        }

        String projectName = iProject.getName();

        // Check if the stop action has already been issued of if a start action was never issued before.
        if (projectTabController.getProjectConnector(projectName) == null) {
            String msg = "No start request was issued first or the stop request was already issued on project " + projectName
                    + ". Issue a start request before you issue the run tests request.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. ProjectTabController: " + projectTabController);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.run_tests_no_prior_start, projectName), true);
            return;
        }

        // Check if the terminal tab associated with this call was marked as closed. This scenario may occur if a previous
        // attempt to start the server in dev mode was issued successfully, but there was a failure in the process or
        // there was an unexpected case that caused the terminal process to end. Note that objects associated with the previous
        // start attempt will be cleaned up on the next restart attempt.
        if (projectTabController.isProjectTabMarkedClosed(projectName)) {
            String msg = "The terminal tab that is running project " + projectName
                    + " is not active due to an unexpected error or external action. Review the terminal output for more details. "
                    + "Once the circumstance that caused the terminal tab to be inactive is determined and resolved, "
                    + "issue a start request before you issue the run tests request.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. ProjectTabController: " + projectTabController);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.run_tests_terminal_not_active, projectName), true);
            return;
        }

        try {
            // Issue the command on the terminal.
            projectTabController.writeToTerminalStream(projectName, DEVMODE_COMMAND_RUN_TESTS.getBytes());
        } catch (Exception e) {
            String msg = "An error was detected when the run tests request was processed on project " + projectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.run_tests_general_error, projectName), e, true);
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
            String msg = "An error was detected when the view integration test report request was processed. The object that represents the selected project was not found. When you use the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.mvn_int_test_report_no_project_found, null), true);
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
                String msg = "No integration test results were found for project " + projectName + ". Select \""
                        + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" before you select \""
                        + DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Path: " + path);
                }
                ErrorHandler.processErrorMessage(NLS.bind(Messages.mvn_int_test_report_none_found, new String[] { projectName,
                        DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT }), true);
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserTabTitle = projectName + " " + BROWSER_MVN_IT_REPORT_NAME_SUFFIX;
            openTestReport(projectName, path, path.toString(), browserTabTitle, browserTabTitle);
        } catch (Exception e) {
            String msg = "An error was detected when the view integration test report request was processed on project " + projectName
                    + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.mvn_int_test_report_general_error, projectName), e, true);
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
            String msg = "An error was detected when the view unit test report request was processed. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.mvn_unit_test_report_no_project_found, null), true);
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
                String msg = "No unit test results were found for project " + projectName + ". Select \""
                        + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" before you select \""
                        + DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Path: " + path);
                }
                ErrorHandler.processErrorMessage(NLS.bind(Messages.mvn_unit_test_report_none_found, new String[] { projectName,
                        DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT }), true);
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserTabTitle = projectName + " " + BROWSER_MVN_UT_REPORT_NAME_SUFFIX;
            openTestReport(projectName, path, path.toString(), browserTabTitle, browserTabTitle);
        } catch (Exception e) {
            String msg = "An error was detected when the view unit test report request was processed on project " + projectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.mvn_unit_test_report_general_error, projectName), e, true);
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
            String msg = "An error was detected when the view test report request was processed. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.gradle_test_report_no_project_found, null), true);
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
                String msg = "No test results were found for project " + projectName + ". Select \""
                        + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" before you select \""
                        + DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Path: " + path);
                }
                ErrorHandler
                        .processErrorMessage(
                                NLS.bind(Messages.gradle_test_report_none_found, new String[] { projectName,
                                        DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT }),
                                true);
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserTabTitle = projectName + " " + BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX;
            openTestReport(projectName, path, path.toString(), browserTabTitle, browserTabTitle);
        } catch (Exception e) {
            String msg = "An error was detected when the view test report request was processed on project " + projectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.gradle_test_report_general_error, projectName));
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
        String logFileName = System.getProperty(MVN_RUN_APP_LOG_FILE);
        if (logFileName != null && !logFileName.isEmpty()) {
            // TODO - could abort if either of these env variables is already set but by guarding with sysprop no risk
            // of accidental usage.

            // mvn
            envs.add("MAVEN_ARGS=--log-file " + logFileName);
            // mvnw
            envs.add("MAVEN_CONFIG=--log-file " + logFileName);
        }

        projectTabController.runOnTerminal(projectName, projectPath, cmd, envs);
    }

    /**
     * Informs the users of the error and prompts them to chose whether or not to allow the Liberty plugin stop command to be issued
     * for the specified project.
     * 
     * @param projectName The name of the project for which the the Liberty plugin stop command is issued.
     * @param baseMsg The base message to display.
     */
    private void handleStopActionError(String projectName, String baseMsg) {
        String stopPromptMsg = NLS.bind(Messages.issue_stop_prompt, null);
        String msg = baseMsg + "\n\n" + stopPromptMsg;
        Integer response = ErrorHandler.processWarningMessage(msg, true, new String[] { "Yes", "No" }, 0);
        if (response != null && response == 0) {
            issueLPStopCommand(projectName);
        }
    }

    /**
     * Issues the Liberty plugin stop command to stop the Liberty server associated with the specified project.
     * 
     * @param projectName The name of the project for which the the Liberty plugin stop command is issued.
     */
    private void issueLPStopCommand(String projectName) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, projectName);
        }

        try {
            // Get the internal object representing the input project name.
            Project project = projectModel.getProject(projectName);
            if (project == null) {
                throw new Exception("Unable to find internal the instance of project " + projectName);
            }

            // Get the absolute path to the application project.
            String projectPath = project.getPath();
            if (projectPath == null) {
                throw new Exception("Unable to find the path associated with project " + projectName);
            }

            // TODO - for multi-module case, consider additional warning if this is an aggregate module with multiple sub-modules.
            // Of course we'd have to be smart enough to know this were the case in order to issue such a warning

            // Build the command.
            String cmd = "";
            String buildTypeName;
            BuildType buildType = project.getBuildType();
            if (buildType == Project.BuildType.MAVEN) {
                cmd = CommandBuilder.getMavenCommandLine(projectPath, "io.openliberty.tools:liberty-maven-plugin:stop", pathEnv, false);
                buildTypeName = "Maven";
            } else if (buildType == Project.BuildType.GRADLE) {
                cmd = CommandBuilder.getGradleCommandLine(projectPath, "libertyStop", pathEnv, false);
                buildTypeName = "Gradle";
            } else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project " + projectName
                        + "does not appear to be a Maven or Gradle built project.");
            }

            // Issue the command.
            String[] cmdParts = cmd.split(" ");
            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.directory(new File(projectPath));
            pb.redirectErrorStream(true);
            pb.environment().put("JAVA_HOME", JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath());

            /*
             * Per: https://stackoverflow.com/questions/29793071/rcp-no-progress-dialog-when-starting-a-job it seems that job.setUser(true)
             * is no longer enough to result in the creation of a progress dialog.
             */
            Job job = new Job("Stopping server via " + buildTypeName + " plugin") {

                @Override
                protected IStatus run(IProgressMonitor monitor) {

                    if (monitor.isCanceled()) {
                        return Status.CANCEL_STATUS;
                    }

                    try {
                        Process p = pb.start();

                        boolean completed = false;

                        for (int elapsed = 0; completed == false && elapsed < STOP_TIMEOUT_SECONDS; elapsed++) {
                            if (monitor.isCanceled()) {
                                p.destroy();
                                return Status.CANCEL_STATUS;
                            }
                            completed = p.waitFor(1, TimeUnit.SECONDS);
                        }

                        if (!completed) {
                            setProperty(STOP_JOB_COMPLETION_TIMEOUT, Boolean.TRUE);
                        } else {
                            setProperty(STOP_JOB_COMPLETION_EXIT_CODE, p.exitValue());
                            if (p.exitValue() != 0) {
                                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                StringBuilder builder = new StringBuilder();
                                String line = null;
                                while ((line = reader.readLine()) != null) {
                                    builder.append(line);
                                    builder.append(System.getProperty("line.separator"));
                                }
                                setProperty(STOP_JOB_COMPLETION_OUTPUT, builder.toString());
                            }
                        }
                    } catch (Exception e) {
                        ErrorHandler.processErrorMessage(NLS.bind(Messages.plugin_stop_issue_error, null), e, false);
                    }
                    return Status.OK_STATUS;
                }

            };

            job.addJobChangeListener(new JobChangeAdapter() {
                @Override
                public void done(IJobChangeEvent event) {

                    runningJobs.remove(event.getJob());
                    if (event.getResult().equals(Status.CANCEL_STATUS)) {
                        return;
                    }

                    /*
                     * Check for timeout
                     */
                    Object timeoutOnCompletion = event.getJob().getProperty(STOP_JOB_COMPLETION_TIMEOUT);
                    if (Boolean.TRUE.equals(timeoutOnCompletion)) {
                        // Need to do this on main thread since it's displayed to the user.
                        Display.getDefault().syncExec(new Runnable() {
                            @Override
                            public void run() {

                                String msg = "The Liberty Maven or Gradle stop command issued for project " + projectName
                                        + " timed out after " + STOP_TIMEOUT_SECONDS + " seconds.";
                                if (Trace.isEnabled()) {
                                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg);
                                }
                                ErrorHandler.rawErrorMessageDialog(NLS.bind(Messages.plugin_stop_timeout,
                                        new String[] { projectName, Integer.toString(STOP_TIMEOUT_SECONDS) }));
                            }
                        });
                        return;
                    }

                    /*
                     * Check for bad exit value
                     */
                    Object rc = event.getJob().getProperty(STOP_JOB_COMPLETION_EXIT_CODE);
                    if (rc != Integer.valueOf(0)) {
                        String outputTxt = (String) event.getJob().getProperty(STOP_JOB_COMPLETION_OUTPUT);
                        Logger.logError("stop command failed, process output: " + outputTxt);
                        Display.getDefault().syncExec(new Runnable() {
                            @Override
                            public void run() {
                                ErrorHandler.processErrorMessage(NLS.bind(Messages.plugin_stop_failed, rc), true);
                            }
                        });
                        return;
                    }
                }
            });

            job.setUser(true);
            runningJobs.put(job, Boolean.TRUE);
            job.schedule();
        } catch (Exception e) {
            String msg = "An error was detected while processing the Liberty Maven or Gradle stop command on project " + projectName;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.plugin_stop_general_error, projectName), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectName);
        }
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
     * Unregisters the input terminal listener.
     * 
     * @param projectName The name of the project the input listener is registered for.
     * @param listener The listener implementation.
     */
    public void unregisterTerminalListener(String projectName, TerminalListener listener) {
        projectTabController.unregisterTerminalListener(projectName, listener);
    }

    /**
     * Refreshes the dashboard view.
     */
    public void refreshDashboardView(boolean reportError) {
        if (dashboardView != null) {
            dashboardView.refreshDashboardView(projectModel, reportError);
        }
    }

    /**
     * Cancel running jobs and avoid error message, e.g. on closing Eclipse IDE
     */
    public void cancelRunningJobs() {
        // Cancel will remove job from 'runningJobs' Map
        runningJobs.keySet().forEach(j -> j.cancel());
    }
}
