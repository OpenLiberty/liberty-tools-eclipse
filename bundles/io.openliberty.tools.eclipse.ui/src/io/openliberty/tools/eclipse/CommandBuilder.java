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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Builds commands for the supported build tools: Maven and Gradle.
 */
public class CommandBuilder {

    private String projectPath;

    private String pathEnv;

    private boolean isMaven;

    /**
     * @param projectPath The project path used to locate the wrapper script and its companion files.
     * @param pathEnv     The PATH env var.
     * @param isMaven     true for Maven, false for Gradle.
     */
    private CommandBuilder(String projectPath, String pathEnv, boolean isMaven) {
        super();
        this.projectPath = projectPath;
        this.pathEnv = pathEnv;
        this.isMaven = isMaven;
    }

    /**
     * Constructs a Maven command using the given input parameters.
     *
     * @param targetProjectModel The project model of the module to target.
     * @param goal               The Maven goal to run.
     * @param runClean           The run clean indicator.
     * @param userParms          The user-supplied parameters or null.
     * @param pathEnv            The PATH env var.
     *
     * @return A CommandData object containing the Maven command and the required execution path.
     *
     * @throws CommandNotFoundException
     */
    public static CommandData constructMavenCommand(ProjectModel targetProjectModel, String goal, boolean runClean, String userParms,
                                                    String pathEnv) throws CommandNotFoundException {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { targetProjectModel, goal, runClean, userParms, pathEnv});
        }

        String targetProjectPath = targetProjectModel.getPath();
        ProjectModel targetParentModel = targetProjectModel.getParentProjectModel();
        String parentProjectPath = (targetParentModel != null) ? targetParentModel.getPath() : null;

        StringBuilder args = new StringBuilder();
        if (runClean) {
            args.append("clean").append(" ");
        }
        args.append(goal);

        // Add module selector (-pl :moduleName -am) when this is a child module of a multi-module build.
        // This is done so Maven targets only the selected module and its upstream
        // dependencies. The command is executed from the parent project directory.
        if (parentProjectPath != null) {
            args.append(" -pl :").append(targetProjectModel.getName()).append(" -am");
        }

        if (userParms != null && !userParms.isBlank()) {
            args.append(" ").append(userParms.trim());
        }

        CommandBuilder builder = new CommandBuilder(targetProjectPath, pathEnv, true);
        String cmd = builder.getBuildToolExecPath();

        // On Windows, mvnw.cmd locates the maven project base directory (.mvn) by walking up from
        // the process working directory rather than from the script's own directory. When in a multi-module
        // project, if the child has its own wrapper and the command is executed from the parent
        // directory, the walk fails to find the child's .mvn directory.
        // To work around this on Windows, the following is done:
        // - Set the process working directory to the child's directory so that the mvnw.cmd walk
        //   can find the .mvn directory.
        // - Append "-f <parent-pom>" to the command to process the parent/aggregator pom so that
        //   the reactor sees all of the modules.
        String executionPath = (targetParentModel != null) ? targetParentModel.getPath() : targetProjectPath;
        if ((cmd.endsWith("mvnw") || cmd.endsWith("mvnw.cmd")) && Utils.isWindows() && parentProjectPath != null && !targetProjectPath.equals(parentProjectPath)) {
            String parentPom = Paths.get(parentProjectPath, "pom.xml").toAbsolutePath().toString();
            args.append(" -f ").append(encloseCmdInQuotesIfNeeded(parentPom));
            executionPath = targetProjectPath;
        }

        String cmdLine = builder.appendArgsToCommand(cmd, args.toString());

        CommandData result = new CommandData(cmdLine, executionPath);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, result);
        }

        return result;
    }

    /**
     * Constructs a Gradle command using the given input parameters.
     *
     * @param targetProjectModel The project model of the module to target.
     * @param taskName           The Gradle task to run.
     * @param runClean           The run clean indicator.
     * @param userParms          The user-supplied parameters or null.
     * @param pathEnv            The PATH env var.
     *
     * @return A CommandData object containing the Gradle command and the required execution path.
     *
     * @throws CommandNotFoundException
     */
    public static CommandData constructGradleCommand(ProjectModel targetProjectModel, String taskName, boolean runClean, String userParms,
                                                     String pathEnv) throws CommandNotFoundException {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { targetProjectModel, taskName, runClean, userParms, pathEnv });
        }

        String targetProjectPath = targetProjectModel.getPath();
        ProjectModel targetParentModel = targetProjectModel.getParentProjectModel();
        String targetParentPath = (targetParentModel != null) ? targetParentModel.getPath() : null;

        // When this is a child module, qualify each task name with the subproject path
        // so Gradle routes the task to the correct subproject when run from the root directory.
        String qualifiedTask;
        String qualifiedClean;
        if (targetParentPath != null) {
            String subprojectName = Paths.get(targetProjectPath).getFileName().toString();
            String subprojectPrefix = ":" + subprojectName + ":";
            qualifiedTask = subprojectPrefix + taskName;
            qualifiedClean = subprojectPrefix + "clean";
        } else {
            qualifiedTask = taskName;
            qualifiedClean = "clean";
        }

        StringBuilder args = new StringBuilder();
        if (runClean) {
            args.append(qualifiedClean).append(" ");
        }
        args.append(qualifiedTask);
        if (userParms != null && !userParms.isBlank()) {
            args.append(" ").append(userParms.trim());
        }

        CommandBuilder builder = new CommandBuilder(targetProjectPath, pathEnv, false);
        String cmd = builder.getBuildToolExecPath();
        String cmdLine = builder.appendArgsToCommand(cmd, args.toString());

        String executionPath = (targetParentPath != null) ? targetParentPath : targetProjectPath;
        CommandData result = new CommandData(cmdLine, executionPath);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, result);
        }

        return result;
    }

    /**
     * Constructs a Gradle daemon stop command (gradle --stop).
     *
     * @param targetProjectModel The project model.
     * @param pathEnv            The PATH env var.
     *
     * @return A CommandData object containing the daemon stop command and execution path.
     *
     * @throws CommandNotFoundException
     */
    public static CommandData constructGradleStopDaemonCommand(ProjectModel targetProjectModel, String pathEnv) throws CommandNotFoundException {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { targetProjectModel, pathEnv });
        }

        String targetProjectPath = targetProjectModel.getPath();
        CommandBuilder builder = new CommandBuilder(targetProjectPath, pathEnv, false);
        String cmd = builder.getBuildToolExecPath();
        String cmdLine = builder.appendArgsToCommand(cmd, "--stop");

        CommandData result = new CommandData(cmdLine, targetProjectPath);
    
        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, result);
        }
        return result;
    }

    /**
     * Resolves the build tool executable to use.
     * It looks for a defined wrapper first. If not present,
     * it looks for the executable path in preferences. If one is not present,
     * it looks for the mvn executable in the PATH environment variable.
     */
    private String getBuildToolExecPath() throws CommandBuilder.CommandNotFoundException {
        String cmd = getBuildToolWrapper();
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
            String errorMsg = "Unable to find " + (isMaven ? "Maven" : "Gradle") + " executable or wrapper";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, errorMsg);
            }
            if (isMaven) {
                ErrorHandler.processPreferenceErrorMessage(Messages.getMessage("maven_exec_not_found"), true);
            } else {
                ErrorHandler.processPreferenceErrorMessage(Messages.getMessage("gradle_exec_not_found"), true);
            }
            throw new CommandNotFoundException(errorMsg);
        }

        return encloseCmdInQuotesIfNeeded(cmd);
    }

    /**
     * Detects the wrapper script associated with the supported build tools (Maven and Gradle).
     *
     * @return The absolute wrapper path when a valid wrapper is found, or null when none is found.
     */
    private String getBuildToolWrapper() {
        String cmd = null;
        if (isMaven) {
            Path p2mw = (Utils.isWindows()) ? Paths.get(projectPath, "mvnw.cmd") : Paths.get(projectPath, "mvnw");
            Path p2mwProps = Paths.get(projectPath, ".mvn", "wrapper", "maven-wrapper.properties");

            if (p2mw.toFile().exists() && p2mwProps.toFile().exists()) {
                cmd = p2mw.toAbsolutePath().toString();
            }
        } else {
            Path p2gw = (Utils.isWindows()) ? Paths.get(projectPath, "gradlew.bat") : Paths.get(projectPath, "gradlew");
            Path p2gwJar = Paths.get(projectPath, "gradle", "wrapper", "gradle-wrapper.jar");
            Path p2gwProps = Paths.get(projectPath, "gradle", "wrapper", "gradle-wrapper.properties");

            if (p2gw.toFile().exists() && p2gwJar.toFile().exists() && p2gwProps.toFile().exists()) {
                cmd = p2gw.toAbsolutePath().toString();
            }
        }

        if (cmd == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "A build tool wrapper was not found for project " + projectPath);
            }
            return null;
        }

        return cmd;
    }

    /**
     * Returns the build tool executable path configured in the Eclipse IDE Liberty preference settings.
     * 
     * @return The build tool executable path configured in the Eclipse IDE Liberty preference settings.
     * 
     * @throws IllegalStateException
     */
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
            return cmdPathStr;
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Unable to find the mvn/gradle installation defined in preferences: " + cmdPathStr);
            }
            return null;
        }
    }

    /**
     * Retrieves the build tool executable from the PATH environment variable visible to the Eclipse IDE.
     *
     * @return The build tool executable from the PATH environment variable visible to the Eclipse IDE.
     *
     * @throws IllegalStateException If the PATH environment variable cannot be accessed.
     */
    private String getCommandFromPathEnvVar() throws IllegalStateException {
        String executableBaseName = getExecBaseName();
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

        return foundCmd;
    }

    /**
     * Appends the input arguments to the input command.
     * 
     * @param cmd     The command.
     * @param cmdArgs The arguments as string.
     * 
     * @return A properly formatted command containing the input parameters.
     */
    private String appendArgsToCommand(String cmd, String cmdArgs) {
        StringBuilder sb = new StringBuilder();
        if (cmd != null) {
            sb.append(cmd).append(" ").append(cmdArgs);
        }
        return sb.toString();
    }

    /**
     * Returns the expected name of the supported build tools executables.
     * 
     * @return The expected name of the supported build tools executables.
     */
    private String getExecBaseName() {
        if (Utils.isWindows()) {
            return isMaven ? "mvn.cmd" : "gradle.bat";
        } else {
            return isMaven ? "mvn" : "gradle";
        }
    }

    /**
     * Returns the build tool executable path configured in the Eclipse IDE Liberty preference settings.
     * 
     * @return The build tool executable path configured in the Eclipse IDE Liberty preference settings.
     */
    private String getInstallLocationPreferenceString() {
        if (isMaven) {
            return LibertyDevPlugin.getDefault().getPreferenceStore().getString("MVNPATH");
        } else {
            return LibertyDevPlugin.getDefault().getPreferenceStore().getString("GRADLEPATH");
        }
    }

    /**
     * Command not found exception.
     */
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

    /**
     * Encloses the given string in double quotes if it contains spaces.
     */
    private static String encloseCmdInQuotesIfNeeded(String cmd) {
        if (cmd.contains(" ")) {
            return "\"" + cmd + "\"";
        }
        return cmd;
    }

    /**
     * Holds the assembled command string and the directory from which the command must
     * be executed.
     */
    public static class CommandData {

        private String commandLine;
        private String executionPath;

        public CommandData(String commandLine, String executionPath) {
            this.commandLine = commandLine;
            this.executionPath = executionPath;
        }

        /**
         * Returns the fully-assembled command line string.
         * 
         * @return The fully-assembled command line string.
         */
        public String getCommand() {
            return commandLine;
        }

        /**
         * Returns the directory from which the command must be executed.
         *
         * @return The directory from which the command must be executed.
         */
        public String getExecutionPath() {
            return executionPath;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return String.format("CommandData{executionPath=%s, command=%s}", executionPath, commandLine);
        }
    }
}
