/*******************************************************************************
* Copyright (c) 2023 IBM Corporation and others.
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

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.AbstractLibertyPluginSWTBotTest;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests LSP4Jakarta functionality within Liberty Tools for Eclipse
 */
public class LibertyPluginSWTBotLSP4JakartaTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Application name - using Maven app
     */
    static final String MAVEN_APP_NAME = "jakartasample";

    /**
     * Test app relative path.
     */
    static final Path mavenProjectPath = Paths.get("resources", "applications", "maven", "jakartasample");

    static ArrayList<String> projectPaths = new ArrayList<String>();

    /**
     * Expected type-ahead options when typing "rest_" in an empty file (class-level only)
     */
    static String[] restClassSnippetOptions = new String[] { "rest_class" };
    
    /**
     * Expected type-ahead options when typing "rest_" inside a class (method-level)
     */
    static String[] restMethodSnippetOptions = new String[] { "rest_head", "rest_get",
            "rest_post", "rest_put", "rest_delete" };
    
    /**
     * Text to add to editor
     */
    static final String WEB_SERVLET_IMPORT_STRING = "import jakarta.servlet.annotation.WebServlet;\r\n";
    static final String WEB_SERVLET_EMPTY_ANNO_STRING = "@WebServlet()\r\n";

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
        
        
//        bot.sleep(5000);
        
//        // Trigger Maven build to download dependencies
//        System.out.println("INFO: Triggering Maven build to download Jakarta dependencies...");
//        bot.menu("Project").menu("Clean...").click();
//        bot.shell("Clean").activate();
//        bot.button("Clean").click();
//        bot.sleep(5000);
        
        // Give the language server time to initialize after project import and build
        // This is crucial for Jakarta snippet suggestions to work
        // Language server needs time to:
        // 1. Download Maven dependencies (jakarta.jakartaee-api, microprofile)
        // 2. Index the project classpath
        // 3. Activate LSP4Jakarta features
        System.out.println("INFO: Waiting for language server to initialize and index Jakarta dependencies...");
//        Thread.sleep(30000); // 30 seconds - increased for Maven dependency download and indexing
    }

    @AfterAll
    public static void cleanup() {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
        unsetBuildCmdPathInPreferences(bot, "Maven");
    }
    /**
     * Expected quick-fixes
     */
    static String[] webServlet_quickFixes = new String[] { "Add the `urlPatterns` attribute to @WebServlet",
            "Add the `value` attribute to @WebServlet" };

    /**
     * Expected type-ahead options when at highest level in class.
     */
    static String[] typeAheadOptions_classLevel = new String[] { "rest_class", "persist_entity", "servlet_doget", "servlet_dopost",
            "servlet_generic", "servlet_webfilter" };

    /**
     * Expected type-ahead options within REST class
     */
    static String[] typeAheadOptions_inClass = new String[] { "persist_context", "persist_context_extended",
            "persist_context_extended_unsync", "rest_head", "rest_get", "rest_post", "rest_put", "rest_delete", "tx_user_inject",
            "tx_user_jndi" };

//    /**
//     * Setup.
//     * 
//     * @throws Exception
//     */
//    @BeforeAll
//    public static void setup() throws Exception {
//
//        commonSetup();
//
//        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
//        projectPaths.add(wrapperProjectPath.toString());
//
//        // Maybe redundant but we really want to cleanup. We really want to
//        // avoid wasting time debugging tricky differences in behavior because of a dirty re-run
//        for (String p : projectPaths) {
//            cleanupProject(p);
//        }
//
//        importMavenProjects(workspaceRoot, projectPaths);
//    }
//
//    @AfterAll
//    public static void cleanup() {
//        for (String p : projectPaths) {
//            cleanupProject(p);
//        }
//    }

    /**
     * Verify the class level snippets are available
     */
//    @Test
//    public void testClassLevelSnippets() {
//
//        try {
//         // Open Project Explorer
//            bot.viewByTitle("Project Explorer").show();
//
//            // Open RestApplication.java
//            SWTBotTreeItem file = bot.tree()
//                    .expandNode("jakartasample")
//                    .expandNode("src")
//                    .expandNode("main")
//                    .expandNode("java")
//                    .expandNode("test")
//                    .expandNode("rest")
//                    .getNode("RestApplication.java");
//
//            file.doubleClick();
//
//            bot.sleep(3000);
//
//            // Get opened editor
//            SWTBotEclipseEditor editor =
//                    bot.editorByTitle("RestApplication.java")
//                       .toTextEditor();
//            // Clear existing content
//            String content = editor.getText();
//
//            if (content != null && content.length() > 0) {
//                editor.selectRange(0, 0, content.length());
//                editor.insertText("");
//            }
//
//            bot.sleep(1000);
//            // Get type-ahead list
//            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestApplication.java", "", 0, 0);
//            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));
//
//            boolean allFound = true;
//            List<String> missingOptions = new ArrayList<String>();
//            for (String option : typeAheadOptions_classLevel) {
//                if (!typeAheadOptions.contains(option)) {
//                    allFound = false;
//                    missingOptions.add(option);
//                }
//            }
//
//            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));
//
//        } catch (Exception e) {
//            fail("Unexpected exception was thrown: " + e);
//        }
//    }

    /**
     * Verify the in class snippets are available
     */
//    @Test
//    public void testInClassSnippets() {
//
//        try {
//            // Open Project Explorer
//            bot.viewByTitle("Project Explorer").show();
//
//            // Open RestApplication.java
//            SWTBotTreeItem file = bot.tree().expandNode("jakartasample").expandNode("src").expandNode("main").expandNode("java").expandNode("test").expandNode("rest").getNode("RestApplication.java");
//
//            file.doubleClick();
//
//            bot.sleep(3000);
//
//            // Get opened editor
////            SWTBotEclipseEditor editor = bot.editorByTitle("RestApplication.java").toTextEditor();
//
//            bot.sleep(1000);
//
//            // Get type-ahead list
//            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestApplication.java", "", 6, 0);
//            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));
//
//            boolean allFound = true;
//            List<String> missingOptions = new ArrayList<String>();
//            for (String option : typeAheadOptions_inClass) {
//                if (!typeAheadOptions.contains(option)) {
//                    allFound = false;
//                    missingOptions.add(option);
//                }
//            }
//
//            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));
//
//        } catch (Exception e) {
//            fail("Unexpected exception was thrown: " + e);
//        }
//    }
//
    /**
     * Verify diagnostics and quick fixes
     */
    @Test
//    @Disabled("Issue 377")
    public void testDiagnosticsAndQuickFixes() {

        try {
//             Open Project Explorer
          bot.viewByTitle("Project Explorer").show();

          // Open RestApplication.java
          SWTBotTreeItem file = bot.tree()
                          .expandNode("jakartasample")
                          .expandNode("src")
                          .expandNode("main")
                          .expandNode("java")
                          .expandNode("test")
                          .expandNode("rest")
                          .getNode("RestApplication.java");

          file.doubleClick();
       // Get opened editor
        SWTBotEclipseEditor editor =
                bot.editorByTitle("RestApplication.java")
                   .toTextEditor();
//        Clear existing content
        String content = editor.getText();

        if (content != null && content.length() > 0) {
            editor.selectRange(0, 0, content.length());
            editor.insertText("");
        }

        

          bot.sleep(3000);
            // Select the "servlet_generic" snippet
            SWTBotPluginOperations.selectTypeAheadOption(bot, "RestApplication.java", "servlet_generic", 0, 0);
            bot.sleep(3000);
         // Add WebServlet annotation
            SWTBotPluginOperations.addTextToEditor(bot, "RestApplication.java", WEB_SERVLET_EMPTY_ANNO_STRING, 8, 0);
            SWTBotPluginOperations.addTextToEditor(bot, "RestApplication.java", WEB_SERVLET_IMPORT_STRING, 7, 0);

            // Get quick-fix list
            List<String> quickFixes = SWTBotPluginOperations.getQuickFixList(bot, "RestApplication.java");

            boolean allFound = true;
            List<String> missingFixes = new ArrayList<String>();
            for (String fix : webServlet_quickFixes) {
                if (!quickFixes.contains(fix)) {
                    allFound = false;
                    missingFixes.add(fix);
                }
            }

            assertTrue(allFound, "Missing quick-fixes: " + Arrays.toString(missingFixes.toArray()));

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        } 
    }
  
}