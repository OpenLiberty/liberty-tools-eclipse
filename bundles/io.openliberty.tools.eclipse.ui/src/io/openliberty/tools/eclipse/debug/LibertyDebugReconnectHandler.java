package io.openliberty.tools.eclipse.debug;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.handlers.HandlerUtil;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.ui.launch.StartTab;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

public class LibertyDebugReconnectHandler extends AbstractHandler {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {

        Object target = null;
        ISelection selection = HandlerUtil.getActiveWorkbenchWindow(event)
                .getActivePage().getSelection();
        if (selection != null & selection instanceof IStructuredSelection) {
            IStructuredSelection strucSelection = (IStructuredSelection) selection;
            Object[] elements = strucSelection.toArray();
            target = elements[0];
        }

        ILaunch launch = null;
        IDebugTarget debugTarget = null;

        // This action can be performed from a launch, a process, or a debug target.
        // The object param will therefore be an ILaunch, an IProcess, or IDebugTarget object.
        if (target instanceof ILaunch) {
            launch = (ILaunch) target;
            debugTarget = launch.getDebugTarget();
        } else if (target instanceof IProcess) {
            launch = ((IProcess) target).getLaunch();
            debugTarget = launch.getDebugTarget();
        } else {
            debugTarget = (IDebugTarget) target;
            launch = debugTarget.getLaunch();
        }

        if (launch != null) {
            DevModeOperations devModeOps = DevModeOperations.getInstance();

            String projectName = null;
            try {
                projectName = launch.getLaunchConfiguration().getAttribute(StartTab.PROJECT_NAME, "");
            } catch (CoreException e) {
                String msg = "An error was detected during debugger reconnect";
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
                }
                ErrorHandler.processErrorMessage(NLS.bind(Messages.project_name_error, null), e, true);
            }

            if (projectName != null && !projectName.isBlank()) {
                Project project = devModeOps.getProjectModel().getProject(projectName);

                // Reconnect debugger
                if (devModeOps.isProjectStarted(projectName)) {
                    DebugModeHandler debugModeHandler = devModeOps.getDebugModeHandler();
                    debugModeHandler.startDebugAttacher(project, launch, null);
                }

                // Remove old debug target
                if (debugTarget != null) {
                    launch.removeDebugTarget(debugTarget);
                }
            }
        }
        return target;
    }

}