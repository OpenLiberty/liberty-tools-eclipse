/*******************************************************************************
* Copyright (c) 2022, 2026 IBM Corporation and others.
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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;

/**
 * The activator class controls the plug-in life cycle.
 */
public class LibertyDevPlugin extends AbstractUIPlugin {

    /** Liberty tools plugin ID. */
    public static final String PLUGIN_ID = "io.openliberty.tools.eclipse.ui";

    /** Liberty tools debug ID. */
    public static final String DEBUG_OPTIONS_ID = "io.openliberty.tools.eclipse";

    /** Shared instance of this plugin. */
    private static LibertyDevPlugin plugin;

    /** Bundle reference. */
    private static Bundle bundle;

    /** Cached SVG-support check result; null means not yet evaluated. */
    private static volatile Boolean isSvgSupported;

    /** Resource change listener instance. */
    private IResourceChangeListener resourceChangeListener;

    /**
     * Constructor.
     */
    public LibertyDevPlugin() {
    }

    /**
     * Starts the plugin, initialises the workspace model, and registers listeners.
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        bundle = context.getBundle();

        // Register the trace listener.
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(DebugOptions.LISTENER_SYMBOLICNAME, LibertyDevPlugin.DEBUG_OPTIONS_ID);
        context.registerService(DebugOptionsListener.class.getName(), new Trace(), props);

        // Classify all projects in the workspace.
        DevModeOperations.getInstance().getWorkspaceModel().createNewCompleteWorkspaceModelWithClassify();

        // Register a workspace listener for cleanup.
        registerListeners();
    }

    /**
     * Stops the plugin, cancels running jobs, and unregisters listeners.
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        DevModeOperations.getInstance().cancelRunningJobs();
        unregisterListeners();
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance.
     *
     * @return The shared instance.
     */
    public static LibertyDevPlugin getDefault() {
        return plugin;
    }

    /**
     * Registers listeners.
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

    /**
     * Returns true if the runtime platform supports native SVG rendering.
     *
     * @return True if SVG rendering is available; false otherwise.
     */
    public static synchronized boolean isSvgSupported() {
        if (isSvgSupported == null) {
            Bundle svgBundle = Platform.getBundle("org.eclipse.swt.svg");
            isSvgSupported = (svgBundle != null
                              && (svgBundle.getState() & (Bundle.RESOLVED | Bundle.ACTIVE)) != 0);
        }

        return isSvgSupported;
    }

    /**
     * Loads the best available icon for the given base name from the plugin bundle.
     *
     * Background:
     * SVG icon formats are supported starting in Eclipse 2025-06. The use of PNG icon
     * formats has been deprecated. In order to support older IDEs, we need to handle
     * both type of icons since SVG icon formats are the standard now.
     *
     * Resolution order:
     * SVG -> PNG.
     *
     * @param baseName The icon base name without extension, e.g. "mavenTag".
     *
     * @return The loaded Image, or null if the icon could not be found.
     */
    public static Image loadIcon(String baseName) {
        try {
            Bundle b = getDefault().getBundle();

            // Check if there is SVG support. If so use SVG icon.
            if (isSvgSupported()) {
                URL url = b.getEntry("icons/" + baseName + ".svg");
                if (url != null) {
                    ImageDescriptor desc = AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, url.getPath());
                    if (desc != null) {
                        return desc.createImage();
                    }
                }
            }

            // If there is not SVG support or the SVG icon was not found, check
            // for png icons.
            URL url = b.getEntry("icons/" + baseName + ".png");
            if (url == null) {
                return null;
            }
            ImageDescriptor desc = AbstractUIPlugin.imageDescriptorFromPlugin(PLUGIN_ID, url.getPath());
            
            return (desc != null) ? desc.createImage() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the path to the input directory within this plugin's workarea.
     * The workarea is located in the user's currently active workspace:
     * workspaces/my_workspace/.metadata/.plugins/io.openliberty.tools.eclipse.ui/subDirPath
     *
     * @param subDirPath The well formed sub-directory path.
     *
     * @return The path to the input directory within this plugin's workarea.
     *
     * @throws IOException If an error occurs while creating the directory.
     */
    public static String getWorkareaDir(String subDirPath) throws IOException {
        IPath pluginStateDirPath = Platform.getStateLocation(bundle);
        String stateDirPath = pluginStateDirPath.toOSString();
        Path fullPath = pluginStateDirPath.toPath();
        if (subDirPath != null && !subDirPath.isEmpty()) {
            fullPath = Paths.get(stateDirPath, subDirPath);
        }

        File outputDir = fullPath.toFile();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException(Messages.getMessage("starter_workarea_dir_error", fullPath));
        }

        return fullPath.toString();
    }
}
