/*******************************************************************************
* Copyright (c) 2024 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.debug;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.internal.debug.ui.DebugUIMessages;
import org.eclipse.jdt.internal.debug.ui.HotCodeReplaceErrorDialog;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.ui.launch.StartTab;

/**
 * This class is an extension of the Eclipse JDT HotCodeReplaceErrorDialog. It provides
 * a custom display when a hot code replace failure occurs allowing for the user to refresh the
 * debugger.
 */
public class LibertyHotCodeReplaceErrorDialog extends HotCodeReplaceErrorDialog {

    public LibertyHotCodeReplaceErrorDialog(Shell parentShell, String dialogTitle, String message, IStatus status, String preferenceKey,
            String toggleMessage, IPreferenceStore store, IDebugTarget target) {
        super(parentShell, dialogTitle, message, status, preferenceKey, toggleMessage, store, target);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createDetailsButton(parent);
        getButton(IDialogConstants.OK_ID).setText(DebugUIMessages.HotCodeReplaceErrorDialog_0);
        createButton(parent, DISCONNECT_ID, "Refresh", false);

        blockMnemonicWithoutModifier(getToggleButton());
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.jface.dialogs.Dialog#buttonPressed(int)
     */
    @Override
    protected void buttonPressed(final int id) {
        if (id == DISCONNECT_ID) {
            final CoreException[] ex = new DebugException[1];
            final String[] operation = new String[1];
            ex[0] = null;
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        operation[0] = DebugUIMessages.HotCodeReplaceErrorDialog_6;
//                        target.disconnect();

                        // Restart the debugger
                        DevModeOperations devModeOps = DevModeOperations.getInstance();

                        ILaunch launch = target.getLaunch();
                        String projectName = launch.getLaunchConfiguration().getAttribute(StartTab.PROJECT_NAME, "");
                        Project project = devModeOps.getProjectModel().getProject(projectName);

                        DebugModeHandler debugModeHandler = devModeOps.getDebugModeHandler();
                        if (devModeOps.isProjectStarted(projectName)) {
                         	devModeOps.restartServer(projectName);
                            launch.removeDebugTarget(target);
 
                         }
                        debugModeHandler.startDebugAttacher(project, launch, null);
                    } catch (CoreException e) {
                        ex[0] = e;
                    }
                }
            };
            BusyIndicator.showWhile(getShell().getDisplay(), r);
            if (ex[0] != null) {
                JDIDebugUIPlugin.statusDialog(NLS.bind(DebugUIMessages.HotCodeReplaceErrorDialog_2, operation), ex[0].getStatus());
            }
            okPressed();
        } else {
            super.buttonPressed(id);
        }
    }
}
