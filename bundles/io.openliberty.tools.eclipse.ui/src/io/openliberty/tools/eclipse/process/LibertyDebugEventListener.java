package io.openliberty.tools.eclipse.process;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.osgi.util.NLS;

import com.google.inject.internal.Messages;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

public class LibertyDebugEventListener implements IDebugEventSetListener {

    private String projectName;

    public LibertyDebugEventListener(String projectName) {
        this.projectName = projectName;
    }

    @Override
    public void handleDebugEvents(DebugEvent[] events) {
        for (int i = 0; i < events.length; i++) {
            Object source = events[i].getSource();

            if (source instanceof IProcess && events[i].getKind() == DebugEvent.TERMINATE) {

                // This is an IProcess terminate event. Check if the IProcess matches
                IProcess iProcess = (IProcess) source;
                if (projectName.equals(iProcess.getLabel())) {
                    // We match - cleanup
                    DevModeOperations devModeOps = DevModeOperations.getInstance();
                    Project project = null;

                    try {
                        project = devModeOps.getProjectModel().getProject(projectName);
                        if (project != null) {
                        	Utils.enableAppMonitoring(false, project);
                        }

                    } catch (Exception e) {
                        String msg = "An error was detected when the view integration test report request was processed on project " + projectName
                                + ".";
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
                        }
                        return;
                    }
                    devModeOps.cleanupProcess(projectName);
                    DebugPlugin.getDefault().removeDebugEventListener(this);
                }
            }
        }
    }

}
