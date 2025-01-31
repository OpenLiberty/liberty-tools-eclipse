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
package io.openliberty.tools.eclipse.process;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Manages the set up running dev mode processes.
 */
public class ProcessController {

    /** The set of processes associated with different application projects. */
    private static final ConcurrentHashMap<String, Process> projectProcessMap = new ConcurrentHashMap<String, Process>();

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
     * @param command The command to execute.
     * @param envs The environment properties to be set for the process.
     * 
     * @throws IOException
     */
    public Process runProcess(String projectName, String projectPath, String command, List<String> envs) throws IOException {

        List<String> commandList = Arrays.asList(command.split(" "));
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(commandList);
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

        return process;
    }

    /**
     * Writes the input data to the running process associated with the input project name.
     *
     * @param projectName The application project name.
     * @param content The data to write.
     *
     * @throws Exception
     */
    public void writeToProcessStream(String projectName, String data) throws Exception {
        Process process = projectProcessMap.get(projectName);

        if (process == null) {
            String msg = "Unable to write to the process associated with project " + projectName
                    + ". Internal process object not found.";
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
     * @param projectName - The name of the project to clean up.
     */
    public void cleanup(String projectName) {
        projectProcessMap.remove(projectName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Class: ").append(instance.getClass().getName()).append(": ");
        sb.append("projectProcessMap size: ").append(projectProcessMap.size()).append(", ");
        sb.append("projectProcessMap: ").append(projectProcessMap);
        return sb.toString();
    }
}
