package liberty.tools.ui.terminal;

import org.eclipse.tm.terminal.view.core.interfaces.ITerminalService;
import org.eclipse.tm.terminal.view.core.interfaces.ITerminalTabListener;

/**
 * Listens for terminal tab termination.
 */
public class TerminalTabListenerImpl implements ITerminalTabListener {

    /**
     * The terminal service instance being used to open the terminal and run a command.
     */
    ITerminalService terminalService;

    /**
     * The name of the project being processed.
     */
    String projectName;

    /**
     * Constructor.
     * 
     * @param terminalService The terminal service instance being used to open the terminal and run a command.
     * @param projectName The name of the current project being processed.
     */
    public TerminalTabListenerImpl(ITerminalService terminalService, String projectName) {
        this.terminalService = terminalService;
        this.projectName = projectName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void terminalTabDisposed(Object source, Object data) {
        // Cleanup the connector from our cache.
        LocalDevModeLauncherDelegate.getInstance().removeConnector(projectName);

        // Remove this listener from the service calling this listener.
        terminalService.removeTerminalTabListener(this);
    }
}
