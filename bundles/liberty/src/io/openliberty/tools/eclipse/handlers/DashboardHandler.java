/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

import io.openliberty.tools.eclipse.logging.Trace;

public class DashboardHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().showView("io.openliberty.tools.eclipse.views.liberty.devmode.dashboard");
        } catch (Exception e) {
            String msg = "Unable to open the Liberty dashboard view";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_HANDLERS, msg, e);
            }
            throw new ExecutionException(msg, e);
        }

        return null;
    }
}
