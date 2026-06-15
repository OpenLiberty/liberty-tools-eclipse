/*******************************************************************************
* Copyright (c) 2023, 2025 IBM Corporation and others.
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests Jakarta REST snippet suggestions in Java files within Liberty Tools for Eclipse
 */
public class SWTBotJavaFileTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Application name - using Gradle wrapper app with spaces
     */
    static final String GRADLE_WRAPPER_APP_NAME = "liberty-gradle-test-wrapper-app-with-spaces";

    /**
     * Test app relative path.
     */
    static final Path wrapperProjectPath = Paths.get("resources", "applications", "apps with spaces", "liberty-gradle-test-wrapper-app-with-spaces");

    static ArrayList<File> projectsToInstall = new ArrayList<File>();

    /**
     * Expected type-ahead options when typing "rest_"
     */
    static String[] restSnippetOptions = new String[] { "rest_class", "rest_head", "rest_get", 
            "rest_post", "rest_put", "rest_delete" };

    /**
     * Setup.
     * 
     * @throws Exception
     */
    @BeforeAll
    public static void setup() throws Exception {

        commonSetup();
        
        File wrapperProject = wrapperProjectPath.toFile();
        projectsToInstall.add(wrapperProject);

        // Cleanup to avoid issues from previous runs
        for (File p : projectsToInstall) {
            cleanupProject(p.toString());
        }

        // Import as Gradle project
        importGradleApplications(projectsToInstall);

        // Set the preferences
        setBuildCmdPathInPreferences(bot, "Gradle");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();
        
        // Give the language server time to initialize after project import
        // This is crucial for Jakarta snippet suggestions to work
        // Liberty Tools should be automatically enabled on Gradle Liberty projects
        System.out.println("INFO: Waiting for language server to initialize...");
        Thread.sleep(10000); // Increased to 10 seconds
    }

    @AfterAll
    public static void cleanup() {
        for (File p : projectsToInstall) {
            cleanupProject(p.toString());
        }
        unsetBuildCmdPathInPreferences(bot, "Gradle");
    }

    /**
     * Tests that Jakarta REST snippets are available when typing "rest_" in a Java file
     * Creates a new Java class, clears content, types "rest_", checks for suggestions, and selects "rest_class"
     */
    @Test
    public void testRestSnippetSuggestions() {

        // Delete file if it already exists from previous run
        File testFile = new File(wrapperProjectPath.toAbsolutePath() + "/src/main/java/test/maven/liberty/web/app/RestTestClass.java");
        if (testFile.exists()) {
            System.out.println("INFO: Deleting existing RestTestClass.java file before test");
            LibertyPluginTestUtils.deleteFile(testFile);
        }

        try {
            // Create new Java class with content cleared
            System.out.println("INFO: Creating new Java class RestTestClass");
            SWTBotPluginOperations.createNewClass(bot, GRADLE_WRAPPER_APP_NAME, "RestTestClass", true);
            
            // Wait for editor to be ready and language server to process the file
            System.out.println("INFO: Waiting for language server to process file...");
            bot.sleep(5000); // Increased to 5 seconds
            
            // Type "rest_" and get auto-complete suggestions
            System.out.println("INFO: Typing 'rest_' and checking for suggestions");
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestTestClass.java", "rest_", 0, 0);
            System.out.println("INFO: Type-ahead options found for 'rest_' = " + Arrays.toString(typeAheadOptions.toArray()));

            // Verify that REST snippet options are present
            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : restSnippetOptions) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing REST snippet options: " + Arrays.toString(missingOptions.toArray()));
            
            // If rest_class is available, select it
            if (typeAheadOptions.contains("rest_class")) {
                System.out.println("INFO: Selecting 'rest_class' snippet");
                SWTBotPluginOperations.selectTypeAheadOption(bot, "RestTestClass.java", "rest_class", 0, 0);
                System.out.println("INFO: Successfully selected 'rest_class' snippet");
            }

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        } finally {
            // Delete the test file
            System.out.println("INFO: Deleting RestTestClass.java file");
            LibertyPluginTestUtils.deleteFile(testFile);
        }
    }

    /**
     * Tests that Jakarta REST method snippets are available when typing "rest_" in a Java file
     * Creates a new Java class, clears content, types "rest_", checks for method suggestions
     */
    @Test
    public void testRestMethodSnippetSuggestions() {

        // Delete file if it already exists from previous run
        File testFile = new File(wrapperProjectPath.toAbsolutePath() + "/src/main/java/test/maven/liberty/web/app/RestMethodTest.java");
        if (testFile.exists()) {
            System.out.println("INFO: Deleting existing RestMethodTest.java file before test");
            LibertyPluginTestUtils.deleteFile(testFile);
        }

        try {
            // Create new Java class with content cleared
            System.out.println("INFO: Creating new Java class RestMethodTest");
            SWTBotPluginOperations.createNewClass(bot, GRADLE_WRAPPER_APP_NAME, "RestMethodTest", true);
            
            // Wait for editor to be ready and language server to process the file
            System.out.println("INFO: Waiting for language server to process file...");
            bot.sleep(5000); // Increased to 5 seconds
            
            // Type "rest_" and get auto-complete suggestions
            System.out.println("INFO: Typing 'rest_' and checking for method suggestions");
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestMethodTest.java", "rest_", 0, 0);
            System.out.println("INFO: Type-ahead options found for 'rest_' = " + Arrays.toString(typeAheadOptions.toArray()));

            // Expected REST method snippets
            String[] inClassRestSnippets = new String[] { "rest_head", "rest_get", "rest_post", "rest_put", "rest_delete" };

            // Verify that REST method snippet options are present
            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : inClassRestSnippets) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing REST method snippet options: " + Arrays.toString(missingOptions.toArray()));

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        } finally {
            // Delete the test file
            System.out.println("INFO: Deleting RestMethodTest.java file");
            LibertyPluginTestUtils.deleteFile(testFile);
        }
    }
}

// Made with Bob
