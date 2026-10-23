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
package io.openliberty.tools.eclipse.server.logs;

import java.nio.file.Path;

/**
 * Holds Liberty server log data based on set configuration.
 */
public final class LogConfig {

    /** The Liberty logs directory, or null if undetermined. */
    private Path logsDir;

    /** The active messages log file name. */
    private String messagesFileName;

    /** The active trace log file name. */
    private String traceFileName;

    /**
     * Creates a log configuration.
     *
     * @param logsDir          Absolute path to the Liberty logs directory, or null if undetermined.
     * @param messagesFileName Active messages log file name. Null or blank falls back to the default.
     * @param traceFileName    Active trace log file name. Null or blank falls back to the default.
     */
    public LogConfig(Path logsDir, String messagesFileName, String traceFileName) {
        this.logsDir = logsDir;
        this.messagesFileName = (messagesFileName != null && !messagesFileName.isBlank()) ? messagesFileName : LogFileManager.DEFAULT_MESSAGES_FILE;
        this.traceFileName = (traceFileName != null && !traceFileName.isBlank()) ? traceFileName : LogFileManager.DEFAULT_TRACE_FILE;
    }

    /**
     * Returns the absolute path to the Liberty logs directory, or null if undetermined.
     *
     * @return The logs directory path, or null.
     */
    public Path getLogsDir() {
        return logsDir;
    }

    /**
     * Returns the active messages log file name. Never null or blank.
     *
     * @return The active messages log file name.
     */
    public String getMessagesFileName() {
        return messagesFileName;
    }

    /**
     * Returns the active trace log file name. Never null or blank.
     *
     * @return The active trace log file name.
     */
    public String getTraceFileName() {
        return traceFileName;
    }
}
