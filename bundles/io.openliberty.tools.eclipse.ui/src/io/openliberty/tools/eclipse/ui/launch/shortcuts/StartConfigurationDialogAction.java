package io.openliberty.tools.eclipse.ui.launch.shortcuts;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher;
import io.openliberty.tools.eclipse.utils.Dialog;

/**
 * Liberty start configuration dialog action shortcut.
 */
public class StartConfigurationDialogAction implements ILaunchShortcut {

    public static final String LAUNCH_GROUP_RUN_ID = "org.eclipse.debug.ui.launchGroup.run";

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(ISelection selection, String mode) {
        try {
            openLaunchConfigurationsDialog();
        } catch (Exception e) {
            String msg = "An error was detected while processing the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG
                    + "\" launch shortcut.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
            return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void launch(IEditorPart part, String mode) {
        try {
            openLaunchConfigurationsDialog();
        } catch (Exception e) {
            String msg = "An error was detected while processing the \"" + LaunchConfigurationDelegateLauncher.LAUNCH_SHORTCUT_START_CONFIG
                    + "\" launch shortcut.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            Dialog.displayErrorMessageWithDetails(msg, e);
            return;
        }
    }

    /**
     * Open the launch configurations dialog.
     */
    public static void openLaunchConfigurationsDialog() {
        ILaunchConfiguration latest = DebugUITools.getLastLaunch(LAUNCH_GROUP_RUN_ID);
        IStructuredSelection selection = (latest != null) ? new StructuredSelection(latest) : new StructuredSelection();
        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        DebugUITools.openLaunchConfigurationDialogOnGroup(shell, selection, LAUNCH_GROUP_RUN_ID);
    }
}
