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
package io.openliberty.tools.eclipse.debug;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.internal.debug.ui.DebugUIMessages;
import org.eclipse.jdt.internal.debug.ui.HotCodeReplaceErrorDialog;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Shell;

import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.ui.launch.shortcuts.StopAction;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

public class LibertyHotCodeReplaceErrorDialog extends HotCodeReplaceErrorDialog {

    public LibertyHotCodeReplaceErrorDialog(Shell parentShell, String dialogTitle, String message, IStatus status, String preferenceKey,
            String toggleMessage, IPreferenceStore store, IDebugTarget target) {
        super(parentShell, dialogTitle, message, status, preferenceKey, toggleMessage, store, target);
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.jface.dialogs.Dialog#buttonPressed(int)
     */
    @Override
    protected void buttonPressed(final int id) {
        if (id == TERMINATE_ID || id == DISCONNECT_ID || id == RESTART_ID) {
            final DebugException[] ex = new DebugException[1];
            final String[] operation = new String[1];
            ex[0] = null;
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (id == TERMINATE_ID) {
                            operation[0] = DebugUIMessages.HotCodeReplaceErrorDialog_5;
                            ILaunch launch = target.getLaunch();
                            launch.terminate();
                        } else if (id == DISCONNECT_ID) {
                            operation[0] = DebugUIMessages.HotCodeReplaceErrorDialog_6;
                            target.disconnect();
                        } else {
                            operation[0] = DebugUIMessages.HotCodeReplaceErrorDialog_8;

                            restartDevMode(Utils.getActiveProject());

                        }
                    } catch (DebugException e) {
                        ex[0] = e;
                    } catch (Exception e) {
                        e.printStackTrace();
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

    /**
     * Issues the Liberty plugin stop command to stop the Liberty server associated with the specified project.
     * 
     * @param projectName The name of the project for which the the Liberty plugin stop command is issued.
     */
    public void restartDevMode(IProject iProject) {
        try {
            // Cleanly stop dev mode
            StopAction.run(iProject);

        } catch (Exception e) {
            ErrorHandler.processErrorMessage(NLS.bind(Messages.plugin_stop_issue_error, null), e, false);
        }

        Job job = Job.create("Relaunching dev mode ...", (ICoreRunnable) monitor -> {
            String projectName = iProject.getName();

            ILaunch launch = target.getLaunch();
            int timeout = 60;
            while (timeout != 0) {
                if (launch.isTerminated()) {
                    timeout = 0;
                } else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        ErrorHandler.processErrorMessage(NLS.bind(Messages.plugin_stop_issue_error, null), e, false);
                    }
                    timeout = timeout - 1;
                }
            }

            if (launch.isTerminated()) {
                // Restart launch
                ILaunchConfiguration config = launch.getLaunchConfiguration();
                if (config != null && config.exists()) {
                    DebugUITools.launch(config, launch.getLaunchMode());
                }
            } else {
                // Dev mode did not stop.... now what?
            }
        });

        job.schedule();
    }
}
