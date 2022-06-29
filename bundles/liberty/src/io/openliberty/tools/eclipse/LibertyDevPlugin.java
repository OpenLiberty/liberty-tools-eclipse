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
    public static final String PLUGIN_ID = "liberty";

    // The shared instance
    private static LibertyDevPlugin plugin;

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
        props.put(DebugOptions.LISTENER_SYMBOLICNAME, LibertyDevPlugin.PLUGIN_ID);
        context.registerService(DebugOptionsListener.class.getName(), new Trace(), props);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
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
}
