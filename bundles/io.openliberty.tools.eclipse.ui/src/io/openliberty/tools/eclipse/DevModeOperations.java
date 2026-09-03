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
package io.openliberty.tools.eclipse;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import io.openliberty.tools.eclipse.CommandBuilder.CommandData;
import io.openliberty.tools.eclipse.CommandBuilder.CommandNotFoundException;
import io.openliberty.tools.eclipse.debug.DebugModeHandler;
import io.openliberty.tools.eclipse.logging.Logger;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.ProjectModel.BuildType;
import io.openliberty.tools.eclipse.model.WorkspaceModel;
import io.openliberty.tools.eclipse.process.ConsoleOutputInterceptor;
import io.openliberty.tools.eclipse.process.DevModeStateHandler;
import io.openliberty.tools.eclipse.process.ProcessController;
import io.openliberty.tools.eclipse.ui.ModuleSelectionDialog;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Provides the implementation of all supported dev mode operations.
 */
public class DevModeOperations {

    /**
     * Liberty module state filter options. Used to resolve modules
     * based on their state (active/started or inactive/stopped).
     */
    public enum ModuleStateFilter {
        ALL,
        ACTIVE,
        INACTIVE
    }

    /**
     * Represents the combined active/inactive/mixed state of all modules associated
     * with a project.
     */
    public enum ProjectAggregatedState {
        ACTIVE,
        INACTIVE,
        MIXED
    }

    /**
     * Supported actions types.
     */
    public static enum DashboardAction {
        START("Start"),
        START_CFG("Start..."),
        START_CTR("Start in container"),
        DEBUG("Debug"),
        DEBUG_CFG("Debug..."),
        DEBUG_CTR("Debug in container"),
        STOP("Stop"),
        RUN_TESTS("Run tests"),
        OPEN_MVN_IT_TEST_REPORT("View integration test report"),
        OPEN_MVN_UT_TEST_REPORT("View unit test report"),
        OPEN_GRADLE_TEST_REPORT("View test report");

        private final String name;

        private DashboardAction(String name) {
            this.name = name;
        }

        /**
         * Returns the name of the dashboard action.
         * 
         * @return The name of the dashboard action.
         */
        public String getName() {
            return name;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return this.name;
        }
    }

    /**
     * Constants.
     */
    public static final String DEVMODE_START_PARMS_DIALOG_TITLE = Messages.getMessage("devmode_start_dialog_title");
    public static final String DEVMODE_START_PARMS_DIALOG_MSG = Messages.getMessage("devmode_start_dialog_msg");

    public static final String DEVMODE_COMMAND_EXIT = "exit" + System.lineSeparator();
    public static final String DEVMODE_COMMAND_RUN_TESTS = System.lineSeparator();

    public static final String BROWSER_MVN_IT_REPORT_NAME_SUFFIX = "failsafe report";
    public static final String BROWSER_MVN_UT_REPORT_NAME_SUFFIX = "surefire report";
    public static final String BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX = "test report";

    private static final String ANSI_SUPPORT_QUALIFIER = "org.eclipse.ui.console";
    private static final String ANSI_SUPPORT_KEY = "ANSI_support_enabled";

    private static final int STOP_TIMEOUT_SECONDS = 60;
    protected static final QualifiedName STOP_JOB_COMPLETION_TIMEOUT = new QualifiedName("io.openliberty.tools.eclipse.ui", "stopJobCompletionTimeout");
    protected static final QualifiedName STOP_JOB_COMPLETION_EXIT_CODE = new QualifiedName("io.openliberty.tools.eclipse.ui", "stopJobCompletionExitCode");
    protected static final QualifiedName STOP_JOB_COMPLETION_OUTPUT = new QualifiedName("io.openliberty.tools.eclipse.ui", "stopJobCompletionOutput");
    private Map<Job, Boolean> runningJobs = new ConcurrentHashMap<Job, Boolean>();

    /**
     * Remembers the last confirmed module selections per aggregator project.
     * The key is the aggregator project name. The value is the list of module names
     * that were last confirmed by the user in the multi-select dialog. Used to restore
     * pre-checked items the next time the dialog opens for the same aggregator.
     */
    private final Map<String, List<String>> lastSelections = new ConcurrentHashMap<String, List<String>>();

    /**
     * Holds pre-allocated ServerSocket instances used to reserve Liberty debug ports for
     * multi-module parallel starts. Each socket is opened on port 0 (OS-assigned) and kept
     * open until DevModeOperations.start() or startInContainer() consumes it for that module.
     * Keeping the socket open prevents other OS processes from binding the same port in the
     * window between allocation and Liberty server startup.
     */
    private final Map<String, ServerSocket> libertyDebugPortReservations = new ConcurrentHashMap<String, ServerSocket>();

    /**
     * Tracks the last known app state for every project by project name. Updated at every
     * state transition so that the state can be seeded into the newly created ProjectModel
     * instances after the workspace model is rebuilt on a dashboard refresh. Entries whose
     * state is STOPPED are removed because STOPPED is the default initial state of every
     * new ProjectModel.
     */
    private final Map<String, ProjectModel.AppState> projectStateTable = new ConcurrentHashMap<String, ProjectModel.AppState>();

    /**
     * Process controller instance.
     */
    private ProcessController processController;

    /**
     * Dashboard object reference.
     */
    private WorkspaceModel workspaceModel;

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
        workspaceModel = new WorkspaceModel();
        pathEnv = System.getenv("PATH");
        debugModeHandler = new DebugModeHandler(this);
    }

    /**
     * Because the current class is used as a singleton this effectively provides a singleton for the model object returned
     * 
     * @return a complete model of the projects in the workspace
     */
    public WorkspaceModel getWorkspaceModel() {
        return workspaceModel;
    }

    /**
     * Provides a singleton reference to the debug mode handler
     * 
     * @return the debug mode handler
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
     * @param iProject     The project instance to associate with this action.
     * @param parms        The configuration parameters to be used when starting dev mode.
     * @param javaHomePath The configuration java installation home to be set in the process running dev mode.
     * @param launch       The launch associated with this run.
     * @param mode         The configuration mode.
     */
    public void start(ProjectModel targetProjectModel, String parms, String javaHomePath, ILaunch launch, String mode, boolean runProjectClean) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { targetProjectModel, parms, javaHomePath, mode, runProjectClean });
        }

        String targetProjectName = targetProjectModel.getName();

        try {
            // If in debug mode, adjust the start parameters.
            String userParms = (parms == null) ? "" : parms.trim();
            String startParms = null;
            String debugPort = null;
            if (ILaunchManager.DEBUG_MODE.equals(mode)) {
                debugPort = debugModeHandler.calculateDebugPort(targetProjectModel, userParms);
                startParms = debugModeHandler.addDebugDataToStartParms(targetProjectModel, debugPort, userParms);
            } else {
                startParms = userParms;
            }

            // Append color styling to start parms. When running in non-debug mode, also
            // append any pre-reserved Liberty debug port so that parallel module starts
            // each bind a distinct port and do not race to claim the plugin default (7777).
            BuildType buildType = targetProjectModel.getBuildType();
            if (buildType == ProjectModel.BuildType.Maven) {

                StringBuffer updateStartParms = new StringBuffer(startParms);
                updateStartParms.append(" ");

                boolean ansiSupported = Platform.getPreferencesService().getBoolean(ANSI_SUPPORT_QUALIFIER, ANSI_SUPPORT_KEY, true, null);

                if (ansiSupported) {
                    updateStartParms.append("-Dstyle.color=always");
                } else {
                    updateStartParms.append("-Dstyle.color=never");
                }

                // Consume any pre-reserved debug port. Only present for multi-module starts.
                if (!ILaunchManager.DEBUG_MODE.equals(mode) && targetProjectModel.isBatchStarted()) {
                    String reservedPort = consumeLibertyDebugPortReservation(targetProjectName);
                    if (reservedPort != null) {
                        updateStartParms.append(" ").append(DebugModeHandler.MAVEN_DEVMODE_DEBUG_PORT_PARM).append("=").append(reservedPort);
                    }
                }

                startParms = updateStartParms.toString();
            } else if (buildType == ProjectModel.BuildType.Gradle && targetProjectModel.isBatchStarted()) {
                // Consume any pre-reserved debug port. Only present for multi-module starts.
                if (!ILaunchManager.DEBUG_MODE.equals(mode)) {
                    String reservedPort = consumeLibertyDebugPortReservation(targetProjectName);
                    if (reservedPort != null) {
                        startParms = startParms + " " + DebugModeHandler.GRADLE_DEVMODE_DEBUG_PORT_PARM + "=" + reservedPort;
                    }
                }
            }

            // Prepare the Liberty plugin container dev mode command.
            String cmd = "";
            String targetProjectExecPath = null;

            if (buildType == ProjectModel.BuildType.Maven) {
                CommandData commandData = CommandBuilder.constructMavenCommand(targetProjectModel,
                                                                               "io.openliberty.tools:liberty-maven-plugin:dev",
                                                                               runProjectClean,
                                                                               startParms,
                                                                               pathEnv);
                cmd = commandData.getCommand();
                targetProjectExecPath = commandData.getExecutionPath();
            } else if (buildType == ProjectModel.BuildType.Gradle) {

                CommandData commandData = CommandBuilder.constructGradleCommand(targetProjectModel, "libertyDev", runProjectClean, startParms, pathEnv);
                cmd = commandData.getCommand();
                targetProjectExecPath = commandData.getExecutionPath();

                if (runProjectClean == true) {
                    try {
                        CommandData stopGradleDaemonCmdData = CommandBuilder.constructGradleStopDaemonCommand(targetProjectModel, pathEnv);
                        executeCommand(stopGradleDaemonCmdData.getCommand(), stopGradleDaemonCmdData.getExecutionPath());
                    } catch (IOException | InterruptedException e) {
                        Logger.logError("An attempt to stop the Gradle daemon failed.");
                    }
                }
            } else {
                throw new Exception(Messages.getMessage("unexpected_build_type", buildType, targetProjectName));
            }

            // Run the application in dev mode.
            startDevMode(cmd, targetProjectModel, targetProjectExecPath, javaHomePath, launch, mode);

            // If there is a debugPort, start the job to attach the debugger to the Liberty server JVM.
            if (debugPort != null) {
                debugModeHandler.startDebugAttacher(targetProjectModel, launch, debugPort);
            }
        } catch (CommandNotFoundException e) {
            String msg = "Maven or Gradle command not found for project " + targetProjectName;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            return;
        } catch (Exception e) {
            String msg = Messages.getMessage("start_general_error", targetProjectName);
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, targetProjectModel);
        }
    }

    /**
     * Starts the Liberty server in dev mode in a container.
     * 
     * @param iProject     The project instance to associate with this action.
     * @param parms        The configuration parameters to be used when starting dev mode.
     * @param javaHomePath The configuration java installation home to be set in the process running dev mode.
     * @param launch       The launch associated with this run.
     * @param mode         The configuration mode.
     */
    public void startInContainer(ProjectModel targetProjectModel, String parms, String javaHomePath, ILaunch launch, String mode, boolean runProjectClean) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { targetProjectModel, parms, javaHomePath, mode, runProjectClean });
        }

        String targetProjectName = targetProjectModel.getName();

        try {
            // If in debug mode, adjust the start parameters.
            String userParms = (parms == null) ? "" : parms.trim();
            String startParms = null;
            String debugPort = null;
            if (ILaunchManager.DEBUG_MODE.equals(mode)) {
                debugPort = debugModeHandler.calculateDebugPort(targetProjectModel, userParms);
                startParms = debugModeHandler.addDebugDataToStartParms(targetProjectModel, debugPort, userParms);
            } else {
                startParms = userParms;
            }

            // Append color styling to start parms. When running in non-debug mode, also
            // append any pre-reserved Liberty debug port so that parallel module starts
            // each bind a distinct port and do not race to claim the plugin default (7777).
            BuildType buildType = targetProjectModel.getBuildType();
            if (buildType == ProjectModel.BuildType.Maven) {

                StringBuffer updateStartParms = new StringBuffer(startParms);
                updateStartParms.append(" ");

                boolean ansiSupported = Platform.getPreferencesService().getBoolean(ANSI_SUPPORT_QUALIFIER, ANSI_SUPPORT_KEY, true, null);

                if (ansiSupported) {
                    updateStartParms.append("-Dstyle.color=always");
                } else {
                    updateStartParms.append("-Dstyle.color=never");
                }

                // Consume any pre-reserved debug port. Only present for multi-module starts.
                if (!ILaunchManager.DEBUG_MODE.equals(mode) && targetProjectModel.isBatchStarted()) {
                    String reservedPort = consumeLibertyDebugPortReservation(targetProjectName);
                    if (reservedPort != null) {
                        updateStartParms.append(" ").append(DebugModeHandler.MAVEN_DEVMODE_DEBUG_PORT_PARM).append("=").append(reservedPort);
                    }
                }

                startParms = updateStartParms.toString();
            } else if (buildType == ProjectModel.BuildType.Gradle) {
                // Consume any pre-reserved debug port. Only present for multi-module starts.
                if (!ILaunchManager.DEBUG_MODE.equals(mode) && targetProjectModel.isBatchStarted()) {
                    String reservedPort = consumeLibertyDebugPortReservation(targetProjectName);
                    if (reservedPort != null) {
                        startParms = startParms + " " + DebugModeHandler.GRADLE_DEVMODE_DEBUG_PORT_PARM + "=" + reservedPort;
                    }
                }
            }

            // Prepare the Liberty plugin container dev mode command.
            String cmd = "";
            String targetProjectExecPath = null;
            if (buildType == ProjectModel.BuildType.Maven) {
                CommandData commandData = CommandBuilder.constructMavenCommand(targetProjectModel,
                                                                               "io.openliberty.tools:liberty-maven-plugin:devc",
                                                                               runProjectClean,
                                                                               startParms,
                                                                               pathEnv);
                cmd = commandData.getCommand();
                targetProjectExecPath = commandData.getExecutionPath();
            } else if (buildType == ProjectModel.BuildType.Gradle) {

                CommandData commandData = CommandBuilder.constructGradleCommand(targetProjectModel, "libertyDevc", runProjectClean, startParms, pathEnv);
                cmd = commandData.getCommand();
                targetProjectExecPath = commandData.getExecutionPath();

                if (runProjectClean == true) {
                    try {
                        CommandData stopGradleDaemonCmdData = CommandBuilder.constructGradleStopDaemonCommand(targetProjectModel, pathEnv);
                        executeCommand(stopGradleDaemonCmdData.getCommand(), stopGradleDaemonCmdData.getExecutionPath());
                    } catch (IOException | InterruptedException e) {
                        Logger.logError("An attempt to stop the Gradle daemon failed.");
                    }
                }
            } else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project " + targetProjectName
                                    + " does not appear to be a Maven or Gradle built project.");
            }

            // Run the application in dev mode.
            startDevMode(cmd, targetProjectModel, targetProjectExecPath, javaHomePath, launch, mode);

            // If there is a debugPort, start the job to attach the debugger to the Liberty server JVM.
            if (debugPort != null) {
                debugModeHandler.startDebugAttacher(targetProjectModel, launch, debugPort);
            }
        } catch (Exception e) {
            String msg = Messages.getMessage("start_container_general_error", targetProjectName);
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, targetProjectModel);
        }
    }

    /**
     * Stops the Liberty server.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void stop(ProjectModel targetProjectModel) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, targetProjectModel);
        }

        Utils.reEnableAppMonitoring(targetProjectModel);
        String targetProjectName = targetProjectModel.getName();

        try {
            // Check if the stop action has already been issued or if a start action was never issued before.
            if (!isProjectStarted(targetProjectModel)) {
                String msg = Messages.getMessage("stop_already_issued", targetProjectName);
                ErrorHandler.processErrorMessage(msg, true);
                return;
            }

            // Issue the command to the process. The state is only advanced to STOPPING
            // after a successful write. That is so that a write failure leaves the state unchanged.
            // Note that process cleanup is deferred until the dev mode has stopped.
            // This is done by the state handler, and the debug event listener.
            processController.writeToProcessStream(targetProjectName, DEVMODE_COMMAND_EXIT);

            // Transition to STOPPING so the dashboard shows the stopping icon.
            targetProjectModel.setAppState(ProjectModel.AppState.STOPPING);
            cacheAppState(targetProjectName, ProjectModel.AppState.STOPPING);
            refreshDashboardLabel(targetProjectModel);
        } catch (Exception e) {
            String msg = Messages.getMessage("stop_general_error", targetProjectName);
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                        "An error occurred while processing the stop request on the " + targetProjectName + " project.", e);
            }
            ErrorHandler.processErrorMessage(msg, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, targetProjectModel);
        }
    }

    /**
     * Cleans up the process resources for the specified project.
     * Synchronized to prevent duplicate cleanup when both DevModeStateHandler
     * and LibertyDebugEventListener race to call this method on process termination.
     *
     * @param projectName The name of the project whose process should be cleaned up.
     */
    public synchronized void cleanupProcess(String projectName) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, projectName);
        }

        ProjectModel projectModel = workspaceModel.getProjectByName(projectName);

        // If the project is already STOPPED, cleanup was already performed by the first
        // caller. The second caller (whichever of DevModeStateHandler or
        // LibertyDebugEventListener arrived later) exits here.
        if (projectModel == null || projectModel.getAppState() == ProjectModel.AppState.STOPPED) {
            if (Trace.isEnabled()) {
                Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "Already stopped or not found. No-op.");
            }
            return;
        }

        processController.cleanup(projectName, projectModel.getPath());
        projectModel.setAppState(ProjectModel.AppState.STOPPED);
        cacheAppState(projectName, ProjectModel.AppState.STOPPED);
        refreshDashboardLabel(projectModel);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectModel + " ProcessController: " + processController);
        }
    }

    /**
     * Runs the tests provided by the application.
     *
     * @param inputProject The project instance to associate with this action.
     */
    public void runTests(ProjectModel targetProjectModel) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, targetProjectModel);
        }

        if (targetProjectModel == null) {
            String msg = "An error was detected when the run tests request was processed. The object that represents the selected project was not found. When you use the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("run_tests_no_project_found"), true);
            return;
        }

        String targetProjectName = targetProjectModel.getName();

        // Check if the stop action has already been issued or if a start action was never issued before.
        if (!isProjectStarted(targetProjectModel)) {
            String msg = "No start request was issued first or the stop request was already issued on project " + targetProjectName
                         + ". Issue a start request before you issue the run tests request.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. ProcessController: " + processController);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("run_tests_no_prior_start", targetProjectName), true);
            return;
        }

        try {
            // Issue the command on the console.
            processController.writeToProcessStream(targetProjectName, DEVMODE_COMMAND_RUN_TESTS);
        } catch (Exception e) {
            String msg = "An error was detected when the run tests request was processed on project " + targetProjectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("run_tests_general_error", targetProjectName), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, targetProjectModel);
        }
    }

    /**
     * Open Maven integration test report.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void openMavenIntegrationTestReport(ProjectModel targetProjectModel) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, targetProjectModel);
        }

        if (targetProjectModel == null) {
            String msg = "An error was detected when the view integration test report request was processed. The object that represents the selected project was not found. When you use the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("mvn_int_test_report_no_project_found"), true);
            return;
        }

        String targetProjectName = targetProjectModel.getName();

        try {
            // Get the absolute path to the application project.
            String targetProjectPath = targetProjectModel.getPath();
            if (targetProjectPath == null) {
                throw new Exception("Unable to find the path to selected project " + targetProjectName);
            }

            // Get the path to the test report.
            Path path = getMavenITReportPath(targetProjectPath, targetProjectName, true);

            if (path != null) {
                // Display the report on the browser. Browser display is based on eclipse configuration preferences.
                String browserTabTitle = targetProjectName + " " + BROWSER_MVN_IT_REPORT_NAME_SUFFIX;
                openTestReport(targetProjectName, path, path.toString(), browserTabTitle, browserTabTitle);
            }
        } catch (Exception e) {
            String msg = "An error was detected when the view integration test report request was processed on project " + targetProjectName
                         + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("mvn_int_test_report_general_error", targetProjectName), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, targetProjectModel);
        }
    }

    /**
     * Open Maven unit test report.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void openMavenUnitTestReport(ProjectModel targetProjectModel) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, targetProjectModel);
        }

        if (targetProjectModel == null) {
            String msg = "An error was detected when the view unit test report request was processed. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("mvn_unit_test_report_no_project_found"), true);
            return;
        }

        String targetProjectName = targetProjectModel.getName();

        try {
            // Get the absolute path to the application project.
            String targetProjectPath = targetProjectModel.getPath();
            if (targetProjectPath == null) {
                throw new Exception("Unable to find the path to selected project " + targetProjectName);
            }

            // Get the path to the test report.
            Path path = getMavenUTReportPath(targetProjectPath, targetProjectName, true);

            if (path != null) {
                // Display the report on the browser. Browser display is based on eclipse configuration preferences.
                String browserTabTitle = targetProjectName + " " + BROWSER_MVN_UT_REPORT_NAME_SUFFIX;
                openTestReport(targetProjectName, path, path.toString(), browserTabTitle, browserTabTitle);
            }
        } catch (Exception e) {
            String msg = "An error was detected when the view unit test report request was processed on project " + targetProjectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("mvn_unit_test_report_general_error", targetProjectName), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, targetProjectModel);
        }
    }

    /**
     * Open Gradle test report.
     * 
     * @param inputProject The project instance to associate with this action.
     */
    public void openGradleTestReport(ProjectModel targetProjectModel) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, targetProjectModel);
        }

        if (targetProjectModel == null) {
            String msg = "An error was detected when the view test report request was processed. The object representing the selected project could not be found. When using the Run Configuration launcher, be sure to select a project or project content first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op.");
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("gradle_test_report_no_project_found"), true);
            return;
        }

        String targetProjectName = targetProjectModel.getName();

        try {
            // Get the absolute path to the application project.
            String targetProjectPath = targetProjectModel.getPath();
            if (targetProjectPath == null) {
                throw new Exception("Unable to find the path to selected project " + targetProjectName);
            }

            // Get the path to the test report.
            Path path = getGradleTestReportPath(targetProjectPath);
            if (!path.toFile().exists()) {
                String msg = "No test results were found for project " + targetProjectName + ". Select \""
                             + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" before you select \""
                             + DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Path: " + path);
                }
                ErrorHandler.processErrorMessage(
                                                 Messages.getMessage("gradle_test_report_none_found", targetProjectName,
                                                                     DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT),
                                                 true);
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserTabTitle = targetProjectName + " " + BROWSER_GRADLE_TEST_REPORT_NAME_SUFFIX;
            openTestReport(targetProjectName, path, path.toString(), browserTabTitle, browserTabTitle);
        } catch (Exception e) {
            String msg = "An error was detected when the view test report request was processed on project " + targetProjectName + ".";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
            }
            ErrorHandler.processErrorMessage(Messages.getMessage("gradle_test_report_general_error", targetProjectName));
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, targetProjectModel);
        }
    }

    /**
     * Opens the specified report in a browser.
     *
     * @param projectName The application project name.
     * @param path        The path to the HTML report file.
     * @param browserId   The Id to use for the browser display.
     * @param name        The name to use for the browser display.
     * @param toolTip     The tool tip to use for the browser display.
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
     * @param cmd          The command to run.
     * @param projectModel The model of the project currently being processed.
     * @param projectPath  The project's path.
     * @param mode         The Eclipse launch mode used to start dev mode.
     *
     * @throws Exception If an error occurs while running the specified command.
     */
    public void startDevMode(String cmd, ProjectModel projectModel, String projectPath, String javaInstallPath, ILaunch launch, String mode) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { projectModel.getName(), cmd, projectPath, mode });
        }

        String projectName = projectModel.getName();

        // Determine the environment properties to be set in the process running dev mode.
        List<String> envs = new ArrayList<String>(1);

        // The value for JAVA_HOME comes from the underlying configuration. The configuration allows
        // the java installation to be custom defined, execution environment defined, or workspace defined.
        envs.add("JAVA_HOME=" + javaInstallPath);

        // Transition to STARTING state immediately so the dashboard shows the spinner
        // before any console output arrives. Expand the parent first so the child row
        // is already visible when the label is repainted with the spinner icon.
        projectModel.setAppState(ProjectModel.AppState.STARTING);
        cacheAppState(projectName, ProjectModel.AppState.STARTING);
        refreshDashboardLabel(projectModel);

        processController.runProcess(projectName, projectPath, cmd, envs, true, launch);

        // Register the dev mode state handler so that Liberty console messages trigger
        // the appropriate in-plugin reactions.
        ConsoleOutputInterceptor interceptor = processController.getInterceptor(projectPath);
        if (interceptor != null) {
            interceptor.addHandler(new DevModeStateHandler(projectModel, mode));
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectModel + " ProcessController: " + processController);
        }
    }

    /**
     * Issues the Liberty plugin stop command to stop the Liberty server associated with the specified project.
     * If the stop command completes successfully, the optional onSuccess runnable is invoked on the UI thread.
     *
     * @param projectName The name of the project for which the Liberty plugin stop command is issued.
     * @param onSuccess   A runnable to invoke on the UI thread after a successful stop, or null if no action is needed.
     */
    public void issueStopCommand(String projectName, Runnable onSuccess) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, projectName);
        }

        try {
            // Get the internal object representing the input project name.
            ProjectModel targetProjectModel = workspaceModel.getProjectByName(projectName);

            // Validate that we know about the selected project.
            if (targetProjectModel == null) {
                throw new IllegalStateException(Messages.getMessage("internal_project_not_found", projectName));
            }

            // Build the command.
            String cmd = "";
            String targetProjectExecPath = null;
            BuildType buildType = targetProjectModel.getBuildType();
            if (buildType == ProjectModel.BuildType.Maven) {
                CommandData commandData = CommandBuilder.constructMavenCommand(targetProjectModel,
                                                                               "io.openliberty.tools:liberty-maven-plugin:stop",
                                                                               false,
                                                                               null,
                                                                               pathEnv);
                cmd = commandData.getCommand();
                targetProjectExecPath = commandData.getExecutionPath();
            } else if (buildType == ProjectModel.BuildType.Gradle) {
                CommandData commandData = CommandBuilder.constructGradleCommand(targetProjectModel, "libertyStop", false, null, pathEnv);
                cmd = commandData.getCommand();
                targetProjectExecPath = commandData.getExecutionPath();

            } else {
                throw new Exception(Messages.getMessage("unexpected_build_type", targetProjectModel.getBuildType().toString(), projectName));
            }

            String[] cmdParts = cmd.split(" ");
            ProcessBuilder pb = new ProcessBuilder(cmdParts);
            pb.directory(new File(targetProjectExecPath));
            pb.redirectErrorStream(true);
            pb.environment().put("JAVA_HOME", JavaRuntime.getDefaultVMInstall().getInstallLocation().getAbsolutePath());

            // Create a job to stop the dev mode process currently running the target project.
            // Per: https://stackoverflow.com/questions/29793071/rcp-no-progress-dialog-when-starting-a-job it seems that job.setUser(true)
            // is no longer enough to result in the creation of a progress dialog.
            Job job = new Job(Messages.getMessage("stopping_server_job", targetProjectModel.getBuildType().toString())) {

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
                        ErrorHandler.processErrorMessage(Messages.getMessage("plugin_stop_issue_error"), e, false);
                    }
                    return Status.OK_STATUS;
                }

            };

            // Add the listener that will restart dev mode for the target project when stop is complete.
            job.addJobChangeListener(new JobChangeAdapter() {
                @Override
                public void done(IJobChangeEvent event) {

                    runningJobs.remove(event.getJob());
                    if (event.getResult().equals(Status.CANCEL_STATUS)) {
                        return;
                    }

                    /*
                     * Check for timeout.
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
                                ErrorHandler.rawErrorMessageDialog(Messages.getMessage("plugin_stop_timeout",
                                                                                       projectName, Integer.toString(STOP_TIMEOUT_SECONDS)));
                            }
                        });
                        return;
                    }

                    /*
                     * Check for bad exit value.
                     */
                    Object rc = event.getJob().getProperty(STOP_JOB_COMPLETION_EXIT_CODE);
                    if (!Integer.valueOf(0).equals(rc)) {
                        String outputTxt = (String) event.getJob().getProperty(STOP_JOB_COMPLETION_OUTPUT);
                        Logger.logError("stop command failed, process output: " + outputTxt);
                        Display.getDefault().syncExec(new Runnable() {
                            @Override
                            public void run() {
                                ErrorHandler.processErrorMessage(Messages.getMessage("plugin_stop_failed", rc), true);
                            }
                        });
                        return;
                    }

                    // Invoke the post-stop action if one was provided.
                    if (onSuccess != null) {
                        Display.getDefault().asyncExec(onSuccess);
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
            ErrorHandler.processErrorMessage(Messages.getMessage("plugin_stop_general_error", projectName), e, true);
            return;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectName);
        }
    }

    /**
     * Returns the path of the HTML file containing the integration test report.
     *
     * @param projectPath       The project's path.
     * @param projectName       The project's name.
     * @param reportUnavailable The indicator to trace and report back to the user when the reports are not available.
     *
     * @return The path of the HTML file containing the unit test report.
     */
    public static Path getMavenITReportPath(String projectPath, String projectName, boolean reportUnavailable) {
        Path path1 = Paths.get(projectPath, "target", "reports", "failsafe.html");
        Path path2 = Paths.get(projectPath, "target", "site", "failsafe-report.html");
        boolean path1Exists = path1.toFile().exists();
        boolean path2Exists = path2.toFile().exists();

        if (!path1Exists && !path2Exists) {
            if (reportUnavailable) {
                String msg = "No integration test results were found for project " + projectName + ". Select \""
                             + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" before you select \""
                             + DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Paths checked: " + path1 + ", " + path2);
                }
                ErrorHandler.processErrorMessage(Messages.getMessage("mvn_int_test_report_none_found", projectName,
                                                                     DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT),
                                                 true);
            }

            return null;
        }

        return (path1Exists) ? path1 : path2;
    }

    /**
     * Returns the path of the HTML file containing the unit test report.
     *
     * @param projectPath       The project's path.
     * @param projectName       The project's name.
     * @param reportUnavailable The indicator to trace and report back to the user when the reports are not available.
     *
     * @return The path of the HTML file containing the unit test report.
     */
    public static Path getMavenUTReportPath(String projectPath, String projectName, boolean reportUnavailable) {
        Path path1 = Paths.get(projectPath, "target", "reports", "surefire.html");
        Path path2 = Paths.get(projectPath, "target", "site", "surefire-report.html");
        boolean path1Exists = path1.toFile().exists();
        boolean path2Exists = path2.toFile().exists();

        if (!path1Exists && !path2Exists) {
            if (reportUnavailable) {
                String msg = "No unit test results were found for project " + projectName + ". Select \""
                             + DashboardView.APP_MENU_ACTION_RUN_TESTS + "\" before you select \""
                             + DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT + "\" on the menu.";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, msg + " No-op. Paths checked: " + path1 + ", " + path2);
                }
                ErrorHandler.processErrorMessage(Messages.getMessage("mvn_unit_test_report_none_found", projectName,
                                                                     DashboardView.APP_MENU_ACTION_RUN_TESTS, DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT),
                                                 true);
            }

            return null;
        }

        return path1Exists ? path1 : path2;
    }

    /**
     * Returns the path of the HTML file containing the test report.
     *
     * @param projectPath The project's path.
     *
     * @return The custom path of the HTML file containing the test report, or the default location.
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
     * Returns the path to the liberty-plugin-config.xml containing Liberty server data.
     * 
     * @param project The project is either the parent or a project that has a Liberty configuration.
     * 
     * @return The path to the liberty-plugin-config.xml containing Liberty server data.
     * 
     * @throws Exception
     */
    public Path getLibertyPluginConfigXmlPath(ProjectModel project) throws Exception {

        ProjectModel serverProj = getLibertyServerProject(project);
        String buildDir = serverProj.getBuildType() == BuildType.Gradle ? "build" : "target";

        Path path = Paths.get(serverProj.getPath(), buildDir, "liberty-plugin-config.xml");
        return path;
    }

    /**
     * Returns the liberty server module project associated with the input project.
     * 
     * @param project The project is either the parent or a project that has a Liberty configuration
     *                    since this is invoked from the dashboard.
     * 
     * @return The liberty server module project associated with the input project.
     * 
     * @throws Exception
     */
    private ProjectModel getLibertyServerProject(ProjectModel project) throws Exception {
        if (project.isParentOfServerModule()) {
            List<ProjectModel> mmps = project.getChildLibertyServerProjects();
            switch (mmps.size()) {
                case 0:
                    throw new Exception(Messages.getMessage("child_project_not_found"));
                case 1:
                    return mmps.get(0);
                default:
                    throw new Exception(Messages.getMessage("multiple_child_projects_found"));
            }
        }

        return project;
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
                if (firstElement instanceof ProjectModel) {
                    ProjectModel project = (ProjectModel) firstElement;
                    iProject = project.getIProject();
                }
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, iProject);
        }

        return iProject;
    }

    /**
     * Returns the dashboard view instance.
     *
     * @return The dashboard view instance.
     */
    public DashboardView getDashboardView() {
        return dashboardView;
    }

    /**
     * Sets the dashboard view instance.
     *
     * @param dashboardView The dashboard view instance to set.
     */
    public void setDashboardView(DashboardView dashboardView) {
        this.dashboardView = dashboardView;
    }

    /**
     * Returns true if the project is considered started. False, otherwise.
     *
     * @param projectModel The project model.
     *
     * @return True if the project is started. False, otherwise.
     */
    public boolean isProjectStarted(ProjectModel projectModel) {
        String projectName = projectModel.getName();

        // Check the process first 
        if (processController.isProcessStarted(projectName)) {
            return true;
        }

        // Check the cache state next to cover the window after a refresh where
        // newly created ProjectModel instances start as STOPPED regardless of whether dev
        // mode is still running. Only APP_RUNNING is checked here because that is the
        // steady state in which the application is fully up and running.
        return projectStateTable.get(projectName) == ProjectModel.AppState.APP_RUNNING;
    }

    /**
     * Reserves a unique OS-assigned port for the Liberty debug listener of the named module.
     * Opens a ServerSocket on port 0 and keeps it open until the module's start() or
     * startInContainer() method consumes the reservation. Keeping the socket open prevents
     * any other process from binding the same port in the gap between allocation and Liberty
     * server startup.
     *
     * Call this once per module before calling DebugUITools.launch, so that all port
     * reservations are made sequentially and no two modules receive the same port.
     *
     * @param projectName The name of the module for which the port is reserved.
     *
     * @throws IOException If no free port can be obtained from the OS.
     */
    public void reserveLibertyDebugPort(String projectName) throws IOException {
        ServerSocket socket = new ServerSocket(0);
        libertyDebugPortReservations.put(projectName, socket);
    }

    /**
     * Consumes and returns the Liberty debug port reserved for the named module, closing
     * the hold socket so that the OS port becomes available for Liberty to bind. If no
     * reservation exists for the module, returns null.
     *
     * @param projectName The name of the module whose reserved port is to be consumed.
     *
     * @return The reserved port as a string, or null if no reservation exists.
     */
    private String consumeLibertyDebugPortReservation(String projectName) {
        ServerSocket socket = libertyDebugPortReservations.remove(projectName);
        if (socket == null) {
            return null;
        }
        int port = socket.getLocalPort();
        try {
            socket.close();
        } catch (IOException e) {
            // Port was already closed or never bound; the port value is still valid.
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Failed to close Liberty debug port reservation socket for project " + projectName + ".", e);
            }
        }
        return String.valueOf(port);
    }

    /**
     * Restarts the Liberty server for the specified project.
     *
     * @param projectName The name of the project whose server should be restarted.
     */
    public void restartServer(String projectName) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, projectName);
        }

        String restartCommand = "r";
        try {
            processController.writeToProcessStream(projectName, restartCommand);
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, Messages.getMessage("restart_server_error", projectName), e);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectName);
        }
    }

    /**
     * Refreshes the dashboard view.
     *
     * @param reportError Whether to surface errors to the user in a dialog.
     */
    public void refreshDashboardView(boolean reportError) {
        if (dashboardView != null) {
            dashboardView.refreshDashboardView(workspaceModel, reportError);
            populateProjectStatesFromCache();
        }
    }

    /**
     * Records the project state in the state cache map.
     * When the state is STOPPED the entry is removed because STOPPED is the
     * default initial state of every newly constructed ProjectModel.
     *
     * @param projectName The name of the project whose state changed.
     * @param state       The new app state.
     */
    public void cacheAppState(String projectName, ProjectModel.AppState state) {
        if (state == ProjectModel.AppState.STOPPED) {
            projectStateTable.remove(projectName);
        } else {
            projectStateTable.put(projectName, state);
        }
    }

    /**
     * Populates the state of every project in the workspace model from the state cache map.
     * Called after the workspace model is rebuilt so that freshly created ProjectModel
     * instances start with the correct state rather than the default STOPPED. For each
     * project whose state is known and not STOPPED, the label is also refreshed so that
     * the dashboard icon repaints immediately.
     */
    private void populateProjectStatesFromCache() {
        for (Map.Entry<String, ProjectModel.AppState> entry : projectStateTable.entrySet()) {
            ProjectModel projectModel = workspaceModel.getProjectByName(entry.getKey());
            if (projectModel != null) {
                projectModel.setAppState(entry.getValue());
                refreshDashboardLabel(projectModel);
            }
        }
    }

    /**
     * Refreshes only the label (icon + text) for the specified project node in the dashboard
     * tree, without rebuilding the entire workspace model. Also refreshes the parent node if
     * the project is a child module, so that the aggregate icon stays up to date.
     *
     * <p>This method may be called from any thread; the UI update is dispatched via
     * {@code Display.getDefault().asyncExec}.</p>
     *
     * @param projectModel The project whose dashboard label should be repainted.
     */
    public void refreshDashboardLabel(ProjectModel projectModel) {
        if (dashboardView == null) {
            return;
        }
        Display.getDefault().asyncExec(() -> dashboardView.updateLabel(projectModel));
    }

    /**
     * Cancel running jobs and avoid error message, e.g. on closing Eclipse IDE
     */
    public void cancelRunningJobs() {
        // Cancel will remove job from 'runningJobs' Map
        runningJobs.keySet().forEach(j -> j.cancel());
    }

    /**
     * Resolves the target module or modules for a given action.
     *
     * For non-aggregator projects the project itself is returned as a single-element list
     * without showing any dialog, provided it is a Liberty server module.
     *
     * For aggregator projects with exactly one eligible child, that child is returned
     * directly as a single-element list without showing any dialog.
     *
     * For aggregator projects with multiple eligible children, a selection dialog is shown.
     * When multiSelect is true a checkbox dialog with Select All and Deselect All buttons
     * is shown, allowing the user to pick one or more modules. When multiSelect is false a
     * plain single-selection list is shown. The last confirmed multi-select choices for
     * each aggregator are remembered and pre-checked the next time the dialog opens.
     *
     * An empty return list means the user cancelled the dialog.
     *
     * @param projectModel        The project to resolve. May be an aggregator or a leaf module.
     * @param action              The action being performed, used for the dialog title and message.
     * @param expectedModuleState The state filter applied to candidate child modules.
     * @param multiSelect         True to allow selecting multiple modules. False to restrict
     *                                selection to one module.
     *
     * @return The list of resolved target projects. Empty if the user cancelled.
     *
     * @throws Exception If the project is not Liberty-configured or has no matching child modules.
     */
    public List<ProjectModel> resolveCommandTargets(ProjectModel projectModel, DashboardAction action,
                                                    ModuleStateFilter expectedModuleState, boolean multiSelect) throws Exception {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { projectModel, action, expectedModuleState, multiSelect });
        }

        @SuppressWarnings("unchecked")
        final List<ProjectModel>[] result = new List[1];
        result[0] = Collections.emptyList();

        if (projectModel.getBuildConfigMetadata().isAggregator()) {
            List<ProjectModel> allLibertyChildren = workspaceModel.findLibertyDescendants(projectModel);

            // Filter the candidate list by the expected module state.
            // Start and Run Tests require INACTIVE or ACTIVE modules respectively.
            // Stop requires ACTIVE modules. Some actions pass ALL.
            final List<ProjectModel> childModules;
            if (expectedModuleState == ModuleStateFilter.ACTIVE) {
                List<ProjectModel> activeChildren = new ArrayList<>();
                for (ProjectModel child : allLibertyChildren) {
                    if (isProjectStarted(child)) {
                        activeChildren.add(child);
                    }
                }
                if (!allLibertyChildren.isEmpty() && activeChildren.isEmpty()) {
                    throw new Exception(Messages.getMessage("no_active_liberty_modules_found"));
                }
                childModules = activeChildren;
            } else if (expectedModuleState == ModuleStateFilter.INACTIVE) {
                List<ProjectModel> inactiveChildren = new ArrayList<>();
                for (ProjectModel child : allLibertyChildren) {
                    if (!isProjectStarted(child)) {
                        inactiveChildren.add(child);
                    }
                }
                if (!allLibertyChildren.isEmpty() && inactiveChildren.isEmpty()) {
                    throw new Exception(Messages.getMessage("no_inactive_liberty_modules_found"));
                }
                childModules = inactiveChildren;
            } else {
                childModules = allLibertyChildren;
            }

            // A parent with no Liberty children is an internal error: such a project should
            // not appear in the dashboard and the user should not be able to trigger actions on it.
            if (childModules.isEmpty()) {
                throw new Exception(Messages.getMessage("no_liberty_modules_found", projectModel.getName()));
            }

            // Sort candidates alphabetically by name to match the dashboard ordering.
            childModules.sort((p1, p2) -> p1.getName().compareTo(p2.getName()));

            // A single eligible child is used directly without showing any dialog.
            if (childModules.size() == 1) {
                result[0] = childModules;
                if (Trace.isEnabled()) {
                    Trace.getTracer().traceExit(Trace.TRACE_TOOLS, result[0]);
                }
                return result[0];
            }

            // Multiple eligible children: show the appropriate selection dialog.
            final String aggregatorName = projectModel.getName();
            Display.getDefault().syncExec(new Runnable() {
                @Override
                public void run() {
                    Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                    Display display = PlatformUI.getWorkbench().getDisplay();

                    final Image mavenImg = Utils.getImage(display, DashboardView.MAVEN_IMG_TAG_PATH);
                    final Image gradleImg = Utils.getImage(display, DashboardView.GRADLE_IMG_TAG_PATH);

                    try {
                        LabelProvider lp = new LabelProvider() {
                            @Override
                            public String getText(Object element) {
                                ProjectModel pm = (ProjectModel) element;
                                return pm.getName();
                            }

                            @Override
                            public Image getImage(Object element) {
                                ProjectModel pm = (ProjectModel) element;
                                return pm.getBuildType() == BuildType.Maven ? mavenImg : gradleImg;
                            }
                        };

                        String actionName = getTranslatedActionCommand(action);
                        String dialogTitle;
                        String dialogMessage;

                        if (multiSelect) {
                            dialogTitle = Messages.getMessage("select_modules_title", actionName);
                            dialogMessage = Messages.getMessage("select_modules_description");
                        } else {
                            dialogTitle = Messages.getMessage("select_module_title", actionName);
                            dialogMessage = Messages.getMessage("select_module_description");
                        }

                        // Retrieve the saved selections for this aggregator so that previously
                        // chosen modules are pre-checked when the multi-select dialog reopens.
                        List<String> savedNames = multiSelect ? lastSelections.getOrDefault(aggregatorName, Collections.emptyList()) : Collections.emptyList();

                        ModuleSelectionDialog dialog = new ModuleSelectionDialog(shell, dialogTitle, dialogMessage, childModules, lp, multiSelect, savedNames);

                        if (dialog.open() == Window.OK) {
                            result[0] = dialog.getResult();
                            // Save confirmed names so they can be pre-checked next time.
                            if (multiSelect && !result[0].isEmpty()) {
                                List<String> confirmedNames = new ArrayList<>();
                                for (ProjectModel pm : result[0]) {
                                    confirmedNames.add(pm.getName());
                                }
                                lastSelections.put(aggregatorName, confirmedNames);
                            }
                        }
                    } finally {
                        if (mavenImg != null && !mavenImg.isDisposed()) {
                            mavenImg.dispose();
                        }

                        if (gradleImg != null && !gradleImg.isDisposed()) {
                            gradleImg.dispose();
                        }
                    }
                }
            });

        } else {
            // Non-aggregator: the project itself is the target if it is Liberty-enabled.
            if (projectModel.isLibertyServerModule()) {
                result[0] = Collections.singletonList(projectModel);
            } else {
                throw new Exception(Messages.getMessage("project_not_liberty_enabled", projectModel.getName()));
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, result[0]);
        }

        return result[0];
    }

    /**
     * Resolves the target project for viewing test reports.
     * Filters by project dependencies (declared in build config) that have tests,
     * rather than all descendants in the directory hierarchy.
     *
     * @param projectModel The project to resolve.
     * @param action       The dashboard action identifying the type of test report.
     *
     * @return The target project to view test reports for, or null if user cancelled.
     *
     * @throws Exception If no projects with test reports are found.
     */
    public ProjectModel resolveTestReportTarget(ProjectModel projectModel, DashboardAction action) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { projectModel, action });
        }

        final ProjectModel[] targetProject = new ProjectModel[1];

        // Get dependent projects (declared in build config) with test source files.
        List<ProjectModel> dependentsWithTests = getDependentProjectsWithTestReports(projectModel, action);

        // If no dependents have test reports, check whether the selected project itself has one.
        // If neither the project nor any of its dependents have test reports, report the error.
        if (dependentsWithTests.isEmpty()) {
            if (action == DashboardAction.OPEN_MVN_IT_TEST_REPORT) {
                if (getMavenITReportPath(projectModel.getPath(), projectModel.getName(), false) != null) {
                    targetProject[0] = projectModel;
                }
            } else if (action == DashboardAction.OPEN_MVN_UT_TEST_REPORT) {
                if (getMavenUTReportPath(projectModel.getPath(), projectModel.getName(), false) != null) {
                    targetProject[0] = projectModel;
                }
            } else if (action == DashboardAction.OPEN_GRADLE_TEST_REPORT) {
                if (getGradleTestReportPath(projectModel.getPath()).toFile().exists()) {
                    targetProject[0] = projectModel;
                }
            }

            if (targetProject[0] == null) {
                String msg = Messages.getMessage("no_test_reports_found", projectModel.getName());
                throw new Exception(msg);
            }
        } else {
            // At least one dependent has a test report. Also add the selected project itself if applicable.
            List<ProjectModel> projectsToDisplay = new ArrayList<ProjectModel>(dependentsWithTests);

            if (action == DashboardAction.OPEN_MVN_IT_TEST_REPORT) {
                if (getMavenITReportPath(projectModel.getPath(), projectModel.getName(), false) != null) {
                    projectsToDisplay.add(projectModel);
                }
            } else if (action == DashboardAction.OPEN_MVN_UT_TEST_REPORT) {
                if (getMavenUTReportPath(projectModel.getPath(), projectModel.getName(), false) != null) {
                    projectsToDisplay.add(projectModel);
                }
            } else if (action == DashboardAction.OPEN_GRADLE_TEST_REPORT) {
                if (getGradleTestReportPath(projectModel.getPath()).toFile().exists()) {
                    projectsToDisplay.add(projectModel);
                }
            }

            // Sort candidates alphabetically by name to match the dashboard ordering.
            projectsToDisplay.sort((p1, p2) -> p1.getName().compareTo(p2.getName()));

            // If only one module has a test report, use it directly without prompting the user.
            if (projectsToDisplay.size() == 1) {
                targetProject[0] = projectsToDisplay.get(0);
            } else {
                Display.getDefault().syncExec(new Runnable() {
                    @Override
                    public void run() {
                        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                        Display display = PlatformUI.getWorkbench().getDisplay();

                        final Image mavenImg = Utils.getImage(display, DashboardView.MAVEN_IMG_TAG_PATH);
                        final Image gradleImg = Utils.getImage(display, DashboardView.GRADLE_IMG_TAG_PATH);

                        try {
                            String actionCmdName = getTranslatedActionCommand(action);
                            String dialogTitle = Messages.getMessage("select_module_for_test_report_title", actionCmdName);
                            String dialogMessage = Messages.getMessage("select_module_for_test_report_description");

                            ModuleSelectionDialog dialog = new ModuleSelectionDialog(shell, dialogTitle, dialogMessage, projectsToDisplay, new LabelProvider() {
                                @Override
                                public String getText(Object element) {
                                    ProjectModel pm = (ProjectModel) element;
                                    return pm.getName();
                                }

                                @Override
                                public Image getImage(Object element) {
                                    ProjectModel pm = (ProjectModel) element;
                                    return pm.getBuildType() == BuildType.Maven ? mavenImg : gradleImg;
                                }
                            }, false);

                            if (dialog.open() == Window.OK) {
                                targetProject[0] = dialog.getFirstResult();
                            }
                        } finally {
                            if (mavenImg != null && !mavenImg.isDisposed()) {
                                mavenImg.dispose();
                            }
                            if (gradleImg != null && !gradleImg.isDisposed()) {
                                gradleImg.dispose();
                            }
                        }
                    }
                });

                // If the user exited the dialog, return null to signal cancellation.
                if (targetProject[0] == null) {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "Null, possible selection exit. No target selected.");
                    }
                    return null;
                }
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, targetProject[0]);
        }

        return targetProject[0];
    }

    /**
     * Returns the display name string for the given dashboard action.
     *
     * @param action The dashboard action whose display name is needed.
     *
     * @return The display name string for the given dashboard action.
     */
    private String getTranslatedActionCommand(DashboardAction action) {
        String msg = switch (action) {
            case START -> Messages.getMessage("dashboard_action_start");
            case START_CFG -> Messages.getMessage("dashboard_action_start_config");
            case START_CTR -> Messages.getMessage("dashboard_action_start_in_container");
            case DEBUG -> Messages.getMessage("dashboard_action_debug");
            case DEBUG_CFG -> Messages.getMessage("dashboard_action_debug_config");
            case DEBUG_CTR -> Messages.getMessage("dashboard_action_debug_in_container");
            case STOP -> Messages.getMessage("dashboard_action_stop");
            case RUN_TESTS -> Messages.getMessage("dashboard_action_run_tests");
            case OPEN_MVN_IT_TEST_REPORT -> Messages.getMessage("dashboard_action_view_mvn_it_report");
            case OPEN_MVN_UT_TEST_REPORT -> Messages.getMessage("dashboard_action_view_mvn_ut_report");
            case OPEN_GRADLE_TEST_REPORT -> Messages.getMessage("dashboard_action_view_gradle_test_report");
        };

        return msg;
    }

    /**
     * Returns the list of direct and transitive dependent modules (within the same
     * multi-module build) that have test reports present on disk.
     *
     * @param projectModel The project whose transitive dependents are inspected.
     * @param action       The dashboard action identifying the type of test report.
     *
     * @return The list of dependent modules that have test reports on disk.
     */
    public List<ProjectModel> getDependentProjectsWithTestReports(ProjectModel projectModel, DashboardAction action) {
        List<ProjectModel> projectsWithTests = new ArrayList<>();

        for (ProjectModel dependency : projectModel.getTransitiveDependentModules()) {
            if (action == DashboardAction.OPEN_MVN_IT_TEST_REPORT) {
                if (getMavenITReportPath(dependency.getPath(), dependency.getName(), false) != null) {
                    projectsWithTests.add(dependency);
                }
            } else if (action == DashboardAction.OPEN_MVN_UT_TEST_REPORT) {
                if (getMavenUTReportPath(dependency.getPath(), dependency.getName(), false) != null) {
                    projectsWithTests.add(dependency);
                }
            } else if (action == DashboardAction.OPEN_GRADLE_TEST_REPORT) {
                if (getGradleTestReportPath(dependency.getPath()).toFile().exists()) {
                    projectsWithTests.add(dependency);
                }
            }
        }

        return projectsWithTests;
    }

    /**
     * Executes a command in the specified project directory.
     *
     * @param fullCommand The full command string to execute.
     * @param projectPath The path to the project directory where the command should be executed.
     *
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the process is interrupted.
     */
    public void executeCommand(String fullCommand, String projectPath) throws IOException, InterruptedException {
        // Split the full command into individual arguments.
        List<String> command = Arrays.asList(fullCommand.trim().split("\\s+"));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(new File(projectPath)); // Set working directory.

        Process process = builder.start();
        process.waitFor();
    }

    /**
     * Determines whether all, none, or only some of the Liberty modules associated with
     * the given project are currently active.
     *
     * @param projectModel The project to evaluate.
     * 
     * @return The aggregated state for the input project.
     */
    public ProjectAggregatedState computeProjectAggregateState(ProjectModel projectModel) {
        List<ProjectModel> modules;

        if (projectModel.getBuildConfigMetadata() != null && projectModel.getBuildConfigMetadata().isAggregator()) {
            modules = workspaceModel.findLibertyDescendants(projectModel);
        } else {
            modules = new ArrayList<>();
            modules.add(projectModel);
        }

        int activeCount = 0;
        for (ProjectModel module : modules) {
            if (isProjectStarted(module)) {
                activeCount++;
            }
        }

        if (activeCount == 0) {
            return ProjectAggregatedState.INACTIVE;
        } else if (activeCount == modules.size()) {
            return ProjectAggregatedState.ACTIVE;
        } else {
            return ProjectAggregatedState.MIXED;
        }
    }
}
