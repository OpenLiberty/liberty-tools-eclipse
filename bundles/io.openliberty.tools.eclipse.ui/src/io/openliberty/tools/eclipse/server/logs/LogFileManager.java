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
    public static final String MAVEN_LOGS_REL_PATH = "target/liberty/wlp/usr/servers/defaultServer/logs";

    /**
     * Default relative path to the Liberty logs directory for Gradle projects.
     */
    public static final String GRADLE_LOGS_REL_PATH = "build/wlp/usr/servers/defaultServer/logs";

    /** Default messages log file name. */
    public static final String DEFAULT_MESSAGES_FILE = "messages.log";

    /** Default trace log file name. */
    public static final String DEFAULT_TRACE_FILE = "trace.log";

    /** Name of the ffdc directory located under the Liberty logs directory. */
    public static final String FFDC_DIR_NAME = "ffdc";

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
        return getLogs(logsDir, configuredFileName, "messages_", ".log");
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
        return getLogs(logsDir, configuredFileName, "trace_", ".log");
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
            relPath = MAVEN_LOGS_REL_PATH;
        } else if (buildType == BuildType.Gradle) {
            relPath = GRADLE_LOGS_REL_PATH;
        } else {
            return null;
        }
        Path logsDir = Path.of(projectPath).resolve(relPath);

        return new LogConfig(logsDir, DEFAULT_MESSAGES_FILE, DEFAULT_TRACE_FILE);
    }

    /**
     * Searches the input directory for regular files whose name equals the active file name or
     * starts with the prefix and ends with the suffix. Returns at most MAX_FILES_PER_TYPE
     * entries, newest first.
     *
     * @param dir            The directory to scan.
     * @param activeFileName The active log file name.
     * @param prefix         The file name prefix of the rolled log files.
     * @param suffix         The file name suffix of the rolled log files.
     *
     * @return The scan result with matching files and an overflow indicator.
     */
    private LogSearchResult getLogs(Path dir, String activeFileName, String prefix, String suffix) {
        if (!Files.isDirectory(dir)) {
            return new LogSearchResult(List.of(), false);
        }
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

        paths.sort(Comparator.<Path> comparingLong(modifiedTimes::get).reversed()
                             .thenComparing(p -> p.getFileName().toString(), Comparator.reverseOrder()));
    }

    /**
     * Caps the already ordered paths at MAX_FILES_PER_TYPE and sets the overflow indicator.
     *
     * @param ordered The paths in their final display order.
     *
     * @return The scan result with the capped files and an overflow indicator.
     */
    private LogSearchResult capResult(List<Path> ordered) {
        boolean hasMore = ordered.size() > MAX_FILES_PER_TYPE;
        List<Path> capped = hasMore ? ordered.subList(0, MAX_FILES_PER_TYPE) : ordered;
        return new LogSearchResult(List.copyOf(capped), hasMore);
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
        return (name.startsWith("exception_summary_") && name.endsWith(".log"));
    }

    /**
     * Determines whether the file name matches the ffdc log pattern.
     *
     * @param name The file name to test.
     *
     * @return True if the name matches ffdc_*.log.
     */
    private boolean isFfdcLogFile(String name) {
        return name.startsWith("ffdc_") && name.endsWith(".log");
    }

    /**
     * The result of scanning one log type directory. Holds an ordered list of matching paths
     * and a flag indicating whether more files exist beyond the returned set.
     */
    public static final class LogSearchResult {

        /** The ordered list of matching paths, at most MAX_FILES_PER_TYPE entries. */
        private final List<Path> files;

        /** True when more matching files exist than were returned. */
        private final boolean hasMore;

        /**
         * Creates a LogSearchResult.
         *
         * @param files   The ordered list of matching paths.
         * @param hasMore True when more matching files exist than were returned.
         */
        public LogSearchResult(List<Path> files, boolean hasMore) {
            this.files = files;
            this.hasMore = hasMore;
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
         * Returns true when more matching files exist than were returned.
         *
         * @return True when more matching files exist than were returned.
         */
        public boolean hasMore() {
            return hasMore;
        }
    }
}
