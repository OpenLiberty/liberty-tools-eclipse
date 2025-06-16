/*******************************************************************************
* Copyright (c) 2022, 2025 IBM Corporation and others.
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.debug.core.DebugPlugin;
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
import io.openliberty.tools.eclipse.process.ProcessController;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

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

    private static final String ANSI_SUPPORT_QUALIFIER = "org.eclipse.ui.console";
    private static final String ANSI_SUPPORT_KEY = "ANSI_support_enabled";

    private static final int STOP_TIMEOUT_SECONDS = 60;
    protected static final QualifiedName STOP_JOB_COMPLETION_TIMEOUT = new QualifiedName("io.openliberty.tools.eclipse.ui",
            "stopJobCompletionTimeout");
    protected static final QualifiedName STOP_JOB_COMPLETION_EXIT_CODE = new QualifiedName("io.openliberty.tools.eclipse.ui",
            "stopJobCompletionExitCode");
    protected static final QualifiedName STOP_JOB_COMPLETION_OUTPUT = new QualifiedName("io.openliberty.tools.eclipse.ui",
            "stopJobCompletionOutput");
    private Map<Job, Boolean> runningJobs = new ConcurrentHashMap<Job, Boolean>();

    /**
     * Process controller instance.
     */
    private ProcessController processController;

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
        processController = ProcessController.getInstance();
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
     * Provides a singleton reference to the debug mode handler
     * 
     * @returns the debug mode handler
     */
    public DebugModeHandler getDebugModeHandler() {
        return debugModeHandler;
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
     * @param javaHomePath The configuration java installation home to be set in the process running dev mode.
     * @param launch The launch associated with this run.
     * @param mode The configuration mode.
     */
    public void start(IProject iProject, String parms, String javaHomePath, ILaunch launch, String mode, boolean runProjectClean) {

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

            // Append color styling to start parms
            BuildType buildType = project.getBuildType();
            if (buildType == Project.BuildType.MAVEN) {

                StringBuffer updateStartParms = new StringBuffer(startParms);
                updateStartParms.append(" ");

                boolean ansiSupported = Platform.getPreferencesService().getBoolean(ANSI_SUPPORT_QUALIFIER, ANSI_SUPPORT_KEY, true, null);

                if (ansiSupported) {
                    updateStartParms.append("-Dstyle.color=always");
                } else {
                    updateStartParms.append("-Dstyle.color=never");
                }

                startParms = updateStartParms.toString();
            }

            // Prepare the Liberty plugin container dev mode command.
            String cmd = "";

            if (buildType == Project.BuildType.MAVEN) {
                cmd = CommandBuilder.getMavenCommandLine(projectPath, (runProjectClean == true ? " clean " : "" ) +  "io.openliberty.tools:liberty-maven-plugin:dev " + startParms,
                        pathEnv);
			} else if (buildType == Project.BuildType.GRADLE) {

				if (runProjectClean == true) {
					try {
						String stopGradleDaemonCmd= CommandBuilder.getGradleCommandLine(projectPath," --stop", pathEnv);
						executeCommand(stopGradleDaemonCmd, projectPath);
					} catch (IOException | InterruptedException e) {
						 Logger.logError("An attempt to stop the Gradle daemon failed....");
					}

				}
				cmd = CommandBuilder.getGradleCommandLine(projectPath,
						(runProjectClean == true ? " clean " : "") + "libertyDev " + startParms, pathEnv);

			} else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project " + projectName
                        + "does not appear to be a Maven or Gradle built project.");
            }

            // Run the application in dev mode.
            startDevMode(cmd, projectName, projectPath, javaHomePath, launch);

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
     * @param javaHomePath The configuration java installation home to be set in the process running dev mode.
     * @param launch The launch associated with this run.
     * @param mode The configuration mode.
     */
    public void startInContainer(IProject iProject, String parms, String javaHomePath, ILaunch launch, String mode, boolean runProjectClean) {

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

            // Append color styling to start parms
            BuildType buildType = project.getBuildType();
            if (buildType == Project.BuildType.MAVEN) {

                StringBuffer updateStartParms = new StringBuffer(startParms);
                updateStartParms.append(" ");

                boolean ansiSupported = Platform.getPreferencesService().getBoolean(ANSI_SUPPORT_QUALIFIER, ANSI_SUPPORT_KEY, true, null);

                if (ansiSupported) {
                    updateStartParms.append("-Dstyle.color=always");
                } else {
                    updateStartParms.append("-Dstyle.color=never");
                }

                startParms = updateStartParms.toString();
            }

            // Prepare the Liberty plugin container dev mode command.
            String cmd = "";
            if (buildType == Project.BuildType.MAVEN) {
                cmd = CommandBuilder.getMavenCommandLine(projectPath, (runProjectClean == true ? " clean " : "") + "io.openliberty.tools:liberty-maven-plugin:devc " + startParms,
                        pathEnv);
			} else if (buildType == Project.BuildType.GRADLE) {
				if (runProjectClean == true) {
					try {

						String stopGradleDaemonCmd = CommandBuilder.getGradleCommandLine(projectPath, " --stop",
								pathEnv);
						executeCommand(stopGradleDaemonCmd, projectPath);
					} catch (IOException | InterruptedException e) {
						Logger.logError("An attempt to stop the Gradle daemon failed....");
					}
				}
				cmd = CommandBuilder.getGradleCommandLine(projectPath,
						(runProjectClean == true ? " clean " : "") + "libertyDevc " + startParms, pathEnv);
			} else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project " + projectName
                        + "does not appear to be a Maven or Gradle built project.");
            }

            // Run the application in dev mode.
            startDevMode(cmd, projectName, projectPath, javaHomePath, launch);

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
        Project project = projectModel.getProject(projectName);
        
        if (project != null) {
        	Utils.reEnableAppMonitoring(project);
        }

        // Check if the stop action has already been issued of if a start action was never issued before.
        if (!processController.isProcessStarted(projectName)) {
            String msg = NLS.bind(Messages.stop_already_issued, projectName);
            handleStopActionError(projectName, msg);

            return;
        }

        try {
            // Issue the command to the process.
            processController.writeToProcessStream(projectName, DEVMODE_COMMAND_EXIT);

            // Cleanup internal objects.
            cleanupProcess(projectName);

        } catch (Exception e) {
            String msg = NLS.bind(Messages.stop_general_error, projectName);
            handleStopActionError(projectName, msg);

            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectName);
        }
    }

    public void cleanupProcess(String projectName) {
        processController.cleanup(projectName);
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
        if (!processController.isProcessStarted(projectName)) {
            String msg = "No start request was issued first or the stop request was already issued on project " + projectName
                    + ". Issue a start request before you issue the run tests request.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. ProcessController: " + processController);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.run_tests_no_prior_start, projectName), true);
            return;
        }

        try {
            // Issue the command on the console.
            processController.writeToProcessStream(projectName, DEVMODE_COMMAND_RUN_TESTS);
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
            Path path = getMavenIntegrationTestReportPath(projectPath, projectName);

            if (path != null) {
                // Display the report on the browser. Browser display is based on eclipse configuration preferences.
                String browserTabTitle = projectName + " " + BROWSER_MVN_IT_REPORT_NAME_SUFFIX;
                openTestReport(projectName, path, path.toString(), browserTabTitle, browserTabTitle);
            }
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
            Path path = getMavenUnitTestReportPath(projectPath, projectName);

            if (path != null) {
                // Display the report on the browser. Browser display is based on eclipse configuration preferences.
                String browserTabTitle = projectName + " " + BROWSER_MVN_UT_REPORT_NAME_SUFFIX;
                openTestReport(projectName, path, path.toString(), browserTabTitle, browserTabTitle);
            }
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
     * Runs the specified command.
     *
     * @param cmd The command to run.
     * @param projectName The name of the project currently being processed.
     * @param projectPath The project's path.
     *
     * @throws Exception If an error occurs while running the specified command.
     */
    public void startDevMode(String cmd, String projectName, String projectPath, String javaInstallPath, ILaunch launch) throws Exception {
        // Determine the environment properties to be set in the process running dev mode.
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

        Process process = processController.runProcess(projectName, projectPath, cmd, envs, true);

        DebugPlugin.newProcess(launch, process, projectName);
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
                cmd = CommandBuilder.getMavenCommandLine(projectPath, "io.openliberty.tools:liberty-maven-plugin:stop", pathEnv);
                buildTypeName = "Maven";
            } else if (buildType == Project.BuildType.GRADLE) {
                cmd = CommandBuilder.getGradleCommandLine(projectPath, "libertyStop", pathEnv);
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
        } catch (

        Exception e) {
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
    public static Path getMavenIntegrationTestReportPath(String projectPath, String projectName) {
        Path path1 = Paths.get(projectPath, "target", "reports", "failsafe.html");
        Path path2 = Paths.get(projectPath, "target", "site", "failsafe-report.html");

        if (!path1.toFile().exists() && !path2.toFile().exists()) {
            String msg = "No integration test results were found for project " + projectName + ". Select \""
                    + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" before you select \""
                    + DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT + "\" on the menu.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Paths checked: " + path1 + ", " + path2);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.mvn_int_test_report_none_found, new String[] { projectName,
                    DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT }), true);
            return null;
        }

        return path1.toFile().exists() ? path1 : path2;
    }

    public Path getLibertyPluginConfigXmlPath(Project project) throws Exception {  	
    	    	
        Project serverProj = getLibertyServerProject(project);
        String buildDir = serverProj.getBuildType() == BuildType.GRADLE ? "build" : "target";
      
        Path path = Paths.get(serverProj.getPath(), buildDir, "liberty-plugin-config.xml");
        return path;
    }
    

    /**
     * Returns the liberty server module project associated with the input project.
     * 
     * @param project The project to process.
     * 
     * @return The liberty server module project associated with the input project.
     * 
     * @throws Exception
     */
    private Project getLibertyServerProject(Project project) throws Exception {
        if (project.isParentOfServerModule()) {
            List<Project> mmps = project.getChildLibertyServerProjects();
            switch (mmps.size()) {
                case 0:
                    throw new Exception("Unable to find a child project that contains the Liberty server configuration.");
                case 1:
                    return mmps.get(0);
                default:
                    throw new Exception("Multiple child projects containing Liberty server configuration were found.");
            }
        }

        return project;
    }

    /**
     * Returns the path of the HTML file containing the unit test report.
     *
     * @param projectPath The project's path.
     *
     * @return The path of the HTML file containing the unit test report.
     */
    public static Path getMavenUnitTestReportPath(String projectPath, String projectName) {
        Path path1 = Paths.get(projectPath, "target", "reports", "surefire.html");
        Path path2 = Paths.get(projectPath, "target", "site", "surefire-report.html");

        if (!path1.toFile().exists() && !path2.toFile().exists()) {
            String msg = "No unit test results were found for project " + projectName + ". Select \""
                    + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" before you select \""
                    + DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT + "\" on the menu.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Paths checked: " + path1 + ", " + path2);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.mvn_unit_test_report_none_found, new String[] { projectName,
                    DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT }), true);
            return null;
        }

        return path1.toFile().exists() ? path1 : path2;
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
     * Returns true if the start process for the project is active. False, otherwise.
     * 
     * @param projectName The name of the project.
     * 
     * @return true if the start process for the project is active. False, otherwise.
     */
    public boolean isProjectStarted(String projectName) {
        return processController.isProcessStarted(projectName);
    }

    public void restartServer(String projectName) {
    	String restartCommand = "r";
    	try {
    		processController.writeToProcessStream(projectName, restartCommand);
    	} catch (Exception e) {
    		if (Trace.isEnabled()) {
    			Trace.getTracer().trace(Trace.TRACE_TOOLS, "An error was detected during the restart server." + projectName, e);
    		}
    	}
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
    

	public void executeCommand(String fullCommand, String projectPath) throws IOException, InterruptedException {
		// Split the full command into individual arguments
		List<String> command = Arrays.asList(fullCommand.trim().split("\\s+"));

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(new File(projectPath)); // Set working directory

		Process process = builder.start();
		process.waitFor();
	}

}
