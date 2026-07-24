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

import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests Config properties file functionality within Liberty Tools for Eclipse
 */
public class LibertyPluginSWTBotPropertiesFileTest extends AbstractLibertyPluginSWTBotTest {
    /**
     * Test app relative path.
     */
    public static final Path mavenProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");

    public static String[] bootstrapTypeAheadOptions = new String[] { "com.ibm.ws.logging.console.source",
                                                                      "com.ibm.hpel.log.bufferingEnabled",
                                                                      "com.ibm.ws.logging.console.log.level",
                                                                      "com.ibm.ws.logging.console.format",
                                                                      "com.ibm.ws.logging.trace.format",
                                                                      "com.ibm.hpel.trace.outOfSpaceAction" };
    public static String bootstrapPropertiesContent = "com.ibm.ws.logging.console.format";

    public static String[] serverEnvTypeAheadOptions = new String[] { "WLP_DEBUG_ADDRESS",
                                                                      "WLP_LOGGING_CONSOLE_FORMAT",
                                                                      "WLP_LOGGING_CONSOLE_LOGLEVEL",
                                                                      "WLP_LOGGING_JSON_ACCESS_LOG_FIELDS",
                                                                      "WLP_LOGGING_MESSAGE_FORMAT" };
    public static String serverEnvPropertiesContent = "WLP_LOGGING_CONSOLE_LOGLEVEL";
    /**
     * Sample MicroProfile Config properties content
     */
    public static String mpConfigPropertiesContent = "mp.jwt.token.header";
    public static ArrayList<String> projectPaths = new ArrayList<String>();
    /**
     * Expected type-ahead options when at highest level in class.
     */
    public static String[] typeAheadOptions_mpConfig = new String[] { "mp.jwt.token.header", "mp.jwt.decrypt.key.location", "mp.jwt.verify.issuer", "mp.metrics.appName" };
    public static String serverEnvDiagnostics = "is not valid for the variable `WLP_LOGGING_CONSOLE_LOGLEVEL`. [unknown_property_value]";
    /**
     * Expected quick-fixes
     */
    public static String[] serverEnv_quickFixes = new String[] { " Replace value with AUDIT", "Replace value with INFO", "Replace value with WARNING", "Replace value with ERROR",
                                                                 "Replace value with OFF" };

    public static String bootstarpContentForInfo = "com.ibm.ws.logging.console.format=invalid\n";

    public static String bootstrapDiagnostics = "is not valid for the variable `WLP_LOGGING_CONSOLE_LOGLEVEL`. [unknown_property_value]";
    public static String[] bootstrap_quickFixes = new String[] { " Replace value with AUDIT", "Replace value with INFO", "Replace value with WARNING", "Replace value with ERROR",
                                                                 "Replace value with OFF" };

    /**
     * Setup.
     *
     * @throws Exception
     */
    @BeforeAll
    public static void setup() throws Exception {

        commonSetup();

        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
        projectPaths.add(mavenProjectPath.toString());

        // Cleanup to avoid issues from previous runs
        for (String p : projectPaths) {
            cleanupProject(p);
        }

        // Import as Maven project
        importMavenProjects(workspaceRoot, projectPaths);

        // Set the preferences
        setBuildCmdPathInPreferences(bot, "Maven");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();

    }

    @AfterAll
    public static void cleanup() {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
        unsetBuildCmdPathInPreferences(bot, "Maven");
    }

    /**
     * Verify the type ahead options are available for microprofile properties
     */
    @Test
    public void testTypeAheadSuggestionMpConfig() {

        try {

            SWTBotEclipseEditor mgConfigEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                        "src/main/resources/META-INF",
                                                                                        "microprofile-config.properties");
            // Get type-ahead list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "microprofile-config.properties", "", 0, 0);
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : typeAheadOptions_mpConfig) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: microprofile-config.properties" + Arrays.toString(missingOptions.toArray()));
            mgConfigEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Test content assist for property keys in microprofile-config.properties
     */
    @Test
    public void testPropertyKeyContentAssistMpConfig() {

        try {
            SWTBotEclipseEditor mgConfigEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                        "src/main/resources/META-INF",
                                                                                        "microprofile-config.properties");
            mgConfigEditor.show();
            mgConfigEditor.setFocus();

            bot.sleep(1000);

            // Clear and add initial content
            SWTBotPluginOperations.clearContentInEditor(mgConfigEditor);

            bot.sleep(2000);

            // Move to end and try content assist
            mgConfigEditor.navigateTo(mgConfigEditor.getLineCount() - 1, 0);
            mgConfigEditor.autoCompleteProposal("mp.", "mp.jwt.token.header");

            bot.sleep(1000);

            // Verify content was added
            String content = mgConfigEditor.getText();
            assertTrue(content.contains(mpConfigPropertiesContent), "Property is not added correctly - microprofile-config.properties");
            SWTBotPluginOperations.clearContentInEditor(mgConfigEditor);
            mgConfigEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify the type ahead options are available for bootstrap.properties
     */
    @Test
    public void testTypeAheadSuggestionBootstrap() {

        try {

            SWTBotEclipseEditor bootstrapEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/resources/META-INF",
                                                                                         "bootstrap.properties");
            // Get type-ahead list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "bootstrap.properties", "", 0, 0);
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : bootstrapTypeAheadOptions) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: bootstrap.properties" + Arrays.toString(missingOptions.toArray()));
            bootstrapEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Test content assist for property keys in bootstrap.properties
     */
    @Test
    public void testPropertyKeyContentAssistBootstrap() {

        try {

            SWTBotEclipseEditor bootstrapEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/resources/META-INF",
                                                                                         "bootstrap.properties");
            bootstrapEditor.show();
            bootstrapEditor.setFocus();

            bot.sleep(1000);

            // Clear and add initial content
            SWTBotPluginOperations.clearContentInEditor(bootstrapEditor);

            bot.sleep(2000);

            // Move to end and try content assist
            bootstrapEditor.navigateTo(bootstrapEditor.getLineCount() - 1, 0);
            bootstrapEditor.autoCompleteProposal("com.ibm.ws.log", "com.ibm.ws.logging.console.format");

            bot.sleep(3000);

            // Verify content was added
            String content = bootstrapEditor.getText();
            assertTrue(content.contains(bootstrapPropertiesContent), "Property is not added correctly - bootstrap.properties");
            SWTBotPluginOperations.clearContentInEditor(bootstrapEditor);
            bootstrapEditor.close();

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify the type ahead options are available for server.env file
     */
    @Test
    public void testTypeAheadSuggestionServerEnv() {

        try {

            SWTBotEclipseEditor serverEnvEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/resources/META-INF",
                                                                                         "server.env");
            // Get type-ahead list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "server.env", "", 0, 0);
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : serverEnvTypeAheadOptions) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: server.env " + Arrays.toString(missingOptions.toArray()));
            serverEnvEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Test content assist for property keys in server.env file
     */
    @Test
    public void testPropertyKeyContentAssistServerEnv() {

        try {

            SWTBotEclipseEditor serverEnvEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/resources/META-INF",
                                                                                         "server.env");
            serverEnvEditor.show();
            serverEnvEditor.setFocus();

            bot.sleep(1000);

            // Clear and add initial content
            SWTBotPluginOperations.clearContentInEditor(serverEnvEditor);

            bot.sleep(2000);

            // Move to end and try content assist
            serverEnvEditor.navigateTo(serverEnvEditor.getLineCount() - 1, 0);
            serverEnvEditor.autoCompleteProposal("WLP_LOG", "WLP_LOGGING_CONSOLE_LOGLEVEL");

            bot.sleep(1000);

            // Verify content was added
            String content = serverEnvEditor.getText();
            assertTrue(content.contains(serverEnvPropertiesContent), "Property is not added correctly - server.env");
            SWTBotPluginOperations.clearContentInEditor(serverEnvEditor);
            serverEnvEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify quick fixes for server.env file
     */
    @Test
    public void testForVerifyQuickFixesServerEnv() {
        try {

            SWTBotEclipseEditor serverEnvEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/resources/META-INF",
                                                                                         "server.env");
            serverEnvEditor.show();
            serverEnvEditor.setFocus();

            bot.sleep(1000);
            serverEnvEditor.insertText(0, 0, "WLP_LOGGING_CONSOLE_LOGLEVEL=INVALID_PROPERTY_VALUE");

            bot.sleep(10000);

            IEditorPart serverEnvEditorPart = serverEnvEditor.getReference().getEditor(false);

            IFile serverEnvEditorFile = serverEnvEditorPart.getEditorInput().getAdapter(IFile.class);

            if (serverEnvEditorFile == null) {
                fail("Unable to obtain IFile from editor input");
            }

            IMarker[] serverEnvMarkers = serverEnvEditorFile.findMarkers(
                                                                         IMarker.PROBLEM,
                                                                         true,
                                                                         IResource.DEPTH_INFINITE);

            List<String> missingQuickFixes = new ArrayList<>();

            for (IMarker markersItem : serverEnvMarkers) {

                String message = markersItem.getAttribute(IMarker.MESSAGE, "");

                System.out.println("INFO : Marker Message = " + message);

                if (message.contains(serverEnvDiagnostics)) {

                    // Get quick fixes for this marker
                    IMarkerResolution[] resolutions = IDE.getMarkerHelpRegistry().getResolutions(markersItem);

                    System.out.println("INFO : Number of Quick Fixes = " + resolutions.length);

                    for (IMarkerResolution serverEnvResolution : resolutions) {

                        System.out.println("INFO : Quick Fix = " + serverEnvResolution.getLabel());

                        List<String> actualQuickFixes = Arrays.stream(resolutions).map(IMarkerResolution::getLabel).collect(Collectors.toList());
                        missingQuickFixes = Arrays.stream(serverEnv_quickFixes).filter(expected -> !actualQuickFixes.contains(expected)).collect(Collectors.toList());
                    }
                    break;
                }
            }

            assertTrue(!missingQuickFixes.isEmpty(), "Quick fix not found - server.env file");
            // Cleanup
            SWTBotPluginOperations.clearContentInEditor(serverEnvEditor);
            serverEnvEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e.getMessage());
        }
    }

    /**
     * Verify diagnostics fixes for server.env file
     */
    @Test
    public void testDiagnosticsForServerEnv() {
        try {
            SWTBotEclipseEditor serverEnvEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/resources/META-INF",
                                                                                         "server.env");
            serverEnvEditor.show();
            serverEnvEditor.setFocus();
            bot.sleep(1000);

            serverEnvEditor.insertText(1, 0, "WLP_LOGGING_CONSOLE_LOGLEVEL=INVALID_PROPERTY_VALUE");

            bot.sleep(10000);

            IEditorPart serverEnvEditorPart = serverEnvEditor.getReference().getEditor(false);
            IFile serverEnvEditorFile = serverEnvEditorPart.getEditorInput().getAdapter(IFile.class);

            if (serverEnvEditorFile == null) {
                fail("Unable to obtain IFile from editor input");
            }

            IMarker[] serverEnvMarkers = serverEnvEditorFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

            boolean diagnosticFound = false;

            for (IMarker markersItem : serverEnvMarkers) {
                String diagnosticsMessage = markersItem.getAttribute(IMarker.MESSAGE, "");
                System.out.println("INFO : Diagnostic found : " + diagnosticsMessage);

                if (diagnosticsMessage.contains(serverEnvDiagnostics)) {
                    diagnosticFound = true;
                    break;
                }
            }

            assertTrue(diagnosticFound, "Expected diagnostic was not found - server.env file");

            SWTBotPluginOperations.clearContentInEditor(serverEnvEditor);
            serverEnvEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

}