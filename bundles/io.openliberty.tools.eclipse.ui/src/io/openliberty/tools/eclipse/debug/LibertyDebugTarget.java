package io.openliberty.tools.eclipse.debug;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;

import io.openliberty.tools.eclipse.debug.DebugModeHandler.RestartDebugger;

/**
 * This class is an extension of the JDIDebugTarget and allows the debugger to be restarted
 * automatically if the VM restarts or a hot code replace failure occurs.
 */
public class LibertyDebugTarget extends JDIDebugTarget {

    private RestartDebugger restartDebugger;
    private boolean relaunch;

    public LibertyDebugTarget(ILaunch launch, VirtualMachine jvm, String name, RestartDebugger restartDebugger) {
        super(launch, jvm, "Liberty Application Debug: " + name, true, true, null, true);

        this.restartDebugger = restartDebugger;
        this.relaunch = true;

    }

    public void setRelaunch(boolean relaunch) {
        this.relaunch = relaunch;
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

            getLaunch().removeDebugTarget(this);

            // Attempt to restart the debugger
            if (relaunch) {
                restartDebugger.restart();
                restartDebugger = null;
            }
        }
    }

    // @Override
    // public void disconnect() throws DebugException {
    // // If we got here, the user explicitly called "disconnect" on the debugger.
    // // We should not try to reconnect in this case but we should allow the user
    // // to select "relaunch" which should just reconnect the debugger.
    // // Since we cant differentiate between a normal "launch" and a "relaunch",
    // // we will set a flag that we will use to determine if we should launch devMode
    // // or just reconnect the debugger.
    // restartDebugger = null;
    // relaunch = true;
    //
    // super.disconnect();
    //
    // }

}
