/*******************************************************************************
* Copyright (c) 2022, 2023 IBM Corporation and others.
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests LSP4Jakarta functionality within Liberty Tools for Eclipse
 */
public class LibertyPluginSWTBotLSP4JakartaTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Wrapper Application name.
     */
    static final String MVN_WRAPPER_APP_NAME = "liberty.maven.test.wrapper.app";

    /**
     * Test app relative path.
     */
    static final Path wrapperProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-wrapper-app");

    static ArrayList<String> projectPaths = new ArrayList<String>();

    /**
     * Text to add to editor
     */
    static final String WEB_SERVLET_IMPORT_STRING = "import jakarta.servlet.annotation.WebServlet;\r\n";
    static final String WEB_SERVLET_EMPTY_ANNO_STRING = "@WebServlet()\r\n";

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

    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() {

        commonSetup();

        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
        projectPaths.add(wrapperProjectPath.toString());

        // Maybe redundant but we really want to cleanup. We really want to
        // avoid wasting time debugging tricky differences in behavior because of a dirty re-run
        for (String p : projectPaths) {
            cleanupProject(p);
        }

        importMavenProjects(workspaceRoot, projectPaths);
    }

    @AfterAll
    public static void cleanup() {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
    }

    /**
     * Verify the class level snippets are available
     */
    @Test
    public void testClassLevelSnippets() {

        try {
            // Open new class file
            SWTBotPluginOperations.createNewClass(bot, MVN_WRAPPER_APP_NAME, "MyClass", true);

            // Get type-ahead list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "MyClass.java", "", 0, 0);

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : typeAheadOptions_classLevel) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));

        } finally {

            // Delete new file
            LibertyPluginTestUtils.deleteFile(new File(wrapperProjectPath + "/src/main/java/test/maven/liberty/web/app/MyClass.java"));
        }
    }

    /**
     * Verify the in class snippets are available
     */
    @Test
    public void testInClassSnippets() {

        try {
            // Open new class file
            SWTBotPluginOperations.createNewClass(bot, MVN_WRAPPER_APP_NAME, "MyClass", false);

            // Get type-ahead list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "MyClass.java", "", 3, 0);

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : typeAheadOptions_inClass) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));

        } finally {

            // Delete new file
            LibertyPluginTestUtils.deleteFile(new File(wrapperProjectPath + "/src/main/java/test/maven/liberty/web/app/MyClass.java"));
        }
    }

    /**
     * Verify diagnostics and quick fixes
     */
    @Test
    @Disabled("Issue 377")
    public void testDiagnosticsAndQuickFixes() {

        try {
            // Open new class file
            SWTBotPluginOperations.createNewClass(bot, MVN_WRAPPER_APP_NAME, "MyClass", true);

            // Select the "servlet_generic" snippet
            SWTBotPluginOperations.selectTypeAheadOption(bot, "MyClass.java", "servlet_generic", 0, 0);

            // Add WebServlet annotation
            SWTBotPluginOperations.addTextToEditor(bot, "MyClass.java", WEB_SERVLET_EMPTY_ANNO_STRING, 8, 0);
            SWTBotPluginOperations.addTextToEditor(bot, "MyClass.java", WEB_SERVLET_IMPORT_STRING, 7, 0);

            // Get quick-fix list
            List<String> quickFixes = SWTBotPluginOperations.getQuickFixList(bot, "MyClass.java");

            boolean allFound = true;
            List<String> missingFixes = new ArrayList<String>();
            for (String fix : webServlet_quickFixes) {
                if (!quickFixes.contains(fix)) {
                    allFound = false;
                    missingFixes.add(fix);
                }
            }

            assertTrue(allFound, "Missing quick-fixes: " + Arrays.toString(missingFixes.toArray()));

        } finally {

            // Delete new file
            LibertyPluginTestUtils.deleteFile(new File(wrapperProjectPath + "/src/main/java/test/maven/liberty/web/app/MyClass.java"));
        }
    }
}
