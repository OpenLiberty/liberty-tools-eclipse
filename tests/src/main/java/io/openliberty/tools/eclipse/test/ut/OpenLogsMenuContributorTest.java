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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;

import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.ProjectModel.BuildType;
import io.openliberty.tools.eclipse.server.logs.LogFileManager;
import io.openliberty.tools.eclipse.ui.dashboard.OpenLogsMenuContributor;

/**
 * Unit tests for OpenLogsMenuContributor.
 */
public class OpenLogsMenuContributorTest {

    /** Shared temporary directory for test files. */
    private static Path tempDir;

    /**
     * Creates the shared temporary root directory used by all tests.
     *
     * @throws IOException If the temporary directory cannot be created.
     */
    @BeforeAll
    public static void setup() throws IOException {
        tempDir = Files.createTempDirectory("OpenLogsMenuContributorTestDir");
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
     * Tests that no items are contributed when the project is a parent of a server module.
     */
    @Test
    public void testContributeSuppressedOnParentOfServerModule() {
        ProjectModel project = mock(ProjectModel.class);
        when(project.isParentOfServerModule()).thenReturn(true);

        MenuManager menuManager = new MenuManager();
        OpenLogsMenuContributor.contribute(menuManager, project);

        assertEquals(0, menuManager.getSize());
    }

    /**
     * Tests that a disabled Open logs action is added when the logs directory does not exist on disk.
     */
    @Test
    public void testContributeWhenLogsDirectoryDoesNotExist() {
        ProjectModel project = mock(ProjectModel.class);
        when(project.isParentOfServerModule()).thenReturn(false);
        when(project.getPath()).thenReturn(tempDir.toString());
        when(project.getBuildType()).thenReturn(BuildType.Maven);

        MenuManager menuManager = new MenuManager();
        OpenLogsMenuContributor.contribute(menuManager, project);

        IContributionItem[] items = menuManager.getItems();
        assertEquals(2, items.length);
        assertInstanceOf(Separator.class, items[0]);

        assertInstanceOf(ActionContributionItem.class, items[1]);
        ActionContributionItem actionItem = (ActionContributionItem) items[1];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS, actionItem.getAction().getText());
        assertFalse(actionItem.getAction().isEnabled());
    }

    /**
     * Tests that a cascading menu is added when the logs directory exists.
     * The type items must be disabled when no log files are present.
     *
     * @throws IOException If creating the logs directory fails.
     */
    @Test
    public void testContributeWhenLogsDirectoryExistsEmpty() throws IOException {
        Files.createDirectories(tempDir.resolve(LogFileManager.DEFAULT_MAVEN_LOGS_REL_PATH));

        ProjectModel project = mock(ProjectModel.class);
        when(project.isParentOfServerModule()).thenReturn(false);
        when(project.getPath()).thenReturn(tempDir.toString());
        when(project.getBuildType()).thenReturn(BuildType.Maven);

        MenuManager menuManager = new MenuManager();
        OpenLogsMenuContributor.contribute(menuManager, project);

        IContributionItem[] items = menuManager.getItems();
        assertEquals(2, items.length);
        assertInstanceOf(Separator.class, items[0]);

        assertInstanceOf(MenuManager.class, items[1]);
        MenuManager logsMenu = (MenuManager) items[1];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS, logsMenu.getMenuText());

        IContributionItem[] subItems = logsMenu.getItems();
        assertEquals(5, subItems.length);

        // Messages type entry (disabled).
        assertInstanceOf(ActionContributionItem.class, subItems[0]);
        ActionContributionItem msgsItem = (ActionContributionItem) subItems[0];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_MSGS_TYPE, msgsItem.getAction().getText());
        assertFalse(msgsItem.getAction().isEnabled());

        // Trace type entry (disabled).
        assertInstanceOf(ActionContributionItem.class, subItems[1]);
        ActionContributionItem traceItem = (ActionContributionItem) subItems[1];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_TRACE_TYPE, traceItem.getAction().getText());
        assertFalse(traceItem.getAction().isEnabled());

        // FFDC type entry (disabled).
        assertInstanceOf(ActionContributionItem.class, subItems[2]);
        ActionContributionItem ffdcItem = (ActionContributionItem) subItems[2];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_FFDC_TYPE, ffdcItem.getAction().getText());
        assertFalse(ffdcItem.getAction().isEnabled());

        // Separator and Open logs folder action.
        assertInstanceOf(Separator.class, subItems[3]);
        assertInstanceOf(ActionContributionItem.class, subItems[4]);
        ActionContributionItem folderItem = (ActionContributionItem) subItems[4];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_FOLDER, folderItem.getAction().getText());
        assertTrue(folderItem.getAction().isEnabled());
    }

    /**
     * Tests that populated type submenus are added when log files are present.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testContributeWithLogFiles() throws IOException {
        Path logsDir = Files.createDirectories(tempDir.resolve(LogFileManager.DEFAULT_GRADLE_LOGS_REL_PATH));
        Path ffdcDir = Files.createDirectories(logsDir.resolve(LogFileManager.FFDC_DIR_NAME));

        Files.createFile(logsDir.resolve("messages.log"));
        Files.createFile(logsDir.resolve("trace.log"));
        Files.createFile(ffdcDir.resolve("exception_summary_26.10.30_10.00.00.0.log"));

        ProjectModel project = mock(ProjectModel.class);
        when(project.isParentOfServerModule()).thenReturn(false);
        when(project.getPath()).thenReturn(tempDir.toString());
        when(project.getBuildType()).thenReturn(BuildType.Gradle);

        MenuManager menuManager = new MenuManager();
        OpenLogsMenuContributor.contribute(menuManager, project);

        IContributionItem[] items = menuManager.getItems();
        MenuManager logsMenu = (MenuManager) items[1];
        IContributionItem[] subItems = logsMenu.getItems();

        // Messages submenu contains 1 file action.
        assertInstanceOf(MenuManager.class, subItems[0]);
        MenuManager msgsMenu = (MenuManager) subItems[0];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_MSGS_TYPE, msgsMenu.getMenuText());
        assertEquals(1, msgsMenu.getItems().length);
        assertEquals("messages.log", ((ActionContributionItem) msgsMenu.getItems()[0]).getAction().getText());

        // Trace submenu contains 1 file action.
        assertInstanceOf(MenuManager.class, subItems[1]);
        MenuManager traceMenu = (MenuManager) subItems[1];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_TRACE_TYPE, traceMenu.getMenuText());
        assertEquals(1, traceMenu.getItems().length);
        assertEquals("trace.log", ((ActionContributionItem) traceMenu.getItems()[0]).getAction().getText());

        // FFDC submenu contains 1 file action.
        assertInstanceOf(MenuManager.class, subItems[2]);
        MenuManager ffdcMenu = (MenuManager) subItems[2];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_FFDC_TYPE, ffdcMenu.getMenuText());
        assertEquals(1, ffdcMenu.getItems().length);
        assertEquals("exception_summary_26.10.30_10.00.00.0.log", ((ActionContributionItem) ffdcMenu.getItems()[0]).getAction().getText());
    }

    /**
     * Tests that a More... action and separator are appended when the number of log files exceeds the maximum allowed.
     *
     * @throws IOException If creating test files fails.
     */
    @Test
    public void testContributeWithOverflowLogFiles() throws IOException {
        Path logsDir = Files.createDirectories(tempDir.resolve(LogFileManager.DEFAULT_MAVEN_LOGS_REL_PATH));

        // Create the active messages.log as the newest file plus 11 rolled log files (12 total).
        Instant baseTime = Instant.now();
        for (int i = 1; i <= 11; i++) {
            Path file = Files.createFile(logsDir.resolve(String.format("messages_26.10.30_10.%02d.00.log", i)));
            Files.setLastModifiedTime(file, FileTime.from(baseTime.plusSeconds(i)));
        }
        Path activeLog = Files.createFile(logsDir.resolve("messages.log"));
        Files.setLastModifiedTime(activeLog, FileTime.from(baseTime.plusSeconds(100)));

        ProjectModel project = mock(ProjectModel.class);
        when(project.isParentOfServerModule()).thenReturn(false);
        when(project.getPath()).thenReturn(tempDir.toString());
        when(project.getBuildType()).thenReturn(BuildType.Maven);

        MenuManager menuManager = new MenuManager();
        OpenLogsMenuContributor.contribute(menuManager, project);

        IContributionItem[] items = menuManager.getItems();
        MenuManager logsMenu = (MenuManager) items[1];
        MenuManager msgsMenu = (MenuManager) logsMenu.getItems()[0];

        // 10 file actions + 1 separator + 1 More... action = 12 contribution items.
        IContributionItem[] msgsItems = msgsMenu.getItems();
        assertEquals(12, msgsItems.length);

        for (int i = 0; i < 10; i++) {
            assertInstanceOf(ActionContributionItem.class, msgsItems[i]);
        }
        assertEquals("messages.log", ((ActionContributionItem) msgsItems[0]).getAction().getText());
        assertInstanceOf(Separator.class, msgsItems[10]);
        assertInstanceOf(ActionContributionItem.class, msgsItems[11]);

        ActionContributionItem moreItem = (ActionContributionItem) msgsItems[11];
        assertEquals(OpenLogsMenuContributor.MENU_ACTION_OPENLOGS_MORE, moreItem.getAction().getText());
        assertTrue(moreItem.getAction().isEnabled());
    }
}
