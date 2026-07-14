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
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;

/**
 * Tests server.xml functionality with Liberty Config Language Server
 */
public class LibertyPluginSWTBotServerXmlTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Test app relative path.
     */
    public static final Path mavenProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");
    public static ArrayList<String> projectPaths = new ArrayList<String>();

    /**
     * Expected server.xml elements
     */
    public static String[] serverXmlElements = new String[] {
                                                              "keyStore", "logging", "ssl", "httpEndpoint", "applicationManager",
                                                              "featureManager", "application", "dataSource"
    };

    public static String[] serverXmlPlatformElements = new String[] { "jakartaee-10.0", "jakartaee-11.0", "jakartaee-8.0", "jakartaee-9.1", "javaee-6.0", "microProfile-3.2",
                                                                      "microProfile-3.0", "microProfile-4.0" };
    public static String[] serverXmlFeatureElements = new String[] { "acmeCA-2.0", "appAuthentication", "appAuthentication-2.0", "appSecurity-4.0", "jakartaee-10.0",
                                                                     "jakartaeeClient-9.1", "beanValidation-2.0", "mpRestClient-1.0" };
    /**
     * Expected logging attributes
     */
    public static String[] loggingAttributes = new String[] {
                                                              "consoleLogLevel", "copySystemStreams", "isoDateFormat", "logDirectory",
                                                              "maxFileSize", "maxFiles", "messageFormat", "messageSource", "traceFormat",
                                                              "traceSpecification"
    };

    /**
     * Expected features
     */
    public static String[] expectedFeatures = new String[] { "servlet", "jsp", "jaxrs", "cdi", "ejb", "jpa", "jdbc", "jsonp", "jsonb"
    };

    public static String serverXmlPropertiesContent = "<logging></logging>";
    public static String versionlessFeature = "<feature>servlet-3.1</feature>\n"
                                              + "        <platform>jakartaee</platform>";
    public static String serverxmlDiagnostics = "not a valid value of union type 'booleanType'.";

    public static String[] serverxml_quickFixes = new String[] { " Replace value with 'true'", "Replace value with 'true'" };

    public static String inValidFeatureDiagnostcs = "The feature \"invalid-feature-0.7\" does not exist. [incorrect_feature]";
    public static String duplicateFeatureDiagnostcs = "Only one version of a feature may be specified.";
    public static String emptyPlatformWarning = "An empty value for platform is not valid. Specify a valid platform or remove the platform element. [incorrect_platform]";
    public static String emptyFeatureWarning = " An empty value for feature is not valid. Specify a valid feature or remove the feature element.";
    public static String inValidPlatformDiagnostcs = "does not exist";

    SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                 "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                 "src/main/liberty/config",
                                                                                 "server.xml");

    /**
     * Setup.
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
     * Verify the type ahead options are available for server.xml file
     */
    @Test
    public void testTypeAheadSuggestionServerxml() {

        try {

            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            // Position cursor inside server tag
            serverXmlEditor.navigateTo(14, 4);

            bot.sleep(2000);
            serverXmlEditor.insertText("\n");

            // Get completion list
            List<String> typeAheadOptions = SWTBotPluginOperations.getTypeAheadList(bot, "server.xml", "", 14, 4);
            System.out.println("INFO: Type-ahead options found = " + Arrays.toString(typeAheadOptions.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : serverXmlElements) {
                if (!typeAheadOptions.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options: server.xml " + Arrays.toString(missingOptions.toArray()));
            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify "logging" element and check attributes
     */
    @Test
    public void testLoggingElementAttributeCompletion() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            // Position cursor inside server tag
            serverXmlEditor.navigateTo(14, 4);

            serverXmlEditor.insertText("\n");
            serverXmlEditor.autoCompleteProposal("log", "logging");

            bot.sleep(3000);

            // Verify content was added
            String content = serverXmlEditor.getText();
            assertTrue(content.contains(serverXmlPropertiesContent), "Property is not added correctly - server.xml");
            SWTBotPluginOperations.clearContentInEditor(serverXmlEditor);
            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify empty feature tag diagnostic
     */
    @Test
    public void testEmptyfeatureDiagnostic() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");

            serverXmlEditor.navigateTo(15, 8);

            bot.sleep(2000);
            serverXmlEditor.insertText("\n");
            serverXmlEditor.insertText("<feature></feature>");

            bot.sleep(3000);

            // Check for warning diagnostic
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("liberty.maven.test.app");
            IFile serverXmlFile = project.getFile("src/main/liberty/config/server.xml");

            IMarker[] markers = serverXmlFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

            boolean warningFound = false;
            for (IMarker marker : markers) {
                String message = marker.getAttribute(IMarker.MESSAGE, "");
                int severity = marker.getAttribute(IMarker.SEVERITY, -1);
                System.out.println("INFO: Marker - " + message + " (severity: " + severity + ")");
                if (message.contains(emptyFeatureWarning)) {
                    warningFound = true;
                    break;
                }
            }

            assertTrue(warningFound, "Should have warning for empty feature tag");

            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify feature value completion
     */
    @Test
    public void testfeatureValueCompletion() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            serverXmlEditor.navigateTo(15, 8);

            bot.sleep(2000);
            serverXmlEditor.insertText("\n");
            serverXmlEditor.insertText("<feature></feature>");

            bot.sleep(3000);
            // Get platform value completions
            List<String> platforms = SWTBotPluginOperations.getTypeAheadList(bot, "server.xml", "", 15, 17);
            System.out.println("INFO: Available feature values = " + Arrays.toString(platforms.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : serverXmlFeatureElements) {
                if (!platforms.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options for feature: server.xml" + Arrays.toString(missingOptions.toArray()));

            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify diagnostics - invalid feature
     */
    @Test
    public void testInvalidFeatureDiagnostic() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            serverXmlEditor.navigateTo(15, 8);
            bot.sleep(2000);
            serverXmlEditor.insertText("\n");
            serverXmlEditor.insertText("<feature>invalid-feature-0.7</feature>");

            bot.sleep(3000);
            // Check for duplicate diagnostic
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("liberty.maven.test.app");
            IFile serverXmlFile = project.getFile("src/main/liberty/config/server.xml");

            IMarker[] markers = serverXmlFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

            boolean diagnosticInvalid = false;
            for (IMarker marker : markers) {
                String message = marker.getAttribute(IMarker.MESSAGE, "");
                System.out.println("INFO: Diagnostic: " + message);
                if (message.contains(inValidFeatureDiagnostcs)) {
                    diagnosticInvalid = true;
                    break;
                }
            }

            assertTrue(diagnosticInvalid, "Should have diagnostic for invalid feature");
            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify duplicate feature diagnostic
     */
    @Test
    public void testDuplicateFeatureDiagnostic() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            serverXmlEditor.navigateTo(15, 8);

            bot.sleep(2000);
            serverXmlEditor.insertText("\n");
            serverXmlEditor.insertText("<feature>servlet-3.1</feature>\n"
                                       + "        <feature>servlet</feature>");

            bot.sleep(3000);

            // Check for duplicate diagnostic
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("liberty.maven.test.app");
            IFile serverXmlFile = project.getFile("src/main/liberty/config/server.xml");

            IMarker[] markers = serverXmlFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

            boolean diagnosticDuplicate = false;
            for (IMarker marker : markers) {
                String message = marker.getAttribute(IMarker.MESSAGE, "");
                System.out.println("INFO: Diagnostic: " + message);
                if (message.contains(duplicateFeatureDiagnostcs)) {
                    diagnosticDuplicate = true;
                    break;
                }
            }

            assertTrue(diagnosticDuplicate, "Should have diagnostic for duplicate feature");

            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * 
     * Verify quick fixes to support versionless feature
     */
    @Test
    public void testPlatformQuickFixMessage() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            serverXmlEditor.navigateTo(15, 8);

            bot.sleep(2000);
            serverXmlEditor.insertText("\n");
            serverXmlEditor.insertText(versionlessFeature);

            bot.sleep(10000);

            // Check for quick fixes
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("liberty.maven.test.app");
            IFile serverXmlFile = project.getFile("src/main/liberty/config/server.xml");
            IMarker[] markers = serverXmlFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

            boolean correctQuickFixFound = false;
            for (IMarker marker : markers) {
                String message = marker.getAttribute(IMarker.MESSAGE, "");
                System.out.println("message marker " + message);
                if (message.toLowerCase().contains(inValidPlatformDiagnostcs)) {
                    IMarkerResolution[] resolutions = IDE.getMarkerHelpRegistry().getResolutions(marker);
                    for (IMarkerResolution resolution : resolutions) {
                        String label = resolution.getLabel();
                        System.out.println("INFO: Quick fix: " + label);
                        if (label.contains("Replace platform")) {
                            correctQuickFixFound = true;
                            break;
                        }
                    }
                }
            }

            assertTrue(correctQuickFixFound, "Quick fix should say 'Replace platform'");

            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify empty platform tag diagnostic
     */
    @Test
    public void testEmptyPlatformDiagnostic() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            serverXmlEditor.navigateTo(15, 8);

            bot.sleep(2000);
            serverXmlEditor.insertText("\n");
            serverXmlEditor.insertText("<platform></platform>");

            bot.sleep(3000);

            // Check for warning diagnostic
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("liberty.maven.test.app");
            IFile serverXmlFile = project.getFile("src/main/liberty/config/server.xml");

            IMarker[] markers = serverXmlFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

            boolean warningFound = false;
            for (IMarker marker : markers) {
                String message = marker.getAttribute(IMarker.MESSAGE, "");
                int severity = marker.getAttribute(IMarker.SEVERITY, -1);
                System.out.println("INFO: Marker - " + message + " (severity: " + severity + ")");
                if (message.contains(emptyPlatformWarning)) {
                    warningFound = true;
                    break;
                }
            }

            assertTrue(warningFound, "Should have warning for empty platform tag");

            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify platform value completion
     */
    @Test
    public void testPlatformValueCompletion() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            serverXmlEditor.navigateTo(15, 8);

            bot.sleep(2000);
            serverXmlEditor.insertText("\n");
            serverXmlEditor.insertText("<platform></platform>");

            bot.sleep(5000);
            // Get platform value completions
            List<String> platforms = SWTBotPluginOperations.getTypeAheadList(bot, "server.xml", "", 15, 18);
            System.out.println("INFO: Available platform values = " + Arrays.toString(platforms.toArray()));

            boolean allFound = true;
            List<String> missingOptions = new ArrayList<String>();
            for (String option : serverXmlPlatformElements) {
                if (!platforms.contains(option)) {
                    allFound = false;
                    missingOptions.add(option);
                }
            }

            assertTrue(allFound, "Missing type-ahead options for platform: server.xml" + Arrays.toString(missingOptions.toArray()));

            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

    /**
     * Verify diagnostics for server.xml file
     */
    @Test
    public void testDiagnosticsForServerXml() {
        try {
            SWTBotEclipseEditor serverXmlEditor = SWTBotPluginOperations.openFileForTest(bot,
                                                                                         "liberty.maven.test.app (in liberty-maven-test-app)",
                                                                                         "src/main/liberty/config",
                                                                                         "server.xml");
            serverXmlEditor.navigateTo(18, 4);

            bot.sleep(2000);
            serverXmlEditor.insertText("\n");
            serverXmlEditor.insertText(18, 4, "<logging appsWriteJson=\"invalid\"></logging>");
            bot.sleep(3000);

            IEditorPart serverXmlEditorPart = serverXmlEditor.getReference().getEditor(false);
            IFile serverXmlEditorFile = serverXmlEditorPart.getEditorInput().getAdapter(IFile.class);

            if (serverXmlEditorFile == null) {
                fail("Unable to obtain IFile from editor input");
            }

            IMarker[] serverXmlMarkers = serverXmlEditorFile.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);

            boolean diagnosticFound = false;

            for (IMarker markersItem : serverXmlMarkers) {
                String diagnosticsMessage = markersItem.getAttribute(IMarker.MESSAGE, "");
                System.out.println("INFO : Diagnostic found : " + diagnosticsMessage);

                if (diagnosticsMessage.contains(serverxmlDiagnostics)) {
                    diagnosticFound = true;
                    break;
                }
            }

            assertTrue(diagnosticFound, "Expected diagnostic was not found - server.xml file");

            SWTBotPluginOperations.clearContentInEditor(serverXmlEditor);
            serverXmlEditor.close();
        } catch (Exception e) {
            fail("Unexpected exception was thrown: " + e);
        }
    }

}
