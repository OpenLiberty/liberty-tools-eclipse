package liberty.tools.ui;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.part.ViewPart;

import liberty.tools.DevModeOperations;
import liberty.tools.LibertyDevPlugin;
import liberty.tools.utils.Project;

public class DevModeView extends ViewPart {

	public static final DevModeOperations devMode = new DevModeOperations();

	ListViewer viewer;

	Action startAction;
	Action startWithParmAction;
	Action startInContanerAction;
	Action stopAction;
	Action runTestAction;
	Action viewTestReportsAction;

	@Override
	public void createPartControl(Composite parent) {
		viewer = new ListViewer(parent);
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new LabelProvider());
		viewer.setInput(Project.getWokspaceJavaProjects());
		createActions();
		createContextMenu();
		getSite().setSelectionProvider(viewer);
	}

	@Override
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	/**
	 * Creates a right-click menu.
	 */
	private void createContextMenu() {
		MenuManager menuMgr = new MenuManager();
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager mgr) {
				addActionsToContextMenu(mgr);

			}
		});

		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);

		getSite().registerContextMenu("liberty.views.liberty.devmode", menuMgr, viewer);
	}

	/**
	 * Populates the menue context actions.
	 * 
	 * @param mgr The menu manager.
	 */
	private void addActionsToContextMenu(IMenuManager mgr) {
		mgr.add(startAction);
		mgr.add(startWithParmAction);
		mgr.add(startInContanerAction);
		mgr.add(stopAction);
		mgr.add(runTestAction);
		mgr.add(viewTestReportsAction);
		mgr.add(new Separator());
	}

	/**
	 * Create content menu actions.
	 */
	public void createActions() {
		ImageDescriptor libertyImgDesc = ImageDescriptor
				.createFromURL(LibertyDevPlugin.getDefault().getBundle().getResource("icons/openLibertyLogo.png"));

		// Start.
		startAction = new Action("Start") {
			public void run() {
				devMode.start();
			}
		};
		startAction.setImageDescriptor(libertyImgDesc);

		// Start with parameters.
		startWithParmAction = new Action("Start...") {
			public void run() {
				String parms = getStartParms();
				devMode.startWithParms(parms);
			}
		};
		startWithParmAction.setImageDescriptor(libertyImgDesc);

		// Start in container.
		startInContanerAction = new Action("Start in container") {
			public void run() {
				devMode.startInContainer();
			}
		};
		startInContanerAction.setImageDescriptor(libertyImgDesc);

		// Stop.
		stopAction = new Action("Stop") {
			public void run() {
				devMode.stop();
			}
		};
		stopAction.setImageDescriptor(libertyImgDesc);

		// Run tests.
		runTestAction = new Action("Run tests") {
			public void run() {
				devMode.runTests();
			}
		};
		runTestAction.setImageDescriptor(libertyImgDesc);

		// View test reports.
		viewTestReportsAction = new Action("View test reports") {
			public void run() {
				devMode.openTestReports();
			}
		};
		viewTestReportsAction.setImageDescriptor(libertyImgDesc);
	}

	/**
	 * Gets start command parameters provided by the user through an input dialog window.
	 * 
	 * @return The parameters entered by the user.
	 */
	public String getStartParms() {
		String dTitle = "Liberty Development Mode";
		String dMessage = "Specify custom parameters for the liberty dev command.";
		String dInitValue = "";
		IInputValidator iValidator = getParmListValidator();
		Shell shell = Display.getCurrent().getActiveShell();
		InputDialog iDialog = new InputDialog(shell, dTitle, dMessage, dInitValue, iValidator) {
		};
		String userInput = "";
		if (iDialog.open() == Window.OK) {
			userInput = iDialog.getValue();
		}

		return userInput;
	}

	/**
	 * Creates a validator object for user provided parameters.
	 * 
	 * @return A validator object for user provided parameters.
	 */
	public IInputValidator getParmListValidator() {
		return new IInputValidator() {

			@Override
			public String isValid(String text) {
				String[] parmSegments = text.split(" ");
				for (int i = 0; i < parmSegments.length; i++) {
					if (parmSegments[i] != null && !parmSegments[i].isEmpty() && !parmSegments[i].startsWith("-")) {
						return "Parameters must start with -";
					}
				}
				return null;
			}
		};
	}
}
