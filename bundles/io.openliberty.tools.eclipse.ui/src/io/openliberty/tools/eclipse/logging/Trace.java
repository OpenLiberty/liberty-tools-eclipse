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
package io.openliberty.tools.eclipse.logging;

import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.osgi.service.debug.DebugTrace;

import io.openliberty.tools.eclipse.LibertyDevPlugin;

/**
 * Trace listener.
 */
public class Trace implements DebugOptionsListener {

    /**
     * Main debug option. Controls global logging.
     */
    public static final String DEBUG = "/debug";

    /**
     * Trace categories.
     */
    public static final String TRACE_TOOLS = "/trace/tools";
    public static final String TRACE_HANDLERS = "/trace/handlers";
    public static final String TRACE_UI = "/trace/ui";
    public static final String TRACE_UTILS = "/trace/utils";

    /**
     * Main logging option switch.
     */
    private static boolean debugEnabled;

    /**
     * Default DebugTrace instance.
     */
    private static DebugTrace debugTracer = null;

    /**
     * Returns true if the global debug option is configured to be true. False, otherwise.
     *
     * @return True if the debug option is configured to be true. False, otherwise.
     */
    public static boolean isEnabled() {
        return debugEnabled;
    }

    /**
     * Returns the default implementation of org.eclipse.osgi.service.debug.DebugTrace.
     *
     * @return The default implementation of org.eclipse.osgi.service.debug.DebugTrace
     */
    public static DebugTrace getTracer() {
        return debugTracer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void optionsChanged(DebugOptions options) {
        if (debugTracer == null) {
            debugTracer = options.newDebugTrace("liberty");
        }
        debugEnabled = options.getBooleanOption(LibertyDevPlugin.PLUGIN_ID + DEBUG, false);
    }
}