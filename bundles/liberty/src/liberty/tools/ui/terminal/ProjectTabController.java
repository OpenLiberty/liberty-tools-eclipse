package liberty.tools.ui.terminal;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.custom.CTabItem;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.terminal.view.ui.interfaces.IUIConstants;
import org.eclipse.tm.terminal.view.ui.manager.ConsoleManager;

import liberty.tools.ui.terminal.ProjectTab.State;

/**
 * Manages a set of terminal view project tab instances.
 */
public class ProjectTabController {

    /**
     * The set of active Terminal associated with different application projects.
     */
    private static ConcurrentHashMap<String, ProjectTab> projectTabMap = new ConcurrentHashMap<String, ProjectTab>();

    /**
     * TerminalManager instance.
     */
    private static ProjectTabController instance;

    /**
     * Terminal console manager instance.
     */
    private ConsoleManager consoleMgr;

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
     * @param command The command to execute on the terminal.
     * @param envs The environment properties to be set on the terminal.
     */
    public void runOnTerminal(String projectName, String command, List<String> envs) {
        ProjectTab projectTab = new ProjectTab(projectName);
        projectTabMap.put(projectName, projectTab);
        projectTab.runCommand(command, envs);
    }

    /**
     * Writes the input data to the terminal tab associated with the input project name.
     *
     * @param projectName The application project name.
     * @param content The data to write.
     *
     * @throws Exception
     */
    public void writeTerminalStream(String projectName, byte[] data) throws Exception {
        ProjectTab projectTab = projectTabMap.get(projectName);

        if (projectTab == null) {
            throw new Exception("Unable to write to the terminal. Connector not found. Project name: " + projectName);
        }

        projectTab.writeToStream(data);
    }

    /**
     * Finds the tab item object associated with the terminal running the application represented by the input project name.
     *
     * @param projectName The application project name.
     * @param connector The terminal connector object associated with input project name.
     *
     * @return The tab item object associated with the terminal running the application represented by the input project
     *             name.
     */
    public CTabItem findTerminalTab(String projectName, ITerminalConnector connector) {
        CTabItem item = null;

        if (connector != null) {
            item = consoleMgr.findConsole(IUIConstants.ID, null, projectName, connector, null);
        }

        return item;
    }

    public State getTerminalState(String projectName) {
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            return projectTab.getState();
        }

        return null;
    }

    public void setTerminalState(String projectName, State newState) throws Exception {
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab == null) {
            throw new Exception("Unable to set the state. Connector not found. Project name: " + projectName + ". New state: " + newState);
        }

        projectTab.setState(newState);
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
        ProjectTab projectTab = projectTabMap.get(projectName);
        projectTab.setConnector(terminalConnector);
        projectTabMap.put(projectName, projectTab);
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
            if (tabTitle.startsWith("<Closed>")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Cleans up the objects associated with the terminal object represented by the input project name.
     *
     * @param projectName The application project name.
     */
    public void cleanupTerminal(String projectName) {
        // Call the terminal object to do further cleanup.
        ProjectTab projectTab = projectTabMap.get(projectName);
        if (projectTab != null) {
            projectTab.cleanup();
        }

        // Cleanup the connector from our cache.
        projectTabMap.remove(projectName);
    }
}
