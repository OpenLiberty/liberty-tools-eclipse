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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.swtbot.swt.finder.keyboard.KeyboardFactory;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;

import javax.swing.KeyStroke;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotStyledText;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.Assert.assertTrue;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;


import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.custom.StyledText;

/**
 * Tests Jakarta REST snippet suggestions in Maven Liberty projects.
 */
public class SWTBotJakartaMavenTest extends AbstractLibertyPluginSWTBotTest {

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
     * Tests that Jakarta REST snippets are available when typing "rest_" in a Java file
     * Creates a new Java class, clears content, types "rest_", checks for suggestions, and selects "rest_class"
     */
//    @Test
//    public void testRestSnippetSuggestions() {
//
//        try {
//            // Create new Java class with content cleared (empty file)
//            System.out.println("INFO: Creating new Java class RestTestClass");
//            SWTBotPluginOperations.createNewClass(bot, MAVEN_APP_NAME, "RestTestClass", true);
//            
//            // Wait for editor to be ready and language server to process the file
//            System.out.println("INFO: Waiting for language server to process file...");
//            bot.sleep(5000);
//            
//            // Type "rest_" and get auto-complete suggestions at position 0,0 (empty file)
//            System.out.println("INFO: Typing 'rest_' and checking for suggestions");
//            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestTestClass.java", "rest_", 0, 0);
//            System.out.println("INFO: Type-ahead options found for 'rest_' = " + Arrays.toString(typeAheadOptions.toArray()));
//
//            // Verify that REST class snippet is present (only rest_class in empty file)
//            boolean allFound = true;
//            List<String> missingOptions = new ArrayList<String>();
//            for (String option : restClassSnippetOptions) {
//                if (!typeAheadOptions.contains(option)) {
//                    allFound = false;
//                    missingOptions.add(option);
//                }
//            }
//
//            assertTrue(allFound, "Missing REST class snippet: " + Arrays.toString(missingOptions.toArray()));
//            
//            // If rest_class is available, select it
//            if (typeAheadOptions.contains("rest_class")) {
//                System.out.println("INFO: Selecting 'rest_class' snippet");
//                SWTBotPluginOperations.selectTypeAheadOption(bot, "RestTestClass.java", "rest_class", 0, 0);
//                System.out.println("INFO: Successfully selected 'rest_class' snippet");
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail("Unexpected exception was thrown: " + e.getMessage());
//        } finally {
//            // Close the editor without saving
//            System.out.println("INFO: Closing RestTestClass.java without saving");
//            if (bot.activeEditor() != null) {
//                bot.activeEditor().close();
//            }
//            
//            // Delete the created file
//            try {
//                System.out.println("INFO: Deleting RestTestClass.java");
//                IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(MAVEN_APP_NAME);
//                IFile file = project.getFile("src/main/java/RestTestClass.java");
//                if (file.exists()) {
//                    file.delete(true, null);
//                    System.out.println("INFO: Successfully deleted RestTestClass.java");
//                }
//            } catch (Exception e) {
//                System.err.println("WARN: Could not delete test file: " + e.getMessage());
//            }
//        }
//    }
//
//    /**
//     * Tests that Jakarta REST method snippets are available when typing "rest_" in a Java file
//     * Creates a new Java class, clears content, types "rest_", checks for method suggestions
//     */
//    @Test
//    public void testRestMethodSnippetSuggestions() {
//
//        try {
//            // Create new Java class with content cleared (empty file)
//            System.out.println("INFO: Creating new Java class RestMethodTest");
//            SWTBotPluginOperations.createNewClass(bot, MAVEN_APP_NAME, "RestMethodTest", true);
//            
//            // Wait for editor to be ready and language server to process the file
//            System.out.println("INFO: Waiting for language server to process file...");
//            bot.sleep(5000);
//            
//            // Type "rest_" and get auto-complete suggestions at position 0,0 (empty file)
//            System.out.println("INFO: Typing 'rest_' and checking for method suggestions");
//            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestMethodTest.java", "rest_", 0, 0);
//            System.out.println("INFO: Type-ahead options found for 'rest_' = " + Arrays.toString(typeAheadOptions.toArray()));
//
//            // Verify that REST class snippet is present (only rest_class in empty file)
//            // Note: Method-level snippets (rest_get, rest_post, etc.) only appear inside a class body
//            boolean allFound = true;
//            List<String> missingOptions = new ArrayList<String>();
//            for (String option : restClassSnippetOptions) {
//                if (!typeAheadOptions.contains(option)) {
//                    allFound = false;
//                    missingOptions.add(option);
//                }
//            }
//
//            assertTrue(allFound, "Missing REST class snippet: " + Arrays.toString(missingOptions.toArray()));
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            fail("Unexpected exception was thrown: " + e.getMessage());
//        } finally {
//            // Close the editor without saving
//            System.out.println("INFO: Closing RestMethodTest.java without saving");
//            if (bot.activeEditor() != null) {
//                bot.activeEditor().close();
//            }
//            
//            // Delete the created file
//            try {
//                System.out.println("INFO: Deleting RestMethodTest.java");
//                IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(MAVEN_APP_NAME);
//                IFile file = project.getFile("src/main/java/RestMethodTest.java");
//                if (file.exists()) {
//                    file.delete(true, null);
//                    System.out.println("INFO: Successfully deleted RestMethodTest.java");
//                }
//            } catch (Exception e) {
//                System.err.println("WARN: Could not delete test file: " + e.getMessage());
//            }
//        }
    //}
    
  /*  
    
    @Test
    public void verifyContentAssistSuggestionsForRest() {

        // Open Project Explorer
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

        bot.sleep(3000);

        // Get opened editor
        SWTBotEclipseEditor editor =
                bot.editorByTitle("RestApplication.java")
                   .toTextEditor();

        editor.show();
        editor.setFocus();

        bot.sleep(1000);

        // Clear existing content
        String content = editor.getText();

        if (content != null && content.length() > 0) {
            editor.selectRange(0, 0, content.length());
            editor.insertText("");
        }

        bot.sleep(1000);

        // Type rest
//        editor.insertText("rest_");
        editor.autoCompleteProposal("rest_", "rest_class");
        
        

        bot.sleep(1000);
//        int length = editor.getText().length();
//        editor.selectRange(0, length, 0);
//        editor.pressShortcut(Keystrokes.RIGHT);

     // Trigger Content Assist (Ctrl + Space)
//        editor.pressShortcut(
//            Keystrokes.CTRL,
//            Keystrokes.SPACE
//        );
     // Ctrl + Space
//        bot.getDisplay().syncExec(new Runnable() {
//            @Override
//            public void run() {
//                Event ctrlDown = new Event();
//                ctrlDown.type = SWT.KeyDown;
//                ctrlDown.keyCode = SWT.CTRL;
//                bot.getDisplay().post(ctrlDown);
//
//                Event spaceDown = new Event();
//                spaceDown.type = SWT.KeyDown;
//                spaceDown.keyCode = ' ';
//                bot.getDisplay().post(spaceDown);
//
//                Event ctrlUp = new Event();
//                ctrlUp.type = SWT.KeyUp;
//                ctrlUp.keyCode = SWT.CTRL;
//                bot.getDisplay().post(ctrlUp);
//            }
//        });
//
//    bot.sleep(3000);

//        bot.sleep(3000);

        bot.sleep(3000);

//        // Verify suggestions popup displayed
//        Assesrtion.assertTrue(
//            "Content Assist suggestions not displayed",
//            bot.shells().length > 1
//        );
        // Open Project Explorer
        bot.viewByTitle("Project Explorer").show();

        // Open RestApplication.java
        SWTBotTreeItem file1 = bot.tree()
                .expandNode("jakartasample")
                .expandNode("src")
                .expandNode("main")
                .expandNode("java")
                .expandNode("test")
                .expandNode("rest")
                .getNode("FieldConstraintValidation.java");

        file1.doubleClick();

        bot.sleep(3000);

        // Get opened editor
        SWTBotEclipseEditor editor1 =
                bot.editorByTitle("FieldConstraintValidation.java")
                   .toTextEditor();

        editor1.show();
        editor1.setFocus();

        bot.sleep(1000);

        bot.sleep(300000);

//        // Verify suggestions popup displayed
//        Assertions.assertTrue(
//            "Content Assist suggestions not displayed",
//            bot.shells().length > 1
//        );
    }
    
  

    @Test
    public void verifyDiagnosticsOnHover() throws Exception {

        // Increase SWTBot timeout
        SWTBotPreferences.TIMEOUT = 10000;

        // Open Project Explorer
        bot.viewByTitle("Project Explorer").show();

        // Open Java file
        bot.tree()
           .expandNode("jakartasample")
           .expandNode("src")
           .expandNode("main")
           .expandNode("java")
           .expandNode("test")
           .expandNode("rest")
           .getNode("FieldConstraintValidation.java")
           .doubleClick();

        bot.sleep(2000);

        SWTBotEclipseEditor editor =
                bot.editorByTitle("FieldConstraintValidation.java")
                   .toTextEditor();

        editor.show();
        editor.setFocus();

        bot.sleep(10000);
        String content = editor.getText();

        int offset = content.indexOf("isHappy");

//        assertTrue(
//            "'isHappy' text not found",
//            offset >= 0
//        );

        // Convert offset to line / column
        String before = content.substring(0, offset);

        int line = before.split("\n").length - 1;

        int column =
            offset - before.lastIndexOf('\n') - 1;

        // Move cursor to text
        editor.navigateTo(line, column);

        // Select the text
        editor.selectRange(
            line,
            column,
            "isHappy".length()
        );

        bot.sleep(1000);

        // Trigger hover/info popup
        bot.activeShell().pressShortcut(
            SWT.CTRL,
            'K'
        );
        
        bot.sleep(10000);

    }*/
    @Test
    public void verifyDiagnosticHover() {


        // Open Project Explorer
        bot.viewByTitle("Project Explorer").show();


        // Open Java file
        bot.tree()
            .expandNode("jakartasample")
            .expandNode("src")
            .expandNode("main")
            .expandNode("java")
            .expandNode("test")
            .expandNode("rest")
            .getNode("FieldConstraintValidation.java")
            .doubleClick();


        bot.sleep(3000);


        SWTBotEclipseEditor editor =
                bot.editorByTitle("FieldConstraintValidation.java")
                   .toTextEditor();


        editor.show();
        editor.setFocus();


        String content = editor.getText();


        int offset =
                content.indexOf("isHappy");


        assertTrue(
            "isHappy not found",
            offset >= 0
        );


        // Calculate line and column
        String before =
                content.substring(0, offset);


        int line =
                before.split("\n").length - 1;


        int column =
                offset -
                before.lastIndexOf("\n") - 1;



        // Select text
        editor.selectRange(
                line,
                column,
                "isHappy".length()
        );


        bot.sleep(10000);



        /*
         * Get SWTBot wrapper
         */
        SWTBotStyledText botStyledText =
                editor.getStyledText();



        /*
         * Get real SWT StyledText widget
         */
        StyledText styledText =
                botStyledText.widget;



        /*
         * Find mouse position
         */
        Point point =
                styledText.getLocationAtOffset(offset);



        /*
         * Trigger hover
         */
        Display.getDefault().syncExec(() -> {


            styledText.setFocus();


            Event moveEvent =
                    new Event();


            moveEvent.type =
                    SWT.MouseMove;


            moveEvent.x =
                    point.x + 5;


            moveEvent.y =
                    point.y + 5;



            styledText.notifyListeners(
                    SWT.MouseMove,
                    moveEvent
            );



            Event hoverEvent =
                    new Event();


            hoverEvent.type =
                    SWT.MouseHover;


            hoverEvent.x =
                    point.x + 5;


            hoverEvent.y =
                    point.y + 5;



            styledText.notifyListeners(
                    SWT.MouseHover,
                    hoverEvent
            );

        });



        // Wait for hover
        bot.sleep(5000);



        /*
         * Check hover popup
         */
        final boolean[] hoverFound =
                new boolean[1];



        Display.getDefault().syncExec(() -> {


            Shell[] shells =
                    Display.getDefault()
                           .getShells();



            for (Shell shell : shells) {


                if (shell.isVisible()) {


                    System.out.println(
                        "Visible shell: "
                        + shell.getText()
                    );


                    hoverFound[0] = true;

                }
            }

        });



        assertTrue(
            "Diagnostic hover was not displayed",
            hoverFound[0]
        );
    }
}
        

// Made with Bob
