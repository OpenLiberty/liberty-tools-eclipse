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

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.handlers.HandlerUtil;

import io.openliberty.tools.eclipse.LibertyNature;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Command handler associated with the Liberty menu entry in the explorer views.
 */
public class ExplorerMenuHandler extends AbstractHandler {

    /** Liberty menu options */
    public static final String ADD_NATURE_ACTION = "Enable Liberty";

    /**
     * {@inheritDoc}
     */
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_HANDLERS, new Object[] { event });
        }

        ISelection currentSelection = HandlerUtil.getCurrentSelection(event);

        // Validate that a project was selected.
        if (currentSelection.isEmpty()) {
            String msg = "Invalid project. Be sure to select a project first.";

            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_HANDLERS, msg);
            }

            ErrorHandler.processErrorMessage(msg, true);

            return null;
        }

        // Find the associated command.
        Command command = event.getCommand();
        String commandName = "";
        try {
            commandName = command.getName();
        } catch (Exception e) {
            String msg = "Unable to retrieve menu command.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_HANDLERS, msg, e);
            }

            ErrorHandler.processErrorMessage(msg, e, true);
            return null;
        }

        // Retrieve the project object associated with the project selections.
        List<IProject> iProjects = Utils.getProjectFromSelections(currentSelection);

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_HANDLERS, "Command: " + commandName + ". Selected projects: " + iProjects);
        }

        // Iterate over all selections and process the requested command.
        for (IProject iProject : iProjects) {
            try {
                if (iProject.getDescription().hasNature(LibertyNature.NATURE_ID)) {
                    continue;
                }

                switch (commandName) {
                    case ADD_NATURE_ACTION:
                        Project.addNature(iProject, LibertyNature.NATURE_ID);
                        break;
                    default:
                        throw new Exception("invalid command");
                }
            } catch (Exception e) {
                String msg = "Unable to process menu command " + commandName + " on project " + iProject.getName() + ".";

                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_HANDLERS, msg, e);
                }

                ErrorHandler.processErrorMessage(msg, e);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_HANDLERS);
        }

        return null;
    }
}
