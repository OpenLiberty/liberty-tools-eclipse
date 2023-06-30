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
package io.openliberty.tools.eclipse.utils;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.logging.Logger;

/**
 * Handles Error processing.
 */
public class ErrorHandler {

    public static final String TITLE = "Liberty Tools";
    public static final String SUFFIX_MSG = "See Error Log for details.";

    /**
     * Logs a message to the platform log.
     *
     * @param message The message to display.
     * @param throwable The Throwable object.
     */
    public static void processErrorMessage(String message, Throwable throwable) {
        processErrorMessage(message, throwable, false);

    }

    /**
     * Logs a message to the platform log and opens the error dialog, if indicated.
     *
     * @param message The message to log/display.
     * @param throwable The Throwable object.
     * @param displayDialog The indicator to open a dialog.
     */
    public static void processErrorMessage(String message, Throwable throwable, boolean displayDialog) {
        Logger.logError(message, throwable);

        if (displayDialog) {
            Shell shell = Display.getCurrent().getActiveShell();
            String reason = appendSuffix(throwable.getMessage(), SUFFIX_MSG);
            Status status = new Status(IStatus.ERROR, LibertyDevPlugin.PLUGIN_ID, reason);
            ErrorDialog.openError(shell, TITLE, message, status);
        }
    }

    /**
     * Logs a message to the platform log.
     *
     * @param message The message to display.
     */
    public static void processErrorMessage(String message) {
        processErrorMessage(message, false);
    }

    /**
     * Logs a message to the platform log and opens the error dialog, if indicated.
     *
     * @param message The message to display.
     * @param displayDialog The indicator to open a dialog.
     */
    public static void processErrorMessage(String message, boolean displayDialog) {
        Logger.logError(message);

        if (displayDialog) {
            Shell shell = Display.getCurrent().getActiveShell();
            String updatedMessage = appendSuffix(message, SUFFIX_MSG);
            MessageDialog dialog = new MessageDialog(shell, TITLE, null, updatedMessage, MessageDialog.ERROR, new String[] { "OK" }, 0);
            dialog.open();
        }
    }

    /**
     * Logs a message to the platform log and opens the error dialog, without directing the user to look at the error log. This is in
     * contrast to most dialogs which do point the user to the error log. We use this when we know there's not likely to be anything
     * to see in the error log.
     * 
     * @param message The message to display.
     */
    public static void rawErrorMessageDialog(String message) {
        Logger.logError(message);

        Shell shell = Display.getCurrent().getActiveShell();
        MessageDialog dialog = new MessageDialog(shell, TITLE, null, message, MessageDialog.ERROR, new String[] { "OK" }, 0);
        dialog.open();
    }

    /**
     * Logs a message to the platform log and opens the error dialog, if indicated.
     *
     * @param message The message to display.
     * @param displayDialog The indicator to open a dialog.
     * @param buttonLabels The array of button labels to be display on the dialog.
     * @param defaultButton The index number representing the button to be selected as default.
     * 
     * @return The index number representing the button that the user selected.
     */
    public static Integer processWarningMessage(String message, boolean displayDialog, String[] buttonLabels, int defaultButton) {
        Integer response = null;
        Logger.logError(message);

        if (displayDialog) {
            Shell shell = Display.getCurrent().getActiveShell();
            MessageDialog dialog = new MessageDialog(shell, TITLE, null, message, MessageDialog.WARNING, buttonLabels, defaultButton);
            response = Integer.valueOf(dialog.open());
        }

        return response;

    }

    /**
     * Logs a message to the platform log.
     *
     * @param message The message to display.
     * @param throwable The Throwable object.
     */
    public static void processWarningMessage(String message, Throwable throwable) {
        processWarningMessage(message, throwable, false);

    }

    /**
     * Logs a message to the platform log and opens the error dialog, if indicated.
     *
     * @param message The message to log/display.
     * @param throwable The Throwable object.
     * @param displayDialog The indicator to open a dialog.
     */
    public static void processWarningMessage(String message, Throwable throwable, boolean displayDialog) {
        Logger.logWarning(message, throwable);

        if (displayDialog) {
            Shell shell = Display.getCurrent().getActiveShell();
            String reason = appendSuffix(throwable.getMessage(), SUFFIX_MSG);
            Status status = new Status(IStatus.WARNING, LibertyDevPlugin.PLUGIN_ID, reason);
            ErrorDialog.openError(shell, TITLE, message, status);
        }
    }

    /**
     * Logs a message to the platform log.
     *
     * @param message The message to display.
     */
    public static void processWarningMessage(String message) {
        processWarningMessage(message, false);
    }

    /**
     * Logs a message to the platform log and opens the error dialog, if indicated.
     *
     * @param message The message to display.
     * @param displayDialog The indicator to open a dialog.
     */
    public static void processWarningMessage(String message, boolean displayDialog) {
        Logger.logWarning(message);

        if (displayDialog) {
            Shell shell = Display.getCurrent().getActiveShell();
            String updatedMessage = appendSuffix(message, SUFFIX_MSG);
            MessageDialog dialog = new MessageDialog(shell, TITLE, null, updatedMessage, MessageDialog.WARNING, new String[] { "OK" }, 0);
            dialog.open();
        }
    }

    /**
     * Logs a message to the platform log and opens the error dialog, if indicated.
     *
     * @param message The message to display.
     * @param displayDialog The indicator to open a dialog.
     */
    public static void processPreferenceErrorMessage(String message, boolean displayDialog) {
        Logger.logWarning(message);

        if (displayDialog) {
            Shell shell = Display.getCurrent().getActiveShell();
            LibertyToolsMessageDialog ltdialog = new LibertyToolsMessageDialog(shell, TITLE, null, message, MessageDialog.ERROR,
                    new String[] { "OK" }, 0);
            ltdialog.open();
        }
    }

    /**
     * Returns the input message with the input suffix appended.
     * 
     * @param msg The message.
     * @param suffix The suffix to add to message.
     * 
     * @return The input message with the input suffix appended.
     */
    public static String appendSuffix(String msg, String suffix) {
        String newMsg = (msg == null) ? "" : msg.trim();
        if (newMsg.isEmpty() || newMsg.equals(".")) {
            newMsg = suffix;
        } else {
            newMsg = (newMsg.endsWith(".")) ? newMsg + " " + suffix : newMsg + ". " + suffix;
        }

        return newMsg;
    }
}