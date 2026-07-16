/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.launch;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.DevModeOperations.ProjectAggregatedState;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.model.ProjectModel;

/**
 * Eclipse property tester used to enable or disable Liberty Tools launch shortcuts 
 * based on the current aggregate running state of the selected project.
 */
public class LibertyProjectPropertyTester extends PropertyTester {

    /** Property name: true when every Liberty module associated with the project is running. */
    public static final String PROP_AGGREGATE_STATE_ACTIVE = "aggregateStateActive";

    /** Property name: true when no Liberty module associated with the project is running. */
    public static final String PROP_AGGREGATE_STATE_INACTIVE = "aggregateStateInactive";

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { receiver, property, expectedValue });
        }

        // Resolve the IProject from the receiver.
        IProject iProject = null;
        if (receiver instanceof IProject) {
            iProject = (IProject) receiver;
        } else if (receiver instanceof IAdaptable) {
            iProject = ((IAdaptable) receiver).getAdapter(IProject.class);
        }

        if (iProject == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Receiver object is not of the expected type. Receiver: "+ receiver);
            }
            return false;
        }

        // Look up the project model and compute the module state.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        ProjectModel projectModel = devModeOps.getWorkspaceModel().getProjectByLocation(iProject.getLocation().toOSString());

        if (projectModel == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Project: " + iProject.getName() + " is unknown because it is not a Liberty configured project.");
            }
            return false;
        }

        ProjectAggregatedState state = devModeOps.computeProjectAggregateState(projectModel);
        boolean result;

        if (PROP_AGGREGATE_STATE_ACTIVE.equals(property)) {
            result = (state == ProjectAggregatedState.ACTIVE);
        } else if (PROP_AGGREGATE_STATE_INACTIVE.equals(property)) {
            result = (state == ProjectAggregatedState.INACTIVE);
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Unknown property: " + property + ". Returning false.");
            }
            return false;
        }

        // If the caller supplied an explicit expected value in the XML, compare against it.
        if (expectedValue instanceof Boolean) {
            result = result == (Boolean) expectedValue;
        } else if (expectedValue instanceof String) {
            result = Boolean.toString(result).equals(expectedValue);
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, result);
        }

        return result;
    }
}
