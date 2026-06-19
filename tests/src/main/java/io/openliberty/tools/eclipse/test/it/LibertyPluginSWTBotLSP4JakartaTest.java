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

import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.assertTrueDiagnostics;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.invalidField_quickFixes;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.jakartaTypeAheadOptions_classLevel;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.jakartaTypeAheadOptions_inClass;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.projectPaths;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.restClassSnippet;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.restMethodSnippetDel;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.restClassSnippetToAdd;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests LSP4Jakarta functionality within Liberty Tools for Eclipse
 */
public class LibertyPluginSWTBotLSP4JakartaTest extends AbstractLibertyPluginSWTBotTest {

    
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
     * Verify the class level snippets are available
     * 
     */

    @Test
    public void testClassLevelSnippets() {

        try {
            SWTBotEclipseEditor editor = SWTBotPluginOperations.openFileForTest(bot);
            // Clear existing content
            String content = editor.getText();

            if (content != null && content.length() > 0) {
                editor.selectRange(0, 0, content.length());
                editor.insertText("");
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
            editor.close();
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

        SWTBotEclipseEditor editor = SWTBotPluginOperations.openFileForTest(bot);
        bot.sleep(3000);

        editor.show();
        editor.setFocus();

        bot.sleep(1000);

        SWTBotPluginOperations.clearContentInEditor(editor);

        bot.sleep(1000);

        editor.autoCompleteProposal("rest_", "rest_class");

        String contentAfterSnippet = editor.getText();
        System.out.println("INFO : contentAfterSnippet" + contentAfterSnippet);
        boolean contentMatches = contentAfterSnippet.contains(restClassSnippet);
        System.out.println("INFO : contentMatches" + contentMatches);

        assertTrue(contentAfterSnippet.contains(restClassSnippet), "Error while adding rest_class snippet");
        editor.close();
    }

    /**
     * Verify the method level snippets are available
     */

    @Test
    public void testInClassForMethodSnippets() {

        try {
            SWTBotEclipseEditor editor = SWTBotPluginOperations.openFileForTest(bot);
            bot.sleep(1000);

            editor.show();
            editor.setFocus();

            bot.sleep(1000);
            editor.insertText(0, 0, restClassSnippetToAdd);

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
            editor.close();

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

        SWTBotEclipseEditor editor = SWTBotPluginOperations.openFileForTest(bot);
        bot.sleep(3000);

        bot.sleep(1000);
        editor.insertText(0, 0, restClassSnippetToAdd);
        editor.navigateTo(10, 0);
        bot.sleep(1000);
        editor.autoCompleteProposal("rest_", "rest_delete");
        bot.sleep(1000);

        String contentAfterSnippet = editor.getText();
        System.out.println("INFO : contentAfterSnippet" + contentAfterSnippet);
        boolean contentMatches = contentAfterSnippet.contains(restMethodSnippetDel);
        System.out.println("contentMatches-->" + contentMatches);

        assertTrue(contentMatches, "Error while adding rest_delete snippet");
        editor.close();
    }

    /**
     * Verify quick fixes
     */
    @Test
    public void testForVerifyQuickFixesInvalidField() {

        try {
            //Open Project Explorer
            bot.viewByTitle("Project Explorer").show();

            // Open RestApplication.java
            SWTBotTreeItem file = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("java").expandNode("test").expandNode("maven").expandNode("liberty").expandNode("web").expandNode("app").getNode("FieldConstraintValidation.java");

            file.doubleClick();
            // Get opened editor
            SWTBotEclipseEditor editor = bot.editorByTitle("FieldConstraintValidation.java").toTextEditor();
            editor.navigateTo(9, 0);
            editor.click(7, 22);

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
            editor.close();

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

            // Open Project Explorer
            bot.viewByTitle("Project Explorer").show();

            // Open FieldConstraintValidation.java
            SWTBotTreeItem file = bot.tree().expandNode("liberty.maven.test.app (in liberty-maven-test-app)").expandNode("src").expandNode("main").expandNode("java").expandNode("test").expandNode("maven").expandNode("liberty").expandNode("web").expandNode("app").getNode("FieldConstraintValidation.java");

            file.doubleClick();

            // Get opened editor
            SWTBotEclipseEditor editor = bot.editorByTitle("FieldConstraintValidation.java").toTextEditor();

            editor.click(7, 22);

            bot.sleep(5000);

            //Verify diagnostic exists

            IEditorPart editorPart = editor.getReference().getEditor(false);

            IFile workspaceFile = editorPart.getEditorInput().getAdapter(IFile.class);

            if (workspaceFile == null) {
                fail("Unable to obtain IFile from editor input");
            }

            IMarker[] markers = workspaceFile.findMarkers(
                                                          IMarker.PROBLEM,
                                                          true,
                                                          IResource.DEPTH_INFINITE);

            boolean diagnosticFound = false;

            for (IMarker marker : markers) {

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
            editor.close();

        } catch (Exception e) {

            fail(
                 "Unexpected exception was thrown: "
                 + e);
        }
    }
}