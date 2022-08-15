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
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.Utils;

/**
 * Main configuration tab.
 */
public class MainTab extends AbstractLaunchConfigurationTab {

    /** Configuration map key with a value representing the dev mode start parameter. */
    public static final String START_PARM = "io.openliberty.tools.eclipse.launch.start.parm";

    /** Configuration map key with a value representing the last project name associated with the configuration. */
    public static final String PROJECT_NAME = "io.openliberty.tools.eclipse.launch.project.name";

    /** Holds the start parameter text configuration. */
    private Text startParmText;

    /**
     * {@inheritDoc}
     */
    @Override
    public void createControl(Composite parent) {
        Composite composite = new Group(parent, SWT.BORDER);
        setControl(composite);

        // Create a page layout.
        GridLayoutFactory.swtDefaults().numColumns(2).applyTo(composite);

        // Add a text box label.
        Label label = new Label(composite, SWT.NONE);
        label.setText("Start parameter:");
        GridDataFactory.swtDefaults().applyTo(label);

        // Add a text box.
        startParmText = new Text(composite, SWT.BORDER);
        startParmText.setMessage("Example: -DhotTests=true");
        startParmText.addModifyListener(new ModifyListener() {

            /**
             * {@inheritDoc}
             */
            @Override
            public void modifyText(ModifyEvent e) {
                setDirty(true);
                updateLaunchConfigurationDialog();
            }
        });

        GridDataFactory.fillDefaults().grab(true, false).applyTo(startParmText);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeFrom(ILaunchConfiguration configuration) {
        // Initialize the configuration view with previously saved values.
        try {
            String consoleText = configuration.getAttribute(START_PARM, "");
            startParmText.setText(consoleText);
        } catch (CoreException ce) {
            // Trace and ignore.
            String msg = "An error was detected during Run Configuration initialization.";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, ce);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performApply(ILaunchConfigurationWorkingCopy configuration) {
        // Capture the entries typed on the currently active launch configuration.
        configuration.setAttribute(START_PARM, startParmText.getText());

        IProject project = Utils.getActiveProject();
        if (project != null) {
            configuration.setAttribute(PROJECT_NAME, project.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "Main";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Image getImage() {
        return Utils.getLibertyImage(PlatformUI.getWorkbench().getDisplay());
    }
}
