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
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.DevModeOperations.ProjectAggregatedState;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.model.ProjectModel;

/**
 * Eclipse property tester used to enable or disable Liberty Tools Run As shortcuts
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
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { property, args, expectedValue });
        }

        // Resolve the receiver.
        IProject iProject = null;
        if (receiver instanceof IProject) {
            // Project Explorer: Project dir.
            // Package Explorer: Project dir.
            iProject = (IProject) receiver;
        } else if (receiver instanceof IResource) {
            // Project explorer: directories, non-src files.
            // Package explorer: directories, non-src files.
            iProject = ((IResource) receiver).getProject();
        } else if (receiver instanceof IAdaptable) {
            IResource resource = ((IAdaptable) receiver).getAdapter(IResource.class);
            if (resource != null) {
                // Editor: org.eclipse.ui.part.FileEditorInput: files
                // Package Explorer: sub modules
                // Project Explorer: packages/files
                // Outline: class
                iProject = resource.getProject();
            } else {
                iProject = ((IAdaptable) receiver).getAdapter(IProject.class);
            }
        }

        if (iProject == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Receiver object is not of the expected type. Receiver: " + receiver);
            }
            return false;
        }

        // Look up the project model and compute the module state.
        // A project with libertyNature should always be present in the workspace model.
        // If it is not, that is an unexpected state; return false so no shortcuts are shown.
        DevModeOperations devModeOps = DevModeOperations.getInstance();
        ProjectModel projectModel = devModeOps.getWorkspaceModel().getProjectByLocation(iProject.getLocation().toOSString());

        if (projectModel == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Project: " + iProject.getName() + " has libertyNature but is not in the workspace model. This is unexpected.");
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

        result = evaluateResult(result, expectedValue);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, new Object[] {state, result});
        }

        return result;
    }

    /**
     * Compares a computed boolean result against the expected value from the XML expression.
     * When no expected value is supplied, the raw result is returned unchanged.
     *
     * @param result        The computed boolean result for the property.
     * @param expectedValue The expected value supplied in the XML test expression, or null if none.
     *
     * @return The result after applying the comparison, or the raw result if no
     *         expected value was supplied.
     */
    private boolean evaluateResult(boolean result, Object expectedValue) {
        if (expectedValue instanceof Boolean) {
            return result == (Boolean) expectedValue;
        } else if (expectedValue instanceof String) {
            return Boolean.toString(result).equals(expectedValue);
        }
        return result;
    }
}
