package liberty.tools.ui.terminal;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import liberty.tools.LibertyDevPlugin;
import liberty.tools.ui.DashboardView;

/**
 * Represents a terminal tab item within the terminal view associated with a running application project.
 */
public class ProjectTab {

    /**
     * The name of the application project associated with this terminal.
     */
    private String projectName;

    /**
     * Terminal connector instance set by the LocalDevModeLauncherDelegate when the connector is created during initial
     * connection.
     */
    private ITerminalConnector connector;

    /**
     * Terminal view tab item associated with the running application project.
     */
    private CTabItem projectTab;

    /**
     * Terminal service.
     */
    private ITerminalService terminalService;

    /**
     * Terminal tab listener associated with this terminal tab.
     */
    private TerminalTabListenerImpl tabListener;

    /**
     * State of this object.
     */
    private State state;

    /**
     * States.
     */
    public static enum State {
        INACTIVE, STARTED, STOPPED
    };

    /**
     * Constructor.
     *
     * @param projectName The application project name.
     */
    public ProjectTab(String projectName) {
        this.projectName = projectName;
        this.terminalService = TerminalServiceFactory.getService();
        this.tabListener = new TerminalTabListenerImpl(projectName);

        state = State.INACTIVE;
    }

    /**
     * Sets the connector associated with this terminal.
     */
    public void setConnector(ITerminalConnector connector) {
        this.connector = connector;
    }

    /**
     * Returns the connector associated with this terminal.
     */
    public ITerminalConnector getConnector() {
        return connector;
    }

    /**
     * Launches a terminal and runs the input command.
     *
     * @param command The command to run on the terminal.
     * @param envs The list of environment properties to be set on the terminal.
     */
    public void runCommand(String command, List<String> envs) {

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

        terminalService.openConsole(getProperties(envs, command), done);
    }

    /**
     * Returns a map of properties needed to launch a terminal.
     *
     * @param envs Environment variables to be set.
     * @param command The command to execute.
     *
     * @return A map of properties needed to launch a terminal.
     */
    private Map<String, Object> getProperties(List<String> envs, String command) {
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

        return properties;
    }

    /**
     * Updates the tab image with the Liberty logo.
     */
    private void updateImage() {
        projectTab.getDisplay().asyncExec(() -> {
            URL url = LibertyDevPlugin.getDefault().getBundle().getResource(DashboardView.LIBERTY_LOGO_PATH);

            if (url != null) {
                InputStream stream = null;
                try {
                    stream = url.openStream();
                    projectTab.setImage(new Image(projectTab.getDisplay(), stream));
                } catch (Exception e) {
                    // Ignore.
                } finally {
                    try {
                        if (stream != null) {
                            stream.close();
                        }
                    } catch (Exception e) {
                        // Ignore.
                    }
                }
            }
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
        if (connector == null) {
            throw new Exception("Unable to find terminal connector. Be sure to run the start action first.");
        }

        OutputStream terminalStream = connector.getTerminalToRemoteStream();
        if (terminalStream == null) {
            throw new Exception(
                    "Unable to find terminal remote stream. The terminal might not be active. Be sure to run the start action first.");
        }

        showTerminalView();
        terminalStream.write(content);
    }

    /**
     * Returns the active CTabItem object associated with the currently active terminal tab.
     *
     * @return The active CTabItem object associated with the currently active terminal tab.
     */
    private CTabItem getActiveProjectTab() {
        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (activePage == null) {
            return null;
        }

        IViewPart viewPart = activePage.findView(IUIConstants.ID);
        if (viewPart == null || !(viewPart instanceof ITerminalsView)) {
            return null;
        }

        ITerminalsView view = (ITerminalsView) viewPart;
        TabFolderManager manager = view.getAdapter(TabFolderManager.class);

        if (manager == null) {
            return null;
        }

        CTabItem item = manager.getActiveTabItem();

        return item;
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
    }

    /**
     * Shows the terminal view in the foreground and focuses on it.
     */
    public void showTerminalView() {
        // Bring the main terminal view to the front.
        IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        if (activePage == null) {
            return;
        }
        IViewPart viewPart = null;
        try {
            viewPart = activePage.showView(IUIConstants.ID, null, IWorkbenchPage.VIEW_ACTIVATE);
            if (viewPart == null) {
                return;
            }
            activePage.bringToTop(viewPart);

        } catch (Exception e) {
            return;
        }

        // Bring tab item associated with the application project to the front.
        if (viewPart instanceof ITerminalsView) {
            ITerminalsView view = (ITerminalsView) viewPart;
            TabFolderManager manager = view.getAdapter(TabFolderManager.class);

            if (manager == null) {
                return;
            }

            manager.bringToTop(projectTab);
        }
    }
}