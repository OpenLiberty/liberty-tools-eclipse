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
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.ui.IPackagesViewPart;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.ShowInContext;

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

    /** Project Explorer view ID. */
    private static final String PROJECT_EXPLORER_ID = "org.eclipse.ui.navigator.ProjectExplorer";

    /** Package Explorer view ID. */
    private static final String PACKAGE_EXPLORER_ID = "org.eclipse.jdt.ui.PackageExplorer";

    /**
     * Adds the separator and "Open logs" submenu to the given context menu manager for the project.
     * Does nothing when the project is a parent of Liberty server modules.
     *
     * @param mgr     The context menu manager to contribute to.
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
                       logFileManager.getMessagesLogs(logsDir, config.getMessagesFileName()), logsDir, project);

        addTypeSubmenu(logsMenu, MENU_ACTION_OPENLOGS_TRACE_TYPE,
                       logFileManager.getTraceLogs(logsDir, config.getTraceFileName()), logsDir, project);

        addFfdcSubmenu(logsMenu, logFileManager.getFFDCLogs(logsDir), logsDir, project);

        logsMenu.add(new Separator());
        logsMenu.add(new RevealFolderAction(MENU_ACTION_OPENLOGS_FOLDER, logsDir, project));

        mgr.add(logsMenu);
    }

    /**
     * Adds a type submenu for messages or trace files. When no files match, adds a disabled entry
     * with the type label instead.
     *
     * @param parent  The menu manager to add the submenu to.
     * @param label   The label of the type submenu.
     * @param result  The scan result for the log type.
     * @param logsDir The Liberty logs directory. The More entry reveals this folder.
     * @param project The project selected in the dashboard.
     */
    private static void addTypeSubmenu(MenuManager parent, String label, LogFileManager.LogSearchResult result,
                                       Path logsDir, ProjectModel project) {
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
            sub.add(new RevealFolderAction(MENU_ACTION_OPENLOGS_MORE, logsDir, project));
        }

        parent.add(sub);
    }

    /**
     * Adds the ffdc type submenu. When no files match, adds a disabled entry instead. The 'More...' entry
     * reveals, sets focus, and expands the ffdc folder.
     *
     * @param parent  The menu manager to add the submenu to.
     * @param result  The scan result for the ffdc files.
     * @param logsDir The Liberty logs directory that contains the ffdc folder.
     * @param project The project selected in the dashboard.
     */
    private static void addFfdcSubmenu(MenuManager parent, LogFileManager.LogSearchResult result,
                                       Path logsDir, ProjectModel project) {
        Path ffdcDir = logsDir.resolve(LogFileManager.FFDC_DIR_NAME);

        if (result.getFiles().isEmpty()) {
            Action disabled = new Action(MENU_ACTION_OPENLOGS_FFDC_TYPE) {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void run() {
                    // No-op. No FFDC files found.
                }
            };
            disabled.setEnabled(false);
            parent.add(disabled);
            return;
        }

        MenuManager sub = new MenuManager(MENU_ACTION_OPENLOGS_FFDC_TYPE);
        for (Path file : result.getFiles()) {
            sub.add(new OpenFileAction(file.getFileName().toString(), file));
        }

        if (result.hasMore()) {
            sub.add(new Separator());
            sub.add(new RevealFolderAction(MENU_ACTION_OPENLOGS_MORE, ffdcDir, project));
        }

        parent.add(sub);
    }

    /**
     * Opens a log file in an Eclipse editor directly from the file system.
     */
    private static final class OpenFileAction extends Action {

        /** The log file to open. */
        private final Path file;

        /**
         * Creates an action that opens the given log file.
         *
         * @param label The menu label of the action.
         * @param file  The log file to open.
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

            // Check if the file exists.
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

    /**
     * Reveals a logs folder in Package Explorer or Project Explorer. If the folder is not
     * in the user's workspace, the folder is opened using the system's file manager.
     */
    private static final class RevealFolderAction extends Action {

        /** The folder to show. */
        private final Path folder;

        /** The project that owns the folder. */
        private final ProjectModel project;

        /**
         * Creates an action that reveals the given folder.
         *
         * @param label   The menu label of the action.
         * @param folder  The folder to reveal.
         * @param project The project that owns the folder.
         */
        RevealFolderAction(String label, Path folder, ProjectModel project) {
            super(label);
            this.folder = folder;
            this.project = project;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            if (Trace.isEnabled()) {
                Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { folder.toString(), project.getName() });
            }

            // Check if the folder is a directory.
            if (!Files.isDirectory(folder)) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().traceExit(Trace.TRACE_TOOLS, "Logs folder cannot be found: " + folder);
                }

                ErrorHandler.processErrorMessage(Messages.getMessage("logs_folder_not_found", folder.getFileName().toString()), true);
                return;
            }

            // Refresh the workspace path leading to the folder in the background, and show it.
            Job refreshJob = new Job(Messages.getMessage("logs_refresh_job")) {
                /**
                 * {@inheritDoc}
                 */
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    refreshToFolder(folder, project, monitor);
                    Display.getDefault().asyncExec(() -> showFolder(folder));
                    return Status.OK_STATUS;
                }
            };
            refreshJob.setSystem(true);
            refreshJob.schedule();

            if (Trace.isEnabled()) {
                Trace.getTracer().traceExit(Trace.TRACE_TOOLS, folder.toString());
            }
        }
    }

    /**
     * Refreshes the workspace from the project root down to the target folder, one level at a time.
     *
     * @param folder  The folder that will be revealed.
     * @param project The project that owns the folder.
     * @param monitor The progress monitor for the refresh.
     */
    private static void refreshToFolder(Path folder, ProjectModel project, IProgressMonitor monitor) {
        // Build the path segments from the project down to the target folder.
        Path projectPath = Path.of(project.getPath());
        Path relative;
        try {
            relative = projectPath.relativize(folder);
        } catch (IllegalArgumentException e) {
            // Folder is not under the project. No workspace refresh needed.
            return;
        }

        // Refresh one container at a time, stopping when a level is not found.
        IContainer current = project.getIProject();
        try {
            current.refreshLocal(IResource.DEPTH_ONE, monitor);
            for (int i = 0; i < relative.getNameCount(); i++) {
                String segment = relative.getName(i).toString();
                IResource member = current.findMember(segment);
                if (!(member instanceof IContainer)) {
                    break;
                }
                current = (IContainer) member;
                current.refreshLocal(IResource.DEPTH_ONE, monitor);
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Failed to refresh workspace path to: " + folder, e);
            }
        }
    }

    /**
     * Shows the input folder in the explorer view that is currently being used.
     * If neither of the explorers are opened, the project explorer is opened to show the input folder.
     * If the input folder is not in the workspace, open it using the system file manager.
     *
     * @param folder The folder to show.
     */
    private static void showFolder(Path folder) {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "No active workbench window found. Unable to show folder: " + folder);
            }
            return;
        }

        IWorkbenchPage page = window.getActivePage();
        if (page == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "No active workbench page found. Unable to show folder: " + folder);
            }
            return;
        }

        // Map the folder to a workspace resource. Prefer a match in the owning project.
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IContainer[] containers = root.findContainersForLocationURI(folder.toUri());

        IContainer target = null;
        for (IContainer c : containers) {
            if (c.exists()) {
                target = c;
                break;
            }
        }

        // Not in the workspace or not yet visible after refresh.
        if (target == null) {
            Program.launch(folder.toString());
            return;
        }

        // Use whichever explorer is already open. If none are opened, open the Project Explorer.
        IViewPart openExplorer = page.findView(PROJECT_EXPLORER_ID);
        if (openExplorer == null) {
            openExplorer = page.findView(PACKAGE_EXPLORER_ID);
        }

        String preferredId = (openExplorer != null) ? openExplorer.getSite().getId() : PROJECT_EXPLORER_ID;
        showInView(page, preferredId, target);
    }

    /**
     * Shows and expands the target container in the given view.
     *
     * @param page   The workbench page that hosts the view.
     * @param viewId The ID of the view to reveal the container in.
     * @param target The container to reveal.
     */
    private static void showInView(IWorkbenchPage page, String viewId, IContainer target) {
        try {
            IViewPart view = page.showView(viewId);
            if (view instanceof IShowInTarget) {
                ShowInContext context = new ShowInContext(null, new StructuredSelection(target));
                boolean shown = ((IShowInTarget) view).show(context);
                if (shown) {
                    expandInView(view, target);
                }
            }
        } catch (PartInitException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Could not open view: " + viewId, e);
            }
        }
    }

    /**
     * Selects and expands the target container in the given view. Project Explorer (CommonNavigator)
     * and Package Explorer (IPackagesViewPart) both expose public API to expand and select, so both
     * behave identically.
     *
     * @param view   The view that contains the container.
     * @param target The container to select and expand.
     */
    private static void expandInView(IViewPart view, IContainer target) {
        try {
            if (view instanceof CommonNavigator) {
                // Project Explorer and any CommonNavigator-based view.
                CommonNavigator nav = (CommonNavigator) view;
                nav.getCommonViewer().expandToLevel(target, 1);
                nav.getCommonViewer().setSelection(new StructuredSelection(target), true);
            } else if (view instanceof IPackagesViewPart) {
                // Package Explorer.
                IPackagesViewPart pkg = (IPackagesViewPart) view;
                pkg.getTreeViewer().expandToLevel(target, 1);
                pkg.getTreeViewer().setSelection(new StructuredSelection(target), true);
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Could not expand folder in view: " + view.getSite().getId(), e);
            }
        }
    }
}
