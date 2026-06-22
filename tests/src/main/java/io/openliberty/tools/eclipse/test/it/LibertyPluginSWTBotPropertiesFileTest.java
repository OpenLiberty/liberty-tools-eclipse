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

import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.bootstrapPropertiesContent;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.bootstrapTypeAheadOptions;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.mpConfigPropertiesContent;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.projectPaths;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.serverEnvDiagnostics;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.serverEnvPropertiesContent;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.serverEnvTypeAheadOptions;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.serverEnv_quickFixes;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.typeAheadOptions_mpConfig;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests MicroProfile Config properties file functionality within Liberty Tools for Eclipse
 */
public class LibertyPluginSWTBotPropertiesFileTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Setup.
     *
     * @throws Exception
     */
    @BeforeAll
    public static void setup() throws Exception {

        commonSetup();

        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
        projectPaths.add(ConstantsForAutomatedTest.mavenProjectPath.toString());

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
     * 
     */

    @Test
    public void testTypeAheadSuggestionMpConfig() {

        try {
            // Open Project Explorer
            bot.viewByTitle("Project Explorer").show();

            SWTBotTreeItem mgConfigFile = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("microprofile-config.properties");

            mgConfigFile.doubleClick();

            bot.sleep(1000);
            SWTBotEclipseEditor mgConfigEditor = bot.editorByTitle("microprofile-config.properties").toTextEditor();
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
            SWTBotTreeItem mgConfigFile = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("microprofile-config.properties");

            mgConfigFile.doubleClick();

            bot.sleep(1000);

            SWTBotEclipseEditor mgConfigEditor = bot.editorByTitle("microprofile-config.properties").toTextEditor();
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
     * 
     */

    @Test
    public void testTypeAheadSuggestionBootstrap() {

        try {
            // Open Project Explorer
            bot.viewByTitle("Project Explorer").show();

            SWTBotTreeItem bootstrapFile = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("bootstrap.properties");

            bootstrapFile.doubleClick();

            bot.sleep(1000);
            SWTBotEclipseEditor bootstrapEditor = bot.editorByTitle("bootstrap.properties").toTextEditor();
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
            SWTBotTreeItem bootstrapFile = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("bootstrap.properties");

            bootstrapFile.doubleClick();

            bot.sleep(1000);

            // Get opened editor

            SWTBotEclipseEditor bootstrapEditor = bot.editorByTitle("bootstrap.properties").toTextEditor();

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
     * 
     */

    @Test
    public void testTypeAheadSuggestionServerEnv() {

        try {
            // Open Project Explorer
            bot.viewByTitle("Project Explorer").show();
            SWTBotTreeItem serverEnvFile = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("server.env");

            serverEnvFile.doubleClick();

            bot.sleep(1000);
            SWTBotEclipseEditor serverEnvEditor = bot.editorByTitle("server.env").toTextEditor();
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
            SWTBotTreeItem serverEnvFile = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("server.env");

            serverEnvFile.doubleClick();

            bot.sleep(1000);

            // Get opened editor
            SWTBotEclipseEditor serverEnvEditor = bot.editorByTitle("server.env").toTextEditor();
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
            SWTBotTreeItem serverEnvFile = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("server.env");

            serverEnvFile.doubleClick();

            SWTBotEclipseEditor serverEnvEditor = bot.editorByTitle("server.env").toTextEditor();

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
            SWTBotTreeItem serverEnvFile = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("server.env");

            serverEnvFile.doubleClick();
            bot.sleep(1000);

            SWTBotEclipseEditor serverEnvEditor = bot.editorByTitle("server.env").toTextEditor();
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