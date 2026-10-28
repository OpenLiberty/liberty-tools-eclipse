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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
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
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.navigator.CommonNavigator;
import org.eclipse.ui.navigator.ICommonFilterDescriptor;
import org.eclipse.ui.navigator.INavigatorFilterService;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.ShowInContext;

import io.openliberty.tools.eclipse.logging.Logger;
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
     * No entries are added when the project is a parent of a Liberty server module.
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

        addTypeSubmenu(logsMenu, MENU_ACTION_OPENLOGS_FFDC_TYPE,
                       logFileManager.getFFDCLogs(logsDir), logsDir.resolve(LogFileManager.FFDC_DIR_NAME), project);

        logsMenu.add(new Separator());
        logsMenu.add(new RevealFolderAction(MENU_ACTION_OPENLOGS_FOLDER, logsDir, project));

        mgr.add(logsMenu);
    }

    /**
     * Adds a type submenu for messages, trace, or ffdc files. When no files match, adds a disabled
     * entry with the type label instead. When the result has more files than shown, a 'More...' entry
     * is added that reveals fileFolder.
     *
     * @param parent     The menu manager to add the submenu to.
     * @param label      The label of the type submenu.
     * @param result     The scan result for the log type.
     * @param fileFolder The folder that contains the log files and that the 'More...' entry will reveal.
     * @param project    The project selected in the dashboard.
     */
    private static void addTypeSubmenu(MenuManager parent, String label, LogFileManager.LogSearchResult result,
                                       Path fileFolder, ProjectModel project) {
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
            sub.add(new RevealFolderAction(MENU_ACTION_OPENLOGS_MORE, fileFolder, project));
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
                    Display.getDefault().asyncExec(() -> showFolder(folder, project));
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
        // A folder outside the project directory is not part of the workspace. No refresh is needed.
        Path projectPath = Path.of(project.getPath()).toAbsolutePath().normalize();
        Path normalizedFolder = folder.toAbsolutePath().normalize();
        if (!normalizedFolder.startsWith(projectPath)) {
            return;
        }
        Path relative = projectPath.relativize(normalizedFolder);

        // Refresh one level at a time, stopping when a level is not found.
        IProject iProject = project.getIProject();
        try {
            iProject.refreshLocal(IResource.DEPTH_ONE, monitor);
            for (int i = 0; i < relative.getNameCount(); i++) {
                IFolder level = iProject.getFolder(org.eclipse.core.runtime.Path.fromOSString(relative.subpath(0, i + 1).toString()));
                if (!level.exists()) {
                    break;
                }
                level.refreshLocal(IResource.DEPTH_ONE, monitor);
            }
        } catch (Exception e) {
            String traceMsg = "Failed to refresh workspace path to: " + folder;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, traceMsg, e);
            }
            Logger.logError(traceMsg, e);
        }
    }

    /**
     * Shows the input folder in the explorer view that is currently being used.
     * If neither explorer is open, the Project Explorer is opened to show the input folder.
     * If the input folder is not in the workspace, opens it using the system file manager.
     *
     * @param folder  The folder to show.
     * @param project The project that owns the folder.
     */
    private static void showFolder(Path folder, ProjectModel project) {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            String traceMsg = "No active workbench window found. Unable to show folder: " + folder;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, traceMsg);
            }
            Logger.logError(traceMsg);
            return;
        }

        IWorkbenchPage page = window.getActivePage();
        if (page == null) {
            String traceMsg = "No active workbench page found. Unable to show folder: " + folder;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, traceMsg);
            }
            Logger.logError(traceMsg);
            return;
        }

        // Resolve the folder against the current project. A folder outside the project, such as a
        // customized Liberty output directory, will not have a resource because it is external to the workspace.
        Path projectPath = Path.of(project.getPath());
        IFolder target = null;
        if (folder.startsWith(projectPath)) {
            Path relative = projectPath.relativize(folder);
            IFolder candidate = project.getIProject().getFolder(org.eclipse.core.runtime.Path.fromOSString(relative.toString()));
            if (candidate.exists()) {
                target = candidate;
            }
        }

        // Not in the workspace or not yet visible after refresh. Use the system file manager.
        if (target == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Folder is not in the workspace. Opening with the system file manager: " + folder);
            }
            Program.launch(folder.toString());
            return;
        }

        // Use the explorer that is currently in use. If none are opened, open the Project Explorer.
        String explorerId = selectExplorerToUse(page);
        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_TOOLS, "Selected explorer: " + explorerId + ". Target: " + target.getFullPath());
        }
        showInView(page, explorerId, target);
    }

    /**
     * Determines which explorer view to use when both or neither the Project Explorer and the Package Explorer are open.
     *
     * @param page The workbench page that hosts the views.
     *
     * @return The ID of the explorer view to use. The Project Explorer ID if neither explorer is open.
     */
    private static String selectExplorerToUse(IWorkbenchPage page) {
        // Prefer the explorer the user is working in, if one is the active part. The action is usually started
        // from the Liberty dashboard, so this applies only when the dashboard is not the active part.
        IWorkbenchPart activePart = page.getActivePart();
        if (activePart instanceof IViewPart) {
            String activeId = ((IViewPart) activePart).getSite().getId();
            if (PROJECT_EXPLORER_ID.equals(activeId) || PACKAGE_EXPLORER_ID.equals(activeId)) {
                return activeId;
            }
        }

        // Neither explorer is active. Select a visible one. The Project Explorer takes precedence if both are visible.
        IViewPart projectExplorer = page.findView(PROJECT_EXPLORER_ID);
        IViewPart packageExplorer = page.findView(PACKAGE_EXPLORER_ID);
        if (projectExplorer != null && page.isPartVisible(projectExplorer)) {
            return PROJECT_EXPLORER_ID;
        }
        if (packageExplorer != null && page.isPartVisible(packageExplorer)) {
            return PACKAGE_EXPLORER_ID;
        }

        // No explorer is visible. Use the Package Explorer only if it is the only one open. Otherwise default to the
        // Project Explorer, which is also the explorer opened when neither exists.
        if (projectExplorer == null && packageExplorer != null) {
            return PACKAGE_EXPLORER_ID;
        }
        return PROJECT_EXPLORER_ID;
    }

    /**
     * Shows and expands the target resource in the given view. For the Project Explorer, the user is asked
     * to remove any filters that hide the target. Nothing is shown if the user declines. The Package Explorer
     * uses its own built-in filter dialog when it cannot reveal the target.
     *
     * @param page The workbench page that hosts the view.
     * @param viewId The ID of the view to reveal the resource in.
     * @param target The resource to reveal.
     */
    private static void showInView(IWorkbenchPage page, String viewId, IResource target) {
        try {
            IViewPart view = page.showView(viewId);

            if (view instanceof CommonNavigator && !removeBlockingFilters((CommonNavigator) view, target)) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "The user chose not to remove the filters that hide: " + target.getFullPath());
                }
                return;
            }

            if (view instanceof IShowInTarget) {
                ShowInContext context = new ShowInContext(null, new StructuredSelection(target));
                boolean shown = ((IShowInTarget) view).show(context);
                if (shown) {
                    expandInView(view, target);
                }
            }
        } catch (PartInitException e) {
            String traceMsg = "Could not open view: " + viewId;
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, traceMsg, e);
            }
            Logger.logError(traceMsg, e);
        }
    }

    /**
     * Asks the user whether to deactivate the Project Explorer filters that hide the target resource, and
     * deactivates them when the user agrees. A filter is treated as blocking when its select method returns false
     * for the top level folder of the project that contains the target, such as the Gradle build folder. The
     * deactivation is persisted by the filter service.
     *
     * @param navigator The Project Explorer.
     * @param target The resource that must be visible.
     *
     * @return True if the target can be revealed. False if the user declined to remove the blocking filters.
     */
    private static boolean removeBlockingFilters(CommonNavigator navigator, IResource target) {
        INavigatorFilterService filterService = navigator.getNavigatorContentService().getFilterService();

        // Find the top level folder of the project that contains the target.
        IResource topFolder = target;
        while (topFolder.getParent() != null && topFolder.getParent().getType() != IResource.PROJECT) {
            topFolder = topFolder.getParent();
        }

        List<String> activeIds = new ArrayList<>();
        List<String> blockingIds = new ArrayList<>();
        for (ICommonFilterDescriptor descriptor : filterService.getVisibleFilterDescriptors()) {
            String id = descriptor.getId();
            if (!filterService.isActive(id)) {
                continue;
            }
            activeIds.add(id);

            ViewerFilter filter = filterService.getViewerFilter(descriptor);
            if (filter != null && !filter.select(navigator.getCommonViewer(), topFolder.getParent(), topFolder)) {
                blockingIds.add(id);
            }
        }

        if (blockingIds.isEmpty()) {
            return true;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_TOOLS, "Project Explorer filters hide " + topFolder.getFullPath() + ": " + blockingIds);
        }

        boolean remove = MessageDialog.openQuestion(navigator.getSite().getShell(), Messages.getMessage("logs_filtered_dialog_title"),
                                                    Messages.getMessage("logs_filtered_dialog_message"));
        if (!remove) {
            return false;
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_TOOLS, "Deactivating Project Explorer filters that hide " + topFolder.getFullPath() + ": " + blockingIds);
        }

        // The service deactivates every visible filter that is not in the list, so keep the other active filters.
        activeIds.removeAll(blockingIds);
        filterService.activateFilterIdsAndUpdateViewer(activeIds.toArray(new String[0]));
        return true;
    }

    /**
     * Selects and expands the target resource in the given view. Project Explorer (CommonNavigator)
     * and Package Explorer (IPackagesViewPart) both expose public API to expand and select, so both
     * behave identically.
     *
     * @param view   The view that contains the resource.
     * @param target The resource to select and expand.
     */
    private static void expandInView(IViewPart view, IResource target) {
        try {
            if (view instanceof CommonNavigator) {
                // Project Explorer.
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
            String traceMsg = "Could not expand folder in view: " + view.getSite().getId();
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, traceMsg, e);
            }
            Logger.logError(traceMsg, e);
        }
    }
}
