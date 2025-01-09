/*******************************************************************************
* Copyright (c) 2024 IBM Corporation and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     IBM Corporation - initial implementation
*******************************************************************************/
package io.openliberty.tools.eclipse.debug;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.jdt.internal.debug.core.model.JDIDebugTarget;

import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;

/**
 * This class is an extension of the JDIDebugTarget and allows the debugger to be
 * disconnected without triggering a terminate of the entire launch. This is necessary
 * since our launch contains both the debugger instance AND the Maven/devmode process.
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

            // Fire a change event to trigger a refresh of the debug view
            fireChangeEvent(DebugEvent.CONTENT);
        }

    }
}
