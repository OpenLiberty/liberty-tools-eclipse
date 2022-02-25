package liberty.tools.utils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import liberty.tools.LibertyDevPlugin;

public class Dialog {

    static String dTitle = "Liberty Development Mode";

    /**
     * Displays an error message dialog with details.
     * 
     * @param message   The message to display.
     * @param throwable The Throwable object to display in the details section.
     */
    public static void displayErrorMessageWithDetails(String message, Throwable throwable) {

        List<Status> stackTraceStatusList = new ArrayList<>();
        for (StackTraceElement stackEntry : throwable.getStackTrace()) {
            Status stackTraceStatus = new Status(IStatus.ERROR, LibertyDevPlugin.PLUGIN_ID, stackEntry.toString());
            stackTraceStatusList.add(stackTraceStatus);
        }

        MultiStatus status = new MultiStatus(LibertyDevPlugin.PLUGIN_ID, IStatus.ERROR,
                stackTraceStatusList.toArray(new Status[] {}), throwable.getMessage(), throwable);
        Shell shell = Display.getCurrent().getActiveShell();
        ErrorDialog.openError(shell, dTitle, message, status);
    }

    /**
     * Displays an Error message dialog.
     * 
     * @param message The message to display.
     */
    public static void displayErrorMessage(String message) {
        Shell shell = Display.getCurrent().getActiveShell();
        MessageDialog dialog = new MessageDialog(shell, dTitle, null, message, MessageDialog.ERROR,
                new String[] { "OK" }, 0);
        dialog.open();
    }

    /**
     * Displays a Warning message dialog.
     * 
     * @param message The message to display.
     */
    public static void displayWarningMessage(String message) {
        Shell shell = Display.getCurrent().getActiveShell();
        MessageDialog dialog = new MessageDialog(shell, dTitle, null, message, MessageDialog.WARNING,
                new String[] { "OK" }, 0);
        dialog.open();
    }
}
