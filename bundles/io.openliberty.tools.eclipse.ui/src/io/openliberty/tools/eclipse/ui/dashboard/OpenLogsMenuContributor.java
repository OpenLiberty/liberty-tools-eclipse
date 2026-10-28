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
package io.openliberty.tools.eclipse.ui.dashboard;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.server.logs.LogConfig;
import io.openliberty.tools.eclipse.server.logs.LogFileManager;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

/**
 * Builds and populates the 'Open logs' entry in the Liberty Dashboard context menu.
 */
public class OpenLogsMenuContributor {

    /** Label of the 'Open logs' context menu entry. */
    public static final String MENU_ACTION_OPENLOGS = Messages.getMessage("logs_menu_open_logs");

    /** Label of the 'Open logs folder' context menu entry. */
    public static final String MENU_ACTION_OPENLOGS_FOLDER = Messages.getMessage("logs_menu_open_logs_folder");

    /** Label of the 'More...' context menu entry. */
    public static final String MENU_ACTION_OPENLOGS_MORE = Messages.getMessage("logs_menu_more");

    /** Label of the messages log type context menu entry. */
    public static final String MENU_ACTION_OPENLOGS_MSGS_TYPE = Messages.getMessage("logs_menu_type_messages");

    /** Label of the trace log type context menu entry. */
    public static final String MENU_ACTION_OPENLOGS_TRACE_TYPE = Messages.getMessage("logs_menu_type_trace");

    /** Label of the ffdc log type context menu entry. */
    public static final String MENU_ACTION_OPENLOGS_FFDC_TYPE = Messages.getMessage("logs_menu_type_ffdc");

    /**
     * Adds the separator and "Open logs" submenu to the given context menu manager for the project.
     * No entries are added when the project is a parent of a Liberty server module.
     *
     * @param mgr The context menu manager to contribute to.
     * @param project The project selected in the dashboard.
     */
    public static void contribute(IMenuManager mgr, ProjectModel project) {
        // Suppress the entry on multi-module parents.
        if (project.isParentOfServerModule()) {
            return;
        }

        mgr.add(new Separator());

        LogFileManager logFileManager = LogFileManager.getInstance();
        LogConfig config = logFileManager.resolveLogConfig(project);

        // Gray out the entry when the location cannot be determined or does not exist.
        if (config == null || config.getLogsDir() == null || !Files.isDirectory(config.getLogsDir())) {
            Action disabled = new Action(MENU_ACTION_OPENLOGS) {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void run() {
                    // No-op. Entry is disabled.
                }
            };
            disabled.setEnabled(false);
            mgr.add(disabled);
            return;
        }

        Path logsDir = config.getLogsDir();
        MenuManager logsMenu = new MenuManager(MENU_ACTION_OPENLOGS);

        addTypeSubmenu(logsMenu, MENU_ACTION_OPENLOGS_MSGS_TYPE,
                       logFileManager.getMessagesLogs(logsDir, config.getMessagesFileName()), logsDir);

        addTypeSubmenu(logsMenu, MENU_ACTION_OPENLOGS_TRACE_TYPE,
                       logFileManager.getTraceLogs(logsDir, config.getTraceFileName()), logsDir);

        addTypeSubmenu(logsMenu, MENU_ACTION_OPENLOGS_FFDC_TYPE,
                       logFileManager.getFFDCLogs(logsDir), logsDir.resolve(LogFileManager.FFDC_DIR_NAME));

        logsMenu.add(new Separator());
        logsMenu.add(new BrowseFolderAction(MENU_ACTION_OPENLOGS_FOLDER, logsDir));

        mgr.add(logsMenu);
    }

    /**
     * Adds a type submenu for messages, trace, or ffdc files. When no files match, adds a disabled
     * entry with the type label instead. When the result has more files than shown, a 'More...' entry
     * is added that browses the folder of the log type.
     *
     * @param parent The menu manager to add the submenu to.
     * @param label The label of the type submenu.
     * @param result The scan result for the log type.
     * @param fileFolder The folder that contains the log files and that the 'More...' entry will browse.
     */
    private static void addTypeSubmenu(MenuManager parent, String label, LogFileManager.LogSearchResult result, Path fileFolder) {
        if (result.getFiles().isEmpty()) {
            Action disabled = new Action(label) {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void run() {
                    // No-op. Type has no matching files.
                }
            };
            disabled.setEnabled(false);
            parent.add(disabled);
            return;
        }

        MenuManager sub = new MenuManager(label);
        for (Path file : result.getFiles()) {
            sub.add(new OpenFileAction(file.getFileName().toString(), file));
        }

        if (result.hasMore()) {
            sub.add(new Separator());
            sub.add(new BrowseFolderAction(MENU_ACTION_OPENLOGS_MORE, fileFolder));
        }

        parent.add(sub);
    }

    /**
     * Opens a log file in an Eclipse editor.
     */
    private static final class OpenFileAction extends Action {

        /** The log file to open. */
        private final Path file;

        /**
         * Creates an action that opens the given log file.
         *
         * @param label The menu label of the action.
         * @param file The log file to open.
         */
        OpenFileAction(String label, Path file) {
            super(label);
            this.file = file;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            openFile(file);
        }
    }

    /**
     * Opens a file dialog on a folder so that the user can select a file to open.
     */
    private static final class BrowseFolderAction extends Action {

        /** The folder that the file dialog opens on. */
        private final Path folder;

        /**
         * Creates an action that browses the given folder.
         *
         * @param label The menu label of the action.
         * @param folder The folder that the file dialog opens on.
         */
        BrowseFolderAction(String label, Path folder) {
            super(label);
            this.folder = folder;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            browseFolder(folder);
        }
    }

    /**
     * Opens a file dialog on the given folder and opens the file that the user selects. The dialog lists the
     * files of the folder whether or not the folder is in the workspace. Nothing happens if the user cancels.
     *
     * @param folder The folder that the file dialog opens on.
     */
    private static void browseFolder(Path folder) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, folder.toString());
        }

        if (!Files.isDirectory(folder)) {
            if (Trace.isEnabled()) {
                Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "Logs folder cannot be found: " + folder);
            }

            ErrorHandler.processErrorMessage(Messages.getMessage("logs_folder_not_found", folder.toString()), true);
            return;
        }

        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "No active workbench window. Unable to browse folder: " + folder);
            }
            return;
        }

        FileDialog dialog = new FileDialog(window.getShell(), SWT.OPEN);
        dialog.setText(Messages.getMessage("logs_file_dialog_title"));
        dialog.setFilterPath(folder.toString());
        String selected = dialog.open();

        if (selected != null) {
            openFile(Path.of(selected));
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, selected);
        }
    }

    /**
     * Opens a log file in an Eclipse editor. The file is opened as a workspace resource when the workspace
     * knows it. Otherwise it is opened directly from the file system.
     *
     * @param file The log file to open.
     */
    private static void openFile(Path file) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, file.toString());
        }

        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "No active workbench window.");
            }
            return;
        }

        if (!Files.exists(file)) {
            if (Trace.isEnabled()) {
                Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "Log file cannot be found: " + file);
            }

            ErrorHandler.processErrorMessage(Messages.getMessage("logs_open_file_not_found", file.getFileName().toString()), true);
            return;
        }

        IWorkbenchPage page = window.getActivePage();
        if (page == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "No active workbench page found. Unable to open log file: " + file);
            }
            return;
        }

        // Prefer opening as a workspace resource when the file is known to the workspace.
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IFile[] workspaceFiles = root.findFilesForLocationURI(file.toUri());
        IFile workspaceFile = null;
        for (IFile f : workspaceFiles) {
            if (f.exists()) {
                workspaceFile = f;
                break;
            }
        }

        try {
            if (workspaceFile != null) {
                // File is in the workspace. Open it as a workspace resource.
                IDE.openEditor(page, workspaceFile, true);
            } else {
                // File is outside the workspace. Open it directly from the filesystem.
                IFileStore fileStore = EFS.getLocalFileSystem().getStore(file.toUri());
                FileStoreEditorInput input = new FileStoreEditorInput(fileStore);
                IDE.openEditor(page, input, IDE.getEditorDescriptor(file.getFileName().toString(), true, true).getId());
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Failed to open log file: " + file, e);
            }

            ErrorHandler.processErrorMessage(Messages.getMessage("logs_open_file_failed", file.getFileName().toString()), e, true);
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, file.toString());
        }
    }
}
