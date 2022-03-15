package liberty.tools.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.handlers.HandlerUtil;

public class DashboardHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        try {
            HandlerUtil.getActiveWorkbenchWindow(event).getActivePage().showView("liberty.views.liberty.devmode.dashboard");
        } catch (Exception e) {
            throw new ExecutionException("Unable to open the Liberty dashboard view", e);
        }

        return null;
    }
}