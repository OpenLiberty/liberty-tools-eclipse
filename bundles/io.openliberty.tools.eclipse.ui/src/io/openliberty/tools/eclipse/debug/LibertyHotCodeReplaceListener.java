/*******************************************************************************
* Copyright (c) 2024, 2025 IBM Corporation and others.
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

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaHotCodeReplaceListener;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;
import org.eclipse.jdt.internal.debug.ui.DebugUIMessages;
import org.eclipse.jdt.internal.debug.ui.HotCodeReplaceErrorDialog;
import org.eclipse.jdt.internal.debug.ui.IJDIPreferencesConstants;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jdt.internal.debug.ui.snippeteditor.ScrapbookLauncher;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Most of the code in this class is taken the Eclipse JDT JavaHotCodeReplaceListener:
 * .
 * https://raw.githubusercontent.com/eclipse-jdt/eclipse.jdt.debug/master/org.eclipse.jdt.debug.ui/ui/org/eclipse/jdt/internal/debug/ui/JavaHotCodeReplaceListener.java
 * .
 * The behavior is the same except our LibertyHotCodeReplaceErrorDialog is used.
 */
public class LibertyHotCodeReplaceListener implements IJavaHotCodeReplaceListener {

    private LibertyHotCodeReplaceErrorDialog fHotCodeReplaceFailedErrorDialog = null;

    private ILabelProvider fLabelProvider = DebugUITools.newDebugModelPresentation();
    private final String toggleMessage = DebugUIMessages.JDIDebugUIPlugin_5;

    /**
     * @see IJavaHotCodeReplaceListener#hotCodeReplaceSucceeded(IJavaDebugTarget)
     */
    @Override
    public void hotCodeReplaceSucceeded(IJavaDebugTarget target) {
    }

    /**
     * @see IJavaHotCodeReplaceListener#hotCodeReplaceFailed(IJavaDebugTarget, DebugException)
     */
    @Override
    public void hotCodeReplaceFailed(final IJavaDebugTarget target, final DebugException exception) {
        if ((exception != null
                && !JDIDebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IJDIPreferencesConstants.PREF_ALERT_HCR_FAILED)) ||
                ((exception == null) && !JDIDebugUIPlugin.getDefault().getPreferenceStore()
                        .getBoolean(IJDIPreferencesConstants.PREF_ALERT_HCR_NOT_SUPPORTED))
                || checkFailurePopUpPref(target)) {
            return;
        }

        // do not report errors for snippet editor targets
        // that do not support HCR. HCR is simulated by using
        // a new class loader for each evaluation
        ILaunch launch = target.getLaunch();
        if (launch.getAttribute(ScrapbookLauncher.SCRAPBOOK_LAUNCH) != null) {
            if (!target.supportsHotCodeReplace()) {
                return;
            }
        }
        final Display display = JDIDebugUIPlugin.getStandardDisplay();
        if (display.isDisposed()) {
            return;
        }

        String name = null;
        try {
            name = target.getName();
        } catch (DebugException e) {
            name = fLabelProvider.getText(target);
        }
        final String vmName = name;
        final IStatus status;
        final String preference;
        final String alertMessage;
        ILaunchConfiguration config = target.getLaunch().getLaunchConfiguration();
        final String launchName = (config != null ? config.getName() : DebugUIMessages.JavaHotCodeReplaceListener_0);
        if (exception == null) {
            status = new Status(IStatus.WARNING, JDIDebugUIPlugin.getUniqueIdentifier(), IStatus.WARNING,
                    DebugUIMessages.JDIDebugUIPlugin_The_target_VM_does_not_support_hot_code_replace_1, null);
            preference = IJDIPreferencesConstants.PREF_ALERT_HCR_NOT_SUPPORTED;
            alertMessage = DebugUIMessages.JDIDebugUIPlugin_3;
        } else {
            status = new Status(IStatus.WARNING, JDIDebugUIPlugin.getUniqueIdentifier(), IStatus.WARNING, exception.getMessage(),
                    exception.getCause());
            preference = IJDIPreferencesConstants.PREF_ALERT_HCR_FAILED;
            alertMessage = DebugUIMessages.JDIDebugUIPlugin_1;
        }
        final String title = DebugUIMessages.JDIDebugUIPlugin_Hot_code_replace_failed_1;
        final String message = NLS.bind(
                DebugUIMessages.JDIDebugUIPlugin__0__was_unable_to_replace_the_running_code_with_the_code_in_the_workspace__2,
                new Object[] { vmName, launchName });
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                if (display.isDisposed()) {
                    return;
                }
                if (fHotCodeReplaceFailedErrorDialog != null) {
                    Shell shell = fHotCodeReplaceFailedErrorDialog.getShell();
                    if (shell != null && !shell.isDisposed()) {
                        return;
                    }
                }
                Shell shell = JDIDebugUIPlugin.getActiveWorkbenchShell();
                fHotCodeReplaceFailedErrorDialog = new LibertyHotCodeReplaceErrorDialog(shell, title, message, status, preference,
                        alertMessage,
                        toggleMessage, JDIDebugUIPlugin.getDefault().getPreferenceStore(), target) {
                    @Override
                    public boolean close() {
                        fHotCodeReplaceFailedErrorDialog = null;
                        return super.close();
                    }
                };
                fHotCodeReplaceFailedErrorDialog.setBlockOnOpen(false);
                fHotCodeReplaceFailedErrorDialog.open();
            }
        });
    }

    /**
     * @see IJavaHotCodeReplaceListener#obsoleteMethods(IJavaDebugTarget)
     */
    @Override
    public void obsoleteMethods(final IJavaDebugTarget target) {
        if (!JDIDebugUIPlugin.getDefault().getPreferenceStore().getBoolean(IJDIPreferencesConstants.PREF_ALERT_OBSOLETE_METHODS)
                || checkFailurePopUpPref(target)) {
            return;
        }
        final Display display = JDIDebugUIPlugin.getStandardDisplay();
        if (display.isDisposed()) {
            return;
        }
        final String vmName = fLabelProvider.getText(target);
        final String dialogTitle = DebugUIMessages.JDIDebugUIPlugin_Obsolete_methods_remain_1;
        final String message = NLS.bind(DebugUIMessages.JDIDebugUIPlugin__0__contains_obsolete_methods_1, new Object[] { vmName });
        final IStatus status = new Status(IStatus.WARNING, JDIDebugUIPlugin.getUniqueIdentifier(), IStatus.WARNING,
                DebugUIMessages.JDIDebugUIPlugin_Stepping_may_be_hazardous_1, null);
        final String toggleMessage = DebugUIMessages.JDIDebugUIPlugin_2;
        final String toggleMessage2 = DebugUIMessages.JDIDebugUIPlugin_5;
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                if (display.isDisposed()) {
                    return;
                }
                Shell shell = JDIDebugUIPlugin.getActiveWorkbenchShell();
                HotCodeReplaceErrorDialog dialog = new HotCodeReplaceErrorDialog(shell, dialogTitle, message, status,
                        IJDIPreferencesConstants.PREF_ALERT_OBSOLETE_METHODS, toggleMessage, toggleMessage2,
                        JDIDebugUIPlugin.getDefault().getPreferenceStore(), target);
                dialog.setBlockOnOpen(false);
                dialog.open();
            }
        });
    }

    /**
     * Check whether user has enabled or disabled HCR failure error pop up for current debug session
     *
     * @param target
     *        IJavaDebugTarget of current debugging session
     * 
     * @return false if user wishes to see failure pop up, else true if user don't want see pop up
     */
    private boolean checkFailurePopUpPref(IJavaDebugTarget target) {
        if (target instanceof JDIDebugTarget jdiTarget) {
            return jdiTarget.isHcrFailurePopUpEnabled();
        }
        return false;
    }
}
