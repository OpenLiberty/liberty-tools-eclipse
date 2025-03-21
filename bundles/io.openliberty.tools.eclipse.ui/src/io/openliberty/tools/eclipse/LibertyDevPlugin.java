/*******************************************************************************
* Copyright (c) 2022, 2025 IBM Corporation and others.
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
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.openliberty.tools.eclipse.logging.Trace;

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

        // Classify all projects in the workspace.
        DevModeOperations.getInstance().getProjectModel().createNewCompleteWorkspaceModelWithClassify();

        // Register a workspace listener for cleanup.
        registerListeners();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        DevModeOperations.getInstance().cancelRunningJobs();
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
        PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
            registerResourceChangeListener();
        });
    }

    /**
     * Unregisters listeners.
     */
    private void unregisterListeners() {
        unregisterResourceChangeListener();
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
}
