package io.openliberty.tools.eclipse.process;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.launch.StartTab;
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
            DevModeOperations devModeOps = DevModeOperations.getInstance();
            boolean enableEnhancedMonitoring = true;
            
            if (source instanceof IProcess && events[i].getKind() == DebugEvent.TERMINATE) {

                // This is an IProcess terminate event. Check if the IProcess matches
                IProcess iProcess = (IProcess) source;
                if (projectName.equals(iProcess.getLabel())) {
                    // We match - cleanup
                    Project project = devModeOps.getProjectModel().getProject(projectName);

                    try {
                        enableEnhancedMonitoring = iProcess.getLaunch().getLaunchConfiguration()
                                .getAttribute(StartTab.PROJECT_DEBUG_ENHANCED_MONITORING, true);
                        if (project != null && enableEnhancedMonitoring) {
                            Utils.reEnableAppMonitoring(project);
                        }
                    } catch (CoreException e) {
                        String msg = "An error detected while getting the start params from the launch configuration.";
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
                        }
                    }
                    devModeOps.cleanupProcess(projectName);
                    DebugPlugin.getDefault().removeDebugEventListener(this);
                }
            } else if (events[i].getKind() == DebugEvent.CHANGE && source instanceof IDebugTarget target
                    && target.isDisconnected()) {
                ILaunch launch = target.getLaunch();
                IProcess[] processes = (launch != null) ? launch.getProcesses() : new IProcess[0];

                if (processes.length > 0 && projectName.equals(processes[0].getLabel())) {
                    Project project = devModeOps.getProjectModel().getProject(projectName);
                    try {
                        enableEnhancedMonitoring = launch.getLaunchConfiguration()
                                .getAttribute(StartTab.PROJECT_DEBUG_ENHANCED_MONITORING, true);
                        if (project != null && enableEnhancedMonitoring) {
                            Utils.reEnableAppMonitoring(project);
                        }
                    } catch (CoreException e) {
                        String msg = "An error detected while getting the start params from the launch configuration.";
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
                        }
                    }
                }
            }
        }
    }

}
