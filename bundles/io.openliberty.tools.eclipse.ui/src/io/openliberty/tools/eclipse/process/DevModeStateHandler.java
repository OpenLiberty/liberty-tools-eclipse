/*******************************************************************************
* Copyright (c) 2026 IBM Corporation and others.
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

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.swt.widgets.Display;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher.RuntimeEnv;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationHelper;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Scans each line of dev mode console output for patterns that indicate a change
 * in dev mode state, and triggers the appropriate in-plugin reaction for each match.
 *
 * New patterns are added by inserting a PatternAction entry into the list built by
 * buildPatternActions(). Each entry pairs a PatternMatcher with a Consumer action.
 */
public class DevModeStateHandler implements IConsoleLineHandler {

    /**
     * Encapsulates a single pattern check used in the pattern-action table.
     */
    private interface PatternMatcher {

        /**
         * Returns true if the given line matches this pattern.
         *
         * @param line The line of console output to test.
         * 
         * @return True if the line matches, false otherwise.
         */
        boolean matches(String line);
    }

    /**
     * A PatternMatcher that checks whether the line matches a compiled regular expression.
     */
    private static class RegexMatcher implements PatternMatcher {

        private final Pattern pattern;

        RegexMatcher(String regex) {
            this.pattern = Pattern.compile(regex);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(String line) {
            return pattern.matcher(line).find();
        }
    }

    /**
     * A PatternMatcher that checks whether the line contains a fixed substring.
     */
    private static class ContainsMatcher implements PatternMatcher {

        private final String substring;

        ContainsMatcher(String substring) {
            this.substring = substring;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(String line) {
            return line.contains(substring);
        }
    }

    /**
     * Associates a PatternMatcher with the action to invoke when the pattern is found.
     */
    private static class PatternAction {

        final PatternMatcher matcher;
        final Consumer<String> action;

        PatternAction(PatternMatcher matcher, Consumer<String> action) {
            this.matcher = matcher;
            this.action = action;
        }
    }

    /** The project model for the project whose process output this handler is observing. */
    private final ProjectModel projectModel;

    /** The launch mode (run or debug) originally used to start dev mode for this project. */
    private final String mode;

    /** Pattern-action table. Each entry is evaluated in order for every console line. */
    private final List<PatternAction> patternActions;

    /**
     * Constructs a handler for the specified project.
     *
     * @param projectModel The model of the project whose dev mode process is being monitored.
     * @param mode         The Eclipse launch mode (ILaunchManager.RUN_MODE or ILaunchManager.DEBUG_MODE)
     *                         originally used to start dev mode.
     */
    public DevModeStateHandler(ProjectModel projectModel, String mode) {
        this.projectModel = projectModel;
        this.mode = mode;
        this.patternActions = buildPatternActions();
    }

    /**
     * Builds the pattern-action table. Each entry pairs a matcher with the reaction
     * to invoke when the pattern is found on a console line.
     *
     * @return The list of pattern-action pairs.
     */
    private List<PatternAction> buildPatternActions() {
        return Arrays.asList(
                             // Gradle and Maven both emit a message of the form
                             // "The server <name> is already running." when dev mode tries to start
                             // against an already-running server instance.
                             new PatternAction(new RegexMatcher("The server .+ is already running\\."), line -> handleAlreadyRunning()));
    }

    /**
     * Shows a Yes/No warning dialog informing the user that dev mode is already running
     * outside of Liberty Tools. If the user selects Yes, the running dev mode process is
     * stopped and dev mode is restarted using the original launch configuration so that
     * Liberty Tools can fully manage the project.
     */
    private void handleAlreadyRunning() {
        Display.getDefault().asyncExec(() -> {
            String targetProjectName = projectModel.getName();
            String msg = Messages.getMessage("server_already_running", targetProjectName);
            Integer response = ErrorHandler.processWarningMessage(msg, true, new String[] { "Yes", "No" }, 0);
            if (response != null && response == 0) {
                DevModeOperations.getInstance().issueStopCommand(projectModel.getName(), () -> {
                    try {
                        // Update the active selection to the selected target project if the original selection does not match the target.
                        if (targetProjectName != null) {
                            Utils.updateActiveSelection(projectModel);
                        }

                        // Determine what configuration to use.
                        LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
                        ILaunchConfiguration configuration = launchConfigHelper.getLaunchConfiguration(projectModel, mode, RuntimeEnv.LOCAL);
                        DebugUITools.launch(configuration, mode);
                    } catch (Exception e) {
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                                    "DevModeStateHandler: failed to restart dev mode for project "
                                                                       + projectModel.getName(),
                                                    e);
                        }
                        ErrorHandler.processErrorMessage(
                                                         Messages.getMessage("start_general_error", targetProjectName), e, true);
                    }
                });
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleLine(String projectName, String line) {
        //Evaluates each known pattern against the given line and invokes the associated
        // action for any that match.
        for (PatternAction pa : patternActions) {
            if (pa.matcher.matches(line)) {
                pa.action.accept(line);
            }
        }
    }
}
