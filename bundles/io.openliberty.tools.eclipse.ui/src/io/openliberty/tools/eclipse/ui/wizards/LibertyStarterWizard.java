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
import java.util.HashMap;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
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
import org.json.JSONArray;

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
        
        // Flag to prevent recursive listener calls
        private boolean isUpdatingVersions = false;

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

              // Group label and text field.
            Label groupLabel = new Label(fieldsComposite, SWT.NONE);
            groupLabel.setText("Group");
            groupLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            groupText = new Text(fieldsComposite, SWT.BORDER);
            groupText.setText(starter.getDefaultGroupName());
            groupText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));


            // ProjectName/Artifact label and text field.
            Label artifactLabel = new Label(fieldsComposite, SWT.NONE);
            artifactLabel.setText("ProjectName/Artifact");
            artifactLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

            artifactText = new Text(fieldsComposite, SWT.BORDER);
            artifactText.setText(starter.getDefaultProjectName());
            artifactText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));


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
                    if (isUpdatingVersions) {
                        return;
                    }

                    String selectedEE = javaEECombo.getText();
                    String currentMP = microProfileCombo.getText();

                    if (selectedEE == null || selectedEE.isEmpty() || "None".equals(selectedEE)) {
                        return;
                    }

                    if (currentMP != null && !currentMP.isEmpty() && !"None".equals(currentMP)) {
                        if (isCompatible(selectedEE, currentMP)) {
                            checkAndUpdateJavaSE(selectedEE, currentMP);
                            return;
                        }
                    }

                    String compatibleMP = getFirstCompatibleMPVersion(selectedEE);

                    if (compatibleMP != null) {
                        String oldMP = (currentMP != null && !"None".equals(currentMP)) ? currentMP : null;

                        isUpdatingVersions = true;
                        microProfileCombo.setText(compatibleMP);
                        isUpdatingVersions = false;

                        if (oldMP != null && !oldMP.equals(compatibleMP)) {
                            final String message = String.format(
                                    "MicroProfile Version has been automatically updated from %s to %s for compatibility with Jakarta EE %s.",
                                    oldMP, compatibleMP, selectedEE);
                            getControl().getDisplay().asyncExec(new Runnable() {
                                public void run() {
                                    setMessage(message, IMessageProvider.INFORMATION);
                                }
                            });
                        }

                        checkAndUpdateJavaSE(selectedEE, compatibleMP);
                    } else {
                        String warnMsg = "No compatible MicroProfile version found for Jakarta EE " + selectedEE;
                        setMessage(warnMsg, IMessageProvider.WARNING);
                        Logger.logWarning(warnMsg);
                    }
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
                    if (isUpdatingVersions) {
                        return;
                    }

                    String selectedMP = microProfileCombo.getText();
                    String currentEE = javaEECombo.getText();

                    if (selectedMP == null || selectedMP.isEmpty() || "None".equals(selectedMP)) {
                        return;
                    }

                    if (currentEE != null && !currentEE.isEmpty() && !"None".equals(currentEE)) {
                        if (isCompatible(currentEE, selectedMP)) {
                            setMessage(null);
                            checkAndUpdateJavaSE(currentEE, selectedMP);
                            return;
                        }
                    }

                    String compatibleEE = getFirstCompatibleEEVersion(selectedMP);

                    if (compatibleEE != null) {
                        String oldEE = (currentEE != null && !"None".equals(currentEE)) ? currentEE : null;

                        isUpdatingVersions = true;
                        javaEECombo.setText(compatibleEE);
                        isUpdatingVersions = false;

                        if (oldEE != null && !oldEE.equals(compatibleEE)) {
                            final String message = String.format(
                                    "Jakarta EE Version has been automatically updated from %s to %s for compatibility with MicroProfile %s.",
                                    oldEE, compatibleEE, selectedMP);
                            getControl().getDisplay().asyncExec(new Runnable() {
                                public void run() {
                                    setMessage(message, IMessageProvider.INFORMATION);
                                }
                            });
                        }

                        checkAndUpdateJavaSE(compatibleEE, selectedMP);
                    } else {
                        String warnMsg = "No compatible Jakarta EE version found for MicroProfile " + selectedMP;
                        setMessage(warnMsg, IMessageProvider.WARNING);
                        Logger.logWarning(warnMsg);
                    }
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

            // javaSECombo validates page and also checks compatibility with current EE/MP selections.
            // javaEECombo and microProfileCombo have their own SelectionAdapters that handle everything.
            javaSECombo.addSelectionListener(new SelectionAdapter() {
                /**
                 * {@inheritDoc}
                 */
                @Override
                public void widgetSelected(SelectionEvent e) {
                    if (!isUpdatingVersions) {
                        checkAndUpdateJavaSE(javaEECombo.getText(), microProfileCombo.getText());
                    }
                    validatePage();
                }
            });
        }

        private boolean validatePage() {
            // Validate Group
            String groupRaw = groupText.getText();
            String group = groupRaw.trim();
            String groupErrorMsg = "Valid characters for package names include a-z, A-Z, '_' and 0-9. Packages must be separated by '.'";

            if (group.isEmpty()) {
                setErrorMessage("Group cannot be empty.");
                setPageComplete(false);
                return false;
            }

            // Check for leading/trailing spaces (website doesn't allow them)
            if (!groupRaw.equals(group)) {
                setErrorMessage(groupErrorMsg);
                setPageComplete(false);
                return false;
            }

            // Validate Group format (matches website validation)
            if (!isValidGroupName(group)) {
                setErrorMessage(groupErrorMsg);
                setPageComplete(false);
                return false;
            }

            // Validate Artifact
            String artifactRaw = artifactText.getText();
            String artifact = artifactRaw.trim();
            String artifactErrorMsg = "Valid characters include a-z separated by '-'";

            if (artifact.isEmpty()) {
                setErrorMessage("Artifact cannot be empty.");
                setPageComplete(false);
                return false;
            }

            // Check for leading/trailing spaces (website doesn't allow them)
            if (!artifactRaw.equals(artifact)) {
                setErrorMessage(artifactErrorMsg);
                setPageComplete(false);
                return false;
            }

            // Validate Artifact format (matches website validation)
            if (!isValidArtifactName(artifact)) {
                setErrorMessage(artifactErrorMsg);
                setPageComplete(false);
                return false;
            }

            setErrorMessage(null);
            setPageComplete(true);
            return true;
        }
        
        /**
         * Validates artifact name according to Liberty Starter website rules.
         *
         * Rules (from https://start.openliberty.io/):
         * - Only lowercase letters (a-z)
         * - Hyphens (-) as separators
         * - NO numbers, uppercase, underscores, or spaces
         *
         * @param artifact The artifact name to validate
         * @return true if valid, false otherwise
         */
        private boolean isValidArtifactName(String artifact) {
            if (artifact == null || artifact.isEmpty()) {
                return false;
            }
            
            // Check each character: must be lowercase letter or hyphen
            for (int i = 0; i < artifact.length(); i++) {
                char c = artifact.charAt(i);
                if (!(c >= 'a' && c <= 'z') && c != '-') {
                    return false;
                }
            }
            
            // Should not start or end with hyphen
            if (artifact.startsWith("-") || artifact.endsWith("-")) {
                return false;
            }
            
            // Should not have consecutive hyphens
            if (artifact.contains("--")) {
                return false;
            }
            
            return true;
        }
        
        /**
         * Validates group name according to Liberty Starter website rules.
         *
         * Rules (from https://start.openliberty.io/):
         * - Lowercase letters (a-z)
         * - Uppercase letters (A-Z)
         * - Numbers (0-9)
         * - Underscores (_)
         * - Dots (.) as package separators
         *
         * @param group The group name to validate
         * @return true if valid, false otherwise
         */
        private boolean isValidGroupName(String group) {
            if (group == null || group.isEmpty()) {
                return false;
            }
            
            // Check for leading/trailing dots or consecutive dots
            if (group.startsWith(".") || group.endsWith(".") || group.contains("..")) {
                return false;
            }
            
            // Split by dots and validate each segment
            String[] segments = group.split("\\.");
            if (segments.length == 0) {
                return false;
            }
            
            for (String segment : segments) {
                if (!isValidPackageSegment(segment)) {
                    return false;
                }
            }
            
            return true;
        }
        
        /**
         * Validates a single segment of a package name.
         * Must contain only: a-z, A-Z, 0-9, _
         *
         * @param segment The package segment to validate
         * @return true if valid, false otherwise
         */
        private boolean isValidPackageSegment(String segment) {
            if (segment == null || segment.isEmpty()) {
                return false;
            }
            
            // Check each character: must be letter, digit, or underscore
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return false;
                }
            }
            
            return true;
        }

        /**
         * Checks if the selected Jakarta EE and MicroProfile versions are compatible.
         *
         * @param eeVersion The Jakarta EE version
         * @param mpVersion The MicroProfile version
         * @return true if compatible, false otherwise
         */
        private boolean isCompatible(String eeVersion, String mpVersion) {
            if (eeVersion == null || mpVersion == null || eeVersion.isEmpty() || mpVersion.isEmpty()) {
                return true; // No validation if either is empty
            }

            try {
                LibertyProjectStarter starter = LibertyProjectStarter.getInstance();
                HashMap<String, JSONArray> ee2mp = starter.getDependenciesEE2MP();
                
                // Check if compatibility data is loaded
                if (ee2mp == null || ee2mp.isEmpty()) {
                    Logger.logWarning("Compatibility data not loaded yet");
                    return true; // Assume compatible if data not loaded
                }
                
                JSONArray compatibleMP = ee2mp.get(eeVersion);

                if (compatibleMP != null) {
                    for (int i = 0; i < compatibleMP.length(); i++) {
                        if (compatibleMP.getString(i).equals(mpVersion)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.logError("Error checking compatibility", e);
                return true; // Assume compatible on error
            }

            return false;
        }

        /**
         * Gets the highest compatible MicroProfile version for the given Jakarta EE version.
         * This matches the website behavior which selects the highest (most recent) compatible version.
         *
         * @param eeVersion The Jakarta EE version
         * @return The highest compatible MicroProfile version, or null if none found
         */
        private String getFirstCompatibleMPVersion(String eeVersion) {
            try {
                LibertyProjectStarter starter = LibertyProjectStarter.getInstance();
                HashMap<String, JSONArray> ee2mp = starter.getDependenciesEE2MP();
                
                // Check if compatibility data is loaded
                if (ee2mp == null || ee2mp.isEmpty()) {
                    Logger.logWarning("Compatibility data (EE2MP) not loaded - map is empty");
                    return null;
                }
                
                JSONArray compatibleMP = ee2mp.get(eeVersion);
                
                if (compatibleMP == null) {
                    Logger.logWarning("No compatibility data found for Jakarta EE " + eeVersion);
                    return null;
                }

                // Find the HIGHEST (last) non-"None" version to match website behavior
                // The API returns versions in ascending order (e.g., 2.2, 3.0, 3.3, 4.0, 4.1)
                // Website selects the highest version (4.1), so we iterate backwards
                String highestVersion = null;
                for (int i = compatibleMP.length() - 1; i >= 0; i--) {
                    String version = compatibleMP.getString(i);
                    if (!"None".equals(version)) {
                        highestVersion = version;
                        break;
                    }
                }
                
                // If we found a version, return it
                if (highestVersion != null) {
                    return highestVersion;
                }
                
                // If all versions are "None", return "None"
                if (compatibleMP.length() > 0) {
                    return "None";
                }
            } catch (Exception e) {
                Logger.logError("Error getting compatible MP version for EE " + eeVersion, e);
            }
            return null;
        }

        /**
         * Gets the highest compatible Jakarta EE version for the given MicroProfile version.
         * This matches the website behavior which selects the highest (most recent) compatible version.
         *
         * @param mpVersion The MicroProfile version
         * @return The highest compatible Jakarta EE version, or null if none found
         */
        private String getFirstCompatibleEEVersion(String mpVersion) {
            try {
                LibertyProjectStarter starter = LibertyProjectStarter.getInstance();
                HashMap<String, JSONArray> mp2ee = starter.getDependenciesMP2EE();
                
                // Check if compatibility data is loaded
                if (mp2ee == null || mp2ee.isEmpty()) {
                    Logger.logWarning("Compatibility data (MP2EE) not loaded - map is empty");
                    return null;
                }
                
                JSONArray compatibleEE = mp2ee.get(mpVersion);
                
                if (compatibleEE == null) {
                    Logger.logWarning("No compatibility data found for MicroProfile " + mpVersion);
                    return null;
                }

                // Find the HIGHEST (last) non-"None" version to match website behavior
                // The API returns versions in ascending order (e.g., 8.0, 9.0, 9.1, 10.0, 11.0)
                // Website selects the highest version, so we iterate backwards
                String highestVersion = null;
                for (int i = compatibleEE.length() - 1; i >= 0; i--) {
                    String version = compatibleEE.getString(i);
                    if (!"None".equals(version)) {
                        highestVersion = version;
                        break;
                    }
                }
                
                // If we found a version, return it
                if (highestVersion != null) {
                    return highestVersion;
                }
                
                // If all versions are "None", return "None"
                if (compatibleEE.length() > 0) {
                    return "None";
                }
            } catch (Exception e) {
                Logger.logError("Error getting compatible EE version for MP " + mpVersion, e);
            }
            return null;
        }

        /**
         * Validates and auto-updates Java SE version based on Jakarta EE and MicroProfile selections.
         * Implements the same logic as the Liberty Starter website frontend (builds.js).
         *
         * Rules:
         * - Jakarta EE 11.0 requires Java SE 17+
         * - Jakarta EE 10.0 requires Java SE 11+
         * - MicroProfile 6.0, 6.1, 7.0, 7.1 require Java SE 11+
         *
         * @param eeVersion The Jakarta EE version
         * @param mpVersion The MicroProfile version
         */
        private void checkAndUpdateJavaSE(String eeVersion, String mpVersion) {
            if (isUpdatingVersions) {
                return;
            }

            try {
                String currentJavaSE = javaSECombo.getText();
                String requiredJavaSE = null;
                String reason = "";

                // Jakarta EE 11.0 requires Java SE 17+
                if ("11.0".equals(eeVersion)) {
                    if ("8".equals(currentJavaSE) || "11".equals(currentJavaSE)) {
                        requiredJavaSE = "17";
                        reason = "Jakarta EE 11.0 requires a minimum of Java SE 17";
                    }
                }
                // Jakarta EE 10.0 or MicroProfile 6.0+ requires Java SE 11+
                else if ("10.0".equals(eeVersion) ||
                         "6.0".equals(mpVersion) || "6.1".equals(mpVersion) ||
                         "7.0".equals(mpVersion) || "7.1".equals(mpVersion)) {
                    if ("8".equals(currentJavaSE)) {
                        requiredJavaSE = "11";
                        if ("10.0".equals(eeVersion)) {
                            reason = "Jakarta EE 10.0 requires a minimum of Java SE 11";
                        } else {
                            reason = "MicroProfile " + mpVersion + " requires a minimum of Java SE 11";
                        }
                    }
                }

                if (requiredJavaSE != null) {
                    isUpdatingVersions = true;
                    javaSECombo.setText(requiredJavaSE);
                    isUpdatingVersions = false;

                    final String message = String.format(
                            "Java SE Version has been automatically updated from %s to %s. %s.",
                            currentJavaSE, requiredJavaSE, reason);
                    getControl().getDisplay().asyncExec(new Runnable() {
                        public void run() {
                            setMessage(message, IMessageProvider.INFORMATION);
                        }
                    });
                }
            } catch (Exception e) {
                Logger.logError("Error checking Java SE requirements", e);
            }
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
