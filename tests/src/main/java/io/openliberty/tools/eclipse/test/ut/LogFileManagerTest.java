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
package io.openliberty.tools.eclipse.test.ut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.ProjectModel.BuildType;
import io.openliberty.tools.eclipse.server.logs.LogConfig;
import io.openliberty.tools.eclipse.server.logs.LogFileManager;
import io.openliberty.tools.eclipse.server.logs.LogFileManager.LogSearchResult;

/**
 * Unit tests for LogFileManager and LogConfig.
 */
public class LogFileManagerTest {

    /** Shared temporary directory for test files. */
    private static Path tempDir;

    /**
     * Creates the shared temporary root directory used by all tests.
     *
     * @throws IOException If the temporary directory cannot be created.
     */
    @BeforeAll
    public static void setup() throws IOException {
        tempDir = Files.createTempDirectory("LogFileManagerTestDir");
    }

    /**
     * Deletes all files written during a test, leaving the root directory intact for the next test.
     *
     * @throws IOException If file cleanup fails.
     */
    @AfterEach
    public void tearDown() throws IOException {
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(tempDir)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // Ignore. Best effort.
                }
            });
        }
    }

    /**
     * Deletes the shared temporary directory after all tests complete.
     *
     * @throws IOException If directory deletion fails.
     */
    @AfterAll
    public static void cleanUp() throws IOException {
        Files.deleteIfExists(tempDir);
    }

    /**
     * Tests that getInstance returns a non-null singleton instance of LogFileManager.
     */
    @Test
    public void testGetInstance() {
        LogFileManager instance1 = LogFileManager.getInstance();
        LogFileManager instance2 = LogFileManager.getInstance();
        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }

    /**
     * Tests that LogConfig stores and returns configured values.
     * If the configured values for messages and trace file names are null or blank, test
     * that the defaults are used.
     */
    @Test
    public void testMsgAndTraceLogConfigDefaultsAndCustomValues() {
        Path logsDir = tempDir.resolve("logs");
        LogConfig customConfig = new LogConfig(logsDir, "custom_messages.log", "custom_trace.log");
        assertEquals(logsDir, customConfig.getLogsDir());
        assertEquals("custom_messages.log", customConfig.getMessagesFileName());
        assertEquals("custom_trace.log", customConfig.getTraceFileName());

        LogConfig defaultConfigNull = new LogConfig(logsDir, null, null);
        assertEquals(LogFileManager.DEFAULT_MESSAGES_FILE, defaultConfigNull.getMessagesFileName());
        assertEquals(LogFileManager.DEFAULT_TRACE_FILE, defaultConfigNull.getTraceFileName());

        LogConfig defaultConfigBlank = new LogConfig(logsDir, "  ", "");
        assertEquals(LogFileManager.DEFAULT_MESSAGES_FILE, defaultConfigBlank.getMessagesFileName());
        assertEquals(LogFileManager.DEFAULT_TRACE_FILE, defaultConfigBlank.getTraceFileName());

        LogConfig nullDirConfig = new LogConfig(null, null, null);
        assertNull(nullDirConfig.getLogsDir());
    }

    /**
     * Tests resolving log configuration for Maven and Gradle projects.
     */
    @Test
    public void testResolveLogConfig() {
        LogFileManager manager = LogFileManager.getInstance();

        // Maven project.
        ProjectModel mavenProject = mock(ProjectModel.class);
        when(mavenProject.getPath()).thenReturn(tempDir.toString());
        when(mavenProject.getBuildType()).thenReturn(BuildType.Maven);

        LogConfig mavenConfig = manager.resolveLogConfig(mavenProject);
        assertNotNull(mavenConfig);
        Path expectedMavenLogsDir = tempDir.resolve(LogFileManager.DEFAULT_MAVEN_LOGS_REL_PATH);
        assertEquals(expectedMavenLogsDir, mavenConfig.getLogsDir());
        assertEquals(LogFileManager.DEFAULT_MESSAGES_FILE, mavenConfig.getMessagesFileName());
        assertEquals(LogFileManager.DEFAULT_TRACE_FILE, mavenConfig.getTraceFileName());

        // Gradle project.
        ProjectModel gradleProject = mock(ProjectModel.class);
        when(gradleProject.getPath()).thenReturn(tempDir.toString());
        when(gradleProject.getBuildType()).thenReturn(BuildType.Gradle);

        LogConfig gradleConfig = manager.resolveLogConfig(gradleProject);
        assertNotNull(gradleConfig);
        Path expectedGradleLogsDir = tempDir.resolve(LogFileManager.DEFAULT_GRADLE_LOGS_REL_PATH);
        assertEquals(expectedGradleLogsDir, gradleConfig.getLogsDir());

        // Project with unknown build type.
        ProjectModel unknownProject = mock(ProjectModel.class);
        when(unknownProject.getPath()).thenReturn(tempDir.toString());
        when(unknownProject.getBuildType()).thenReturn(BuildType.Unknown);
        assertNull(manager.resolveLogConfig(unknownProject));

        // Project with null path.
        ProjectModel nullPathProject = mock(ProjectModel.class);
        when(nullPathProject.getPath()).thenReturn(null);
        when(nullPathProject.getBuildType()).thenReturn(BuildType.Maven);
        assertNull(manager.resolveLogConfig(nullPathProject));
    }

    /**
     * Tests that no messages logs are returned when searching a logs directory that has none.
     */
    @Test
    public void testGetMessagesLogsUsingAnEmptyLogDir() {
        LogFileManager manager = LogFileManager.getInstance();
        Path nonExistentDir = tempDir.resolve("empty_logs_dir");
        LogSearchResult result = manager.getMessagesLogs(nonExistentDir, LogFileManager.DEFAULT_MESSAGES_FILE);
        assertNotNull(result);
        assertTrue(result.getFiles().isEmpty());
        assertFalse(result.exceedsMaxAllowed());
    }

    /**
     * Tests scanning messages logs for matching active and rolled files.
     * Files that do not follow the configured messages log name should be excluded
     * from the result list.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testGetMessagesLogsMatchingAndSorting() throws IOException {
        LogFileManager manager = LogFileManager.getInstance();
        Path logsDir = Files.createDirectories(tempDir.resolve("logs"));

        Path activeLog = Files.createFile(logsDir.resolve("messages.log"));
        Path rolledLog1 = Files.createFile(logsDir.resolve("messages_26.10.30_10.00.00.log"));
        Path rolledLog2 = Files.createFile(logsDir.resolve("messages_26.10.30_11.00.00.log"));

        // Unrelated files that must be ignored.
        Files.createFile(logsDir.resolve("trace.log"));
        Files.createFile(logsDir.resolve("console.log"));
        Files.createFile(logsDir.resolve("messages.txt"));
        Files.createFile(logsDir.resolve("messages_backup"));
        Files.createDirectories(logsDir.resolve("messages_folder.log"));

        // Set timestamps: active log (messages.log) is newest, followed by more recently rolled logs.
        Instant now = Instant.now();
        Files.setLastModifiedTime(activeLog, FileTime.from(now));
        Files.setLastModifiedTime(rolledLog2, FileTime.from(now.minusSeconds(50)));
        Files.setLastModifiedTime(rolledLog1, FileTime.from(now.minusSeconds(1000)));

        LogSearchResult result = manager.getMessagesLogs(logsDir, LogFileManager.DEFAULT_MESSAGES_FILE);
        assertNotNull(result);
        assertFalse(result.exceedsMaxAllowed());

        List<Path> files = result.getFiles();
        assertEquals(3, files.size());
        assertEquals(activeLog, files.get(0));
        assertEquals(rolledLog2, files.get(1));
        assertEquals(rolledLog1, files.get(2));
    }

    /**
     * Tests that messages log files with identical modification timestamps are sorted by name descending.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testGetMessagesLogsTieBreakSorting() throws IOException {
        LogFileManager manager = LogFileManager.getInstance();
        Path logsDir = Files.createDirectories(tempDir.resolve("logs"));

        Path activeLog = Files.createFile(logsDir.resolve("messages.log"));
        Path rolledLogA = Files.createFile(logsDir.resolve("messages_26.10.30_10.00.00.log"));
        Path rolledLogB = Files.createFile(logsDir.resolve("messages_26.10.30_11.00.00.log"));

        Instant now = Instant.now();
        Files.setLastModifiedTime(activeLog, FileTime.from(now));

        FileTime sameTime = FileTime.from(now.minusSeconds(100));
        Files.setLastModifiedTime(rolledLogA, sameTime);
        Files.setLastModifiedTime(rolledLogB, sameTime);

        LogSearchResult result = manager.getMessagesLogs(logsDir, LogFileManager.DEFAULT_MESSAGES_FILE);
        List<Path> files = result.getFiles();
        assertEquals(3, files.size());
        assertEquals(activeLog, files.get(0));
        assertEquals(rolledLogB, files.get(1));
        assertEquals(rolledLogA, files.get(2));
    }

    /**
     * Tests that messages logs files returned are capped at MAX_FILES_PER_TYPE and that the overflow indicator is set.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testGetMessagesLogsCapping() throws IOException {
        LogFileManager manager = LogFileManager.getInstance();
        Path logsDir = Files.createDirectories(tempDir.resolve("logs"));

        // Create the active messages.log as the newest file plus 11 rolled log files (12 total).
        Instant baseTime = Instant.now();
        for (int i = 1; i <= 11; i++) {
            String name = String.format("messages_26.10.30_10.%02d.00.log", i);
            Path file = Files.createFile(logsDir.resolve(name));
            Files.setLastModifiedTime(file, FileTime.from(baseTime.plusSeconds(i)));
        }
        Path activeLog = Files.createFile(logsDir.resolve("messages.log"));
        Files.setLastModifiedTime(activeLog, FileTime.from(baseTime.plusSeconds(100)));

        LogSearchResult result = manager.getMessagesLogs(logsDir, LogFileManager.DEFAULT_MESSAGES_FILE);
        assertTrue(result.exceedsMaxAllowed());
        assertEquals(LogFileManager.MAX_FILES_PER_TYPE, result.getFiles().size());
        
        // Newest is the active messages.log, followed by rolled files in descending timestamp order.
        assertEquals("messages.log", result.getFiles().get(0).getFileName().toString());
        assertEquals("messages_26.10.30_10.11.00.log", result.getFiles().get(1).getFileName().toString());
        assertEquals("messages_26.10.30_10.03.00.log", result.getFiles().get(9).getFileName().toString());
    }

    /**
     * Tests that when a custom configured trace file name is configured, only files with that name in it are returned.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testGetTraceLogsCustomFileName() throws IOException {
        LogFileManager manager = LogFileManager.getInstance();
        Path logsDir = Files.createDirectories(tempDir.resolve("logs"));

        Path traceLog = Files.createFile(logsDir.resolve("custom_trace.log"));
        Path rolledTrace = Files.createFile(logsDir.resolve("custom_trace_26.10.30_10.00.00.log"));
        Files.createFile(logsDir.resolve("trace.log"));

        // Active trace log is newer than the rolled trace log.
        Instant now = Instant.now();
        Files.setLastModifiedTime(traceLog, FileTime.from(now));
        Files.setLastModifiedTime(rolledTrace, FileTime.from(now.minusSeconds(100)));

        LogSearchResult result = manager.getTraceLogs(logsDir, "custom_trace.log");
        assertEquals(2, result.getFiles().size());
        assertEquals(traceLog, result.getFiles().get(0));
        assertEquals(rolledTrace, result.getFiles().get(1));
        assertFalse(result.exceedsMaxAllowed());
    }

    /**
     * Tests that trace logs file returned are capped at MAX_FILES_PER_TYPE and that the overflow indicator is set.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testGetTraceLogsCapping() throws IOException {
        LogFileManager manager = LogFileManager.getInstance();
        Path logsDir = Files.createDirectories(tempDir.resolve("logs"));

        // Create the active trace.log as the newest file plus 11 rolled log files (12 total).
        Instant baseTime = Instant.now();
        for (int i = 1; i <= 11; i++) {
            String name = String.format("trace_26.10.30_10.%02d.00.log", i);
            Path file = Files.createFile(logsDir.resolve(name));
            Files.setLastModifiedTime(file, FileTime.from(baseTime.plusSeconds(i)));
        }
        Path activeLog = Files.createFile(logsDir.resolve("trace.log"));
        Files.setLastModifiedTime(activeLog, FileTime.from(baseTime.plusSeconds(100)));

        LogSearchResult result = manager.getTraceLogs(logsDir, LogFileManager.DEFAULT_TRACE_FILE);
        assertTrue(result.exceedsMaxAllowed());
        assertEquals(LogFileManager.MAX_FILES_PER_TYPE, result.getFiles().size());

        // Newest is the active trace.log, followed by rolled files in descending timestamp order.
        assertEquals("trace.log", result.getFiles().get(0).getFileName().toString());
        assertEquals("trace_26.10.30_10.11.00.log", result.getFiles().get(1).getFileName().toString());
        assertEquals("trace_26.10.30_10.03.00.log", result.getFiles().get(9).getFileName().toString());
    }

    /**
     * Tests that no FFDC logs are returned when the ffdc sub-directory is missing.
     */
    @Test
    public void testGetFFDCLogsDirectoryNotFound() {
        LogFileManager manager = LogFileManager.getInstance();
        Path logsDir = tempDir.resolve("logs");
        LogSearchResult result = manager.getFFDCLogs(logsDir);
        assertNotNull(result);
        assertTrue(result.getFiles().isEmpty());
        assertFalse(result.exceedsMaxAllowed());
    }

    /**
     * Tests that if there are files in the FFDC directory, the exception summary files are returned first
     * followed by the ffdc files.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testGetFFDCLogsOrderingAndFiltering() throws IOException {
        LogFileManager manager = LogFileManager.getInstance();
        Path logsDir = Files.createDirectories(tempDir.resolve("logs"));
        Path ffdcDir = Files.createDirectories(logsDir.resolve(LogFileManager.FFDC_DIR_NAME));

        Path summaryOld = Files.createFile(ffdcDir.resolve("exception_summary_26.10.30_10.00.00.0.log"));
        Path summaryNew = Files.createFile(ffdcDir.resolve("exception_summary_26.10.30_11.00.00.0.log"));
        Path ffdcOld = Files.createFile(ffdcDir.resolve("ffdc_26.10.30_10.00.00.0.log"));
        Path ffdcNew = Files.createFile(ffdcDir.resolve("ffdc_26.10.30_11.00.00.0.log"));

        // Unrelated files.
        Files.createFile(ffdcDir.resolve("summary.txt"));
        Files.createFile(ffdcDir.resolve("other_file.log"));
        Files.createDirectories(ffdcDir.resolve("exception_summary_folder.log"));

        Instant now = Instant.now();
        Files.setLastModifiedTime(summaryOld, FileTime.from(now.minusSeconds(100)));
        Files.setLastModifiedTime(summaryNew, FileTime.from(now.minusSeconds(50)));
        Files.setLastModifiedTime(ffdcOld, FileTime.from(now.minusSeconds(80)));
        Files.setLastModifiedTime(ffdcNew, FileTime.from(now.minusSeconds(20)));

        LogSearchResult result = manager.getFFDCLogs(logsDir);
        assertNotNull(result);
        assertFalse(result.exceedsMaxAllowed());

        List<Path> files = result.getFiles();
        assertEquals(4, files.size());
        // Exception summaries first, newest to oldest.
        assertEquals(summaryNew, files.get(0));
        assertEquals(summaryOld, files.get(1));
        // Followed by ffdc files, newest to oldest.
        assertEquals(ffdcNew, files.get(2));
        assertEquals(ffdcOld, files.get(3));
    }

    /**
     * Tests that ffdc related logs files returned are capped at MAX_FILES_PER_TYPE and that the overflow indicator is set.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testGetFFDCLogsCapping() throws IOException {
        LogFileManager manager = LogFileManager.getInstance();
        Path logsDir = Files.createDirectories(tempDir.resolve("logs"));
        Path ffdcDir = Files.createDirectories(logsDir.resolve(LogFileManager.FFDC_DIR_NAME));

        Instant baseTime = Instant.now();
        for (int i = 1; i <= 6; i++) {
            Path summary = Files.createFile(ffdcDir.resolve(String.format("exception_summary_26.10.30_10.%02d.00.0.log", i)));
            Files.setLastModifiedTime(summary, FileTime.from(baseTime.plusSeconds(i)));

            Path ffdc = Files.createFile(ffdcDir.resolve(String.format("ffdc_26.10.30_10.%02d.00.0.log", i)));
            Files.setLastModifiedTime(ffdc, FileTime.from(baseTime.plusSeconds(i)));
        }

        LogSearchResult result = manager.getFFDCLogs(logsDir);
        assertTrue(result.exceedsMaxAllowed());
        assertEquals(LogFileManager.MAX_FILES_PER_TYPE, result.getFiles().size());
    }
}
