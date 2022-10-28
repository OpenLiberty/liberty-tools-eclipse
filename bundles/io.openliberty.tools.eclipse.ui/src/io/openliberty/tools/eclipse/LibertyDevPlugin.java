/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
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
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * The activator class controls the plug-in life cycle
 */
public class LibertyDevPlugin extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "io.openliberty.tools.eclipse.ui";

    public static final String DEBUG_OPTIONS_ID = "io.openliberty.tools.eclipse";

    // The shared instance
    private static LibertyDevPlugin plugin;

    private static IResourceChangeListener resourceChangeListener;

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
        resourceChangeListener = new LibertyResourceChangeListener();
        ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener, IResourceChangeEvent.PRE_BUILD);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener);
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static LibertyDevPlugin getDefault() {
        return plugin;
    }
}
