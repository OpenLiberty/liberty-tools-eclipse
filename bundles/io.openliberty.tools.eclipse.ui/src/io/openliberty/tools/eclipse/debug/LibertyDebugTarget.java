package io.openliberty.tools.eclipse.debug;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;

/**
 * This class is an extension of the JDIDebugTarget and allows the debugger to be restarted
 * automatically if the VM restarts or a hot code replace failure occurs.
 */
public class LibertyDebugTarget extends JDIDebugTarget {

    public LibertyDebugTarget(ILaunch launch, VirtualMachine jvm, String name) {
        super(launch, jvm, "Liberty Application Debug: " + name, true, true, null, true);
    }

    @Override
    public void handleVMDeath(VMDeathEvent event) {
        disconnected();
    }

    @Override
    public void handleVMDisconnect(VMDisconnectEvent event) {
        disconnected();
    }

    /**
     * Updates the state of this target for disconnection from the VM.
     */
    @Override
    protected void disconnected() {
        setDisconnecting(false);
        if (!isDisconnected()) {
            setDisconnected(true);
            cleanup();
            fireChangeEvent(DebugEvent.CONTENT);
        }

    }
}
