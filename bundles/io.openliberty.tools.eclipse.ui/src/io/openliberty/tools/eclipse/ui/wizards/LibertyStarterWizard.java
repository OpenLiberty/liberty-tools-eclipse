/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.wizards;

import java.io.IOException;
import java.net.URL;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWizard;

import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.logging.Logger;

/**
 * Wizard for creating a new Liberty Starter Project.
 */
public class LibertyStarterWizard extends Wizard implements INewWizard, IWorkbenchWizard {

    IDialogSettings starterSettingsSection;

    private LibertyStarterMainPage mainPage;

    private LibertyProjectStarter starter;

    /** Liberty icon path. */
    public static final String LIBERTY_ICON_PATH = "icons/openLibertyLogo_60.png";

    /** This wizard's dialog settings section. */
    private static final String PREF_SECTION = "io.openliberty.tools.eclipse.wizards.starter";

    /** Default location indicator setting. */
    private static final String PREF_USE_DEFAULT_LOCATION = "main.page.use.default.location";

    /** Application download location setting. */
    private static final String PREF_CUSTOM_LOCATION = "main.page.app.location";

    /**
     * Constructor for LibertyStarterWizard.
     */
    public LibertyStarterWizard() {
        super();
        setNeedsProgressMonitor(true);
        setWindowTitle("Liberty Project Starter");

        IDialogSettings settings = LibertyDevPlugin.getDefault().getDialogSettings();
        starterSettingsSection = settings.getSection(PREF_SECTION);
        if (starterSettingsSection == null) {
            starterSettingsSection = settings.addNewSection(PREF_SECTION);
        }
        setDialogSettings(starterSettingsSection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        starter = LibertyProjectStarter.getInstance();
        try {
            starter.loadData();
        } catch (Exception e) {
            Logger.logError("An error occurred while loading Liberty starter data.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addPages() {
        mainPage = new LibertyStarterMainPage();
        addPage(mainPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean performFinish() {
        // Get data from the page.
        String group = mainPage.getGroup();
        String artifact = mainPage.getArtifact();
        String buildTool = mainPage.getBuildTool();
        String javaSEVersion = mainPage.getJavaSEVersion();
        String javaEEVersion = mainPage.getJavaEEVersion();
        String microProfileVersion = mainPage.getMicroProfileVersion();
        String locationText = mainPage.getLocationText();
        boolean useDefaultLocation = mainPage.getUseDefaultLocation();

        if (locationText == null || locationText.isEmpty()) {
            mainPage.setErrorMessage("Location cannot be empty.");
            return false;
        }

        // Save preferences.
        savePreferences(useDefaultLocation, locationText);
        try {
            starter.generateStarter(artifact, group, buildTool, javaEEVersion, javaSEVersion, microProfileVersion,
                                    locationText);
        } catch (Exception e) {
            Logger.logError("An error occurred while attempting to generate and install the starter project.", e);
            mainPage.setErrorMessage(e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Saves user preferred settings.
     *
     * @param useDefaultLocation Indicates whether the default location should be used.
     * @param location           The location of the application in the file system.
     */
    private void savePreferences(boolean useDefaultLocation, String location) {
        starterSettingsSection.put(PREF_USE_DEFAULT_LOCATION, useDefaultLocation);
        starterSettingsSection.put(PREF_CUSTOM_LOCATION, location);
    }

    /**
     * Inner class for the wizard page.
     */
    private class LibertyStarterMainPage extends WizardPage {

        private Text groupText;
        private Text artifactText;
        private Button useDefaultLocationCheckbox;
        private Text locationText;
        private Button browseButton;
        private Button mavenRadio;
        private Button gradleRadio;
        private Combo javaSECombo;
        private Combo javaEECombo;
        private Combo microProfileCombo;

        protected LibertyStarterMainPage() {
            super("libertyStarterPage");
            setTitle("Liberty Project Starter");
            setDescription("Select your preferred development tools.");
            URL imgURL = LibertyDevPlugin.getDefault().getBundle().getResource(LIBERTY_ICON_PATH);
            ImageDescriptor imgDesc = ImageDescriptor.createFromURL(imgURL);
            setImageDescriptor(imgDesc);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void createControl(Composite parent) {
            Composite container = new Composite(parent, SWT.NONE);
            GridLayout mainLayout = new GridLayout(1, false);
            mainLayout.marginWidth = 20;
            mainLayout.marginHeight = 20;
            mainLayout.verticalSpacing = 15;
            container.setLayout(mainLayout);
            container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

            createProjectInfoSection(container);
            createBuildToolSection(container);
            createVersionsSection(container);
            createLocationSection(container);

            addListeners();

            setControl(container);
            setPageComplete(validatePage());
        }

        /**
         * Creates the project name and group input fields.
         *
         * @param container The parent composite.
         */
        private void createProjectInfoSection(Composite container) {
            Composite fieldsComposite = new Composite(container, SWT.NONE);
            GridLayout fieldsLayout = new GridLayout(2, false);
            fieldsLayout.horizontalSpacing = 10;
            fieldsLayout.verticalSpacing = 5;
            fieldsLayout.marginWidth = 0;
            fieldsLayout.marginHeight = 0;
            fieldsComposite.setLayout(fieldsLayout);
            fieldsComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

            // ProjectName/Artifact label and text field.
            Label artifactLabel = new Label(fieldsComposite, SWT.NONE);
            artifactLabel.setText("ProjectName/Artifact");
            artifactLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            artifactText = new Text(fieldsComposite, SWT.BORDER);
            artifactText.setText(starter.getDefaultProjectName());
            artifactText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            artifactText.addModifyListener(new ModifyListener() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void modifyText(ModifyEvent e) {
                    String artifact = artifactText.getText().trim();
                    if (artifact.isEmpty()) {
                        setErrorMessage("ProjectName/Artifact cannot be empty.");
                        setPageComplete(false);
                    } else {
                        setErrorMessage(null);
                        setMessage(null);
                        setPageComplete(true);
                    }
                }
            });

            // Group label and text field.
            Label groupLabel = new Label(fieldsComposite, SWT.NONE);
            groupLabel.setText("Group");
            groupLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            groupText = new Text(fieldsComposite, SWT.BORDER);
            groupText.setText(starter.getDefaultGroupName());
            groupText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            groupText.addModifyListener(new ModifyListener() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void modifyText(ModifyEvent e) {
                    String group = groupText.getText().trim();
                    if (group.isEmpty()) {
                        setErrorMessage("Group cannot be empty.");
                        setPageComplete(false);
                    } else {
                        setErrorMessage(null);
                        setMessage(null);
                        setPageComplete(true);
                    }
                }
            });
        }

        /**
         * Creates the build tool radio button selection (Maven or Gradle).
         *
         * @param container The parent composite.
         */
        private void createBuildToolSection(Composite container) {
            Composite buildToolComposite = new Composite(container, SWT.NONE);
            GridLayout buildToolLayout = new GridLayout(3, false);
            buildToolLayout.marginWidth = 0;
            buildToolLayout.marginHeight = 0;
            buildToolLayout.horizontalSpacing = 0;
            buildToolLayout.verticalSpacing = 20;
            buildToolComposite.setLayout(buildToolLayout);
            buildToolComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            Label buildToolLabel = new Label(buildToolComposite, SWT.NONE);
            buildToolLabel.setText("Build Tool");
            buildToolLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            mavenRadio = new Button(buildToolComposite, SWT.RADIO);
            mavenRadio.setText("Maven");
            mavenRadio.setSelection("maven".equals(starter.getDefaultBuildType()));
            GridData mavenRadioGD = new GridData(SWT.LEFT, SWT.CENTER, false, false);
            mavenRadioGD.horizontalIndent = 37;
            mavenRadio.setLayoutData(mavenRadioGD);

            gradleRadio = new Button(buildToolComposite, SWT.RADIO);
            gradleRadio.setText("Gradle");
            gradleRadio.setSelection("gradle".equals(starter.getDefaultBuildType()));
            gradleRadio.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
        }

        /**
         * Creates the Java SE, Jakarta EE, and MicroProfile version dropdowns.
         *
         * @param container The parent composite.
         */
        private void createVersionsSection(Composite container) {
            Composite versionsComposite = new Composite(container, SWT.NONE);
            GridLayout versionsLayout = new GridLayout(6, false);
            versionsLayout.horizontalSpacing = 0;
            versionsLayout.verticalSpacing = 0;
            versionsLayout.marginWidth = 0;
            versionsLayout.marginHeight = 0;
            versionsComposite.setLayout(versionsLayout);

            // Java SE Version.
            Label javaSELabel = new Label(versionsComposite, SWT.NONE);
            javaSELabel.setText("Java SE Version");
            javaSELabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));

            javaSECombo = new Combo(versionsComposite, SWT.BORDER | SWT.READ_ONLY);
            javaSECombo.setItems(starter.getJseOptions());
            javaSECombo.select(starter.getJseOptions().length - 1);
            GridData javaSEData = new GridData(SWT.LEFT, SWT.CENTER, true, false);
            javaSEData.heightHint = 35;
            javaSEData.widthHint = 90;
            javaSEData.minimumWidth = 90;
            javaSECombo.setLayoutData(javaSEData);

            // Jakarta EE Version.
            Label javaEELabel = new Label(versionsComposite, SWT.NONE);
            javaEELabel.setText("Java EE/Jakarta EE Version");
            GridData javaEELabelGD = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
            javaEELabelGD.horizontalIndent = 20;
            javaEELabel.setLayoutData(javaEELabelGD);

            javaEECombo = new Combo(versionsComposite, SWT.BORDER | SWT.READ_ONLY);
            javaEECombo.setItems(starter.getJeeOptions());
            javaEECombo.select(starter.getJeeOptions().length - 1);
            GridData javaEEData = new GridData(SWT.LEFT, SWT.CENTER, true, false);
            javaEEData.heightHint = 35;
            javaEEData.widthHint = 90;
            javaEEData.minimumWidth = 90;
            javaEECombo.setLayoutData(javaEEData);

            javaEECombo.addSelectionListener(new SelectionAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void widgetSelected(SelectionEvent e) {
                    // TODO: Validate EE VS MP code here.
                }
            });

            // MicroProfile Version.
            Label microProfileLabel = new Label(versionsComposite, SWT.NONE);
            microProfileLabel.setText("MicroProfile Version");
            GridData microProfileLabelGD = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
            microProfileLabelGD.horizontalIndent = 20;
            microProfileLabel.setLayoutData(microProfileLabelGD);

            microProfileCombo = new Combo(versionsComposite, SWT.BORDER | SWT.READ_ONLY);
            microProfileCombo.setItems(starter.getMpOptions());
            microProfileCombo.select(starter.getMpOptions().length - 1);
            GridData microProfileData = new GridData(SWT.LEFT, SWT.CENTER, true, false);
            microProfileData.heightHint = 35;
            microProfileData.widthHint = 90;
            microProfileData.minimumWidth = 90;
            microProfileCombo.setLayoutData(microProfileData);

            microProfileCombo.addSelectionListener(new SelectionAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void widgetSelected(SelectionEvent e) {
                    // TODO: Validate MP VS EE code here.
                }
            });
        }

        /**
         * Creates the location group containing the default location checkbox,
         * the location text field, and the browse button.
         *
         * @param container The parent composite.
         */
        private void createLocationSection(Composite container) {
            Group locationGroup = new Group(container, SWT.LEFT);
            GridLayout gLayout = new GridLayout();
            gLayout.marginHeight = 0;
            gLayout.marginWidth = 0;
            locationGroup.setLayout(gLayout);
            locationGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            boolean savedUseDefault = (starterSettingsSection.get(PREF_USE_DEFAULT_LOCATION) != null)
                    ? starterSettingsSection.getBoolean(PREF_USE_DEFAULT_LOCATION)
                    : true;

            // Use default location checkbox.
            useDefaultLocationCheckbox = new Button(locationGroup, SWT.CHECK);
            useDefaultLocationCheckbox.setText("Use default location");
            useDefaultLocationCheckbox.setSelection(savedUseDefault);
            useDefaultLocationCheckbox.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

            useDefaultLocationCheckbox.addSelectionListener(new SelectionAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void widgetSelected(SelectionEvent e) {
                    boolean configuredUseDefLoc = useDefaultLocationCheckbox.getSelection();
                    if (configuredUseDefLoc) {
                        try {
                            locationText.setText(starter.getDefaultStarterDirPath());
                        } catch (IOException ex) {
                            setErrorMessage("Unable to create the default starter directory: " + ex.getMessage());
                        }
                    }
                    locationText.setEnabled(!configuredUseDefLoc);
                    browseButton.setEnabled(!configuredUseDefLoc);
                }
            });

            // Location row: label, text field, browse button.
            Composite locationComposite = new Composite(locationGroup, SWT.NONE);
            GridLayout locationLayout = new GridLayout(3, false);
            locationLayout.marginWidth = 0;
            locationLayout.marginHeight = 0;
            locationLayout.horizontalSpacing = 10;
            locationComposite.setLayout(locationLayout);
            locationComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

            Label locationLabel = new Label(locationComposite, SWT.NONE);
            locationLabel.setText("Location:");
            GridData locationLabelData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
            locationLabelData.widthHint = 70;
            locationLabel.setLayoutData(locationLabelData);

            locationText = new Text(locationComposite, SWT.BORDER);

            String savedLocation = starterSettingsSection.get(PREF_CUSTOM_LOCATION);
            String defaultLocation = "";
            try {
                defaultLocation = starter.getDefaultStarterDirPath();
            } catch (IOException ex) {
                setErrorMessage("Unable to create the default starter directory: " + ex.getMessage());
            }
            String initialLocation = (!savedUseDefault && savedLocation != null && !savedLocation.isEmpty())
                    ? savedLocation
                    : defaultLocation;
            locationText.setText(initialLocation);
            locationText.setEnabled(!useDefaultLocationCheckbox.getSelection());

            locationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

            browseButton = new Button(locationComposite, SWT.PUSH);
            browseButton.setText("Browse...");
            browseButton.setEnabled(!useDefaultLocationCheckbox.getSelection());
            GridData browseData = new GridData(SWT.RIGHT, SWT.CENTER, false, false);
            browseData.widthHint = 90;
            browseButton.setLayoutData(browseData);

            browseButton.addSelectionListener(new SelectionAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void widgetSelected(SelectionEvent e) {
                    DirectoryDialog dialog = new DirectoryDialog(getShell());
                    dialog.setText("Select Location");
                    dialog.setFilterPath(locationText.getText());
                    String selectedDir = dialog.open();
                    if (selectedDir != null) {
                        locationText.setText(selectedDir);
                    }
                }
            });
        }

        private void addListeners() {
            ModifyListener modifyListener = new ModifyListener() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void modifyText(ModifyEvent e) {
                    validatePage();
                }
            };

            groupText.addModifyListener(modifyListener);
            artifactText.addModifyListener(modifyListener);
            locationText.addModifyListener(modifyListener);

            // Add listeners to radio buttons.
            SelectionListener radioListener = new SelectionAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void widgetSelected(SelectionEvent e) {
                    validatePage();
                }
            };
            mavenRadio.addSelectionListener(radioListener);
            gradleRadio.addSelectionListener(radioListener);

            // Add listeners to combo boxes.
            SelectionListener comboListener = new SelectionAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void widgetSelected(SelectionEvent e) {
                    validatePage();
                }
            };
            javaSECombo.addSelectionListener(comboListener);
            javaEECombo.addSelectionListener(comboListener);
            microProfileCombo.addSelectionListener(comboListener);
        }

        private boolean validatePage() {
            String group = groupText.getText().trim();
            if (group.isEmpty()) {
                setErrorMessage("Group cannot be empty.");
                setPageComplete(false);
                return false;
            }

            String artifact = artifactText.getText().trim();
            if (artifact.isEmpty()) {
                setErrorMessage("Artifact cannot be empty.");
                setPageComplete(false);
                return false;
            }

            setErrorMessage(null);
            setMessage(null);
            setPageComplete(true);
            return true;
        }

        // Getters for wizard data.
        public String getGroup() {
            return groupText.getText().trim();
        }

        public String getArtifact() {
            return artifactText.getText().trim();
        }

        public String getBuildTool() {
            return mavenRadio.getSelection() ? "maven" : "gradle";
        }

        public String getJavaSEVersion() {
            return javaSECombo.getText();
        }

        public String getJavaEEVersion() {
            return javaEECombo.getText();
        }

        public String getMicroProfileVersion() {
            return microProfileCombo.getText();
        }

        public String getLocationText() {
            return locationText.getText().trim();
        }

        public boolean getUseDefaultLocation() {
            return useDefaultLocationCheckbox.getSelection();
        }
    }
}
