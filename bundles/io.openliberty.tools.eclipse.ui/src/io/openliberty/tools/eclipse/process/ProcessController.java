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
package io.openliberty.tools.eclipse.process;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Manages the set of running dev mode processes.
 */
public class ProcessController {

    /** The set of processes associated with different application projects. */
    private static final ConcurrentHashMap<String, Process> projectProcessMap = new ConcurrentHashMap<String, Process>();

    /** The set of console output interceptors keyed by project path. */
    private static final ConcurrentHashMap<String, ConsoleOutputInterceptor> interceptorMap = new ConcurrentHashMap<String, ConsoleOutputInterceptor>();

    /** Instance of this class */
    private static ProcessController instance;

    /**
     * Constructor.
     */
    private ProcessController() {
    }

    /**
     * Returns a singleton instance of this class.
     *
     * @return A singleton instance of this class.
     */
    public static ProcessController getInstance() {
        if (instance == null) {
            instance = new ProcessController();
        }

        return instance;
    }

    /**
     * Runs the specified command as a system process.
     *
     * @param projectName The application project name.
     * @param projectPath The application project path.
     * @param command     The command to execute.
     * @param envs        The environment properties to be set for the process.
     * @param printCmd    Whether to echo the command to the console before running it.
     * @param launch      The Eclipse launch object used to register the process in the debug framework.
     *
     * @throws IOException If an error occurs while starting the process.
     */
    public void runProcess(String projectName, String projectPath, String command, List<String> envs, boolean printCmd, ILaunch launch) throws IOException {

        List<String> commandList = new ArrayList<String>();

        // Add exec statements and print commands
        if (Utils.isWindows()) {
            commandList.add("cmd.exe");
            commandList.add("/c");
            if (printCmd) {
                StringBuilder sb = new StringBuilder();
                sb.append("echo && ");
                sb.append("echo Liberty Tools running command: " + command);
                sb.append(" from directory: " + projectPath + " && ");
                sb.append(command);
                command = sb.toString();
            }

        } else {
            commandList.add("/bin/sh");
            if (printCmd) {
                commandList.add("-xc");
            } else {
                commandList.add("-c");
            }
        }

        commandList.add(command);

        ProcessBuilder builder = new ProcessBuilder(commandList);

        builder.directory(new File(projectPath));

        // Add environment variables
        Map<String, String> environment = builder.environment();

        for (String env : envs) {
            String[] keyValues = env.split("=");
            String key = keyValues[0];
            String value = keyValues[1];
            environment.put(key, value);
        }

        Process process = builder.start();

        projectProcessMap.put(projectName, process);

        // Register a termination listener with the Debug plugin framework.
        addTerminateListener(projectName);

        // Launch the Java process as part of the Debug plugin framework, which wraps
        // the raw Java process and monitors it.
        // Cleanup for the initiated process is done by the registered listener.
        IProcess iProcess = DebugPlugin.newProcess(launch, process, projectName);

        // Create a console output interceptor and register it on both the stdout and
        // stderr monitors so that handlers can react to messages on either stream.
        ConsoleOutputInterceptor interceptor = new ConsoleOutputInterceptor(projectName);
        IStreamMonitor outMonitor = iProcess.getStreamsProxy().getOutputStreamMonitor();
        IStreamMonitor errMonitor = iProcess.getStreamsProxy().getErrorStreamMonitor();
        outMonitor.addListener(interceptor);
        errMonitor.addListener(interceptor);
        interceptorMap.put(projectPath, interceptor);
    }

    /**
     * Returns the ConsoleOutputInterceptor registered for the specified project path, or null
     * if no process is currently running for that project.
     *
     * @param projectPath The file system path of the project.
     * @return The ConsoleOutputInterceptor for the project, or null if none exists.
     */
    public ConsoleOutputInterceptor getInterceptor(String projectPath) {
        return interceptorMap.get(projectPath);
    }

    private void addTerminateListener(String projectName) {
        DebugPlugin.getDefault().addDebugEventListener(new LibertyDebugEventListener(projectName));
    }

    /**
     * Writes the input data to the running process associated with the input project name.
     *
     * @param projectName The application project name.
     * @param content     The data to write.
     *
     * @throws Exception
     */
    public void writeToProcessStream(String projectName, String data) throws Exception {
        Process process = projectProcessMap.get(projectName);

        if (process == null) {
            String msg = Messages.getMessage("process_write_error", projectName);
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg + ". Data to write: " + new String(data));
            }
            throw new Exception(msg);
        }

        PrintWriter writer = new PrintWriter(process.getOutputStream());
        writer.println(data);
        writer.flush();
    }

    /**
     * Returns true if there is a process associated with this project and the
     * process is alive.
     * 
     * @param projectName - The name of the project to check.
     * 
     * @return True if the process is alive. False otherwise.
     */
    public boolean isProcessStarted(String projectName) {
        Process process = projectProcessMap.get(projectName);
        if (process != null) {
            return process.isAlive();
        }

        return false;
    }

    /**
     * Cleans up any objects associated with this project.
     *
     * @param projectName The name of the project to clean up.
     * @param projectPath The file system path of the project.
     */
    public void cleanup(String projectName, String projectPath) {
        projectProcessMap.remove(projectName);
        if (projectPath != null) {
            ConsoleOutputInterceptor interceptor = interceptorMap.remove(projectPath);
            if (interceptor != null) {
                interceptor.flush();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Class: ").append(instance.getClass().getName()).append(": ");
        sb.append("projectProcessMap size: ").append(projectProcessMap.size()).append(", ");
        sb.append("projectProcessMap: ").append(projectProcessMap).append(", ");
        sb.append("interceptorMap size: ").append(interceptorMap.size()).append(", ");
        sb.append("interceptorMap: ").append(interceptorMap);
        return sb.toString();
    }
}
