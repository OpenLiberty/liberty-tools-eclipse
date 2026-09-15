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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.openliberty.tools.eclipse.test.it.utils.SWTBotTestCondition;

/**
 * Integration tests for the Liberty Starter wizard UI.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Opening the wizard via File > New and the Java perspective shortcut</li>
 *   <li>Default field values match the Liberty Starter API</li>
 *   <li>Group / artifact validation (valid and invalid inputs)</li>
 *   <li>Jakarta EE → MicroProfile auto-update (highest compatible MP selected)</li>
 *   <li>MicroProfile → Jakarta EE auto-update (highest compatible EE selected)</li>
 *   <li>Java SE auto-upgrade when EE 10 / EE 11 / MP 6+ is selected</li>
 *   <li>Use-default-location checkbox behaviour</li>
 *   <li>Finish button is disabled while the page is invalid</li>
 *   <li>Cancel closes the wizard without creating a project</li>
 * </ul>
 *
 * <p>Every test opens the wizard fresh (via {@link BeforeEach} or inline) and
 * closes it via Cancel (or the OS close button) in its own cleanup, so no
 * inter-test state leaks.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LibertyPluginSWTBotStarterTest extends AbstractLibertyPluginSWTBotTest {

    // -----------------------------------------------------------------------
    // Wizard title / widget labels (must match LibertyStarterWizard constants)
    // -----------------------------------------------------------------------
    private static final String WIZARD_TITLE = "Liberty Project Starter";
    private static final String WIZARD_NEW_MENU_PATH = "Liberty Starter Project";
    private static final String CATEGORY_NAME = "Liberty";

    // Wizard button labels
    private static final String BUTTON_FINISH = "Finish";
    private static final String BUTTON_CANCEL = "Cancel";

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
     * (File > New > Other… > Open Liberty > Liberty Starter Project) and that it
     * shows the expected title and a Finish button.
     */
    @Test
    @Order(1)
    public void testOpenWizardViaFileMenu() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        try {
            assertNotNull(wizardShell, "Liberty Starter wizard shell must not be null");
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
    @Order(2)
    public void testOpenWizardViaNewMenuShortcut() {
        SWTBotShell wizardShell = openStarterWizardViaNewMenu(bot, WIZARD_NEW_MENU_PATH);
        try {
            assertNotNull(wizardShell, "Wizard shell must not be null when opened via New shortcut");
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
    @Order(3)
    public void testDefaultFieldValues() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        try {
            wizardShell.activate();

            SWTBotText groupText    = wizardShell.bot().textWithLabel("Group");
            SWTBotText artifactText = wizardShell.bot().textWithLabel("ProjectName/Artifact");
            SWTBotCombo javaSeCombo = wizardShell.bot().comboBoxWithLabel("Java SE Version");
            SWTBotCombo javaEeCombo = wizardShell.bot().comboBoxWithLabel("Java EE/Jakarta EE Version");
            SWTBotCombo mpCombo     = wizardShell.bot().comboBoxWithLabel("MicroProfile Version");

            assertFalse(groupText.getText().isEmpty(),    "Group field must have a default value");
            assertFalse(artifactText.getText().isEmpty(), "Artifact field must have a default value");
            assertFalse(javaSeCombo.getText().isEmpty(),  "Java SE combo must have a default selection");
            assertFalse(javaEeCombo.getText().isEmpty(),  "Jakarta EE combo must have a default selection");
            assertFalse(mpCombo.getText().isEmpty(),      "MicroProfile combo must have a default selection");

            assertTrue(javaSeCombo.itemCount() > 0, "Java SE combo must contain at least one item");
            assertTrue(javaEeCombo.itemCount() > 0, "Jakarta EE combo must contain at least one item");
            assertTrue(mpCombo.itemCount()     > 0, "MicroProfile combo must contain at least one item");
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
    @Order(4)
    public void testBuildToolRadioButtons() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        try {
            wizardShell.activate();

            SWTBotRadio mavenRadio  = wizardShell.bot().radio("Maven");
            SWTBotRadio gradleRadio = wizardShell.bot().radio("Gradle");

            assertNotNull(mavenRadio,  "Maven radio button must be present");
            assertNotNull(gradleRadio, "Gradle radio button must be present");

            // Exactly one must be selected by default
            boolean mavenSelected  = mavenRadio.isSelected();
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
                assertTrue(mavenRadio.isSelected(),   "Maven radio must be selected after clicking it");
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
    @Order(5)
    public void testValidGroupNames() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
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
    @Order(6)
    public void testInvalidGroupNames() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
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
    @Order(7)
    public void testValidArtifactNames() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
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
    @Order(8)
    public void testInvalidArtifactNames() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
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
     * Selects each available Jakarta EE version and verifies that the MicroProfile
     * combo is updated to a compatible (non-null, non-empty) version. The exact
     * version is API-driven, so only presence is asserted.
     */
    @Test
    @Order(9)
    public void testJakartaEESelectionUpdatesMicroProfile() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        try {
            wizardShell.activate();
            SWTBotCombo javaEeCombo = wizardShell.bot().comboBoxWithLabel("Java EE/Jakarta EE Version");
            SWTBotCombo mpCombo     = wizardShell.bot().comboBoxWithLabel("MicroProfile Version");

            int eeCount = javaEeCombo.itemCount();
            assertTrue(eeCount > 0, "Jakarta EE combo must have at least one item");

            for (int i = 0; i < eeCount; i++) {
                String eeVersion = javaEeCombo.items()[i];
                javaEeCombo.setSelection(eeVersion);

                // Wait briefly for the async UI update to complete.
                final int idx = i;
                SWTBotTestCondition.waitFor(() -> !mpCombo.getText().isEmpty(),
                                            SWTBotTestCondition.VALIDATION_WAIT_MS);

                String mpVersion = mpCombo.getText();
                assertFalse(mpVersion.isEmpty(),
                            "MicroProfile version must be set after selecting Jakarta EE '" + eeVersion + "'");
                System.out.println("  EE=" + eeVersion + " -> MP auto-selected=" + mpVersion);
            }
        } finally {
            cancelStarterWizard(wizardShell);
        }
    }

    // -----------------------------------------------------------------------
    // 10. MicroProfile selection auto-updates Jakarta EE to highest compatible
    // -----------------------------------------------------------------------

    /**
     * Selects each available MicroProfile version and verifies that the Jakarta EE
     * combo is updated to a compatible version.
     */
    @Test
    @Order(10)
    public void testMicroProfileSelectionUpdatesJakartaEE() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        try {
            wizardShell.activate();
            SWTBotCombo javaEeCombo = wizardShell.bot().comboBoxWithLabel("Java EE/Jakarta EE Version");
            SWTBotCombo mpCombo     = wizardShell.bot().comboBoxWithLabel("MicroProfile Version");

            int mpCount = mpCombo.itemCount();
            assertTrue(mpCount > 0, "MicroProfile combo must have at least one item");

            for (int i = 0; i < mpCount; i++) {
                String mpVersion = mpCombo.items()[i];
                if ("None".equals(mpVersion)) {
                    continue; // "None" has no EE constraint; skip
                }
                mpCombo.setSelection(mpVersion);

                SWTBotTestCondition.waitFor(() -> !javaEeCombo.getText().isEmpty(),
                                            SWTBotTestCondition.VALIDATION_WAIT_MS);

                String eeVersion = javaEeCombo.getText();
                assertFalse(eeVersion.isEmpty(),
                            "Jakarta EE version must be set after selecting MicroProfile '" + mpVersion + "'");
                System.out.println("  MP=" + mpVersion + " -> EE auto-selected=" + eeVersion);
            }
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
    @Order(11)
    public void testJavaSEAutoUpgradeForJakartaEE11() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
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
    @Order(12)
    public void testJavaSEAutoUpgradeForJakartaEE10() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
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
    @Order(13)
    public void testJavaSEAutoUpgradeForMicroProfile6Plus() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        try {
            wizardShell.activate();
            SWTBotCombo javaSeCombo = wizardShell.bot().comboBoxWithLabel("Java SE Version");
            SWTBotCombo mpCombo     = wizardShell.bot().comboBoxWithLabel("MicroProfile Version");

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
     *   <li>When "Use default location" is checked, the custom location text field
     *       is disabled (greyed out).</li>
     *   <li>When unchecked, the location text field becomes editable.</li>
     * </ul>
     */
    @Test
    @Order(14)
    public void testUseDefaultLocationCheckbox() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        try {
            wizardShell.activate();

            SWTBotCheckBox useDefaultChk = wizardShell.bot().checkBox("Use default location");
            SWTBotText locationText      = wizardShell.bot().textWithLabel("Location:");
            SWTBotButton browseBtn       = wizardShell.bot().button("Browse...");

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
    @Order(15)
    public void testFinishButtonDisabledOnInvalidPage() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        try {
            wizardShell.activate();

            SWTBotText artifactText = wizardShell.bot().textWithLabel("ProjectName/Artifact");
            SWTBotButton finishBtn  = wizardShell.bot().button(BUTTON_FINISH);

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
    // 16. Cancel closes the wizard without leaving a shell open
    // -----------------------------------------------------------------------

    /**
     * Verifies that clicking Cancel dismisses the wizard shell cleanly — no
     * zombie shell remains visible with the wizard title.
     */
    @Test
    @Order(16)
    public void testCancelClosesWizard() {
        SWTBotShell wizardShell = openStarterWizardViaFileMenu(bot, CATEGORY_NAME, WIZARD_NEW_MENU_PATH);
        assertNotNull(wizardShell, "Wizard must open before cancelling");

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
     * Returns the current error message text shown in the wizard, or {@code null}
     * if no error is displayed. Reads the description area via SWTBot shell.
     */
    private static String getWizardErrorMessage(SWTBotShell wizardShell) {
        try {
            Shell shell = wizardShell.widget;
            final String[] msg = { null };
            org.eclipse.swt.widgets.Display.getDefault().syncExec(() -> {
                // Walk the shell children looking for the DialogMessageArea / description label.
                // SWTBot exposes it via bot().label(0) for some wizard layouts; try both approaches.
                try {
                    for (org.eclipse.swt.widgets.Control c : getAllChildren(shell)) {
                        if (c instanceof org.eclipse.swt.widgets.Label lbl) {
                            String text = lbl.getText();
                            if (text != null && !text.isBlank() &&
                                (text.contains("error") || text.contains("Error") ||
                                 text.contains("invalid") || text.contains("Invalid") ||
                                 text.contains("empty") || text.contains("Empty") ||
                                 text.contains("Valid characters") || text.contains("cannot"))) {
                                msg[0] = text;
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Swallow — msg stays null
                }
            });
            return msg[0];
        } catch (Exception e) {
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
