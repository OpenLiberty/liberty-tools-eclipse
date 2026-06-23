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
 * Tests Microprofile functionality within Liberty Tools for Eclipse
 */
public class LibertyPluginSWTBotMicroProfileTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Application name - using Maven app
     */
    public static final String MAVEN_APP_NAME = "liberty.maven.test.app";

    /**
     * Test app relative path.
     */
    public static final Path mavenProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");

    public static String mpClassSnippet = "import org.eclipse.microprofile.health.Liveness;\n"
                                          + "\n"
                                          + "import jakarta.enterprise.context.ApplicationScoped;\n"
                                          + "\n"
                                          + "@Liveness\n"
                                          + "@ApplicationScoped";

    public static ArrayList<String> projectPaths = new ArrayList<String>();

    /**
     * Expected type-ahead options when at highest level in class.
     */
    public static String[] mptypeAheadOptions_classLevel = new String[] { "mpliveness", "mpreadiness", "mpnrc" };

    /**
     * Expected type-ahead options within REST class
     */
    public static String[] mptypeAheadOptions_inClass = new String[] { "mpliveness", "mpreadiness", "mpnrc" };
    public static String mpDiagnostics = "implementing the HealthCheck interface should use the @Liveness, @Readiness or @Startup annotation. [HealthAnnotationMissing]";

    public static String[] mpSnippet_quickFixes = new String[] { "Insert @Liveness" };

    public static String contentForMpDiagnostics = "package test.rest;\n"
                                                   + "\n"
                                                   + "import org.eclipse.microprofile.health.HealthCheck;\n"
                                                   + "import org.eclipse.microprofile.health.HealthCheckResponse;\n"
                                                   + "\n"
                                                   + "import jakarta.enterprise.context.ApplicationScoped;\n"
                                                   + "\n"
                                                   + "@ApplicationScoped\n"
                                                   + "public class RestApplicationTest implements HealthCheck {\n"
                                                   + "\n"
                                                   + " @Override\n"
                                                   + " public HealthCheckResponse call() {\n"
                                                   + "         return HealthCheckResponse.named(FieldConstraintValidation.class.getSimpleName()).withData(\"live\",true).up().build();\n"
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
     * Verify the class level snippets are available for Microprofile
     */
    @Test
    public void testClassLevelSnippetsMpProperties() {

        try {

            SWTBotEclipseEditor mpEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                  "src/main/java/test/maven/liberty/web/app",
                                                                                  "RestTestClass.java");
            // Clear existing content
            String fileContent = mpEditor.getText();

            if (fileContent != null && fileContent.length() > 0) {
                mpEditor.selectRange(0, 0, fileContent.length());
                mpEditor.insertText("");
            }

            bot.sleep(1000);
            SWTBotPluginOperations.clearContentInEditor(mpEditor);
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
            SWTBotPluginOperations.clearContentInEditor(mpEditor);
            mpEditor.close();
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

        try {

            SWTBotEclipseEditor mpEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                  "src/main/java/test/maven/liberty/web/app",
                                                                                  "RestTestClass.java");
            bot.sleep(3000);

            mpEditor.show();
            mpEditor.setFocus();

            bot.sleep(1000);

            SWTBotPluginOperations.clearContentInEditor(mpEditor);

            bot.sleep(1000);

            mpEditor.autoCompleteProposal("mp", "mpliveness");

            String contentAfterSnippet = mpEditor.getText();
            System.out.println("INFO : contentAfterSnippet" + contentAfterSnippet);
            boolean contentMatches = contentAfterSnippet.contains(mpClassSnippet);
            System.out.println("INFO : contentMatches" + contentMatches);

            assertTrue(contentAfterSnippet.contains(mpClassSnippet), "Error while adding mpliveness snippet");
            SWTBotPluginOperations.clearContentInEditor(mpEditor);
            mpEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify quick fixes for invalid mp properties
     */
    @Test
    public void testForVerifyQuickFixesMpProperties() {

        try {
            SWTBotEclipseEditor mpEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                  "src/main/java/test/maven/liberty/web/app",
                                                                                  "RestTestClass.java");
            bot.sleep(1000);

            mpEditor.show();
            mpEditor.setFocus();
            SWTBotPluginOperations.clearContentInEditor(mpEditor);
            mpEditor.insertText(contentForMpDiagnostics);
            mpEditor.navigateTo(9, 0);
            mpEditor.click(7, 22);

            bot.sleep(5000);
            // Get quick-fix list
            List<String> quickFixes = SWTBotPluginOperations.getQuickFixList(bot, "RestTestClass.java");
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
            SWTBotPluginOperations.clearContentInEditor(mpEditor);
            mpEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify diagnostics for invalid mp property
     */
    @Test
    public void testDiagnosticsForMpProperties() {

        try {

            SWTBotEclipseEditor mpEditor = SWTBotPluginOperations.openFileForTest(bot, "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                  "src/main/java/test/maven/liberty/web/app",
                                                                                  "RestTestClass.java");
            bot.sleep(1000);

            mpEditor.show();
            mpEditor.setFocus();
            SWTBotPluginOperations.clearContentInEditor(mpEditor);
            mpEditor.insertText(contentForMpDiagnostics);
            mpEditor.navigateTo(9, 0);
            mpEditor.click(9, 22);

            bot.sleep(5000);

            //Verify diagnostic exists

            IEditorPart mpEditorPart = mpEditor.getReference().getEditor(false);

            IFile currentWorkspaceFile = mpEditorPart.getEditorInput().getAdapter(IFile.class);

            if (currentWorkspaceFile == null) {
                fail("Unable to obtain IFile from editor input");
            }

            IMarker[] mpMarkers = currentWorkspaceFile.findMarkers(
                                                                   IMarker.PROBLEM,
                                                                   true,
                                                                   IResource.DEPTH_INFINITE);

            boolean diagnosticFound = false;

            for (IMarker markerItem : mpMarkers) {

                String message = markerItem.getAttribute(IMarker.MESSAGE, "");

                System.out.println("INFO : Diagnostic found : " + message);

                if (message.contains(mpDiagnostics)) {
                    diagnosticFound = true;
                    break;
                }
            }

            assertTrue(
                       diagnosticFound,
                       "Expected diagnostic was not found");
            SWTBotPluginOperations.clearContentInEditor(mpEditor);
            mpEditor.close();
        } catch (Exception e) {

            fail(
                 "Unexpected exception was thrown: "
                 + e);
        }
    }
}