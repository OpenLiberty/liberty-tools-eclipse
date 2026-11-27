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
package io.openliberty.tools.eclipse.ls.plugin;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class LibertyToolsLSPlugin extends AbstractUIPlugin {

    // The plug-in ID
    public static final String PLUGIN_ID = "io.openliberty.tools.eclipse.lsp4e"; //$NON-NLS-1$

    // The shared instance
    private static LibertyToolsLSPlugin plugin;

    /**
     * The constructor
     */
    public LibertyToolsLSPlugin() {
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        restoreJavaPreferenceNode();
    }

    /**
     * The JDT LS bundle (org.eclipse.jdt.ls.core) redirects the global JavaManipulation preference node from
     * org.eclipse.jdt.ui to its own node when it activates. That causes JDT operations in the IDE, such as Organize
     * Imports, to read JDT LS defaults instead of the user's JDT UI preferences. Activate JDT LS here and point the
     * preference node back to JDT UI.
     */
    private void restoreJavaPreferenceNode() {
        try {
            Bundle jdtLs = Platform.getBundle("org.eclipse.jdt.ls.core"); //$NON-NLS-1$
            if (jdtLs != null && jdtLs.getState() != Bundle.ACTIVE) {
                jdtLs.start(Bundle.START_TRANSIENT);
            }
            if (!JavaUI.ID_PLUGIN.equals(JavaManipulation.getPreferenceNodeId())) {
                // setPreferenceNodeId only accepts a new value when the current one is null.
                JavaManipulation.setPreferenceNodeId(null);
                JavaManipulation.setPreferenceNodeId(JavaUI.ID_PLUGIN);
            }
        } catch (Exception e) {
            logException("Unable to restore the Java preference node.", e); //$NON-NLS-1$
        }
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
    public static LibertyToolsLSPlugin getDefault() {
        return plugin;
    }

    public static void logException(String localizedMessage, JavaModelException e) {
        // TODO Auto-generated method stub

    }

    public static String getPluginId() {
        return LibertyToolsLSPlugin.PLUGIN_ID;
    }

    public static void log(IStatus status) {
        getDefault().getLog().log(status);
    }

    public static void logException(String errMsg, Throwable ex) {
        getDefault().getLog().log(new Status(IStatus.ERROR, getPluginId(), errMsg, ex));

    }

}
