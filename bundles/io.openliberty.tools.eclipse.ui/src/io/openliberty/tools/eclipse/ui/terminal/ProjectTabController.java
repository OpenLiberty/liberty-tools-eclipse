/*******************************************************************************
* Copyright (c) 2022, 2024 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.terminal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.custom.CTabFolder2Listener;
import org.eclipse.ui.IViewPart;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Manages a set of console view project tab instances.
 */
public class ProjectTabController {

    /** The set of active Consoles associated with different application projects. */
    private static final ConcurrentHashMap<String, ProjectTab> projectTabMap = new ConcurrentHashMap<String, ProjectTab>();

    /** The set of terminal listeners associated with the different application projects. */
    private static final ConcurrentHashMap<String, List<TerminalListener>> projectTerminalListenerMap = new ConcurrentHashMap<String, List<TerminalListener>>();

    /** TerminalManager instance. */
    private static ProjectTabController instance;

    /** The active terminal view part. It is refreshed when the terminal reopens. */
    private IViewPart viewPart;

    /**
     * Constructor.
     */
    private ProjectTabController() {
    }

    /**
     * Returns a singleton instance of this class.
     *
     * @return A singleton instance of this class.
     */
    public static ProjectTabController getInstance() {
        if (instance == null) {
            instance = new ProjectTabController();
        }

        return instance;
    }

    /**
     * Runs the specified command on a terminal.
     *
     * @param projectName The application project name.
     * @param projectPath The application project path.
     * @param command The command to execute on the terminal.
     * @param envs The environment properties to be set on the terminal.
     * 
     * @throws IOException
     */
    public Process runOnTerminal(String projectName, String projectPath, String command, List<String> envs) throws IOException {
        ProjectTab projectTab = new ProjectTab(projectName);
        projectTabMap.put(projectName, projectTab);
        return projectTab.runCommand(projectPath, command, envs);
    }

    /**
     * Writes the input data to the terminal tab associated with the input project name.
     *
     * @param projectName The application project name.
     * @param content The data to write.
     *
     * @throws Exception
     */
    public void writeToProcessStream(String projectName, String data) throws Exception {
        ProjectTab projectTab = projectTabMap.get(projectName);

        if (projectTab == null) {
            String msg = "Unable to write to the terminal associated with project " + projectName
                    + ". Internal poject tab object not found.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg + ". Data to write: " + new String(data));
            }
            throw new Exception(msg);
        }

        projectTab.writeToProcess(data);
    }

    /**
     * Returns the ProjectTab instance associated with the specified project name.
     *
     * @param projectName The application project name.
     *
     * @return the ProjectTab instance associated with the specified project name.
     */
    public ProjectTab getProjectTab(String projectName) {
        return projectTabMap.get(projectName);
    }

    public boolean isStarted(String projectName) {
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            return projectTab.isStarted();
        }

        return false;
    }

    /**
     * Exits Liberty dev mode running on all active terminal tabs in the view.
     */
    public void processTerminalViewCleanup() {
        for (Map.Entry<String, ProjectTab> entry : projectTabMap.entrySet()) {
            String projectName = entry.getKey();
            ProjectTab projectTab = entry.getValue();
            exitDevModeOnTerminalTab(projectName, projectTab);
        }
    }

    /**
     * Exits dev mode processing running on a terminal tab.
     * 
     * @param projectName The name of the project associated with the dev mode process.
     * @param projectTab The project tab object representing the terminal tab where dev mode is running.
     */
    public void exitDevModeOnTerminalTab(String projectName, ProjectTab projectTab) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectName, projectTab });
        }

        if (projectTab != null) {
            try {
                // Run the exit command on the terminal. This will trigger dev mode cleanup processing.
                projectTab.writeToProcess(DevModeOperations.DEVMODE_COMMAND_EXIT);

                // Wait for the command issued to take effect. This also handles some cases where
                // the terminal tab/view is terminated while dev mode is starting, but the command
                // is not processed until dev mode finishes starting. On Mac or Linux, a
                // runtime shutdown hook mechanism will make sure that dev mode cleanup is processed in
                // "all" cases.
                // Note that this is a best effort approach workaround for Windows where the runtime
                // shutdown hooks are not called.
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (Exception e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI, "Failed to exit dev mode associated with project " + projectName, e);
                }
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, projectName);
        }

    }

    /**
     * Cleans up the objects associated with the terminal object represented by the specified project name.
     *
     * @param projectName The application project name.
     */
    public void processTerminalTabCleanup(String projectName) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectName, projectTabMap.size() });
        }

        // Remove the connector from the connector map cache.
        projectTabMap.remove(projectName);

        // Call cleanup on all registered terminal listeners and remove them from the terminal map cache.
        List<TerminalListener> listeners = projectTerminalListenerMap.get(projectName);
        if (listeners != null) {
            synchronized (listeners) {
                Iterator<TerminalListener> i = listeners.iterator();
                while (i.hasNext()) {
                    i.next().cleanup();
                }
            }
        }
        projectTerminalListenerMap.remove(projectName);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI,
                    Utils.objectsToString(projectName, projectTabMap.size(), projectTerminalListenerMap.size()));
        }
    }

    /**
     * Registers the input terminal listener.
     * 
     * @param projectName The name of the project for which the listener is registered.
     * @param listener The listener implementation.
     */
    public void registerTerminalListener(String projectName, TerminalListener listener) {
        List<TerminalListener> listeners = projectTerminalListenerMap.get(projectName);
        if (listeners == null) {
            listeners = Collections.synchronizedList(new ArrayList<TerminalListener>());
        }

        listeners.add(listener);
        projectTerminalListenerMap.put(projectName, listeners);
    }

    /**
     * Unregisters the input terminal listener.
     * 
     * @param projectName The name of the project the input listener is registered for.
     * @param listener The listener implementation.
     */
    public void unregisterTerminalListener(String projectName, TerminalListener listener) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectName, listener, projectTerminalListenerMap.size() });
        }

        List<TerminalListener> listeners = projectTerminalListenerMap.get(projectName);
        if (listeners != null) {
            listeners.remove(listener);
            projectTerminalListenerMap.put(projectName, listeners);
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, projectTerminalListenerMap.size());
        }
    }

    /**
     * Removes the listener registered with the Eclipse terminal view folder.
     * 
     * @param listener The listener to remove.
     */
    public void unregisterCTabFolder2Listener(CTabFolder2Listener listener) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { listener, viewPart });
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Class: ").append(instance.getClass().getName()).append(": ");
        sb.append("projectTabMap size: ").append(projectTabMap.size()).append(", ");
        sb.append("projectTabMap: ").append(projectTabMap);
        return sb.toString();
    }
}
