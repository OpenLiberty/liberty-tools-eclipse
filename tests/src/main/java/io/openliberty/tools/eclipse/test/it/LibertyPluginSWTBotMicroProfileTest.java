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

import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.contentForMpDiagnostics;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.mpSnippet_quickFixes;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.mpClassSnippet;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.mpDiagnostics;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.projectPaths;
import static io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest.mptypeAheadOptions_classLevel;
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
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.ConstantsForAutomatedTest;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests Microprofile functionality within Liberty Tools for Eclipse
 */
public class LibertyPluginSWTBotMicroProfileTest extends AbstractLibertyPluginSWTBotTest {

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
     * Verify the class level snippets are available for Microprofile
     * 
     */

    @Test
    public void testClassLevelSnippetsMpProperties() {

        try {

            SWTBotEclipseEditor editor = SWTBotPluginOperations.openFileForTest(bot);
            // Clear existing content
            String content = editor.getText();

            if (content != null && content.length() > 0) {
                editor.selectRange(0, 0, content.length());
                editor.insertText("");
            }

            bot.sleep(1000);
            SWTBotPluginOperations.clearContentInEditor(editor);
            // Get type-ahead list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "RestTestClass.java", "", 0, 0);
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : mptypeAheadOptions_classLevel) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: " + Arrays.toString(missingOptions.toArray()));
            SWTBotPluginOperations.clearContentInEditor(editor);
            editor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Tests that Microprofile snippets are available when typing "mpliveness" in a Java file
     * Creates a new Java class, clears content, types "mp", checks for suggestions, and selects "mpliveness"
     **/

    @Test
    public void verifyContentAssistSugForMpClassSnippet() {

        SWTBotEclipseEditor editor = SWTBotPluginOperations.openFileForTest(bot);
        bot.sleep(3000);

        editor.show();
        editor.setFocus();

        bot.sleep(1000);

        SWTBotPluginOperations.clearContentInEditor(editor);

        bot.sleep(1000);

        editor.autoCompleteProposal("mp", "mpliveness");

        String contentAfterSnippet = editor.getText();
        System.out.println("INFO : contentAfterSnippet" + contentAfterSnippet);
        boolean contentMatches = contentAfterSnippet.contains(mpClassSnippet);
        System.out.println("INFO : contentMatches" + contentMatches);

        assertTrue(contentAfterSnippet.contains(mpClassSnippet), "Error while adding mpliveness snippet");
        SWTBotPluginOperations.clearContentInEditor(editor);
        editor.close();
    }

    /**
     * Verify quick fixes
     */
    @Test
    public void testForVerifyQuickFixesMpProperties() {

        try {
            SWTBotEclipseEditor editor = SWTBotPluginOperations.openFileForTest(bot);
            bot.sleep(1000);

            editor.show();
            editor.setFocus();
            SWTBotPluginOperations.clearContentInEditor(editor);
            editor.insertText(contentForMpDiagnostics);
            editor.navigateTo(9, 0);
            editor.click(7, 22);

            bot.sleep(5000);
            // Get quick-fix list
            List<String> quickFixes = SWTBotPluginOperations.getQuickFixList(bot, "RestApplication.java");
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(quickFixes.toArray()));

            boolean allFound = true;
            List<String> missingFixes = new ArrayList<String>();
            for (String fix : mpSnippet_quickFixes) {
                System.out.println("fix-->" + fix);
                if (!quickFixes.contains(fix)) {
                    allFound = false;
                    missingFixes.add(fix);
                }
            }

            assertTrue(allFound, "Missing quick-fixes: " + Arrays.toString(missingFixes.toArray()));
            SWTBotPluginOperations.clearContentInEditor(editor);
            editor.close();

        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify diagnostics
     */

    @Test
    public void testDiagnosticsForMpProperties() {

        try {
            SWTBotEclipseEditor editor = SWTBotPluginOperations.openFileForTest(bot);
            bot.sleep(1000);

            editor.show();
            editor.setFocus();
            SWTBotPluginOperations.clearContentInEditor(editor);
            editor.insertText(contentForMpDiagnostics);
            editor.navigateTo(9, 0);
            editor.click(9, 22);

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

                if (message.contains(mpDiagnostics)) {
                    diagnosticFound = true;
                    break;
                }
            }

            assertTrue(
                      diagnosticFound,
                       "Expected diagnostic was not found");
            SWTBotPluginOperations.clearContentInEditor(editor);
            editor.close();
        } catch (Exception e) {

            fail(
                 "Unexpected exception was thrown: "
                 + e);
        }
    }
}