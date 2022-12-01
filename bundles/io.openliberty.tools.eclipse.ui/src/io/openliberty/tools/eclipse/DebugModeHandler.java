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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.Project.BuildType;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationHelper;
import io.openliberty.tools.eclipse.ui.launch.StartTab;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTabController;
import io.openliberty.tools.eclipse.ui.terminal.TerminalListener;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

public class DebugModeHandler {

    /** Default host name. */
    public static String DEFAULT_ATTACH_HOST = "localhost";

    /** Maven: Dev mode debug port argument key. */
    public static String MAVEN_DEVMODE_DEBUG_PORT_PARM = "-DdebugPort";

    /** Gradle: Dev mode debug port argument key. */
    public static String GRADLE_DEVMODE_DEBUG_PORT_PARM = "--libertyDebugPort";

    /** WLP_DEBUG_ADDRESS property key. */
    public static String WLP_ENV_DEBUG_ADDRESS = "WLP_DEBUG_ADDRESS";

    /** WLP server environment file name. */
    public static String WLP_SERVER_ENV_FILE_NAME = "server.env";

    /** WLP server environment file backup name. */
    public static String WLP_SERVER_ENV_BAK_FILE_NAME = "server.env.bak";

    /** Debug Perspective ID. */
    public static String DEBUG_PERSPECTIVE_ID = "org.eclipse.debug.ui.DebugPerspective";

    /** Job status return code indicating that an error took place while attempting to attach the debugger to the JVM. */
    public static int JOB_STATUS_DEBUGGER_CONN_ERROR = 1;

    /** Instance to this class. */
    private LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();

    /** DevModeOperations instance. */
    private DevModeOperations devModeOps;

    /**
     * Constructor.
     */
    public DebugModeHandler(DevModeOperations devModeOps) {
        this.devModeOps = devModeOps;
    }

    /**
     * Returns the input configuration parameters with the debug port argument appended.
     * 
     * @param project The project associated with this call.
     * @param debugPort The debug port to add to the config parameters.
     * @param configParms The input parameters from the Run configuration's dialog.
     * 
     * @return The input configuration parameters with the debug port argument appended.
     * 
     * @throws Exception
     */
    public String addDebugDataToStartParms(Project project, String debugPort, String configParms) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { project, debugPort, configParms });
        }

        String startParms = configParms;
        String addendum = null;

        if (debugPort != null && !debugPort.isEmpty()) {
            BuildType buildType = project.getBuildType();
            if (buildType == BuildType.MAVEN) {
                if (!configParms.contains(MAVEN_DEVMODE_DEBUG_PORT_PARM)) {
                    addendum = MAVEN_DEVMODE_DEBUG_PORT_PARM + "=" + debugPort;
                }
            } else if (buildType == BuildType.GRADLE) {
                if (!configParms.contains(GRADLE_DEVMODE_DEBUG_PORT_PARM)) {
                    addendum = GRADLE_DEVMODE_DEBUG_PORT_PARM + "=" + debugPort;
                }
            } else {
                throw new Exception("Unexpected project build type: " + buildType + ". Project" + project.getIProject().getName()
                        + "does not appear to be a Maven or Gradle built project.");
            }
        }

        if (addendum != null) {
            StringBuffer updatedParms = new StringBuffer(configParms);
            updatedParms.append((configParms.isEmpty()) ? "" : " ").append(addendum);
            startParms = updatedParms.toString();
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, new Object[] { project, startParms });
        }

        return startParms;
    }

    /**
     * Determines and returns the debug port to be used.
     * 
     * @param project The project
     * @param inputParms
     * 
     * @return The debug port to be used.
     */
    public String calculateDebugPort(Project project, String inputParms) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { project, inputParms });
        }

        String debugPort = null;

        // 1. Check if the debugPort was specified as part of start parameters. If so, use that port first.
        String searchKey = null;

        BuildType buildType = project.getBuildType();
        if (buildType == BuildType.MAVEN) {
            searchKey = MAVEN_DEVMODE_DEBUG_PORT_PARM;
        } else if (buildType == BuildType.GRADLE) {
            searchKey = GRADLE_DEVMODE_DEBUG_PORT_PARM;
        } else {
            throw new Exception("Unexpected project build type: " + buildType + ". Project" + project.getIProject().getName()
                    + "does not appear to be a Maven or Gradle built project.");
        }

        if (inputParms.contains(searchKey)) {
            String[] parts = inputParms.split("\\s+");
            for (String part : parts) {
                if (part.contains(searchKey)) {
                    String[] debugParts = part.split("=");
                    debugPort = debugParts[1].trim();
                    break;
                }
            }
        }

        // 2. Get a random port.
        if (debugPort == null) {
            try (ServerSocket socket = new ServerSocket(0)) {
                int randomPort = socket.getLocalPort();
                debugPort = String.valueOf(randomPort);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { project, debugPort });
        }

        return debugPort;

    }

    /**
     * Starts the job that will attempt to connect the debugger with the server's JVM.
     * 
     * @param project The project for which the debugger needs to be attached.
     * @param debugPort The debug port to use to attach the debugger to.
     * 
     * @throws Exception
     */
    public void startDebugAttacher(Project project, String debugPort) {
        String projectName = project.getIProject().getName();

        Job job = new Job("Attaching Debugger to JVM...") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    if (monitor.isCanceled()) {
                        return Status.CANCEL_STATUS;
                    }

                    String portToConnect = waitForSocketActivation(project, DEFAULT_ATTACH_HOST, debugPort, monitor);
                    if (portToConnect == null) {
                        return Status.CANCEL_STATUS;
                    }

                    createRemoteJavaAppDebugConfig(project, DEFAULT_ATTACH_HOST, portToConnect, monitor);

                } catch (Exception e) {
                    return new Status(IStatus.ERROR, LibertyDevPlugin.PLUGIN_ID, JOB_STATUS_DEBUGGER_CONN_ERROR,
                            "An error was detected while attaching the debugger to the JVM.", e);
                }

                return Status.OK_STATUS;
            }
        };

        // Register a listener with the terminal tab controller. This listener handles cleanup when the terminal or terminal tab is
        // terminated while actively processing work.
        TerminalListener terminalListener = new TerminalListener() {
            /**
             * {@inheritDoc}
             */
            @Override
            public void cleanup() {
                job.cancel();
            }
        };
        devModeOps.registerTerminalListener(project.getIProject().getName(), terminalListener);

        // Register a job change listener. This listener performs job completion processing.
        job.addJobChangeListener(new JobChangeAdapter() {
            @Override
            public void done(IJobChangeEvent event) {
                devModeOps.deregisterTerminalListener(projectName, terminalListener);
                IStatus result = event.getResult();
                IWorkbench workbench = PlatformUI.getWorkbench();
                Display display = workbench.getDisplay();

                if (result.isOK()) {
                    display.syncExec(new Runnable() {
                        public void run() {
                            openDebugPerspective();
                        }
                    });
                } else {
                    Throwable t = result.getException();

                    if (t != null) {
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_UI, t.getMessage(), t);
                        }

                        ErrorHandler.processErrorMessage(t.getMessage(), t, false);
                    }
                }
            }
        });

        job.schedule();
    }

    /**
     * Opens the debug perspective with the terminal and liberty dashboard views.
     */
    private void openDebugPerspective() {
        // Open the debug perspective.
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
        String currentId = window.getActivePage().getPerspective().getId();
        if (!DEBUG_PERSPECTIVE_ID.equals(currentId)) {
            try {
                workbench.showPerspective(DEBUG_PERSPECTIVE_ID, window);
            } catch (Exception e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI, e.getMessage(), e);
                }

                ErrorHandler.processErrorMessage(e.getMessage(), e, false);
                return;
            }
        }

        // Open the terminal view.
        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        try {
            IViewPart terminalView = activePage.findView(ProjectTabController.TERMINAL_VIEW_ID);
            if (terminalView == null) {
                activePage.showView(ProjectTabController.TERMINAL_VIEW_ID);
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, e.getMessage(), e);
            }

            ErrorHandler.processErrorMessage(e.getMessage(), e, false);
        }

        // Open the dashboard view.
        try {
            IViewPart dashboardView = activePage.findView(DashboardView.ID);
            if (dashboardView == null) {
                activePage.showView(DashboardView.ID);
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, e.getMessage(), e);
            }

            ErrorHandler.processErrorMessage(e.getMessage(), e, false);
        }
    }

    /**
     * Returns the default path of the server.env file after Liberty server deployment.
     * 
     * @param project The project for which this operations is being performed.
     * 
     * @return The default path of the server.env file after Liberty server deployment.
     * 
     * @throws Exception
     */
    private Path getServerEnvPath(Project project) throws Exception {
        String projectPath = project.getPath();
        String projectName = project.getName();
        Path basePath = null;
        BuildType buildType = project.getBuildType();
        if (buildType == Project.BuildType.MAVEN) {
            basePath = Paths.get(projectPath, "target", "liberty", "wlp", "usr", "servers");
        } else if (buildType == Project.BuildType.GRADLE) {
            basePath = Paths.get(projectPath, "build", "wlp", "usr", "servers");
        } else {
            throw new Exception("Unexpected project build type: " + buildType + ". Project" + projectName
                    + "does not appear to be a Maven or Gradle built project.");
        }

        // Make sure the base path exists. If not return null.
        File basePathFile = new File(basePath.toString());
        if (!basePathFile.exists()) {
            return null;
        }

        try (Stream<Path> matchedStream = Files.find(basePath, 2, (path, basicFileAttribute) -> {
            if (basicFileAttribute.isRegularFile()) {
                return path.getFileName().toString().equalsIgnoreCase(WLP_SERVER_ENV_FILE_NAME);
            }
            return false;
        });) {
            List<Path> matchedPaths = matchedStream.collect(Collectors.toList());
            int numberOfFilesFound = matchedPaths.size();

            if (numberOfFilesFound != 1) {
                if (numberOfFilesFound == 0) {
                    String msg = "Unable to find the server.env file for project " + projectName + ".";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg);
                    }
                    return null;
                } else {
                    String msg = "More than one server.env files were found for project " + projectName
                            + ". Unable to determine the server.env file to use.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg);
                    }
                    ErrorHandler.processErrorMessage(msg, false);
                    throw new Exception(msg);
                }
            }
            return matchedPaths.get(0);
        }
    }

    /**
     * Returns the port value associated with the WLP_DEBUG_ADDRESS entry in server.env. Null if not found. If there are multiple
     * WLP_DEBUG_ADDRESS entries, the last entry is returned.
     * 
     * @param serverEnv The server.env file object.
     * 
     * @return Returns the port value associated with the WLP_DEBUG_ADDRESS entry in server.env. Null if not found. If there are
     *         multiple WLP_DEBUG_ADDRESS entries, the last entry is returned.
     * 
     * @throws Exception
     */
    public String readDebugPortFromServerEnv(File serverEnv) throws Exception {
        String port = null;

        if (serverEnv.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(serverEnv))) {
                String line = null;
                String lastEntry = null;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(WLP_ENV_DEBUG_ADDRESS)) {
                        lastEntry = line;
                    }
                }
                if (lastEntry != null) {
                    String[] parts = lastEntry.split("=");
                    port = parts[1].trim();
                }
            }
        }

        return port;
    }

    /**
     * Returns a new Remote Java Application debug configuration.
     * 
     * @param configuration The configuration being processed.
     * @param host The JVM host to connect to.
     * @param port The JVM port to connect to.
     * @param monitor The progress monitor.
     * 
     * @return A new Remote Java Application debug configuration.
     * 
     * @throws Exception
     */
    private ILaunch createRemoteJavaAppDebugConfig(Project project, String host, String port, IProgressMonitor monitor) throws Exception {
        // Look for an existing config that contains the name of the project. If one is not found create a new config.
        String projectName = project.getIProject().getName();
        ILaunchManager iLaunchManager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType remoteJavaAppConfigType = iLaunchManager
                .getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_REMOTE_JAVA_APPLICATION);
        ILaunchConfiguration[] remoteJavaAppConfigs = iLaunchManager.getLaunchConfigurations(remoteJavaAppConfigType);
        ILaunchConfigurationWorkingCopy remoteJavaAppConfigWCopy = null;

        // There could be multiple entries that contain the project name and it may not be exactly equal to the
        // project name. Pick the last run configuration and update it.
        List<ILaunchConfiguration> projectAssociatedConfigs = new ArrayList<ILaunchConfiguration>();
        for (ILaunchConfiguration remoteJavaAppConfig : remoteJavaAppConfigs) {
            String savedProjectName = remoteJavaAppConfig.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, (String) null);
            if (projectName.equals(savedProjectName)) {
                projectAssociatedConfigs.add(remoteJavaAppConfig);
            }
        }

        if (projectAssociatedConfigs.size() > 0) {
            ILaunchConfiguration lastUsedConfig = launchConfigHelper.getLastRunConfiguration(projectAssociatedConfigs);
            remoteJavaAppConfigWCopy = lastUsedConfig.getWorkingCopy();
        }

        if (remoteJavaAppConfigWCopy == null) {
            String configName = launchConfigHelper.buildConfigurationName(projectName);

            remoteJavaAppConfigWCopy = remoteJavaAppConfigType.newInstance(null, configName);
            remoteJavaAppConfigWCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, projectName);
            remoteJavaAppConfigWCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_CONNECTOR,
                    IJavaLaunchConfigurationConstants.ID_SOCKET_ATTACH_VM_CONNECTOR);
            remoteJavaAppConfigWCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_ALLOW_TERMINATE, false);

        }

        Map<String, String> connectMap = new HashMap<>(2);
        connectMap.put("port", port);
        connectMap.put("hostname", host);
        remoteJavaAppConfigWCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, connectMap);
        remoteJavaAppConfigWCopy.setAttribute(StartTab.PROJECT_RUN_TIME, String.valueOf(System.currentTimeMillis()));

        remoteJavaAppConfigWCopy.doSave();

        return remoteJavaAppConfigWCopy.launch(ILaunchManager.DEBUG_MODE, monitor);
    }

    /**
     * Waits for the JDWP socket on the JVM to start listening for connections.
     * 
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @param monitor The progress monitor instance.
     * 
     * @returns The port that the debugger actually connected to.
     * 
     * @throws Exception
     */
    private String waitForSocketActivation(Project project, String host, String port, IProgressMonitor monitor) throws Exception {
        byte[] handshakeString = "JDWP-Handshake".getBytes(StandardCharsets.US_ASCII);
        int retryLimit = 75;
        int envReadMinLimit = 45;
        int envreadMaxLimit = 75;
        int envReadInterval = 5;

        // Retrieve the location of the server.env in the liberty installation at the default location (wpl/usr/servers/<serverName>).
        Path serverEnvPath = getServerEnvPath(project);

        for (int retryCount = 0; retryCount < retryLimit; retryCount++) {
            // Check if the job was cancelled.
            if (monitor.isCanceled()) {
                return null;
            }

            // Check if the terminal was marked as closed.
            IWorkbench workbench = PlatformUI.getWorkbench();
            Display display = workbench.getDisplay();
            DataHolder data = new DataHolder();

            display.syncExec(new Runnable() {
                public void run() {
                    boolean isClosed = devModeOps.isProjectTerminalTabMarkedClosed(project.getIProject().getName());
                    data.closed = isClosed;
                }
            });

            if (data.closed == true) {
                return null;
            }

            // The server.env path may not yet exist. If it is null, retry.
            if (serverEnvPath == null) {
                TimeUnit.SECONDS.sleep(1);
                serverEnvPath = getServerEnvPath(project);
                if (serverEnvPath == null) {
                    continue;
                }
            }

            // There is a small window in which the allocated random port could have been taken by another process.
            // Check the deployed server.env at the default deployment location (wlp/usr/servers/<serverName>) for the WLP_DEBUG_ADDRESS
            // property. If the port is already in use, dev mode will allocate a random debug port and reflect that by updating the
            // server.env file.
            if (retryCount >= envReadMinLimit && retryCount < envreadMaxLimit && (retryCount % envReadInterval == 0)) {
                // Look for the server.env.bak file before checking the server.env file.
                Path serverEnvBakPath = (serverEnvPath != null) ? serverEnvPath.resolveSibling(WLP_SERVER_ENV_BAK_FILE_NAME) : null;
                if (serverEnvBakPath != null && serverEnvBakPath.toFile().exists()) {
                    String envPort = readDebugPortFromServerEnv(serverEnvPath.toFile());
                    if (envPort != null) {
                        if (!envPort.equals(port)) {
                            port = envPort;
                        }
                    }
                }
            }

            try (Socket socket = new Socket(host, Integer.valueOf(port))) {
                socket.getOutputStream().write(handshakeString);
                return port;
            } catch (ConnectException ce) {
                TimeUnit.SECONDS.sleep(1);
            }
        }

        throw new Exception("Unable to automatically attach the debugger to JVM on host: " + host + " and port: " + port
                + ". If the debug connection timed out but the server did start successfully, you can still manually create a Remote Java Application debug configuration with the mentioned port and attach to the server.");
    }

    private class DataHolder {
        boolean closed;
    }
}
