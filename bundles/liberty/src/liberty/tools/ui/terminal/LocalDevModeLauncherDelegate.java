package liberty.tools.ui.terminal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.terminal.connector.local.launcher.LocalLauncherDelegate;
import org.eclipse.tm.terminal.view.core.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tm.terminal.view.ui.launcher.LauncherDelegateManager;

/**
 * Local launcher delegate extension.
 */
public class LocalDevModeLauncherDelegate extends LocalLauncherDelegate {

    /**
     * Id.
     */
    public static final String id = "liberty.tools.ui.terminal.local.devmode.launcher.delegate";

    /**
     * Label.
     */
    public static final String label = "liberty.local.devmode.launcher.delegate";

    /**
     * The set of connectors associated associated with accessed application projects.
     */
    private ConcurrentHashMap<String, ITerminalConnector> connectors = new ConcurrentHashMap<String, ITerminalConnector>();

    /**
     * Returns an instance of the LocalTerminalLauncherDelegate. Note that there should only be a single delegate instance
     * being used because TerminalService.createTerminalConnector by default does not require the delegate instances to be
     * unique. Therefore, in each case, first instance created is returned.
     * 
     * @return An instance of the LocalTerminalLauncherDelegate
     */
    public static LocalDevModeLauncherDelegate getInstance() {
        return (LocalDevModeLauncherDelegate) LauncherDelegateManager.getInstance().getLauncherDelegate(id, false);
    }

    /**
     * Returns an instance of the local terminal connector associated with the specified project.
     * 
     * @return An instance of the local terminal connector associated with the specified project, or null if the connector
     *     was not found.
     */
    public ITerminalConnector getConnector(String projectName) {
        return connectors.get(projectName);
    }

    /**
     * Removes the connector associated with the specified project.
     * 
     * @return The removed connector associated with the specified project, or null if the connector was not found.
     */
    public ITerminalConnector removeConnector(String projectName) {
        return connectors.remove(projectName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITerminalConnector createTerminalConnector(Map<String, Object> properties) {
        String projectName = (String) properties.get(ITerminalsConnectorConstants.PROP_DATA);
        ITerminalConnector connector = connectors.get(projectName);

        if (connector == null) {
            connector = super.createTerminalConnector(properties);
            connectors.put(projectName, connector);
        }

        return connector;
    }
}