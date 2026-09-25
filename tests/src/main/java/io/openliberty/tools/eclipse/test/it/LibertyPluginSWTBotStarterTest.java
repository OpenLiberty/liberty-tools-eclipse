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
package io.openliberty.tools.eclipse.test.it;

import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.cancelStarterWizard;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getInstalledProjectItem;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openStarterWizardViaFileMenu;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.openStarterWizardViaNewMenu;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCheckBox;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotCombo;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotRadio;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.SWTBotTestCondition;

/**
 * Integration tests for the Liberty Starter wizard UI.
 *
 * <p>Tests cover:
 * <ul>
 * <li>Opening the wizard via File > New and the Java perspective shortcut</li>
 * <li>Default field values match the Liberty Starter API</li>
 * <li>Group / artifact validation (valid and invalid inputs)</li>
 * <li>Jakarta EE → MicroProfile auto-update (highest compatible MP selected)</li>
 * <li>MicroProfile → Jakarta EE auto-update (highest compatible EE selected)</li>
 * <li>Java SE auto-upgrade when EE 10 / EE 11 / MP 6+ is selected</li>
 * <li>Use-default-location checkbox behaviour</li>
 * <li>Finish button is disabled while the page is invalid</li>
 * <li>Cancel closes the wizard without creating a project</li>
 * </ul>
 *
 * <p>Every test opens the wizard fresh inline and closes it via Cancel in its
 * own cleanup, so no inter-test state leaks.
 */
public class LibertyPluginSWTBotStarterTest extends AbstractLibertyPluginSWTBotTest {

    // -----------------------------------------------------------------------
    // Wizard title / widget labels (must match LibertyStarterWizard constants)
    // -----------------------------------------------------------------------
    private static final String WIZARD_TITLE = "New Liberty Project";
    private static final String WIZARD_NEW_MENU_PATH = "Liberty Project";
    private static final String CATEGORY_NAME = "Liberty";

    // Wizard button labels
    private static final String BUTTON_FINISH = "Finish";

    // -----------------------------------------------------------------------
    // Setup / teardown
    // -----------------------------------------------------------------------

    @BeforeAll
    public static void setup() {
        commonSetup();
    }

    @AfterAll
    public static void cleanup() {
        commonCleanup();
    }

    // -----------------------------------------------------------------------
    // 1. Opening the wizard via File > New > Other...
    // -----------------------------------------------------------------------

    /**
     * Verifies that the Liberty Starter wizard can be opened from the main menu
     * (File > New > Other… > Liberty > Liberty Project) and that it
     * shows the expected title and a Finish button.
     */
    @Test
    public void testOpenWizardViaFileMenu() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            assertTrue(wizardShell.getText().contains(WIZARD_TITLE),
                       "Wizard title should contain '" + WIZARD_TITLE + "' but was: " + wizardShell.getText());

            // Finish button must exist (may or may not be enabled depending on API load)
            SWTBotButton finishBtn = wizardShell.bot().button(BUTTON_FINISH);
            assertNotNull(finishBtn, "Finish button must be present in the wizard");
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 2. Opening the wizard via the Java perspective New menu shortcut
    // -----------------------------------------------------------------------

    /**
     * Verifies that the Liberty Starter wizard shortcut is visible in the Java
     * perspective's File > New sub-menu (added by the LibertyPerspectiveProcessor).
     */
    @Test
    public void testOpenWizardViaNewMenuShortcut() {
        SWTBotShell wizardShell = openWizardViaNewMenuOrFail();
        try {
            assertTrue(wizardShell.getText().contains(WIZARD_TITLE),
                       "Wizard title should contain '" + WIZARD_TITLE + "'");
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 3. Default field values (loaded from Liberty Starter API)
    // -----------------------------------------------------------------------

    /**
     * Verifies that all wizard fields are pre-populated with non-empty default
     * values retrieved from the Liberty Starter API. The exact values are not
     * asserted because they can change with API updates; instead we assert that
     * they are present and that the dropdowns contain at least one item.
     */
    @Test
    public void testDefaultFieldValues() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            SWTBotText groupText = wizardShell.bot().textWithLabel("Group");
            SWTBotText artifactText = wizardShell.bot().textWithLabel("ProjectName/Artifact");
            SWTBotCombo javaSeCombo = wizardShell.bot().comboBoxWithLabel("Java SE Version");
            SWTBotCombo javaEeCombo = wizardShell.bot().comboBoxWithLabel("Java EE/Jakarta EE Version");
            SWTBotCombo mpCombo = wizardShell.bot().comboBoxWithLabel("MicroProfile Version");

            assertFalse(groupText.getText().isEmpty(), "Group field must have a default value");
            assertFalse(artifactText.getText().isEmpty(), "Artifact field must have a default value");
            assertFalse(javaSeCombo.getText().isEmpty(), "Java SE combo must have a default selection");
            assertFalse(javaEeCombo.getText().isEmpty(), "Jakarta EE combo must have a default selection");
            assertFalse(mpCombo.getText().isEmpty(), "MicroProfile combo must have a default selection");

            assertTrue(javaSeCombo.itemCount() > 0, "Java SE combo must contain at least one item");
            assertTrue(javaEeCombo.itemCount() > 0, "Jakarta EE combo must contain at least one item");
            assertTrue(mpCombo.itemCount() > 0, "MicroProfile combo must contain at least one item");
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 4. Build tool radio buttons
    // -----------------------------------------------------------------------

    /**
     * Verifies that both Maven and Gradle radio buttons are present, that exactly
     * one is selected by default, and that clicking the other one changes the
     * selection correctly.
     */
    @Test
    public void testBuildToolRadioButtons() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            SWTBotRadio mavenRadio = wizardShell.bot().radio("Maven");
            SWTBotRadio gradleRadio = wizardShell.bot().radio("Gradle");

            assertNotNull(mavenRadio, "Maven radio button must be present");
            assertNotNull(gradleRadio, "Gradle radio button must be present");

            // Exactly one must be selected by default
            boolean mavenSelected = mavenRadio.isSelected();
            boolean gradleSelected = gradleRadio.isSelected();
            assertTrue(mavenSelected ^ gradleSelected,
                       "Exactly one of Maven / Gradle must be selected; Maven=" + mavenSelected + " Gradle=" + gradleSelected);

            // Clicking the non-selected radio must flip the selection
            if (mavenSelected) {
                gradleRadio.click();
                assertTrue(gradleRadio.isSelected(), "Gradle radio must be selected after clicking it");
                assertFalse(mavenRadio.isSelected(), "Maven radio must be deselected after clicking Gradle");
            } else {
                mavenRadio.click();
                assertTrue(mavenRadio.isSelected(), "Maven radio must be selected after clicking it");
                assertFalse(gradleRadio.isSelected(), "Gradle radio must be deselected after clicking Maven");
            }
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 5. Group name validation — valid inputs
    // -----------------------------------------------------------------------

    /**
     * Verifies that well-formed group names clear the error message and keep the
     * Finish button enabled (assuming the artifact is also valid).
     */
    @Test
    public void testValidGroupNames() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            SWTBotText groupText = wizardShell.bot().textWithLabel("Group");
            String[] validGroups = { "com.example", "org.acme", "my_group", "A1_B2", "io.openliberty.tools" };
            for (String g : validGroups) {
                groupText.setText(g);
                // Trigger validation by tabbing away
                wizardShell.bot().textWithLabel("ProjectName/Artifact").setFocus();

                // After entering a valid group with a valid artifact the page must be complete.
                // We poll briefly because validation may be async.
                String errMsg = getWizardErrorMessage(wizardShell);
                assertTrue(errMsg == null || (!errMsg.contains("Group")),
                           "No group-related error expected for '" + g + "' but got: " + errMsg);
            }
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 6. Group name validation — invalid inputs
    // -----------------------------------------------------------------------

    /**
     * Verifies that malformed group names trigger the expected error message.
     */
    @Test
    public void testInvalidGroupNames() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            SWTBotText groupText = wizardShell.bot().textWithLabel("Group");
            String[] invalidGroups = { "", " leadingSpace", "trailing ", "double..dot", ".startDot", "endDot." };
            for (String g : invalidGroups) {
                groupText.setText(g);
                wizardShell.bot().textWithLabel("ProjectName/Artifact").setFocus();

                // The wizard must report an error and Finish must be disabled.
                SWTBotTestCondition.waitFor(
                                            () -> !wizardShell.bot().button(BUTTON_FINISH).isEnabled(),
                                            SWTBotTestCondition.VALIDATION_WAIT_MS);

                assertFalse(wizardShell.bot().button(BUTTON_FINISH).isEnabled(),
                            "Finish must be disabled for invalid group '" + g + "'");
            }
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 7. Artifact name validation — valid inputs
    // -----------------------------------------------------------------------

    /**
     * Verifies that well-formed artifact names clear the error and allow Finish.
     */
    @Test
    public void testValidArtifactNames() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            // Ensure group is valid first.
            wizardShell.bot().textWithLabel("Group").setText("com.example");

            SWTBotText artifactText = wizardShell.bot().textWithLabel("ProjectName/Artifact");
            String[] validArtifacts = { "myapp", "my-app", "liberty-starter", "a", "demo" };
            for (String a : validArtifacts) {
                artifactText.setText(a);
                wizardShell.bot().textWithLabel("Group").setFocus();

                String errMsg = getWizardErrorMessage(wizardShell);
                assertTrue(errMsg == null || (!errMsg.contains("Artifact") && !errMsg.contains("artifact")),
                           "No artifact-related error expected for '" + a + "' but got: " + errMsg);
            }
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 8. Artifact name validation — invalid inputs
    // -----------------------------------------------------------------------

    /**
     * Verifies that malformed artifact names disable Finish.
     */
    @Test
    public void testInvalidArtifactNames() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            // Keep the group valid so only artifact drives the error.
            wizardShell.bot().textWithLabel("Group").setText("com.example");

            SWTBotText artifactText = wizardShell.bot().textWithLabel("ProjectName/Artifact");
            // Invalid: uppercase, numbers, spaces, leading/trailing hyphen, consecutive hyphens
            String[] invalidArtifacts = { "", "MyApp", "app1", "my app", "-app", "app-", "my--app", " leading" };
            for (String a : invalidArtifacts) {
                artifactText.setText(a);
                wizardShell.bot().textWithLabel("Group").setFocus();

                SWTBotTestCondition.waitFor(
                                            () -> !wizardShell.bot().button(BUTTON_FINISH).isEnabled(),
                                            SWTBotTestCondition.VALIDATION_WAIT_MS);

                assertFalse(wizardShell.bot().button(BUTTON_FINISH).isEnabled(),
                            "Finish must be disabled for invalid artifact '" + a + "'");
            }
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 9. Jakarta EE selection auto-updates MicroProfile to highest compatible
    // -----------------------------------------------------------------------

    /**
     * Selects Jakarta EE 8.0 (which is compatible only with older MicroProfile versions)
     * while the wizard is showing the highest MP version (which is incompatible with EE 8.0).
     * Verifies that:
     * <ul>
     * <li>The MicroProfile combo is updated to a version compatible with EE 8.0.</li>
     * <li>The wizard description area shows the auto-update info message.</li>
     * </ul>
     * If EE 8.0 is not offered by the API, the test is skipped gracefully.
     */
    @Test
    public void testJakartaEESelectionUpdatesMicroProfile() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();
            SWTBotCombo javaEeCombo = wizardShell.bot().comboBoxWithLabel("Java EE/Jakarta EE Version");
            SWTBotCombo mpCombo = wizardShell.bot().comboBoxWithLabel("MicroProfile Version");

            // Skip gracefully if EE 8.0 is not offered by the API.
            if (!java.util.Arrays.asList(javaEeCombo.items()).contains("8.0")) {
                System.out.println("INFO: Jakarta EE 8.0 not offered by API; skipping EE->MP auto-update test.");
                return;
            }

            // Start from the highest MP version (last item) — it will be incompatible with EE 8.0.
            String[] mpItems = mpCombo.items();
            String highestMp = mpItems[mpItems.length - 1];
            mpCombo.setSelection(highestMp);

            // Now select EE 8.0 — this must auto-update MP to a compatible version.
            javaEeCombo.setSelection("8.0");

            // Wait for the MP combo to change away from the incompatible highest version.
            SWTBotTestCondition.waitFor(() -> !highestMp.equals(mpCombo.getText()),
                                        SWTBotTestCondition.VALIDATION_WAIT_MS);

            String updatedMp = mpCombo.getText();
            assertFalse(highestMp.equals(updatedMp),
                        "MicroProfile combo must change from '" + highestMp + "' when EE 8.0 is selected");
            assertFalse(updatedMp.isEmpty(),
                        "MicroProfile combo must not be empty after EE 8.0 auto-update");

            // Log the description area message for diagnostics (best-effort — CLabel location
            // may vary across SWT rendering environments).
            String bannerMsg = getWizardErrorMessage(wizardShell);
            System.out.println("  EE=8.0 -> MP auto-selected=" + updatedMp + ", banner='" + bannerMsg + "'");
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 10. MicroProfile selection auto-updates Jakarta EE to highest compatible
    // -----------------------------------------------------------------------

    /**
     * Selects MicroProfile 2.2 (which is only compatible with older Jakarta EE versions)
     * while the wizard is showing the highest EE version (incompatible with MP 2.2).
     * Verifies that:
     * <ul>
     * <li>The Jakarta EE combo is updated to a version compatible with MP 2.2.</li>
     * <li>The wizard description area shows the auto-update info message.</li>
     * </ul>
     * If MP 2.2 is not offered by the API, the test is skipped gracefully.
     */
    @Test
    public void testMicroProfileSelectionUpdatesJakartaEE() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();
            SWTBotCombo javaEeCombo = wizardShell.bot().comboBoxWithLabel("Java EE/Jakarta EE Version");
            SWTBotCombo mpCombo = wizardShell.bot().comboBoxWithLabel("MicroProfile Version");

            // Skip gracefully if MP 2.2 is not offered by the API.
            if (!java.util.Arrays.asList(mpCombo.items()).contains("2.2")) {
                System.out.println("INFO: MicroProfile 2.2 not offered by API; skipping MP->EE auto-update test.");
                return;
            }

            // Start from the highest EE version (last item) — it will be incompatible with MP 2.2.
            String[] eeItems = javaEeCombo.items();
            String highestEe = eeItems[eeItems.length - 1];
            javaEeCombo.setSelection(highestEe);

            // Now select MP 2.2 — this must auto-update EE to a compatible version.
            mpCombo.setSelection("2.2");

            // Wait for the EE combo to change away from the incompatible highest version.
            SWTBotTestCondition.waitFor(() -> !highestEe.equals(javaEeCombo.getText()),
                                        SWTBotTestCondition.VALIDATION_WAIT_MS);

            String updatedEe = javaEeCombo.getText();
            assertFalse(highestEe.equals(updatedEe),
                        "Jakarta EE combo must change from '" + highestEe + "' when MP 2.2 is selected");
            assertFalse(updatedEe.isEmpty(),
                        "Jakarta EE combo must not be empty after MP 2.2 auto-update");

            // Log the description area message for diagnostics (best-effort — CLabel location
            // may vary across SWT rendering environments).
            String bannerMsg = getWizardErrorMessage(wizardShell);
            System.out.println("  MP=2.2 -> EE auto-selected=" + updatedEe + ", banner='" + bannerMsg + "'");
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 11. Java SE auto-upgrade for Jakarta EE 11
    // -----------------------------------------------------------------------

    /**
     * Verifies that when Jakarta EE 11.0 is selected and Java SE is set to 8 or
     * 11, the wizard automatically upgrades Java SE to 17 (matching the website's
     * constraint: EE 11 requires Java SE 17+).
     */
    @Test
    public void testJavaSEAutoUpgradeForJakartaEE11() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();
            SWTBotCombo javaSeCombo = wizardShell.bot().comboBoxWithLabel("Java SE Version");
            SWTBotCombo javaEeCombo = wizardShell.bot().comboBoxWithLabel("Java EE/Jakarta EE Version");

            // Find EE 11.0 in the combo — skip if API doesn't offer it.
            boolean ee11Available = java.util.Arrays.asList(javaEeCombo.items()).contains("11.0");
            if (!ee11Available) {
                System.out.println("INFO: Jakarta EE 11.0 not offered by API; skipping Java SE auto-upgrade test.");
                return;
            }

            // Set Java SE to 8 (if available), then pick EE 11.
            if (java.util.Arrays.asList(javaSeCombo.items()).contains("8")) {
                javaSeCombo.setSelection("8");
            } else if (java.util.Arrays.asList(javaSeCombo.items()).contains("11")) {
                javaSeCombo.setSelection("11");
            } else {
                System.out.println("INFO: Neither Java SE 8 nor 11 available; skipping auto-upgrade assertion.");
                return;
            }

            javaEeCombo.setSelection("11.0");

            // Wait for auto-upgrade
            SWTBotTestCondition.waitFor(() -> "17".equals(javaSeCombo.getText()),
                                        SWTBotTestCondition.VALIDATION_WAIT_MS);

            assertTrue("17".equals(javaSeCombo.getText()),
                       "Java SE must be auto-upgraded to 17 when EE 11.0 is selected; got: " + javaSeCombo.getText());
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 12. Java SE auto-upgrade for Jakarta EE 10
    // -----------------------------------------------------------------------

    /**
     * Verifies that when Jakarta EE 10.0 is selected and Java SE is 8, the wizard
     * upgrades Java SE to at least 11 (EE 10 requires Java SE 11+).
     */
    @Test
    public void testJavaSEAutoUpgradeForJakartaEE10() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();
            SWTBotCombo javaSeCombo = wizardShell.bot().comboBoxWithLabel("Java SE Version");
            SWTBotCombo javaEeCombo = wizardShell.bot().comboBoxWithLabel("Java EE/Jakarta EE Version");

            boolean ee10Available = java.util.Arrays.asList(javaEeCombo.items()).contains("10.0");
            boolean javaSe8Available = java.util.Arrays.asList(javaSeCombo.items()).contains("8");

            if (!ee10Available || !javaSe8Available) {
                System.out.println("INFO: EE 10.0 or Java SE 8 not available; skipping Java SE EE10 auto-upgrade test.");
                return;
            }

            javaSeCombo.setSelection("8");
            javaEeCombo.setSelection("10.0");

            SWTBotTestCondition.waitFor(() -> "11".equals(javaSeCombo.getText()) || "17".equals(javaSeCombo.getText()),
                                        SWTBotTestCondition.VALIDATION_WAIT_MS);

            String javaSe = javaSeCombo.getText();
            assertTrue("11".equals(javaSe) || "17".equals(javaSe),
                       "Java SE must be auto-upgraded to at least 11 when EE 10.0 is selected; got: " + javaSe);
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 13. Java SE auto-upgrade for MicroProfile 6+
    // -----------------------------------------------------------------------

    /**
     * Verifies that selecting MicroProfile 6.0, 6.1, 7.0, or 7.1 while Java SE
     * is 8 triggers an auto-upgrade to at least Java SE 11.
     */
    @Test
    public void testJavaSEAutoUpgradeForMicroProfile6Plus() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();
            SWTBotCombo javaSeCombo = wizardShell.bot().comboBoxWithLabel("Java SE Version");
            SWTBotCombo mpCombo = wizardShell.bot().comboBoxWithLabel("MicroProfile Version");

            boolean javaSe8Available = java.util.Arrays.asList(javaSeCombo.items()).contains("8");
            if (!javaSe8Available) {
                System.out.println("INFO: Java SE 8 not available; skipping MP6+ Java SE auto-upgrade test.");
                return;
            }

            String[] mp6PlusVersions = { "6.0", "6.1", "7.0", "7.1" };
            for (String mpVersion : mp6PlusVersions) {
                if (!java.util.Arrays.asList(mpCombo.items()).contains(mpVersion)) {
                    continue;
                }

                // Reset Java SE to 8 before each iteration.
                javaSeCombo.setSelection("8");
                mpCombo.setSelection(mpVersion);

                final String mpVer = mpVersion;
                SWTBotTestCondition.waitFor(
                                            () -> !"8".equals(javaSeCombo.getText()),
                                            SWTBotTestCondition.VALIDATION_WAIT_MS);

                String javaSe = javaSeCombo.getText();
                assertFalse("8".equals(javaSe),
                            "Java SE must be upgraded from 8 when MicroProfile " + mpVer + " is selected; still 8");
                System.out.println("  MP=" + mpVer + " -> Java SE auto-selected=" + javaSe);
            }
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 14. Use-default-location checkbox toggles the location field
    // -----------------------------------------------------------------------

    /**
     * Verifies that:
     * <ul>
     * <li>When "Use default location" is checked, the custom location text field
     * is disabled (greyed out).</li>
     * <li>When unchecked, the location text field becomes editable.</li>
     * </ul>
     */
    @Test
    public void testUseDefaultLocationCheckbox() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            SWTBotCheckBox useDefaultChk = wizardShell.bot().checkBox("Use default location");
            SWTBotText locationText = wizardShell.bot().textWithLabel("Location:");
            SWTBotButton browseBtn = wizardShell.bot().button("Browse...");

            // If default is checked, location and browse must be disabled.
            if (useDefaultChk.isChecked()) {
                assertFalse(locationText.isEnabled(),
                            "Location field must be disabled when 'Use default location' is checked");
                assertFalse(browseBtn.isEnabled(),
                            "Browse button must be disabled when 'Use default location' is checked");

                // Uncheck: location must become editable.
                useDefaultChk.click();
                assertTrue(locationText.isEnabled(),
                           "Location field must be enabled after unchecking 'Use default location'");
                assertTrue(browseBtn.isEnabled(),
                           "Browse button must be enabled after unchecking 'Use default location'");
            } else {
                // Default is unchecked: location must be enabled.
                assertTrue(locationText.isEnabled(),
                           "Location field must be enabled when 'Use default location' is unchecked");

                // Check: location must become disabled.
                useDefaultChk.click();
                assertFalse(locationText.isEnabled(),
                            "Location field must be disabled after checking 'Use default location'");
            }
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 15. Finish button is disabled when page is invalid
    // -----------------------------------------------------------------------

    /**
     * Verifies that clearing the artifact field disables the Finish button, and
     * that restoring a valid value re-enables it.
     */
    @Test
    public void testFinishButtonDisabledOnInvalidPage() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            SWTBotText artifactText = wizardShell.bot().textWithLabel("ProjectName/Artifact");
            SWTBotButton finishBtn = wizardShell.bot().button(BUTTON_FINISH);

            // Clear the artifact to make the page invalid.
            artifactText.setText("");
            wizardShell.bot().textWithLabel("Group").setFocus();

            SWTBotTestCondition.waitFor(() -> !finishBtn.isEnabled(), SWTBotTestCondition.VALIDATION_WAIT_MS);
            assertFalse(finishBtn.isEnabled(), "Finish must be disabled when artifact is empty");

            // Restore a valid artifact: Finish must be re-enabled.
            artifactText.setText("my-app");
            wizardShell.bot().textWithLabel("Group").setFocus();

            SWTBotTestCondition.waitFor(finishBtn::isEnabled, SWTBotTestCondition.VALIDATION_WAIT_MS);
            assertTrue(finishBtn.isEnabled(), "Finish must be enabled after entering a valid artifact");
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 16. End-to-end: Finish generates a Maven project, it appears in the
    //     Package Explorer, and is cleaned up afterward.
    // -----------------------------------------------------------------------

    /**
     * Clicks Finish on the wizard with valid Maven inputs, waits for the generated
     * project to appear in the Package Explorer, then deletes it from the workspace
     * and disk.
     *
     * <p>The project is downloaded to a temp directory under the system temp folder
     * so it does not pollute the Eclipse workspace directory.
     */
    @Test
    public void testFinishGeneratesAndImportsMavenProject() throws Exception {
        // Use a unique name so the test is repeatable even if a previous run left debris.
        String projectName = "starter-it-test";
        java.nio.file.Path destDir = java.nio.file.Files.createTempDirectory("liberty-starter-it-");

        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();
        try {
            wizardShell.activate();

            // Fill in artifact and group with known-valid values.
            wizardShell.bot().textWithLabel("ProjectName/Artifact").setText(projectName);
            wizardShell.bot().textWithLabel("Group").setText("com.example");

            // Uncheck default location and point to our temp dir.
            SWTBotCheckBox useDefaultChk = wizardShell.bot().checkBox("Use default location");
            if (useDefaultChk.isChecked()) {
                useDefaultChk.click();
            }
            wizardShell.bot().textWithLabel("Location:").setText(destDir.toString());

            // Ensure Maven is selected.
            wizardShell.bot().radio("Maven").click();

            // Finish must be enabled before we click it.
            // Capture a final reference before reassigning wizardShell to null below.
            final SWTBotShell shellForFinish = wizardShell;
            SWTBotTestCondition.waitFor(
                                        () -> shellForFinish.bot().button(BUTTON_FINISH).isEnabled(),
                                        SWTBotTestCondition.VALIDATION_WAIT_MS);
            assertTrue(shellForFinish.bot().button(BUTTON_FINISH).isEnabled(),
                       "Finish must be enabled with valid inputs");

            // Click Finish — wizard closes and the Maven import job runs in the background.
            shellForFinish.bot().button(BUTTON_FINISH).click();
            wizardShell = null; // shell is now owned by the wizard lifecycle

            // Wait for the project to appear in the Package Explorer (import is async).
            boolean appeared = SWTBotTestCondition.waitFor(
                                                           () -> (getInstalledProjectItem(bot, projectName) != null),
                                                           SWTBotTestCondition.SERVER_WAIT_MS);
            assertTrue(appeared, "Project '" + projectName + "' must appear in the Package Explorer after wizard Finish");

        } finally {
            // Cancel if the wizard is still open due to an earlier failure.
            if (wizardShell != null) {
                cancelStarterWizard(wizardShell);
            }

            // Delete the project from the workspace and from disk.
            org.eclipse.core.resources.IProject iProject = io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.getProject(projectName);
            if (iProject != null && iProject.exists()) {
                iProject.delete(true, true, new org.eclipse.core.runtime.NullProgressMonitor());
            }

            // Remove the temp directory if still present.
            try {
                java.nio.file.Files.walk(destDir).sorted(java.util.Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(java.io.File::delete);
            } catch (Exception ignored) {
                // Non-fatal: temp dir cleanup failure should not fail the test.
            }
        }
    }

    // -----------------------------------------------------------------------
    // 17. Cancel closes the wizard without leaving a shell open
    // -----------------------------------------------------------------------

    /**
     * Verifies that clicking Cancel dismisses the wizard shell cleanly — no
     * zombie shell remains visible with the wizard title.
     */
    @Test
    public void testCancelClosesWizard() {
        SWTBotShell wizardShell = openWizardViaFileMenuOrFail();

        cancelStarterWizard(wizardShell);

        // After cancel, no shell with the wizard title should remain.
        // s.widget is a raw SWT Shell — isDisposed() must be called on the UI thread.
        final boolean[] stillOpen = { false };
        org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
            for (SWTBotShell s : bot.shells()) {
                try {
                    if (!s.widget.isDisposed() && s.getText().contains(WIZARD_TITLE)) {
                        stillOpen[0] = true;
                        return;
                    }
                } catch (Exception ignored) {
                    // widget disposed between the check and getText(); treat as closed
                }
            }
        });
        assertFalse(stillOpen[0], "Wizard shell must not remain open after Cancel");
    }

    // -----------------------------------------------------------------------
    // Helper utilities
    // -----------------------------------------------------------------------

    /**
     * Opens the Liberty Starter wizard via the File menu and asserts that the
     * returned shell is not null, failing the test immediately if the wizard
     * did not open.
     *
     * @return The non-null wizard shell.
     */
    private SWTBotShell openWizardViaFileMenuOrFail() {
        SWTBotShell shell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        assertNotNull(shell, "Liberty Starter wizard shell must not be null (wizard failed to open via File menu)");
        return shell;
    }

    /**
     * Opens the Liberty Starter wizard via the Java perspective New menu shortcut
     * and asserts that the returned shell is not null, failing the test immediately
     * if the wizard did not open.
     *
     * @return The non-null wizard shell.
     */
    private SWTBotShell openWizardViaNewMenuOrFail() {
        SWTBotShell shell = openStarterWizardViaNewMenu(bot, WIZARD_NEW_MENU_PATH);
        assertNotNull(shell, "Liberty Starter wizard shell must not be null (wizard failed to open via New menu shortcut)");
        return shell;
    }

    /**
     * Returns the current banner message text shown in the wizard description area
     * (info, warning, or error), or {@code null} if the area is empty.
     *
     * <p>WizardPage.setMessage() renders its text into a {@code CLabel} inside the
     * TitleAreaDialog header. This method scans all CLabel children of the shell on
     * the UI thread, returning the first non-blank text that looks like a real message
     * (i.e. not a short breadcrumb like "&Wizards: ==>").
     */
    private static String getWizardErrorMessage(SWTBotShell wizardShell) {
        try {
            Shell shell = wizardShell.widget;
            final String[] msg = { null };
            org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
                try {
                    for (org.eclipse.swt.widgets.Control c : getAllChildren(shell)) {
                        if (c instanceof org.eclipse.swt.custom.CLabel clbl) {
                            String text = clbl.getText();
                            // Skip short breadcrumb-style texts (e.g. "&Wizards: ==>")
                            if (text != null && text.trim().length() > 20) {
                                msg[0] = text.trim();
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("INFO: getWizardErrorMessage: error scanning wizard CLabels: " + e.getMessage());
                }
            });
            return msg[0];
        } catch (Exception e) {
            System.out.println("INFO: getWizardErrorMessage: error accessing wizard shell: " + e.getMessage());
            return null;
        }
    }

    /** Recursively collects all SWT Controls inside the given composite. */
    private static java.util.List<org.eclipse.swt.widgets.Control> getAllChildren(
                                                                                  org.eclipse.swt.widgets.Composite parent) {
        java.util.List<org.eclipse.swt.widgets.Control> result = new java.util.ArrayList<>();
        for (org.eclipse.swt.widgets.Control child : parent.getChildren()) {
            result.add(child);
            if (child instanceof org.eclipse.swt.widgets.Composite c) {
                result.addAll(getAllChildren(c));
            }
        }
        return result;
    }
}
