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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.osgi.util.NLS;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

public class CommandBuilder {

    private String projectPath;

    private String pathEnv;

    private boolean isMaven;

    /**
     * @param pathEnv
     * @param isMaven true for Maven, false for Gradle
     */
    private CommandBuilder(String projectPath, String pathEnv, boolean isMaven) {
        super();
        this.projectPath = projectPath;
        this.pathEnv = pathEnv;
        this.isMaven = isMaven;
    }

    /**
     * Returns the full Maven command to run.
     *
     * @param projectPath The project's path.
     * @param cmdArgs The mvn command args
     * @param pathEnv The PATH env var
     *
     * @return The full Maven command to run.
     * 
     * @throws CommandNotFoundException
     */
    public static String getMavenCommandLine(String projectPath, String cmdArgs, String pathEnv)
            throws CommandBuilder.CommandNotFoundException {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { projectPath, cmdArgs });
        }
        CommandBuilder builder = new CommandBuilder(projectPath, pathEnv, true);
        String cmd = builder.getCommand();
        String cmdLine = builder.getCommandLineFromArgs(cmd, cmdArgs);
        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, cmdLine);
        }
        return cmdLine;
    }

    public static String getGradleCommandLine(String projectPath, String cmdArgs, String pathEnv)
            throws CommandBuilder.CommandNotFoundException {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { projectPath, cmdArgs });
        }
        CommandBuilder builder = new CommandBuilder(projectPath, pathEnv, false);
        String cmd = builder.getCommand();
        String cmdLine = builder.getCommandLineFromArgs(cmd, cmdArgs);
        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, cmdLine);
        }
        return cmdLine;
    }

    private String getCommand() throws CommandBuilder.CommandNotFoundException {
        String cmd = getCommandFromWrapper();
        if (cmd == null) {
            cmd = getCommandFromPreferences();
        }
        if (cmd == null) {
            cmd = getCommandFromPathEnvVar();
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_TOOLS, "Command = " + cmd);
        }

        if (cmd == null) {

            String errorMsg = "Could not find " + (isMaven ? "Maven" : "Gradle") + " executable or wrapper";

            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, errorMsg);
            }

            if (isMaven) {
                ErrorHandler.processPreferenceErrorMessage(NLS.bind(Messages.maven_exec_not_found, null), true);
            } else {
                ErrorHandler.processPreferenceErrorMessage(NLS.bind(Messages.gradle_exec_not_found, null), true);
            }

            throw new CommandNotFoundException(errorMsg);
        }

        return cmd;
    }

    private String getCommandFromWrapper() {

        String cmd = null;
        if (isMaven) {
            Path p2mw = (Utils.isWindows()) ? Paths.get(projectPath, "mvnw.cmd") : Paths.get(projectPath, "mvnw");
            Path p2mwProps = Paths.get(projectPath, ".mvn", "wrapper", "maven-wrapper.properties");

            if (p2mw.toFile().exists() && p2mwProps.toFile().exists()) {
                cmd = p2mw.toString();
            }
        } else {
            // Check if there is wrapper defined.
            Path p2gw = (Utils.isWindows()) ? Paths.get(projectPath, "gradlew.bat") : Paths.get(projectPath, "gradlew");
            Path p2gwJar = Paths.get(projectPath, "gradle", "wrapper", "gradle-wrapper.jar");
            Path p2gwProps = Paths.get(projectPath, "gradle", "wrapper", "gradle-wrapper.properties");

            if (p2gw.toFile().exists() && p2gwJar.toFile().exists() && p2gwProps.toFile().exists()) {
                cmd = p2gw.toString();
            }
        }
        if (cmd != null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Found wrapper: " + cmd);
            }
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Did NOT find wrapper for projectPath: " + projectPath);
            }
        }
        return cmd;
    }

    private String getCommandFromPreferences() throws IllegalStateException {

        String installLocPref = getInstallLocationPreferenceString();
        if (installLocPref == null || installLocPref.isBlank() || installLocPref.isEmpty()) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS,
                        "The mvn/gradle preference path: " + installLocPref + " was null, blank, or empty");
            }
            return null;
        }

        File tempCmdFile = new File(installLocPref + File.separator + "bin" + File.separator + getExecBaseName());
        String cmdPathStr = tempCmdFile.getPath();

        if (tempCmdFile.exists()) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Found mvn/gradle from preference at path: " + cmdPathStr);
            }
            return cmdPathStr;
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Did NOT find mvn/gradle from preference at path: " + cmdPathStr);
            }
            return null;
        }
    }

    /**
     * @param base name of executable
     * 
     * @return
     */
    private String getCommandFromPathEnvVar() throws IllegalStateException {

        String executableBaseName = getExecBaseName();
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { executableBaseName, pathEnv });
        }

        String foundCmd = null;

        String[] pathMembers = pathEnv.split(File.pathSeparator);
        for (String member : pathMembers) {
            if (member.isBlank() || member.isEmpty()) {
                continue;
            }
            File tempFile = new File(member + File.separator + executableBaseName);
            if (tempFile.exists()) {
                foundCmd = tempFile.getPath();
                break;
            }
        }
        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, foundCmd);
        }
        return foundCmd;
    }

    private String getCommandLineFromArgs(String cmd, String cmdArgs) {
        // Put it all together.
        StringBuilder sb = new StringBuilder();
        if (cmd != null) {
            sb.append(cmd).append(" ").append(cmdArgs);
        }

        return sb.toString();
    }

    private String getExecBaseName() {
        if (Utils.isWindows()) {
            return isMaven ? "mvn.cmd" : "gradle.bat";
        } else {
            return isMaven ? "mvn" : "gradle";
        }
    }

    private String getInstallLocationPreferenceString() {
        if (isMaven) {
            return LibertyDevPlugin.getDefault().getPreferenceStore().getString("MVNPATH");
        } else {
            return LibertyDevPlugin.getDefault().getPreferenceStore().getString("GRADLEPATH");
        }
    }

    public class CommandNotFoundException extends Exception {

        private static final long serialVersionUID = 8469585975896898403L;

        public CommandNotFoundException() {
            super();
        }

        public CommandNotFoundException(String message) {
            super(message);
        }

        public CommandNotFoundException(Throwable cause) {
            super(cause);
        }

    }
}
