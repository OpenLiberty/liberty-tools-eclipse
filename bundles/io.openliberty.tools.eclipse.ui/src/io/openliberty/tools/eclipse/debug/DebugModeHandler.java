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
package io.openliberty.tools.eclipse.debug;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdi.Bootstrap;
import org.eclipse.jdi.TimeoutException;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.Project.BuildType;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
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
            throw new Exception("Unexpected project build type: " + buildType + ". Project " + project.getIProject().getName()
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
     * @param launch The launch to which the debug target will be added.
     * @param debugPort The debug port to use to attach the debugger to.
     * 
     * @throws Exception
     */
    public void startDebugAttacher(Project project, ILaunch launch, String port) {
        String projectName = project.getIProject().getName();

        Job job = new Job("Attaching Debugger to JVM...") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    if (monitor.isCanceled()) {
                        return Status.CANCEL_STATUS;
                    }

                    String debugPort = null;

                    if (port == null) {
                        // Read debug port from server.env. Retry every 3 seconds for 1 minute.
                        int retries = 20;
                        final Exception[] ex = new Exception[1];
                        while (debugPort == null && retries > 0) {
                            // Remove any exceptions we got from the last retry
                            ex[0] = null;

                            try {
                                Path serverEnvPath = getServerEnvFile(project);
                                if (serverEnvPath != null) {
                                    debugPort = readDebugPortFromServerEnv(serverEnvPath);
                                }
                            } catch (Exception e) {
                                // Save this for now until we are done with our retries;
                                ex[0] = new Exception(e);
                            }

                            if (debugPort == null) {
                                Thread.sleep(3000);
                                retries--;
                            }
                        }
                        if (debugPort == null) {
                            // We failed to read the debug port. Throw an exception. This will be caught by the outer
                            // catch block and the job will return with an error.
                            String errorMessage = "Failed to read debug port from server.env file";
                            if (ex[0] != null) {
                                // Add the last exception we got.
                                throw new Exception(errorMessage, ex[0]);
                            } else {
                                throw new Exception(errorMessage);
                            }
                        }
                    } else {
                        debugPort = port;
                    }

                    String portToConnect = waitForSocketActivation(project, DEFAULT_ATTACH_HOST, debugPort, monitor);
                    if (portToConnect == null) {
                        return Status.CANCEL_STATUS;
                    }

                    AttachingConnector connector = getAttachingConnector();
                    Map<String, Argument> map = connector.defaultArguments();
                    configureConnector(map, DEFAULT_ATTACH_HOST, Integer.parseInt(portToConnect));
                    IDebugTarget debugTarget = createRemoteJDTDebugTarget(launch, Integer.parseInt(portToConnect),
                            DEFAULT_ATTACH_HOST,
                            connector, map);

                    launch.addDebugTarget(debugTarget);

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
                devModeOps.unregisterTerminalListener(projectName, terminalListener);
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

    private AttachingConnector getAttachingConnector() {
        List<?> connectors = Bootstrap.virtualMachineManager().attachingConnectors();
        for (int i = 0; i < connectors.size(); i++) {
            AttachingConnector c = (AttachingConnector) connectors.get(i);
            if ("com.sun.jdi.SocketAttach".equals(c.name()))
                return c;
        }

        return null;
    }

    /**
     * Configure the connector properties.
     *
     * @param map argument map
     * @param host the host name or IP address
     * @param portNumber the port number
     */
    private void configureConnector(Map<String, Argument> map, String host, int portNumber) {
        Connector.StringArgument hostArg = (Connector.StringArgument) map.get("hostname");
        hostArg.setValue(host);

        Connector.IntegerArgument portArg = (Connector.IntegerArgument) map.get("port");
        portArg.setValue(portNumber);

        // This timeout value is directly configurable from the "Launch timeout" in Eclipse Preferences.
        // At the point when we connect, we already confirmed that we are able to connect to the socket,
        // so this may not come into play. However, we are keeping it here just in case.
        Connector.IntegerArgument timeoutArg = (Connector.IntegerArgument) map.get("timeout");
        if (timeoutArg != null) {
            int timeout = Platform.getPreferencesService().getInt(
                    "org.eclipse.jdt.launching",
                    JavaRuntime.PREF_CONNECT_TIMEOUT,
                    JavaRuntime.DEF_CONNECT_TIMEOUT,
                    null);
            timeoutArg.setValue(timeout);
        }
    }

    private IDebugTarget createRemoteJDTDebugTarget(ILaunch launch, int remoteDebugPortNum, String hostName,
            AttachingConnector connector, Map<String, Argument> map) throws CoreException {
        if (launch == null || hostName == null || hostName.length() == 0) {
            return null;
        }
        VirtualMachine remoteVM = null;
        Exception ex = null;
        try {
            remoteVM = attachJVM(hostName, remoteDebugPortNum, connector, map);
        } catch (Exception e) {
            ex = e;
        }
        if (remoteVM == null) {
            throw new CoreException(
                    new Status(IStatus.ERROR, this.getClass(), IJavaLaunchConfigurationConstants.ERR_CONNECTION_FAILED, "", ex));
        }
        LibertyDebugTarget libertyDebugTarget = new LibertyDebugTarget(launch, remoteVM, hostName + ":" + remoteDebugPortNum);

        // Add hot code replace listener to listen for hot code replace failure.
        libertyDebugTarget.addHotCodeReplaceListener(new LibertyHotCodeReplaceListener());

        return libertyDebugTarget;
    }

    /**
     * Connect the debugger to the debug port of the target VM
     * 
     * @param hostName
     * @param port
     * @param connector
     * @param map
     * 
     * @return
     */
    private VirtualMachine attachJVM(String hostName, int port, AttachingConnector connector, Map<String, Argument> map) {

        // At this point, the "waitForSocketActivation" method has already been called so we know that we are able
        // to connect to the Liberty server's debug port. However, there is still a gap here where we can get a
        // connection refused exception when we attempt to attach the debugger. This could potentially be due to a difference in
        // how we are writing to the socket, but whatever the reason, we have a timeout mechanism here as well to attempt the
        // connection every 100ms for 10 seconds. Any IOExceptions like a "connection refused" will trigger a retry. Any timeout
        // exceptions within the connector itself will not trigger a retry. We will catch that in the calling method and
        // expose the timeout there. If we exhaust out retries we will return null and the calling method will throw a connection
        // error.

        VirtualMachine vm = null;
        int timeOut = 10000;
        try {
            try {
                vm = connector.attach(map);
            } catch (IOException e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI,
                            "Error occured while trying to connect to the remote virtual machine " + e.getMessage(), e);
                }
            } catch (TimeoutException e2) {
                // do nothing
            }

            while (vm == null && timeOut > 0) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    // do nothing
                }
                timeOut = timeOut - 100;
                try {
                    vm = connector.attach(map);
                } catch (IOException e) {
                    // do nothing
                }
            }
        } catch (IllegalConnectorArgumentsException e) {
            // Do nothing, return vm as null if it fails
        }
        return vm;
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
     * Returns the path of the server.env file after Liberty server deployment.
     * 
     * @param project The project for which this operations is being performed.
     * 
     * @return The path of the server.env file after Liberty server deployment.
     * 
     * @throws Exception
     */
    private Path getServerEnvFile(Project project) throws Exception {

        Project serverProj = getLibertyServerProject(project);
        String projectPath = serverProj.getPath();

        Path libertyPluginConfigXmlPath = devModeOps.getLibertyPluginConfigXmlPath(projectPath);

        // Read server.env path from liberty-plugin-config.xml

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document doc = db.parse(libertyPluginConfigXmlPath.toFile());
        doc.getDocumentElement().normalize();

        NodeList list = doc.getElementsByTagName("serverDirectory");
        Path serverEnvPath = Paths.get(list.item(0).getTextContent(), "server.env");

        // Make sure the server.env path exists. If not return null.
        if (!Files.exists(serverEnvPath)) {
            return null;
        }

        return serverEnvPath;

    }

    /**
     * Returns the port value associated with the WLP_DEBUG_ADDRESS entry in server.env. Null if not found. If there are multiple
     * WLP_DEBUG_ADDRESS entries, the last entry is returned.
     * 
     * @param serverEnv The server.env Path object.
     * 
     * @return Returns the port value associated with the WLP_DEBUG_ADDRESS entry in server.env. Null if not found. If there are
     *         multiple WLP_DEBUG_ADDRESS entries, the last entry is returned.
     * 
     * @throws Exception
     */
    public String readDebugPortFromServerEnv(Path serverEnv) throws Exception {
        String port = null;

        if (Files.exists(serverEnv)) {
            String lastEntry = null;

            Scanner scan = new Scanner(serverEnv);
            String line;

            while (scan.hasNextLine()) {
                line = scan.nextLine();
                if (line.contains(WLP_ENV_DEBUG_ADDRESS)) {
                    lastEntry = line;
                }
            }

            scan.close();

            if (lastEntry != null) {
                String[] parts = lastEntry.split("=");
                port = parts[1].trim();
            }

        }

        return port;
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

        // This is the first of several timeout mechanisms during the debugger connection. This method
        // will attempt a write to the debug port of the target VM (Liberty server) once every second for
        // 3 minutes. This seems like a reasonable amount of time for the debug port to become available
        // so for now we are not making this timeout configurable. If in the future, we need to expose
        // this timeout value, we could potentially tie it to the "Launch timeout" setting of in the
        // Eclipse "Debug" preferences.

        byte[] handshakeString = "JDWP-Handshake".getBytes(StandardCharsets.US_ASCII);
        int retryLimit = 180;
        int envReadInterval = 2;

        for (int retryCount = 0; retryCount < retryLimit; retryCount++) {

            // Check if the job was cancelled.
            if (monitor.isCanceled()) {
                return null;
            }

            // Check if the terminal was marked as closed, but to reduce contention on the UI thread,
            // not every time through the loop. We don't have a clean callback/notification that the
            // terminal session has been marked closed; we're actually going to read the UI element text
            if (retryCount % envReadInterval == 0) {
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
            }

            try (Socket socket = new Socket(host, Integer.valueOf(port))) {
                socket.getOutputStream().write(handshakeString);
                return port;
            } catch (ConnectException ce) {
                TimeUnit.SECONDS.sleep(1);
            }
        }

        throw new Exception("Timed out trying to attach the debugger to JVM on host: " + host + " and port: " + port
                + ".  If the server starts later you might try to manually create a Remote Java Application debug configuration and attach to the server.  You can confirm the debug port used in the terminal output looking for a message like  'Liberty debug port: [ 63624 ]'.");
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

    private class DataHolder {
        boolean closed;
    }
}
