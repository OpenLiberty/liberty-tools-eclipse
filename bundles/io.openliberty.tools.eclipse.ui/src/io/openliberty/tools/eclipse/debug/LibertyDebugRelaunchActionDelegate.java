package io.openliberty.tools.eclipse.debug;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.actions.AbstractDebugActionDelegate;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.ui.launch.StartTab;

public class LibertyDebugRelaunchActionDelegate extends AbstractDebugActionDelegate {

    @Override
    protected void doAction(Object object) {
        ILaunch launch = DebugUIPlugin.getLaunch(object);

        if (launch != null) {
            DevModeOperations devModeOps = DevModeOperations.getInstance();

            String projectName = "";
            try {
                projectName = launch.getLaunchConfiguration().getAttribute(StartTab.PROJECT_NAME, "");
            } catch (CoreException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            Project project = devModeOps.getProjectModel().getProject(projectName);

            if (!devModeOps.isProjectTerminalTabMarkedClosed(projectName)) {
                devModeOps.debugModeHandler.startDebugAttacher(project, launch, null, true);
            }

        }
    }
}
