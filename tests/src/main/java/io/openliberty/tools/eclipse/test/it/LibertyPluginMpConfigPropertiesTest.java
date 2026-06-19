/*******************************************************************************
* Copyright (c) 2024 IBM Corporation and others.
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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;

/**
 * Tests Jakarta REST snippet suggestions in Maven Liberty projects.
 */
public class LibertyPluginMpConfigPropertiesTest extends AbstractLibertyPluginSWTBotTest {

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
    static String[] mpConfigTypeaheadOptions = new String[] { "mp.jwt.token.cookie", "mp.jwt.token.header", "mp.jwt.decrypt.key.location", "mp.health.disable-default-procedures",
                                                     "mp.jwt.verify.issuer"};
    
    

    static String mpConfigProperty ="mp.jwt.token.header=header";
    static String bootstrapProperty="com.ibm.ws.logging.console.format=DEV";
    
    
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
     * Tests that Jakarta REST snippets are available when typing "rest_" in a Java file
     * Creates a new Java class, clears content, types "rest_", checks for suggestions, and selects "rest_class"
     */

//    @Test
//    public void verifyContentAssistSuggestionsForRest() {
//
//        // Open Project Explorer
//        bot.viewByTitle("Project Explorer").show();
//
//        // Open RestApplication.java
//        SWTBotTreeItem file = bot.tree()
//                .expandNode("jakartasample")
//                .expandNode("src")
//                .expandNode("main")
//                .expandNode("resources")
//                .expandNode("META-INF")
//                .getNode("microprofile-config.properties");
//
//        file.doubleClick();
//
//        bot.sleep(3000);
//
//        // Get opened editor
//        SWTBotEclipseEditor editor =
//                bot.editorByTitle("microprofile-config.properties")
//                   .toTextEditor();
//
//        editor.show();
//        editor.setFocus();
//
//        bot.sleep(1000);
//
//        // Clear existing content
//        String content = editor.getText();
//
//        if (content != null && content.length() > 0) {
//            editor.selectRange(0, 0, content.length());
//            editor.insertText("");
//        }
//
//        bot.sleep(1000);
//
//        editor.autoCompleteProposal("mp.jwt", "mp.jwt.token.header");
//        editor.insertText(0, 21, "header");
//        bot.sleep(3000);
//        String contentAfterSnippet = editor.getText();
//        System.out.println("INFO : contentAfterSnippet"+ contentAfterSnippet);
//        boolean contentMatches=contentAfterSnippet.contains(mpConfigProperty);
//        System.out.println("contentMatches-->"+contentMatches);
//
//        assertTrue(contentAfterSnippet.contains(mpConfigProperty), "Error while adding rest_class snippet");
//    }
//    
//    @Test
//    public void verifyContentAssistSuggestionsForBootstrapFileValue() {
//
//        // Open Project Explorer
//        bot.viewByTitle("Project Explorer").show();
//
//        // Open RestApplication.java
//        SWTBotTreeItem file = bot.tree()
//                .expandNode("jakartasample")
//                .expandNode("src")
//                .expandNode("main")
//                .expandNode("resources")
//                .expandNode("META-INF")
//                .getNode("bootstrap.properties");
//
//        file.doubleClick();
//
//        bot.sleep(3000);
//
//        // Get opened editor
//        SWTBotEclipseEditor editor =
//                bot.editorByTitle("bootstrap.properties")
//                   .toTextEditor();
//
//        editor.show();
//        editor.setFocus();
//
//        bot.sleep(1000);
//
//        // Clear existing content
//        String content = editor.getText();
//
//        if (content != null && content.length() > 0) {
//            editor.selectRange(0, 0, content.length());
//            editor.insertText("");
//        }
//
//        bot.sleep(1000);
//
//        editor.autoCompleteProposal("com.ibm", "com.ibm.ws.logging.console.format");
//        editor.insertText(0, 34, "=DEV");
////        editor.click(0, 30);
////        editor.autoCompleteProposal("=", "DEV");
//        bot.sleep(3000);
//        String contentAfterSnippet = editor.getText();
//        System.out.println("INFO : contentAfterSnippet"+ contentAfterSnippet);
//        boolean contentMatches=contentAfterSnippet.contains(bootstrapProperty);
//        System.out.println("contentMatches-->"+contentMatches);
//
//        assertTrue(contentAfterSnippet.contains(bootstrapProperty), "Error while adding rest_class snippet");
//    }
    
//    @Test
//    public void verifyContentAssistSuggestionsForBootstrapFile() {
//
//        // Open Project Explorer
//        bot.viewByTitle("Project Explorer").show();
//
//        // Open RestApplication.java
//        SWTBotTreeItem file = bot.tree()
//                .expandNode("jakartasample")
//                .expandNode("src")
//                .expandNode("main")
//                .expandNode("resources")
//                .expandNode("META-INF")
//                .getNode("bootstrap.properties");
//
//        file.doubleClick();
//
//        bot.sleep(3000);
//
//        // Get opened editor
//        SWTBotEclipseEditor editor =
//                bot.editorByTitle("bootstrap.properties")
//                   .toTextEditor();
//
//        editor.show();
//        editor.setFocus();
//
//        bot.sleep(1000);
//
//        // Clear existing content
//        String content = editor.getText();
//
//        if (content != null && content.length() > 0) {
//            editor.selectRange(0, 0, content.length());
//            editor.insertText("");
//        }
//
//        bot.sleep(1000);
//
//        editor.autoCompleteProposal("com.ibm", "com.ibm.ws.logging.console.format");
//        editor.insertText(0, 34, "= DEV");
////        editor.click(0, 34);
//
//        String contentAfterSnippet = editor.getText();
//        System.out.println("INFO : contentAfterSnippet"+ contentAfterSnippet);
//        boolean contentMatches=contentAfterSnippet.contains(bootstrapProperty);
//        System.out.println("contentMatches-->"+contentMatches);
//
//        assertTrue(contentAfterSnippet.contains(bootstrapProperty), "Error while adding properties in the snippet");
//    }
//    
    
//    @Test
//    public void testDiagnostics() {
//
//        try {
//
//            // Open Project Explorer
//            bot.viewByTitle("Project Explorer").show();
//
//         // Open RestApplication.java
//            SWTBotTreeItem file = bot.tree()
//                    .expandNode("jakartasample")
//                    .expandNode("src")
//                    .expandNode("main")
//                    .expandNode("resources")
//                    .expandNode("META-INF")
//                    .getNode("microprofile-config.properties");
//
//            file.doubleClick();
//
//            bot.sleep(3000);
//
//            // Get opened editor
//            SWTBotEclipseEditor editor =
//                    bot.editorByTitle("microprofile-config.properties")
//                       .toTextEditor();
//         // Clear existing content
//            String content = editor.getText();
//
//            if (content != null && content.length() > 0) {
//                editor.selectRange(0, 0, content.length());
//                editor.insertText("");
//            }
//
//            bot.sleep(1000);
//
//
//            editor.insertText(0, 0,mpConfigProperty );
//            bot.sleep(3000);
//            editor.click(0,0);
//            editor.navigateTo(0, 7);
//            editor.setFocus();
//
//            // Wait for validation/diagnostics
//            bot.sleep(10000);
//
//            /*
//             * ------------------------------------------------------
//             * Verify diagnostic exists
//             * ------------------------------------------------------
//             */
//
//            IEditorPart editorPart =
//                            editor.getReference().getEditor(false);
//
//                    IFile workspaceFile =
//                            editorPart.getEditorInput()
//                                      .getAdapter(IFile.class);
//
//                    if (workspaceFile == null) {
//                        fail("Unable to obtain IFile from editor input");
//                    }
//
//                    IMarker[] markers =
//                            workspaceFile.findMarkers(
//                                    IMarker.PROBLEM,
//                                    true,
//                                    IResource.DEPTH_INFINITE);
//                    System.out.println("markers------>"+markers);
//            boolean diagnosticFound = false;
//
//            for (IMarker marker : markers) {
//
//                String message =
//                        marker.getAttribute(
//                                IMarker.MESSAGE,
//                                "");
//
//                System.out.println(
//                        "Diagnostic found ------>: " + message);
//
//                if (message.contains("Configuration property to specify the HTTP header name expected to contain the JWT token.")) {
//
//                    diagnosticFound = true;
//                    break;
//                }
//            }
//
//            assertTrue(
//                    diagnosticFound,
//                    "Expected diagnostic was not found");
//
//        } catch (Exception e) {
//
//            fail(
//                    "Unexpected exception was thrown: "
//                    + e);
//        }
//    }
    
    
    
    
    @Test
    public void verifyHoverForBootstrapProperty() {
        
        
     // Open Project Explorer
      bot.viewByTitle("Project Explorer").show();

      // Open RestApplication.java
      SWTBotTreeItem file = bot.tree()
              .expandNode("jakartasample")
              .expandNode("src")
              .expandNode("main")
              .expandNode("resources")
              .expandNode("META-INF")
              .getNode("bootstrap.properties");

      file.doubleClick();

      bot.sleep(3000);

      // Get opened editor
      SWTBotEclipseEditor editor =
              bot.editorByTitle("bootstrap.properties")
                 .toTextEditor();

      editor.show();
      editor.setFocus();

      bot.sleep(2000);

   // ensure file has content
   String property = "com.ibm.ws.logging.console.format";

   // position cursor on the property line
   editor.navigateTo(0, 10);

   bot.sleep(1000);

   // get StyledText widget
   StyledText styledText = bot.styledText().widget;

   // IMPORTANT: move REAL mouse pointer to that location
   Display.getDefault().syncExec(() -> {

       // convert widget coordinates to display coordinates
       Point displayPoint = styledText.toDisplay(50, 10);

       // move actual mouse pointer (this triggers hover in Eclipse)
       Display.getDefault().setCursorLocation(displayPoint.x, displayPoint.y);
       bot.sleep(5000);
   });

   bot.sleep(5000);
   for (SWTBotShell shell : bot.shells()) {
       System.out.println("Shell found: " + shell.getText());
   }
}
}
        
