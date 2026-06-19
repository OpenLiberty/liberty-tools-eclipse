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

import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.mpConfigPropertiesContent;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.projectPaths;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.typeAheadOptions_mpConfig;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.bootstrapTypeAheadOptions;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.bootstrapPropertiesContent;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.serverEnvTypeAheadOptions;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.serverEnvPropertiesContent;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
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
     * Verify the type ahead options are available for mp properties
     * 
     */

    @Test
    public void testTypeAheadSuggestionMpConfig() {

        try {
            // Open Project Explorer
            bot.viewByTitle("Project Explorer").show();

            // Navigate to microprofile-config.properties
            SWTBotTreeItem file = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("microprofile-config.properties");

            file.doubleClick();

            bot.sleep(1000);
            SWTBotEclipseEditor editor = bot.editorByTitle("microprofile-config.properties").toTextEditor();
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

            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));
            editor.close();

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
            // Navigate to microprofile-config.properties
            SWTBotTreeItem file = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("microprofile-config.properties");

            file.doubleClick();

            bot.sleep(1000);

            // Get opened editor
            SWTBotEclipseEditor editor = bot.editorByTitle("microprofile-config.properties").toTextEditor();
            editor.show();
            editor.setFocus();

            bot.sleep(1000);

            // Clear and add initial content
            SWTBotPluginOperations.clearContentInEditor(editor);

            bot.sleep(2000);

            // Move to end and try content assist
            editor.navigateTo(editor.getLineCount() - 1, 0);
            editor.autoCompleteProposal("mp.", "mp.jwt.token.header");

            bot.sleep(1000);

            // Verify content was added
            String content = editor.getText();
            assertTrue(content.contains(mpConfigPropertiesContent), "Property is not added correctly");
            SWTBotPluginOperations.clearContentInEditor(editor);
            editor.close();

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify the type ahead options are available for mp properties
     * 
     */

    @Test
    public void testTypeAheadSuggestionBootstrap() {

        try {
            // Open Project Explorer
            bot.viewByTitle("Project Explorer").show();

            // Navigate to microprofile-config.properties
            SWTBotTreeItem file = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("bootstrap.properties");

            file.doubleClick();

            bot.sleep(1000);
            SWTBotEclipseEditor editor = bot.editorByTitle("bootstrap.properties").toTextEditor();
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

            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));
            editor.close();

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Test content assist for property keys in microprofile-config.properties
     */
    @Test
    public void testPropertyKeyContentAssistBootstrap() {

        try {
            // Navigate to microprofile-config.properties
            SWTBotTreeItem file = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("bootstrap.properties");

            file.doubleClick();

            bot.sleep(1000);

            // Get opened editor

            SWTBotEclipseEditor editor = bot.editorByTitle("bootstrap.properties").toTextEditor();

            editor.show();
            editor.setFocus();

            bot.sleep(1000);

            // Clear and add initial content
            SWTBotPluginOperations.clearContentInEditor(editor);

            bot.sleep(2000);

            // Move to end and try content assist
            editor.navigateTo(editor.getLineCount() - 1, 0);
            editor.autoCompleteProposal("com.ibm.ws.log", "com.ibm.ws.logging.console.format");

            bot.sleep(3000);

            // Verify content was added
            String content = editor.getText();
            assertTrue(content.contains(bootstrapPropertiesContent), "Property is not added correctly");
            SWTBotPluginOperations.clearContentInEditor(editor);
            editor.close();

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify the type ahead options are available for mp properties
     * 
     */

    @Test
    public void testTypeAheadSuggestionServerEnv() {

        try {
            // Open Project Explorer
            bot.viewByTitle("Project Explorer").show();

            // Navigate to microprofile-config.properties
            SWTBotTreeItem file = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("server.env");

            file.doubleClick();

            bot.sleep(1000);
            SWTBotEclipseEditor editor = bot.editorByTitle("server.env").toTextEditor();
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

            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));
            editor.close();

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Test content assist for property keys in microprofile-config.properties
     */
    @Test
    public void testPropertyKeyContentAssistServerEnv() {

        try {
            // Navigate to microprofile-config.properties
            SWTBotTreeItem file = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("resources").expandNode("META-INF").getNode("server.env");

            file.doubleClick();

            bot.sleep(1000);

            // Get opened editor
            SWTBotEclipseEditor editor = bot.editorByTitle("server.env").toTextEditor();
            editor.show();
            editor.setFocus();

            bot.sleep(1000);

            // Clear and add initial content
            SWTBotPluginOperations.clearContentInEditor(editor);

            bot.sleep(2000);

            // Move to end and try content assist
            editor.navigateTo(editor.getLineCount() - 1, 0);
            editor.autoCompleteProposal("WLP_LOG", "WLP_LOGGING_CONSOLE_LOGLEVEL");

            bot.sleep(1000);

            // Verify content was added
            String content = editor.getText();
            assertTrue(content.contains(serverEnvPropertiesContent), "Property is not added correctly");
            SWTBotPluginOperations.clearContentInEditor(editor);
            editor.close();

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }
}