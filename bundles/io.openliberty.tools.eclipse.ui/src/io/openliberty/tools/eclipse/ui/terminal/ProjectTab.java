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
package io.openliberty.tools.eclipse.ui.terminal;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Image;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.terminal.view.core.TerminalServiceFactory;
import org.eclipse.tm.terminal.view.core.interfaces.ITerminalService;
import org.eclipse.tm.terminal.view.core.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tm.terminal.view.ui.interfaces.ITerminalsView;
import org.eclipse.tm.terminal.view.ui.interfaces.IUIConstants;
import org.eclipse.tm.terminal.view.ui.tabs.TabFolderManager;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.Project.BuildType;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Represents a terminal tab item within the terminal view associated with a running application project.
 */
public class ProjectTab {

    /** The name of the application project associated with this terminal. */
    private String projectName;

    /**
     * Terminal connector instance set by the LocalDevModeLauncherDelegate when the connector is created during initial connection.
     */
    private ITerminalConnector connector;

    /** Terminal view tab item associated with the running application project. */
    private CTabItem projectTab;

    /** Terminal service. */
    private ITerminalService terminalService;

    /** Terminal tab listener associated with this terminal tab. */
    private TerminalTabListenerImpl tabListener;

    /** State of this object. */
    private State state;

    /** Tab image */
    private Image libertyImage;
    
    /**
     * PID of running server
     */
    private String serverPid;
    
    /**
     * Current time just before command start
     */
    private FileTime preStartTime;

    /** States. */
    public static enum State {
        INACTIVE, STARTED, STOPPED
    };

    /** The NIX shell on which the terminal commands are processed. */
    private final String NIX_SHELL_COMMAND = "/bin/sh";

    /**
     * Constructor.
     *
     * @param projectName The application project name.
     */
    public ProjectTab(String projectName) {
        this.projectName = projectName;
        this.terminalService = TerminalServiceFactory.getService();
        this.tabListener = new TerminalTabListenerImpl(projectName);
        this.libertyImage = Utils.getImage(PlatformUI.getWorkbench().getDisplay(), DashboardView.LIBERTY_LOGO_PATH);
        this.serverPid = null;

        state = State.INACTIVE;
    }

    /**
     * Sets the connector associated with this terminal.
     * 
     * @param connector The terminal connector for terminal interaction.
     */
    public void setConnector(ITerminalConnector connector) {
        this.connector = connector;
    }

    /**
     * Returns the connector associated with this terminal.
     * 
     * @return The connector associated with this terminal.
     */
    public ITerminalConnector getConnector() {
        return connector;
    }

    /**
     * Launches a terminal and runs the input command.
     *
     * @param projectPath The application project path.
     * @param command The command to run on the terminal.
     * @param envs The list of environment properties to be set on the terminal.
     */
    public void runCommand(Project project, String command, List<String> envs) {
    	String projectPath = project.getPath();
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { projectPath, command, envs });
        }

        ITerminalService.Done done = new ITerminalService.Done() {
            @Override
            public void done(IStatus status) {
                // The console tab for the associated project opened.
                if (status.getCode() == IStatus.OK) {

                    // Save the object representing the currently active console tab instance.
                    projectTab = getActiveProjectTab();

                    // Update the tab image with the Liberty logo.
                    updateImage();

                    // Register a terminal tab disposed listener.
                    terminalService.addTerminalTabListener(tabListener);

                    // Update the state.
                    setState(State.STARTED);
                }
            }
        };

        // Get the current time before running the command
        this.preStartTime = FileTime.fromMillis(System.currentTimeMillis());
        
        terminalService.openConsole(getProperties(projectPath, envs, command), done);
        
        // Read and save pid for Liberty server process
        serverPid = getPidOfRunningServer(project);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }
    
    /**
     * 
     * Read the pid of the running server from the messages.log file. 
     * 
     * @param project
     * @return
     */
    private String getPidOfRunningServer(Project project) {
    	try {
        	// If this is the first start for this server, the messages.log file may
    		// not be created yet. Loop for 30 seconds until the file is found.
        	int timeout = 0;
        	Path pathToMessagesLog = getMessagesLogPath(project);
        	while (pathToMessagesLog == null && timeout < 30) {
        		Thread.sleep(1000);
        		timeout++;
        		
        		pathToMessagesLog = getMessagesLogPath(project);
        	}
        	if (pathToMessagesLog != null) {
        		// At this point, we found the messages.log file, but this could be from a previous server start. 
        		// Loop until the creation time of messages.log is after the start time of the issued command.
        		timeout = 0;
        		boolean messagesLogIsCurrent = false;
        		while (!messagesLogIsCurrent && timeout < 30) {
        			Thread.sleep(1000);
        			timeout++;
        			
        			FileTime createTime = Files.readAttributes(pathToMessagesLog, BasicFileAttributes.class).creationTime();
        			
        			messagesLogIsCurrent = createTime.compareTo(preStartTime) > 0;
        		}
        		
        		if (messagesLogIsCurrent) {
        		
	        		if (Trace.isEnabled()) {
			            Trace.getTracer().traceEntry(Trace.TRACE_UI, "Reading pid from " + pathToMessagesLog.toString());
			        }
	                
					for(String line : Files.readAllLines(pathToMessagesLog)) {
						if (line.contains("process =")) {
							String pid = line.split("=")[1].trim().replaceAll("\\D.*", "");;
							if (Trace.isEnabled()) {
					            Trace.getTracer().traceEntry(Trace.TRACE_UI, "Saving pid for server: " + pid);
					        }
							return pid;
						}
					}
					
        		} else {
        			if (Trace.isEnabled()) {
    		            Trace.getTracer().traceEntry(Trace.TRACE_UI, "Current messages.log could not be found.");
    		        }
        		}
        	} else {
        		if (Trace.isEnabled()) {
		            Trace.getTracer().traceEntry(Trace.TRACE_UI, "Timedout waiting for messages.log to be available.");
		        }
        	}
			
		} catch (Exception e) {
			if (Trace.isEnabled()) {
	            Trace.getTracer().traceEntry(Trace.TRACE_UI, "Exception while reading messages.log: " + e.getMessage());
	        }
		}
    	
    	return null;
    }
    
    /**
     * Returns the path of the server's messages.log file after Liberty server deployment.
     * 
     * @param project The project for which this operations is being performed.
     * 
     * @return The path of the server's messages.log file after Liberty server deployment.
     * 
     * @throws Exception
     */
    private Path getMessagesLogPath(Project project) throws Exception {
        String projectPath = project.getPath();
        String projectName = project.getName();
        Path basePath = null;
        BuildType buildType = project.getBuildType();
        if (buildType == Project.BuildType.MAVEN) {
            basePath = Paths.get(projectPath, "target", "liberty", "wlp", "usr", "servers");
        } else if (buildType == Project.BuildType.GRADLE) {
            basePath = Paths.get(projectPath, "build", "wlp", "usr", "servers");
        } else {
            throw new Exception("Unexpected project build type: " + buildType + ". Project" + projectName
                    + "does not appear to be a Maven or Gradle built project.");
        }

        // Make sure the base path exists. If not return null.
        File basePathFile = new File(basePath.toString());
        if (!basePathFile.exists()) {
            return null;
        }

        try (Stream<Path> matchedStream = Files.find(basePath, 3, (path, basicFileAttribute) -> {
            if (basicFileAttribute.isRegularFile()) {
                return path.getFileName().toString().equalsIgnoreCase("messages.log");
            }
            return false;
        });) {
            List<Path> matchedPaths = matchedStream.collect(Collectors.toList());
            int numberOfFilesFound = matchedPaths.size();

            if (numberOfFilesFound != 1) {
                if (numberOfFilesFound == 0) {
                    String msg = "Unable to find the messages.log file for project " + projectName + ".";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg);
                    }
                    return null;
                } else {
                    String msg = "More than one messages.log files were found for project " + projectName
                            + ". Unable to determine the messages.log file to use.";
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, msg);
                    }
                    ErrorHandler.processErrorMessage(msg, false);
                    throw new Exception(msg);
                }
            }
            return matchedPaths.get(0);
        }
    }

    /**
     * Returns a map of properties needed to launch a terminal.
     *
     * @param projectPath The application project path.
     * @param envs Environment variables to be set.
     * @param command The command to execute.
     *
     * @return A map of properties needed to launch a terminal.
     */
    private Map<String, Object> getProperties(String projectPath, List<String> envs, String command) {
        HashMap<String, Object> properties = new HashMap<String, Object>();
        properties.put(ITerminalsConnectorConstants.PROP_TITLE, projectName);
        properties.put(ITerminalsConnectorConstants.PROP_ENCODING, "UTF-8");
        properties.put(ITerminalsConnectorConstants.PROP_DELEGATE_ID, LocalDevModeLauncherDelegate.id);
        properties.put(ITerminalsConnectorConstants.PROP_DATA, projectName);
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_MERGE_ENVIRONMENT, Boolean.TRUE);
        properties.put(ITerminalsConnectorConstants.PROP_FORCE_NEW, Boolean.TRUE);
        properties.put(ITerminalsConnectorConstants.PROP_DATA_NO_RECONNECT, Boolean.TRUE);
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ARGS, command);
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT, envs.toArray(new String[envs.size()]));
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_WORKING_DIR, projectPath);

        // WIN: Terminal command is run using whatever is specified under the ComSpec environment variable, or cmd.exe by default.
        // NIX: Terminal command is run using the system shell.
        if (!Utils.isWindows()) {
            properties.put(ITerminalsConnectorConstants.PROP_PROCESS_PATH, NIX_SHELL_COMMAND);
        }

        return properties;
    }

    /**
     * Updates the tab image with the Liberty logo.
     */
    private void updateImage() {
        projectTab.getDisplay().asyncExec(() -> {
            projectTab.setImage(libertyImage);
        });
    }

    /**
     * Writes to the terminal's output stream.
     *
     * @param content The bytes to be written to the terminal.
     *
     * @throws Exception
     */
    public void writeToStream(byte[] content) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new String(content));
        }

        if (connector == null) {
            String msg = "Unable to find terminal connector. Be sure to run the start action first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg + "Content: " + new String(content));
            }
            throw new Exception(msg);
        }

        OutputStream terminalStream = connector.getTerminalToRemoteStream();
        if (terminalStream == null) {
            String msg = "Unable to find terminal remote stream. The terminal might not be active. Be sure to run the start action first.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg + " Connector: " + connector);
            }
            throw new Exception(msg);
        }

        showTerminalView();
        terminalStream.write(content);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    /**
     * Returns the active CTabItem object associated with the currently active terminal tab.
     *
     * @return The active CTabItem object associated with the currently active terminal tab.
     */
    private CTabItem getActiveProjectTab() {
        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (activePage == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "No active page found. No-op.");
            }
            return null;
        }

        IViewPart viewPart = activePage.findView(IUIConstants.ID);
        if (viewPart == null || !(viewPart instanceof ITerminalsView)) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "No terminal view found. No-op. Active view label: " + activePage.getLabel());
            }
            return null;
        }

        ITerminalsView view = (ITerminalsView) viewPart;
        TabFolderManager manager = view.getAdapter(TabFolderManager.class);

        if (manager == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "No tab folder manager found. No-op. Active view title: " + view.getTitle());
            }
            return null;
        }

        CTabItem item = manager.getActiveTabItem();

        return item;
    }

    /**
     * Returns the tab's title text.
     *
     * @return The tab's title text.
     */
    public String getTitle() {
        String title = null;
        if (projectTab != null) {
            title = projectTab.getText();
        }

        return title;
    }

    /**
     * Returns the current state.
     *
     * @return the current state.
     */
    public State getState() {
        return state;
    }

    /**
     * Sets the state of this object.
     *
     * @param newState The new state.
     */
    public synchronized void setState(State newState) {
        this.state = newState;
    }

    /**
     * Performs cleanup.
     */
    public void cleanup() {
        // Remove the registered listener from the calling service.
        terminalService.removeTerminalTabListener(tabListener);

        // Dispose of the liberty image associated with this tab.
        if (libertyImage != null) {
            libertyImage.dispose();
        }
    }
    
    /**
     * Return the pid of the running server
     */
    public String getServerPid() {
    	return this.serverPid;
    }

    /**
     * Shows the terminal view in the foreground and focuses on it.
     */
    public void showTerminalView() {
        // Bring the main terminal view to the front.
        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (activePage == null) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "No active page found. No-op.");
            }
            return;
        }

        IViewPart viewPart = null;
        try {
            viewPart = activePage.showView(IUIConstants.ID, null, IWorkbenchPage.VIEW_ACTIVATE);
            if (viewPart == null) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI, "No terminal view found. No-op. Active view label: " + activePage.getLabel());
                }
                return;
            }
            activePage.bringToTop(viewPart);

        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, "Error while attempting to show terminal view. Active view title: "
                        + ((viewPart != null) ? viewPart.getTitle() : "null"));
            }
            return;
        }

        // Bring tab item associated with the application project to the front.
        if (viewPart instanceof ITerminalsView) {
            ITerminalsView view = (ITerminalsView) viewPart;
            TabFolderManager manager = view.getAdapter(TabFolderManager.class);

            if (manager == null) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI,
                            "No tab folder manager found. No-op. Terminal view tab title: " + view.getTitle());
                }
                return;
            }

            manager.bringToTop(projectTab);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Class: ").append(this.getClass().getName()).append(": ");
        sb.append("projectName: ").append(projectName).append(", ");
        sb.append("State: ").append(state).append(", ");
        sb.append("Connector: ").append(connector).append(", ");
        sb.append("TabListener: ").append(tabListener);
        return sb.toString();
    }
}
