package io.openliberty.tools.eclipse.ui.launch;

import org.eclipse.debug.ui.console.IConsole;
import org.eclipse.debug.ui.console.IConsoleLineTracker;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.console.IHyperlink;

public class ConsoleLineTracker implements IConsoleLineTracker {

	private IConsole console;
	
	class AbstractHyperlink implements IHyperlink {
        @Override
        public void linkEntered() {
            // do nothing
        }

        @Override
        public void linkExited() {
            // do nothing
        }

        @Override
        public void linkActivated() {
            // do nothing
        }
    }

	@Override
	public void init(IConsole console) {
		this.console = console;
	}

	@Override
	public void lineAppended(IRegion line) {
		
	}

	@Override
	public void dispose() {
		// do nothing
	}

}
