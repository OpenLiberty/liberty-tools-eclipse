/*******************************************************************************
 * Copyright (c) 2022, 2023 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.launch;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Main configuration tab.
 */
public class StartTab extends AbstractLaunchConfigurationTab {

    /** Configuration map key with a value representing the dev mode start parameter. */
    public static final String PROJECT_START_PARM = "io.openliberty.tools.eclipse.launch.start.parm";

    /** Configuration map key with a value representing the last project name associated with the configuration. */
    public static final String PROJECT_NAME = "io.openliberty.tools.eclipse.launch.project.name";

    /** Configuration map key with a value representing the time when the associated project was last run. */
    public static final String PROJECT_RUN_TIME = "io.openliberty.tools.eclipse.launch.project.time.run";

    /** Configuration map key with a value stating whether or not the associated project ran in a container. */
    public static final String PROJECT_RUN_IN_CONTAINER = "io.openliberty.tools.eclipse.launch.project.container.run";

    /** Configuration map key with a value stating whether or not the associated project ran with clean option. */
    public static final String PROJECT_CLEAN = "io.openliberty.tools.eclipse.launch.project.clean";
    
    /** Main preference page ID. */
    public static final String MAIN_PREFERENCE_PAGE_ID = "io.openliberty.tools.eclipse.ui.preferences.page";

    /** Configuration map key with a value stating whether or not the associated project ran in a container. */
    public static final String TAB_NAME = "Start";

    private static final String EXAMPLE_START_PARMS = "Example: -DhotTests=true";

    /** The font to use for the contents of this Tab. */
    private Font font;

    /** Tab image */
    private Image image;

    /** Holds the start parameter text configuration. */
    private Text startParmText;

    /** Holds the project name associated with the configuration being displayed. */
    private Label projectNameLabel;

    /** Holds the run in container check box. */
    private Button runInContainerCheckBox;
    
    /** Holds the project clean check box. */
    private Button projectCleanCheckBox;

    /** DevModeOperations instance. */
    private DevModeOperations devModeOps = DevModeOperations.getInstance();

    /**
     * Constructor.
     */
    public StartTab() {
        image = Utils.getImage(PlatformUI.getWorkbench().getDisplay(), DashboardView.LIBERTY_LOGO_PATH);
        font = PlatformUI.getWorkbench().getDisplay().getSystemFont();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createControl(Composite parent) {
        // Main composite.
        Composite mainComposite = new Composite(parent, SWT.NONE);
        mainComposite.setLayout(new GridLayout(1, false));
        mainComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        setControl(mainComposite);
        createProjectLabel(mainComposite);

        // Parameter group composite.
        Composite parmsGroupComposite = createGroupComposite(mainComposite, "", 2);
        createInputParmText(parmsGroupComposite);
        createRunInContainerButton(parmsGroupComposite);
        createProjectCleanButton(parmsGroupComposite);

        createLabelWithPreferenceLink(mainComposite);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { configuration });
        }

        // Save the active project's name in the configuration.
        IProject activeProject = Utils.getActiveProject();
        if (activeProject != null) {
            configuration.setAttribute(PROJECT_NAME, activeProject.getName());
        }

        configuration.setAttribute(PROJECT_START_PARM, getDefaultStartCommand(activeProject));

        configuration.setAttribute(PROJECT_RUN_IN_CONTAINER, false);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeFrom(ILaunchConfiguration configuration) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { configuration });
        }

        // Initialize the configuration view with previously saved values.
        try {

            String consoleText = configuration.getAttribute(PROJECT_START_PARM, (String) null);
            startParmText.setText(consoleText);

            boolean runInContainer = configuration.getAttribute(PROJECT_RUN_IN_CONTAINER, false);
            runInContainerCheckBox.setSelection(runInContainer);
            
            boolean projectClean = configuration.getAttribute(PROJECT_CLEAN, false);
            projectCleanCheckBox.setSelection(projectClean);

            String projectName = configuration.getAttribute(PROJECT_NAME, (String) null);
            if (projectName == null) {
                super.setErrorMessage(
                        "A project must be selected in order to provide a context to associate the run configuration with.  Either use a tree view like Package Explorer or have an editor window.");
            } else {
                projectNameLabel.setText(projectName);
            }

            setDirty(false);

        } catch (CoreException ce) {
            String msg = "An error was detected during Run Configuration initialization.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, ce);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.run_config_initialize_error, null), ce, true);
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    private boolean checkForIncorrectTerms() {
        boolean valid = true;
        String startParamStr = startParmText.getText();

        if (startParamStr.startsWith("mvn") || startParamStr.startsWith("gradle")) {
            super.setErrorMessage("Don't include mvn or gradle executables, just the parameters");
            valid = false;
        }
        if (startParamStr.contains("liberty:dev") || startParamStr.contains("libertyDev")) {
            super.setErrorMessage("Dev mode detected");
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean isValid(ILaunchConfiguration config) {
        try {
            String configProjectName = config.getAttribute(PROJECT_NAME, (String) null);

            if (configProjectName == null) {
                super.setErrorMessage(
                        "This Run/Debug config is corrupted and can't be used since no project was selected before creating. To create a new Run/Debug config first select a project in the Liberty dashboard, Project/Package explorer view, or via editor.");
                return false;
            }

            IProject selectedProject = Utils.getActiveProject();
            if (selectedProject != null) {
                String selectedProjectName = selectedProject.getName();
                if (!configProjectName.equals(selectedProjectName)) {
                    super.setWarningMessage(
                            "Must use an existing (or new) configuration associated with selected project: " + selectedProjectName);
                    return false;
                }
            }

            // Check if project is already started
            if (devModeOps.isProjectStarted(configProjectName)) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "The start request was already issued on project " + configProjectName);
                }

                super.setErrorMessage(NLS.bind(Messages.start_already_issued, configProjectName));
                return false;
            }
        } catch (CoreException ce) {
            String msg = "Error getting project name";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, ce);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.project_name_error, null), ce, true);
            return false;
        }
        return checkForIncorrectTerms();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {

        String startParamStr = startParmText.getText();

        boolean runInContainerBool = runInContainerCheckBox.getSelection();
        
        boolean projectCleanBool = projectCleanCheckBox.getSelection();

        configuration.setAttribute(PROJECT_RUN_IN_CONTAINER, runInContainerBool);
        
        configuration.setAttribute(PROJECT_CLEAN, projectCleanBool);

        configuration.setAttribute(PROJECT_START_PARM, startParamStr);

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_UI, "In performApply with project name = " + projectNameLabel.getText() + ", text = "
                    + startParamStr + ", runInContainer = " + runInContainerBool);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return TAB_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image getImage() {
        return image;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        if (image != null) {
            image.dispose();
        }
    }

    /**
     * Creates a composite that will display a set of elements as a group.
     * 
     * @param parent The parent composite.
     * @param groupName The title of the group to be created.
     * @param numColumns The number of columns of the grid layout in the composite to be created.
     * 
     * @return A composite that will display a set of elements as a group.
     */
    private Composite createGroupComposite(Composite parent, String groupName, int numColumns) {
        Group group = new Group(parent, SWT.NONE);
        group.setText(groupName);
        group.setLayout(new GridLayout());
        group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Composite composite = new Composite(group, SWT.NONE);
        GridLayout rgLayoutx = new GridLayout(numColumns, false);
        composite.setLayout(rgLayoutx);
        composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        return composite;
    }

    /**
     * Creates the project label entry that shows the name of the project associated with the dialog.
     * 
     * @param parent The parent composite.
     */
    private void createProjectLabel(Composite parent) {
        Composite projectComposite = new Composite(parent, SWT.NONE);
        GridLayout projectLayout = new GridLayout(2, false);
        projectComposite.setLayout(projectLayout);
        projectComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label projectLabel = new Label(projectComposite, SWT.NONE);
        projectLabel.setFont(font);
        projectLabel.setText("Project: ");
        GridDataFactory.swtDefaults().applyTo(projectLabel);

        projectNameLabel = new Label(projectComposite, SWT.NONE);
        projectNameLabel.setFont(font);
        projectNameLabel.setText("");
        GridDataFactory.swtDefaults().applyTo(projectNameLabel);
    }

    /**
     * Creates the labeled input text entry that allows users to enter parameters used to run dev mode.
     * 
     * @param parent The parent composite.
     */
    private void createInputParmText(Composite parent) {
        Label inputParmLabel = new Label(parent, SWT.NONE);
        inputParmLabel.setFont(font);
        inputParmLabel.setText("Start &parameters:");
        GridDataFactory.swtDefaults().indent(20, 0).applyTo(inputParmLabel);

        startParmText = new Text(parent, SWT.BORDER);
        startParmText.setFont(font);
        startParmText.setMessage(EXAMPLE_START_PARMS);
        startParmText.addModifyListener(new ModifyListener() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void modifyText(ModifyEvent e) {
                checkForIncorrectTerms();
                setDirty(true);
                updateLaunchConfigurationDialog();
            }

        });
        GridDataFactory.fillDefaults().grab(true, false).applyTo(startParmText);
    }

    /**
     * Creates the label entry that contains a link to the Liberty preferences.
     * 
     * @param parent The parent composite.
     */
    private void createLabelWithPreferenceLink(Composite parent) {
        Label emptyLineLabel = new Label(parent, SWT.NONE);
        GridDataFactory.swtDefaults().applyTo(emptyLineLabel);

        Link link = new Link(parent, SWT.WRAP);
        link.setFont(font);
        link.setText("Maven/Gradle executable paths can be set in <a>Liberty Preferences</a>");
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(null, MAIN_PREFERENCE_PAGE_ID, null, null);
                dialog.open();
            }
        });
        GridDataFactory.swtDefaults().applyTo(link);
    }

    /**
     * Creates the button entry that indicates whether or not the project should run in a container.
     * 
     * @param parent The parent composite.
     */
    private void createRunInContainerButton(Composite parent) {
        runInContainerCheckBox = new Button(parent, SWT.CHECK);
        runInContainerCheckBox.setText("Run in &Container");
        runInContainerCheckBox.setSelection(false);
        runInContainerCheckBox.setFont(font);
        runInContainerCheckBox.addSelectionListener(new SelectionAdapter() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void widgetSelected(SelectionEvent event) {
                setDirty(true);
                updateLaunchConfigurationDialog();
            }
        });
        GridDataFactory.swtDefaults().applyTo(runInContainerCheckBox);

        Label emptyColumnLabel = new Label(parent, SWT.NONE);
        GridDataFactory.swtDefaults().applyTo(emptyColumnLabel);
    }

    /**
     * Creates the button entry that indicates whether or not the project should run with project clean command.
     * 
     * @param parent The parent composite.
     */
	private void createProjectCleanButton(Composite parent) {
		projectCleanCheckBox = new Button(parent, SWT.CHECK);
		projectCleanCheckBox.setText("Clean project");
		projectCleanCheckBox.setSelection(false);
		projectCleanCheckBox.setFont(font);
		projectCleanCheckBox.addSelectionListener(new SelectionAdapter() {

			/**
			 * {@inheritDoc}
			 */
			@Override
			public void widgetSelected(SelectionEvent event) {
				setDirty(true);
				updateLaunchConfigurationDialog();
			}
		});
		GridDataFactory.swtDefaults().applyTo(projectCleanCheckBox);

		Label emptyColumnLabel = new Label(parent, SWT.NONE);
		GridDataFactory.swtDefaults().applyTo(emptyColumnLabel);
	}

    /**
     * Returns the default start parameters.
     * 
     * @param Active project (may be null if there isn't one)
     * 
     * @return The default start parameters
     */
    private String getDefaultStartCommand(IProject iProject) {
        String parms = "";
        try {
            if (iProject != null) {
                // Verify that the existing projects are projects are read and classified. This maybe the first time
                // this plugin's function is being used.
                devModeOps.verifyProjectSupport(iProject);
                parms = devModeOps.getProjectModel().getDefaultStartParameters(iProject);
            }
        } catch (Exception e) {
            // Report the issue and continue without a initial start command.
            String msg = "An error was detected when the default start parameters were retrieved.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(NLS.bind(Messages.start_parm_retrieve_error, null), e, true);
        }

        return parms;
    }
}
