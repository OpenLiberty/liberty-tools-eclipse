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
package io.openliberty.tools.eclipse.ui.terminal;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Represents a console tab item within the console view associated with a running application project.
 */
public class ProjectTab {

    /** The name of the application project associated with this terminal. */
    private String projectName;

    /** The process running in the console */
    private Process process;

    /**
     * Constructor.
     *
     * @param projectName The application project name.
     */
    public ProjectTab(String projectName) {
        this.projectName = projectName;
    }

    /**
     * Launches a terminal and runs the input command.
     *
     * @param projectPath The application project path.
     * @param command The command to run on the terminal.
     * @param envs The list of environment properties to be set on the terminal.
     * 
     * @throws IOException
     */
    public Process runCommand(String projectPath, String command, List<String> envs) throws IOException {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectPath, command, envs });
        }

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

        process = builder.start();

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }

        return process;
    }

    /**
     * Writes to the process's output stream.
     *
     * @param content The String to be written to the process.
     *
     * @throws Exception
     */
    public void writeToProcess(String content) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { new String(content) });
        }
        if (process != null) {
            PrintWriter writer = new PrintWriter(process.getOutputStream());
            writer.println(content);
            writer.flush();
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    public boolean isStarted() {
        return process != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Class: ").append(this.getClass().getName()).append(": ");
        sb.append("projectName: ").append(projectName).append(", ");
        return sb.toString();
    }
}
