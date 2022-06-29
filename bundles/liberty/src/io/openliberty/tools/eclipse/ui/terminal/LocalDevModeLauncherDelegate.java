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
package io.openliberty.tools.eclipse.ui.terminal;

import java.util.Map;

import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.terminal.connector.local.launcher.LocalLauncherDelegate;
import org.eclipse.tm.terminal.view.core.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tm.terminal.view.ui.launcher.LauncherDelegateManager;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Local launcher delegate extension.
 */
public class LocalDevModeLauncherDelegate extends LocalLauncherDelegate {

    /**
     * LocalDevModeLauncherDelegate extension id.
     */
    public static final String id = "io.openliberty.tools.eclipse.ui.terminal.local.devmode.launcher.delegate";

    /**
     * Returns an instance of the LocalTerminalLauncherDelegate. Note that there should only be a single delegate instance
     * being used. TerminalService.createTerminalConnector by default does not require the delegate instances to be
     * unique, but the first instance created is returned.
     *
     * @return An instance of the LocalTerminalLauncherDelegate
     */
    public static LocalDevModeLauncherDelegate getInstance() {
        return (LocalDevModeLauncherDelegate) LauncherDelegateManager.getInstance().getLauncherDelegate(id, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITerminalConnector createTerminalConnector(Map<String, Object> properties) {
        String projectName = (String) properties.get(ITerminalsConnectorConstants.PROP_DATA);
        ProjectTabController tptm = ProjectTabController.getInstance();
        ITerminalConnector connector = tptm.getProjectConnector(projectName);

        if (connector == null) {
            connector = super.createTerminalConnector(properties);
            tptm.setProjectConnector(projectName, connector);

            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI,
                        "New terminal connection created for project: " + projectName + ". Connector: " + connector);
            }

        }

        return connector;
    }
}
