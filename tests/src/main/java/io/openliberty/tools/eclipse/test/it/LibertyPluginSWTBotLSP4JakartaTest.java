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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests LSP4Jakarta functionality within Liberty Tools for Eclipse
 */
public class LibertyPluginSWTBotLSP4JakartaTest extends AbstractLibertyPluginSWTBotTest {
    /**
     * Application name - using Maven app
     */
    public static final String MAVEN_APP_NAME = "liberty.maven.test.app";

    /**
     * Test app relative path.
     */
    public static final Path mavenProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");

    public static ArrayList<String> projectPaths = new ArrayList<String>();

    public static String[] invalidProperty_quickFixes = new String[] { "Remove invalid line", "Add equals sign" };

    /**
     * Expected type-ahead options when typing "rest_" in an empty file (class-level only)
     */
    public static String[] restClassSnippetOptions = new String[] { "rest_class" };

    /**
     * Expected type-ahead options when typing "rest_" inside a class (method-level)
     */
    public static String[] restMethodSnippetOptions = new String[] { "rest_head", "rest_get",
                                                                     "rest_post", "rest_put", "rest_delete" };

    public static final String invalidField = "@AssertTrue\n"
                                              + "    private int isHappy;";

    /**
     * Expected quick-fixes
     */
    public static String[] invalidField_quickFixes = new String[] { "Remove constraint annotation AssertTrue from element" };

    /**
     * Expected type-ahead options when at highest level in class.
     */
    public static String[] jakartaTypeAheadOptions_classLevel = new String[] { "rest_class", "persist_entity", "servlet_doget", "servlet_dopost",
                                                                               "servlet_generic", "servlet_webfilter" };

    /**
     * Expected type-ahead options within REST class
     */
    public static String[] jakartaTypeAheadOptions_inClass = new String[] { "persist_context", "persist_context_extended",
                                                                            "persist_context_extended_unsync", "rest_head", "rest_get", "rest_post", "rest_put", "rest_delete",
                                                                            "tx_user_inject",
                                                                            "tx_user_jndi" };

    public static String[] jakartaTypeAheadOptions_mpProperties = new String[] { "mp.jwt.token.cookie", "mp.jwt.token.header", "mp.jwt.decrypt.key.location",
                                                                                 "mp.health.disable-default-procedures",
                                                                                 "mp.jwt.verify.issuer", "servlet_webfilter" };

    public static String restClassSnippet = "import jakarta.ws.rs.GET;\n"
                                            + "import jakarta.ws.rs.Path;\n"
                                            + "import jakarta.ws.rs.Produces;\n"
                                            + "import jakarta.ws.rs.core.MediaType;\n"
                                            + "\n"
                                            + "@Path(\"/path\")\n";

    public static String restMethodSnippetDel = "@DELETE\n"
                                                + "@Consumes(MediaType.TEXT_PLAIN)";

    public static String assertTrueDiagnostics = "The @AssertTrue annotation can only be used on boolean and Boolean type fields. [InvalidAnnotationOnNonBooleanMethodOrField]";

    public static String restClassSnippetToAdd = "package test.maven.liberty.web.app;\n"
                                                 + "\n"
                                                 + "import jakarta.ws.rs.GET;\n"
                                                 + "import jakarta.ws.rs.Path;\n"
                                                 + "import jakarta.ws.rs.Produces;\n"
                                                 + "import jakarta.ws.rs.core.MediaType;\n"
                                                 + "\n"
                                                 + "@Path(\"\"\n"
                                                 + "         + \"\")\n"
                                                 + "public class RestTestClass {\n"
                                                 + "\n"
                                                 + " @GET\n"
                                                 + " @Produces(MediaType.TEXT_PLAIN)\n"
                                                 + " public String methodname() {\n"
                                                 + "         return \"hello\";\n"
                                                 + " }\n"
                                                 + "}";

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
     * Verify the class level snippets are available
     */
    @Test
    public void testClassLevelSnippets() {

        try {
            SWTBotEclipseEditor currentEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                       "src/main/java/test/maven/liberty/web/app",
                                                                                       "RestTestClass.java");
            // Clear existing content
            String javaFilecontent = currentEditor.getText();

            if (javaFilecontent != null && javaFilecontent.length() > 0) {
                currentEditor.selectRange(0, 0, javaFilecontent.length());
                currentEditor.insertText("");
            }

            bot.sleep(1000);
            // Get type-ahead list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestApplication.java", "", 0, 0);
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : jakartaTypeAheadOptions_classLevel) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));
            currentEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Tests that Jakarta REST snippets are available when typing "rest_" in a Java file
     * Creates a new Java class, clears content, types "rest_", checks for suggestions, and selects "rest_class"
     **/
    @Test
    public void verifyContentAssistSugForRestClassSnippet() {
        try {
            SWTBotEclipseEditor currentEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                       "src/main/java/test/maven/liberty/web/app",
                                                                                       "RestTestClass.java");
            bot.sleep(3000);

            currentEditor.show();
            currentEditor.setFocus();

            bot.sleep(1000);

            SWTBotPluginOperations.clearContentInEditor(currentEditor);

            bot.sleep(1000);

            currentEditor.autoCompleteProposal("rest_", "rest_class");

            String contentAfterSnippet = currentEditor.getText();
            System.out.println("INFO : contentAfterSnippet" + contentAfterSnippet);
            boolean contentMatches = contentAfterSnippet.contains(restClassSnippet);
            System.out.println("INFO : contentMatches" + contentMatches);

            assertTrue(contentAfterSnippet.contains(restClassSnippet), "Error while adding rest_class snippet");
            currentEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify the method level snippets are available
     */
    @Test
    public void testInClassForMethodSnippets() {

        try {
            SWTBotEclipseEditor currentEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                       "src/main/java/test/maven/liberty/web/app",
                                                                                       "RestTestClass.java");
            bot.sleep(1000);

            currentEditor.show();
            currentEditor.setFocus();

            bot.sleep(1000);
            currentEditor.insertText(0, 0, restClassSnippetToAdd);

            // Get type-ahead list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestApplication.java", "", 10, 0);
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : jakartaTypeAheadOptions_inClass) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));
            currentEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Tests that Jakarta REST snippets are available when typing "rest_" in a Java file
     * Creates a new Java class, clears content, types "rest_", checks for suggestions, and selects "rest_delete"
     */
    @Test
    public void verifyContentAssistSugForRestMethodLevel() {
        try {
            SWTBotEclipseEditor currentEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                       "src/main/java/test/maven/liberty/web/app",
                                                                                       "RestTestClass.java");
            bot.sleep(3000);

            bot.sleep(1000);
            currentEditor.insertText(0, 0, restClassSnippetToAdd);
            currentEditor.navigateTo(10, 0);
            bot.sleep(1000);
            currentEditor.autoCompleteProposal("rest_", "rest_delete");
            bot.sleep(1000);

            String contentAfterSnippet = currentEditor.getText();
            System.out.println("INFO : contentAfterSnippet" + contentAfterSnippet);
            boolean contentMatches = contentAfterSnippet.contains(restMethodSnippetDel);
            System.out.println("contentMatches-->" + contentMatches);

            assertTrue(contentMatches, "Error while adding rest_delete snippet");
            currentEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify quick fixes
     */
    @Test
    public void testForVerifyQuickFixesInvalidField() {

        try {

            SWTBotEclipseEditor currentEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                       "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                       "src/main/java/test/maven/liberty/web/app",
                                                                                       "FieldConstraintValidation.java");
            currentEditor.navigateTo(9, 0);
            currentEditor.click(7, 22);

            bot.sleep(5000);
            // Get quick-fix list
            List<String> quickFixes = SWTBotPluginOperations.getQuickFixList(bot, "FieldConstraintValidation.java");
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(quickFixes.toArray()));

            boolean allFound = true;
            List<String> missingFixes = new ArrayList<String>();
            for (String fix : invalidField_quickFixes) {
                System.out.println("fix-->" + fix);
                if (!quickFixes.contains(fix)) {
                    allFound = false;
                    missingFixes.add(fix);
                }
            }

            assertTrue(allFound, "Missing quick-fixes: " + Arrays.toString(missingFixes.toArray()));
            currentEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify diagnostics
     */
    @Test
    public void testDiagnosticsForInvalidField() {

        try {

            SWTBotEclipseEditor currentEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                       "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                       "src/main/java/test/maven/liberty/web/app",
                                                                                       "FieldConstraintValidation.java");
            currentEditor.click(7, 22);

            bot.sleep(5000);

            //Verify diagnostic exists

            IEditorPart editorPart = currentEditor.getReference().getEditor(false);

            IFile workspaceFile = editorPart.getEditorInput().getAdapter(IFile.class);

            if (workspaceFile == null) {
                fail("Unable to obtain IFile from editor input");
            }

            IMarker[] issueMarkers = workspaceFile.findMarkers(
                                                               IMarker.PROBLEM,
                                                               true,
                                                               IResource.DEPTH_INFINITE);

            boolean diagnosticFound = false;

            for (IMarker marker : issueMarkers) {

                String message = marker.getAttribute(IMarker.MESSAGE, "");

                System.out.println("INFO : Diagnostic found : " + message);

                if (message.contains(assertTrueDiagnostics)) {
                    diagnosticFound = true;
                    break;
                }
            }

            assertTrue(
                       diagnosticFound,
                       "Expected diagnostic was not found");
            currentEditor.close();
        } catch (Exception e) {

            fail("Unexpected exception was thrown: " + e);
        }
    }
}