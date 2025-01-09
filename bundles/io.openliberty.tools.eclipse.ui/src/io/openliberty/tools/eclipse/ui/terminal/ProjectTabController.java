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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Listener;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.terminal.view.ui.interfaces.ITerminalsView;
import org.eclipse.tm.terminal.view.ui.interfaces.IUIConstants;
import org.eclipse.tm.terminal.view.ui.manager.ConsoleManager;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.terminal.ProjectTab.State;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Manages a set of terminal view project tab instances.
 */
public class ProjectTabController {

    /** Terminal view ID. */
    public static final String TERMINAL_VIEW_ID = "org.eclipse.tm.terminal.view.ui.TerminalsView";

    /** The set of active Terminal associated with different application projects. */
    private static final ConcurrentHashMap<String, ProjectTab> projectTabMap = new ConcurrentHashMap<String, ProjectTab>();

    /** The set of terminal listeners associated with the different application projects. */
    private static final ConcurrentHashMap<String, List<TerminalListener>> projectTerminalListenerMap = new ConcurrentHashMap<String, List<TerminalListener>>();

    /** TerminalManager instance. */
    private static ProjectTabController instance;

    /** Terminal console manager instance. */
    private ConsoleManager consoleMgr;

    /** The active terminal view part. It is refreshed when the terminal reopens. */
    private IViewPart viewPart;

    /**
     * Constructor.
     */
    private ProjectTabController() {
        this.consoleMgr = ConsoleManager.getInstance();
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
     */
    public void runOnTerminal(String projectName, String projectPath, String command, List<String> envs) {
        ProjectTab projectTab = new ProjectTab(projectName);
        projectTabMap.put(projectName, projectTab);
        projectTab.runCommand(projectPath, command, envs);
    }

    /**
     * Writes the input data to the terminal tab associated with the input project name.
     *
     * @param projectName The application project name.
     * @param content The data to write.
     *
     * @throws Exception
     */
    public void writeToTerminalStream(String projectName, byte[] data) throws Exception {
        ProjectTab projectTab = projectTabMap.get(projectName);

        if (projectTab == null) {
            String msg = "Unable to write to the terminal associated with project " + projectName
                    + ". Internal poject tab object not found.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg + ". Data to write: " + new String(data));
            }
            throw new Exception(msg);
        }

        projectTab.writeToStream(data, false);
    }

    /**
     * Returns the terminal tab item associated with the specified project name and connector.
     *
     * @param projectName The application project name.
     * @param connector The terminal connector object associated with input project name.
     *
     * @return the terminal tab item associated with the specified project name and connector.
     */
    public CTabItem getTerminalTabItem(String projectName, ITerminalConnector connector) {
        CTabItem item = null;

        if (connector != null) {
            item = consoleMgr.findConsole(IUIConstants.ID, null, projectName, connector, null);
        }

        return item;
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

    public State getTerminalState(String projectName) {
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            return projectTab.getState();
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Internal project tab object associated with project " + projectName
                        + " was not found. ProjectTabMap: " + projectTabMap);
            }
        }

        return null;
    }

    public void setTerminalState(String projectName, State newState) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectName, newState });
        }

        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab == null) {
            String msg = "Internal project tab object associated with project: " + projectName + " was not found. Unable to set state.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg);
            }
            throw new Exception();
        }

        projectTab.setState(newState);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, projectTab.getState());
        }
    }

    /**
     * Returns the connector associated with a terminal running the application represented by the input project name.
     *
     * @param projectName The application project name.
     *
     * @return The Connector associated with a terminal running the application represented by the input project name.
     */
    public ITerminalConnector getProjectConnector(String projectName) {
        ITerminalConnector connector = null;
        ProjectTab projectTab = projectTabMap.get(projectName);

        if (projectTab != null) {
            connector = projectTab.getConnector();
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Internal project tab object associated with project " + projectName
                        + " was not found. Unable to retrieve connector. ProjectTabMap: " + projectTabMap);
            }
        }

        return connector;
    }

    /**
     * Saves the terminal connector instance on the terminal object represented by the input project name.
     *
     * @param projectName The application project name.
     * @param terminalConnector The terminal connector instance.
     */
    public void setProjectConnector(String projectName, ITerminalConnector terminalConnector) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectName, terminalConnector, projectTabMap.size() });
        }

        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            projectTab.setConnector(terminalConnector);
            projectTabMap.put(projectName, projectTab);
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Internal project tab object associated with project " + projectName
                        + " was not found. Unable to retrieve connector. ProjectTabMap: " + projectTabMap);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, projectTabMap.size());
        }
    }

    /**
     * Returns true if the tab title associated with the input project name was marked as closed. False, otherwise.
     *
     * @param projectName The application project name.
     *
     * @return true if the tab title associated with the input project name was marked as closed. False, otherwise.
     */
    public boolean isProjectTabMarkedClosed(String projectName) {
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            String tabTitle = projectTab.getTitle();
            if (tabTitle != null && tabTitle.startsWith("<Closed>")) {
                return true;
            }
        } else {
            // At this point, the project is no longer in the projectTabMap. Either it was never added (this project
            // was never started) or it has already stopped. In either case, the project tab is unavailable (e.g. "closed")
            // for this project. This is particularly needed during debugger restart processing. If the server has stopped
            // the restart uses this method to indicate it can abort reconnecting.
            return true;
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
                projectTab.writeToStream(DevModeOperations.DEVMODE_COMMAND_EXIT.getBytes(), true);

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

        // Call the terminal object to do further cleanup.
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            projectTab.cleanup();
        } else {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Internal project tab object associated with project " + projectName
                        + " was not found. ProjectTabMap: " + projectTabMap);
            }
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

        if (viewPart != null || (viewPart instanceof ITerminalsView)) {
            ITerminalsView terminalView = (ITerminalsView) viewPart;
            CTabFolder tabFolder = terminalView.getAdapter(CTabFolder.class);

            if (tabFolder != null) {
                tabFolder.removeCTabFolder2Listener(listener);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    /**
     * Refreshes the active terminal view part.
     */
    public void refreshViewPart() {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, viewPart);
        }

        IWorkbenchWindow iWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (iWorkbenchWindow != null) {
            IWorkbenchPage activePage = iWorkbenchWindow.getActivePage();

            if (activePage != null) {
                viewPart = activePage.findView(IUIConstants.ID);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, viewPart);
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
