/*******************************************************************************
* Copyright (c) 2022, 2023 IBM Corporation and others.
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

import java.util.Hashtable;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolder2Listener;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.tm.terminal.view.ui.interfaces.ITerminalsView;
import org.eclipse.tm.terminal.view.ui.interfaces.IUIConstants;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.launch.StartTab;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTab;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTabController;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * The activator class controls the plug-in life cycle
 */
public class LibertyDevPlugin extends AbstractUIPlugin {

    /** Liberty tools plugin ID. */
    public static final String PLUGIN_ID = "io.openliberty.tools.eclipse.ui";

    /** Liberty tools debug ID. */
    public static final String DEBUG_OPTIONS_ID = "io.openliberty.tools.eclipse";

    /** Shared instance of this plugin. */
    private static LibertyDevPlugin plugin;

    /** Resource Change listener instance. */
    private IResourceChangeListener resourceChangeListener;

    /** Terminal view part listener instance. */
    private IPartListener2 viewPartListener;

    /** Terminal tab folder listener instance. */
    private CTabFolder2Listener tabFolderListener;

    /** Workbench page instance used to register the terminal part listener. */
    IWorkbenchPage iWorkbenchPage;

    /**
     * Constructor.
     */
    public LibertyDevPlugin() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        // Register the trace listener.
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(DebugOptions.LISTENER_SYMBOLICNAME, LibertyDevPlugin.DEBUG_OPTIONS_ID);
        context.registerService(DebugOptionsListener.class.getName(), new Trace(), props);

        // Register a workspace listener for cleanup.
        registerListeners();

        // Classify all projects in the workspace.
        DevModeOperations.getInstance().getProjectModel().createNewCompleteWorkspaceModelWithClassify();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        unregisterListeners();
        plugin = null;
        super.stop(context);

    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static LibertyDevPlugin getDefault() {
        return plugin;
    }

    /**
     * Register listeners.
     */
    private void registerListeners() {
        registerResourceChangeListener();
        registerPartListener();
    }

    /**
     * Unregisters listeners.
     */
    private void unregisterListeners() {
        unregisterResourceChangeListener();
        unregisterPartListener();
    }

    /**
     * Registers a resource change listener to process actions triggered by project updates.
     */
    private void registerResourceChangeListener() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { resourceChangeListener });
        }

        IWorkspace iWorkspace = ResourcesPlugin.getWorkspace();
        resourceChangeListener = new LibertyResourceChangeListener();
        iWorkspace.addResourceChangeListener(resourceChangeListener, IResourceChangeEvent.PRE_BUILD);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, resourceChangeListener);
        }
    }

    /**
     * Registers a part listener to process Terminal tab view termination cleanup prior to terminal disposal. It processes all the
     * active terminal tabs when the terminal view is closed.
     */
    public void registerPartListener() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { viewPartListener });
        }

        ProjectTabController tabController = ProjectTabController.getInstance();
        viewPartListener = new IPartListener2() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void partOpened(IWorkbenchPartReference partRef) {
                if (IUIConstants.ID.equals(partRef.getId())) {
                    tabController.refreshViewPart();
                    registerCTabFolderListener();
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void partClosed(IWorkbenchPartReference partRef) {
                if (IUIConstants.ID.equals(partRef.getId())) {
                    unregisterCTabFolderListener();
                    tabController.processTerminalViewCleanup();

                }
            }
        };

        IWorkbench iwb = PlatformUI.getWorkbench();
        IWorkbenchWindow activeWindow = iwb.getActiveWorkbenchWindow();
        if (activeWindow != null) {
            iWorkbenchPage = activeWindow.getActivePage();
            if (iWorkbenchPage != null) {
                iWorkbenchPage.addPartListener(viewPartListener);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, Utils.objectsToString(activeWindow, iWorkbenchPage, viewPartListener));
        }
    }

    /**
     * Registers a part listener to process Terminal tab view item termination cleanup prior to terminal disposal. It processes a
     * single active terminal tab closures.
     */
    public void registerCTabFolderListener() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { tabFolderListener });
        }

        ProjectTabController tabController = ProjectTabController.getInstance();
        tabFolderListener = new CTabFolder2Adapter() {

            /**
             * {@inheritDoc}
             */
            public void close(CTabFolderEvent event) {

                CTabItem item = (CTabItem) event.item;
                if (item != null && !item.isDisposed()) {
                    try {
                        String projectName = (String) item.getData(StartTab.PROJECT_NAME);
                        ProjectTab projectTab = tabController.getProjectTab(projectName);
                        tabController.exitDevModeOnTerminalTab(projectName, projectTab);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        CTabFolder tabFolder = getTerminalViewTabFolder();
        if (tabFolder != null) {
            tabFolder.addCTabFolder2Listener(tabFolderListener);
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, Utils.objectsToString(tabFolder, viewPartListener));
        }
    }

    /**
     * Returns the terminal tab view folder instance containing all active terminal tab items.
     * 
     * @return The terminal tab view folder instance containing all active terminal tab items.
     */
    private CTabFolder getTerminalViewTabFolder() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS);
        }

        CTabFolder tabFolder = null;
        IWorkbench iwb = PlatformUI.getWorkbench();
        IWorkbenchWindow activeWindow = iwb.getActiveWorkbenchWindow();
        if (activeWindow != null) {

            IWorkbenchPage activePage = activeWindow.getActivePage();
            if (activePage != null) {
                IViewPart iViewPart = activePage.findView(IUIConstants.ID);
                if (iViewPart != null || (iViewPart instanceof ITerminalsView)) {
                    ITerminalsView terminalView = (ITerminalsView) iViewPart;
                    tabFolder = terminalView.getAdapter(CTabFolder.class);

                    return tabFolder;
                }
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, Utils.objectsToString(activeWindow, tabFolder));
        }

        return tabFolder;
    }

    /**
     * Removes the resource change listener registered with the Eclipse workspace.
     */
    public void unregisterResourceChangeListener() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, resourceChangeListener);
        }

        IWorkspace iWorkspace = ResourcesPlugin.getWorkspace();
        iWorkspace.removeResourceChangeListener(resourceChangeListener);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, iWorkspace);
        }
    }

    /**
     * Removes the listener registered with the Eclipse terminal view folder.
     */
    public void unregisterCTabFolderListener() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, tabFolderListener);
        }

        ProjectTabController tabController = ProjectTabController.getInstance();
        tabController.unregisterCTabFolder2Listener(tabFolderListener);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, tabController);
        }
    }

    /**
     * Removes the listener registered to process terminal tab view item termination cleanup.
     */
    public void unregisterPartListener() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS);
        }

        if (iWorkbenchPage != null) {
            iWorkbenchPage.removePartListener(viewPartListener);
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, Utils.objectsToString(iWorkbenchPage, viewPartListener));
        }
    }
}
