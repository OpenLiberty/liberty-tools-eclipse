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
package io.openliberty.tools.eclipse.ui.launch;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.logging.Trace;
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

    /** Tab image */
    Image image;

    /** Configuration map key with a value stating whether or not the associated project ran in a container. */
    public static final String START_TAB_NAME = "Start";

    private static final String EXAMPLE_START_PARMS = "Example: -DhotTests=true";

    /** Holds the start parameter text configuration. */
    private Text startParmText;

    private Label projectNameLabel;

    /** Holds the run in container check box. */
    private Button runInContainerCheckBox;

    /** DevModeOperations instance. */
    private DevModeOperations devModeOps = DevModeOperations.getInstance();

    /**
     * Constructor.
     */
    public StartTab() {
        image = Utils.getImage(PlatformUI.getWorkbench().getDisplay(), DashboardView.LIBERTY_LOGO_PATH);
    }

    private boolean isValid = true;

    /**
     * {@inheritDoc}
     */
    @Override
    public void createControl(Composite parent) {
        Composite composite = new Group(parent, SWT.BORDER);
        setControl(composite);

        // Create a page layout.
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(composite);

        // Project name
        Label projectLabel = new Label(composite, SWT.NONE);
        projectLabel.setText("Project");
        GridDataFactory.swtDefaults().indent(20, 0).applyTo(projectLabel);

        projectNameLabel = new Label(composite, SWT.NONE);
        projectNameLabel.setText("");
        GridDataFactory.swtDefaults().indent(20, 0).applyTo(projectNameLabel);

        // Add an empty line.
        Label emptyLine = new Label(composite, SWT.NONE);
        GridDataFactory.swtDefaults().span(2, 1).applyTo(emptyLine);

        // Add the input parameter text box label.
        Label inputParmLabel = new Label(composite, SWT.NONE);
        inputParmLabel.setText("Start parameters:");
        GridDataFactory.swtDefaults().indent(20, 0).applyTo(inputParmLabel);

        // Add the input parameter text box.
        startParmText = new Text(composite, SWT.BORDER);
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

        // Add another empty line.
        Label emptyLine2 = new Label(composite, SWT.NONE);
        GridDataFactory.swtDefaults().span(2, 1).applyTo(emptyLine2);

        // Add the check box.
        runInContainerCheckBox = new Button(composite, SWT.CHECK);
        runInContainerCheckBox.setText("Run in Container");
        runInContainerCheckBox.setSelection(false);
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
        Label emptyLabel = new Label(composite, SWT.NONE);
        GridDataFactory.swtDefaults().applyTo(emptyLabel);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { configuration });
        }

        IProject activeProject = getActiveProject();

        // Save the active project's name in the configuration.
        if (activeProject != null) {
            configuration.setAttribute(PROJECT_NAME, activeProject.getName());
        }

        configuration.setAttribute(PROJECT_START_PARM, getDefaultStartCommand(activeProject));

        configuration.setAttribute(PROJECT_RUN_IN_CONTAINER, false);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    private IProject getActiveProject() {
        IProject activeProject = Utils.getActiveProject();
        if (activeProject == null) {
            activeProject = devModeOps.getSelectedDashboardProject();
        }
        return activeProject;
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

            String projectName = configuration.getAttribute(PROJECT_NAME, (String) null);
            if (projectName == null) {
                super.setErrorMessage(
                        "A project must be selected in order to provide a context to associate the run configuration with.  Either use a tree view like Package Explorer or have an editor window.");
                isValid = false;
            } else {
                projectNameLabel.setText(projectName);
            }

            setDirty(false);

        } catch (CoreException ce) {
            traceError(ce, "An error was detected during Run Configuration initialization.");
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI);
        }
    }

    private void traceError(CoreException ce, String msg) {
        ErrorHandler.processErrorMessage(msg, ce, true);
        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_UI, msg, ce);
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
            String projectName = config.getAttribute(PROJECT_NAME, (String) null);
            if (projectName == null) {
                return false;
            }
        } catch (CoreException e) {
            traceError(e, "Error getting project name");
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

        configuration.setAttribute(PROJECT_RUN_IN_CONTAINER, runInContainerBool);

        configuration.setAttribute(PROJECT_START_PARM, startParamStr);
        //
        // try {
        // configuration.doSave();
        // } catch (CoreException e) {
        // traceError(e, "Error saving Run Configuration.");
        // }

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
        return START_TAB_NAME;
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
            String msg = "An error was detected while retrieving the default start parameters.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processErrorMessage(msg, e, true);
        }

        return parms;
    }

}
