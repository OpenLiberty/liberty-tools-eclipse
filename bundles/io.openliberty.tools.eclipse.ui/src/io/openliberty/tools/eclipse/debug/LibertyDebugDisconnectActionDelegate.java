package io.openliberty.tools.eclipse.debug;

import java.util.Arrays;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.actions.AbstractDebugActionDelegate;

public class LibertyDebugDisconnectActionDelegate extends AbstractDebugActionDelegate {

    @Override
    protected void doAction(Object object) throws DebugException {

        ILaunch launch = DebugUIPlugin.getLaunch(object);
        for (IDebugTarget target : Arrays.asList(launch.getDebugTargets())) {
            if (target instanceof LibertyDebugTarget) {
                ((LibertyDebugTarget) target).setRelaunch(false);
            }
            target.disconnect();
        }
    }
}
