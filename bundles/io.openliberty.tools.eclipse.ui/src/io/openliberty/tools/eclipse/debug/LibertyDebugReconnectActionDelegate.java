package io.openliberty.tools.eclipse.debug;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.actions.AbstractDebugActionDelegate;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.ui.launch.StartTab;

public class LibertyDebugReconnectActionDelegate extends AbstractDebugActionDelegate {

    @Override
    protected void doAction(Object object) {
        ILaunch launch = DebugUIPlugin.getLaunch(object);

        if (launch != null) {
            DevModeOperations devModeOps = DevModeOperations.getInstance();

            String projectName = "";
            try {
                projectName = launch.getLaunchConfiguration().getAttribute(StartTab.PROJECT_NAME, "");
            } catch (CoreException e) {
                // TODO - how to handle errors?
                e.printStackTrace();
            }

            Project project = devModeOps.getProjectModel().getProject(projectName);

            // Reconnect debugger
            if (!devModeOps.isProjectTerminalTabMarkedClosed(projectName)) {
                devModeOps.debugModeHandler.startDebugAttacher(project, launch, null, true);
            }

            // Remove old debug target
            launch.removeDebugTarget((IDebugTarget) object);
        }
    }
}
