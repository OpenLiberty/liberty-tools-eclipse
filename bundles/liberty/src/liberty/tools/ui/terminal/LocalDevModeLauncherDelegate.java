package liberty.tools.ui.terminal;

import java.util.Map;

import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.terminal.connector.local.launcher.LocalLauncherDelegate;
import org.eclipse.tm.terminal.view.core.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tm.terminal.view.ui.launcher.LauncherDelegateManager;

/**
 * Local launcher delegate extension.
 */
public class LocalDevModeLauncherDelegate extends LocalLauncherDelegate {

    /**
     * LocalDevModeLauncherDelegate extension id.
     */
    public static final String id = "liberty.tools.ui.terminal.local.devmode.launcher.delegate";

    /**
     * Returns an instance of the LocalTerminalLauncherDelegate. Note that there should only be a single delegate instance
     * being used. TerminalService.createTerminalConnector by default does not require the delegate instances to be
     * unique, but the first instance created is returned.
     *
     * @return An instance of the LocalTerminalLauncherDelegate
     */
    public static LocalDevModeLauncherDelegate getInstance() {
        return (LocalDevModeLauncherDelegate) LauncherDelegateManager.getInstance().getLauncherDelegate(id, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITerminalConnector createTerminalConnector(Map<String, Object> properties) {
        String projectName = (String) properties.get(ITerminalsConnectorConstants.PROP_DATA);
        ProjectTabController tptm = ProjectTabController.getInstance();
        ITerminalConnector connector = tptm.getProjectConnector(projectName);

        if (connector == null) {
            connector = super.createTerminalConnector(properties);
            tptm.setProjectConnector(projectName, connector);
        }

        return connector;
    }
}