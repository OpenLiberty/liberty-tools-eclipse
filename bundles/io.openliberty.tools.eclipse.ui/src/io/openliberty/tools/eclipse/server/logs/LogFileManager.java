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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.ProjectModel.BuildType;

/**
 * Manages the discovery of Liberty server log files.
 */
public class LogFileManager {

    /**
     * Default relative path to the Liberty logs directory for Maven projects.
     */
    public static final String DEFAULT_MAVEN_LOGS_REL_PATH = "target/liberty/wlp/usr/servers/defaultServer/logs";

    /**
     * Default relative path to the Liberty logs directory for Gradle projects.
     */
    public static final String DEFAULT_GRADLE_LOGS_REL_PATH = "build/wlp/usr/servers/defaultServer/logs";

    /** Default log file name extension, including the leading dot. */
    public static final String DEFAULT_FILE_EXTENSION = ".log";

    /** Default messages log file name. */
    public static final String DEFAULT_MESSAGES_FILE = "messages" + DEFAULT_FILE_EXTENSION;

    /** Default trace log file name. */
    public static final String DEFAULT_TRACE_FILE = "trace" + DEFAULT_FILE_EXTENSION;

    /** Name of the ffdc directory located under the Liberty logs directory. */
    public static final String FFDC_DIR_NAME = "ffdc";

    /** File name prefix of the exception summary files located in the ffdc directory. */
    public static final String EXCEPTION_SUMMARY_FILE_PREFIX = "exception_summary_";

    /** File name prefix of the ffdc log files located in the ffdc directory. */
    public static final String FFDC_FILE_PREFIX = "ffdc_";

    /** Maximum number of files returned per log type before the overflow indicator is set. */
    public static final int MAX_FILES_PER_TYPE = 10;

    /** LogFileManager instance. */
    private static final LogFileManager INSTANCE = new LogFileManager();

    /**
     * Returns a singleton instance of this class.
     *
     * @return A singleton instance of this class.
     */
    public static LogFileManager getInstance() {
        return INSTANCE;
    }

    /**
     * Resolves the log configuration data needed for the given project.
     *
     * @param project The project to resolve log configuration for.
     *
     * @return The resolved log configuration data, or null if undetermined.
     */
    public LogConfig resolveLogConfig(ProjectModel project) {
        return getDefaultLogConfigData(project);
    }

    /**
     * Searches the input directory for messages log files.
     *
     * @param logsDir            The Liberty logs directory to scan.
     * @param configuredFileName The active messages log file name, such as messages.log.
     *
     * @return The scan result with matching files and an overflow indicator.
     */
    public LogSearchResult getMessagesLogs(Path logsDir, String configuredFileName) {
        return getLogs(logsDir, configuredFileName);
    }

    /**
     * Searches the input directory for trace log files.
     *
     * @param logsDir            The Liberty logs directory to scan.
     * @param configuredFileName The active trace log file name, such as trace.log.
     *
     * @return The scan result with matching files and an overflow indicator.
     */
    public LogSearchResult getTraceLogs(Path logsDir, String configuredFileName) {
        return getLogs(logsDir, configuredFileName);
    }

    /**
     * Searches for exception_summary and ffdc logs.
     *
     * @param logsDir The Liberty logs directory that contains the ffdc sub-directory.
     *
     * @return The scan result with matching files and an overflow indicator.
     */
    public LogSearchResult getFFDCLogs(Path logsDir) {
        Path ffdcDir = logsDir.resolve(FFDC_DIR_NAME);
        if (!Files.isDirectory(ffdcDir)) {
            return new LogSearchResult(List.of(), false);
        }
        List<Path> summaries = new ArrayList<>();
        List<Path> ffdcs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ffdcDir)) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                if (isExceptionSummaryFile(name)) {
                    summaries.add(entry);
                } else if (isFfdcLogFile(name)) {
                    ffdcs.add(entry);
                }
            }
        } catch (IOException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Failed to scan ffdc directory: " + ffdcDir, e);
            }
        }

        // Sort each group on its own, then place the exception summaries first.
        sortNewestFirst(summaries);
        sortNewestFirst(ffdcs);
        List<Path> ordered = new ArrayList<>(summaries);
        ordered.addAll(ffdcs);
        return capResult(ordered);
    }

    /**
     * Returns the default log configuration data.
     *
     * @param project The project to build the default log configuration for.
     *
     * @return The default log configuration data, or null if the build type is unknown or the project has no location.
     */
    private LogConfig getDefaultLogConfigData(ProjectModel project) {
        String projectPath = project.getPath();
        if (projectPath == null) {
            return null;
        }
        BuildType buildType = project.getBuildType();
        String relPath;
        if (buildType == BuildType.Maven) {
            relPath = DEFAULT_MAVEN_LOGS_REL_PATH;
        } else if (buildType == BuildType.Gradle) {
            relPath = DEFAULT_GRADLE_LOGS_REL_PATH;
        } else {
            return null;
        }
        Path logsDir = Path.of(projectPath).resolve(relPath);

        return new LogConfig(logsDir, DEFAULT_MESSAGES_FILE, DEFAULT_TRACE_FILE);
    }

    /**
     * Searches the input directory for files whose name equals the active file name or
     * matches the rolled file name pattern derived from it. Returns at most MAX_FILES_PER_TYPE
     * entries, newest first.
     *
     * Liberty treats the text after the last dot of the active file name as the extension. A
     * rolled file name is the active file name without its extension, followed by an underscore
     * and a timestamp, followed by the extension.
     * 
     * For example:
     * messages.log, messages_[timestamp].log
     * mytrace, mytrace_[timestamp]
     * my_trace.debug.ext, my_trace.debug_[timestamp].ext
     *
     * @param dir            The directory to scan.
     * @param activeFileName The active log file name.
     *
     * @return The scan result with matching files and an overflow indicator.
     */
    private LogSearchResult getLogs(Path dir, String activeFileName) {
        if (!Files.isDirectory(dir)) {
            return new LogSearchResult(List.of(), false);
        }
        String prefix = getBaseName(activeFileName) + "_";
        String suffix = getExtension(activeFileName);
        List<Path> matched = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                if (name.equals(activeFileName) || (name.startsWith(prefix) && name.endsWith(suffix))) {
                    matched.add(entry);
                }
            }
        } catch (IOException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Failed to scan log directory: " + dir, e);
            }
        }

        // Sort the files.
        sortNewestFirst(matched);

        // Cap the result size to MAX_FILES_PER_TYPE.
        return capResult(matched);
    }

    /**
     * Returns the file name without its extension. The extension starts at the last dot.
     *
     * @param fileName The file name.
     *
     * @return The file name without its extension, or the file name itself if it has no extension.
     */
    private String getBaseName(String fileName) {
        int extIndex = fileName.lastIndexOf('.');
        return (extIndex > 0) ? fileName.substring(0, extIndex) : fileName;
    }

    /**
     * Returns the extension of the file name, including the leading dot. The extension starts at
     * the last dot.
     *
     * @param fileName The file name.
     *
     * @return The extension including the leading dot, or an empty string if there is none.
     */
    private String getExtension(String fileName) {
        int extIndex = fileName.lastIndexOf('.');
        return (extIndex > 0) ? fileName.substring(extIndex) : "";
    }

    /**
     * Sorts the paths in place by last-modified time descending, then by file name descending
     * as the tie-break.
     *
     * @param paths The paths to sort.
     */
    private void sortNewestFirst(List<Path> paths) {
        // Read each modification time once so that the comparator performs no file system access.
        Map<Path, Long> modifiedTimes = new HashMap<>();
        for (Path path : paths) {
            modifiedTimes.put(path, lastModified(path));
        }

        paths.sort(Comparator.<Path> comparingLong(modifiedTimes::get).reversed().thenComparing(p -> p.getFileName().toString(), Comparator.reverseOrder()));
    }

    /**
     * Caps the already ordered paths at MAX_FILES_PER_TYPE and sets the overflow indicator.
     *
     * @param ordered The paths in their final display order.
     *
     * @return The scan result with the capped files and an overflow indicator.
     */
    private LogSearchResult capResult(List<Path> ordered) {
        boolean exceedsMaxAllowed = ordered.size() > MAX_FILES_PER_TYPE;
        List<Path> capped = exceedsMaxAllowed ? ordered.subList(0, MAX_FILES_PER_TYPE) : ordered;
        return new LogSearchResult(List.copyOf(capped), exceedsMaxAllowed);
    }

    /**
     * Returns the last-modified time of the given path in milliseconds.
     *
     * @param path The path to inspect.
     *
     * @return The last-modified time in milliseconds, or 0 if it cannot be read.
     */
    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Unable to detect the last modification time of file: " + path, e);
            }
            return 0L;
        }
    }

    /**
     * Determines whether the file name matches an exception summary log pattern.
     *
     * @param name The file name to test.
     *
     * @return True if the name matches exception_summary_*.log.
     */
    private boolean isExceptionSummaryFile(String name) {
        return name.startsWith(EXCEPTION_SUMMARY_FILE_PREFIX) && name.endsWith(DEFAULT_FILE_EXTENSION);
    }

    /**
     * Determines whether the file name matches the ffdc log pattern.
     *
     * @param name The file name to test.
     *
     * @return True if the name matches ffdc_*.log.
     */
    private boolean isFfdcLogFile(String name) {
        return name.startsWith(FFDC_FILE_PREFIX) && name.endsWith(DEFAULT_FILE_EXTENSION);
    }

    /**
     * The result of scanning one log type directory. Holds an ordered list of matching paths
     * and a flag indicating whether more files exist beyond the returned set.
     */
    public static final class LogSearchResult {

        /** The ordered list of matching paths, at most MAX_FILES_PER_TYPE entries. */
        private final List<Path> files;

        /** True when the matching files exceeds the allowed maximum to show through the context menu. */
        private final boolean exceedsMaxAllowed;

        /**
         * Creates a LogSearchResult.
         *
         * @param files             The ordered list of matching paths.
         * @param exceedsMaxAllowed True when the matching files exceeds the allowed maximum to show through the context menu.
         */
        public LogSearchResult(List<Path> files, boolean exceedsMaxAllowed) {
            this.files = files;
            this.exceedsMaxAllowed = exceedsMaxAllowed;
        }

        /**
         * Returns the ordered list of matching paths.
         *
         * @return The ordered list of matching paths.
         */
        public List<Path> getFiles() {
            return files;
        }

        /**
         * Returns true when the matching files exceeds the allowed maximum to show through the context menu.
         *
         * @return True when the matching files exceeds the allowed maximum to show through the context menu.
         */
        public boolean exceedsMaxAllowed() {
            return exceedsMaxAllowed;
        }
    }
}
