/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
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

import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.deleteFile;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.isInternalBrowserSupportAvailable;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.isTextInFile;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.onWindows;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.validateApplicationOutcome;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.validateTestReportExists;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.test.it.utils.SWTPluginOperations;
import io.openliberty.tools.eclipse.ui.DashboardView;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginSWTBotGradleTest {

    /**
     * Wokbench bot instance.
     */
    static SWTWorkbenchBot bot;

    /**
     * Dashboard instance.
     */
    static SWTBotView dashboard;

    /**
     * Application name.
     */
    static final String GRADLE_APP_NAME = "liberty-gradle-test-app";

    static String testAppPath;

    /**
     * Expected menu items.
     */
    static String[] gradleMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START, DashboardView.APP_MENU_ACTION_START_PARMS,
            DashboardView.APP_MENU_ACTION_START_IN_CONTAINER, DashboardView.APP_MENU_ACTION_STOP, DashboardView.APP_MENU_ACTION_RUN_TESTS,
            DashboardView.APP_MENU_ACTION_VIEW_GRADLE_TEST_REPORT };
    
    static String appMsg = "Hello! How are you today?";
    static String appURL = "http://localhost:9080/" + GRADLE_APP_NAME + "/servlet";

    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() {
        bot = new SWTWorkbenchBot();
        SWTPluginOperations.closeWelcomePage(bot);
        importGradleApplications();
        initialize();
    }

    /**
     * Cleanup.
     */
    @AfterAll
    public static void cleanup() {
        bot.closeAllEditors();
        bot.closeAllShells();
        bot.resetWorkbench();
    }

    @BeforeEach
    public void beforeEach(TestInfo info) {
        System.out.println("INFO: Test " + info.getDisplayName() + " entry: " + java.time.LocalDateTime.now());
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        System.out.println("INFO: Test " + info.getDisplayName() + " exit: " + java.time.LocalDateTime.now());
    }

    /**
     * Makes sure that some basics elements can be accessed or are present before running the tests. The following are
     * checked:
     * 1. The dashboard can be opened and its content retrieved.
     * 2. The dashboard contains the expected applications.
     * 3. The menu for the expected application contains the required actions.
     */
    public static final void initialize() {

        Path projPath = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME);
        File projectFile = projPath.toFile();
        testAppPath = Paths.get(projectFile.getPath()).toAbsolutePath().toString();
        dashboard = SWTPluginOperations.openDashboardUsingMenu(bot);

        // Check that the dashboard can be opened and its content retrieved.
        List<String> projectList = SWTPluginOperations.getDashboardContent(bot, dashboard);

        // Check that dashboard contains the expected applications.
        boolean foundApp = false;
        for (String project : projectList) {
            if (GRADLE_APP_NAME.equals(project)) {
                foundApp = true;
                break;
            }
        }
        Assertions.assertTrue(foundApp, () -> "The dashboard does not contain expected application: " + GRADLE_APP_NAME);

        // Check that the menu for the expected application contains the required actions.
        List<String> menuItems = SWTPluginOperations.getDashboardItemMenuActions(bot, dashboard, GRADLE_APP_NAME);
        Assertions.assertTrue(menuItems.size() == gradleMenuItems.length, () -> "Gradle application " + GRADLE_APP_NAME
                + " does not contain the expected number of menu items: " + gradleMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(gradleMenuItems)),
                () -> "Gradle application " + GRADLE_APP_NAME + " does not contain the expected menu items: " + gradleMenuItems);
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testStart() {
        // Start dev mode.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, GRADLE_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build", appMsg, appURL);

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, GRADLE_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(GRADLE_APP_NAME, false, testAppPath + "/build", appMsg, appURL);

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testStartWithParms() {
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToTestReport + " was not deleted.");

        // Start dev mode with parms.
        SWTPluginOperations.launchAppMenuStartWithParmsAction(bot, dashboard, GRADLE_APP_NAME, "--hotTests");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build", appMsg, appURL);

        // Validate that the test reports were generated.
        validateTestReportExists(pathToTestReport);

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, GRADLE_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(GRADLE_APP_NAME, false, testAppPath + "/build", appMsg, appURL);

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the "Run Tests" menu action and test report view actions if internal browser support is available.
     */
    @Test
    public void testRunTests() {
        // Delete the test report files before we start this test.
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path pathToTestReport = DevModeOperations.getGradleTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToTestReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "Test report file: " + pathToTestReport + " was not be deleted.");

        // Start dev mode.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, GRADLE_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(GRADLE_APP_NAME, true, testAppPath + "/build", appMsg, appURL);

        // Run Tests.
        SWTPluginOperations.launchAppMenuRunTestsAction(bot, dashboard, GRADLE_APP_NAME);

        // Validate that the reports were generated and the the browser editor was launched.
        validateTestReportExists(pathToTestReport);
        if (isInternalBrowserSupportAvailable()) {
            SWTPluginOperations.launchAppMenuViewGradleTestReportAction(bot, dashboard, GRADLE_APP_NAME);
        }

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, GRADLE_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(GRADLE_APP_NAME, false, testAppPath + "/build", appMsg, appURL);

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the refresh action on the dashboard's toolbar.
     * @throws IOException 
     * @throws InterruptedException 
     */
    @Disabled("Issue 58")
    @Test
    public void testRefresh() throws IOException, InterruptedException {
        // Get the list of entries on the dashboard and verify the expected number found.
        List<String> projectList = SWTPluginOperations.getDashboardContent(bot, dashboard);
        boolean gradleAppFound = false;
        for (String project : projectList) {
            if (GRADLE_APP_NAME.equals(project)) {
                gradleAppFound = true;
            }
        }
        Assertions.assertTrue(gradleAppFound, () -> "The gradle test app was not found.");

        Path original = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME, "build.gradle").toAbsolutePath();
        Path original_backup = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME, "build.gradle_backup").toAbsolutePath();
        Path updated = Paths.get("resources", "files", "apps", "gradle", GRADLE_APP_NAME, "build.gradle").toAbsolutePath();

        try {
            // Move build.gradle files
            Files.copy(original, original_backup, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(updated, original, StandardCopyOption.REPLACE_EXISTING);

            // Refresh
            SWTPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is found.
            projectList = SWTPluginOperations.getDashboardContent(bot, dashboard);
            gradleAppFound = false;
            for (String project : projectList) {
                if (GRADLE_APP_NAME.equals(project)) {
                    gradleAppFound = true;
                }
            }
            Assertions.assertFalse(gradleAppFound, () -> "The gradle test app was found.");

        } finally {

            // Reset build.gradle files
            if (onWindows()) {
                // Windows may hold a lock on the file, so retry a few times
                int count = 0;
                while (true) {
                    try {
                        Files.copy(original_backup, original, StandardCopyOption.REPLACE_EXISTING);
                        Files.delete(original_backup);
                        break;
                    } catch (Exception e) {
                        System.out.println("Waiting for Windows file to delete.........");
                        Thread.sleep(3000);
                        if (++count == 50)
                            throw e;
                    }
                }
            } else {
                Files.copy(original_backup, original, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(original_backup);
            }
            Files.delete(original_backup);

            // Validate that the editor was correctly updated.
            Assertions.assertTrue(isTextInFile(original.toString(), "liberty-gradle-plugin"),
                    "The build.gradle file does not contain the Liberty Gradle plugin");

            // Refresh
            SWTPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is found.
            projectList = SWTPluginOperations.getDashboardContent(bot, dashboard);
            gradleAppFound = false;
            for (String project : projectList) {
                if (GRADLE_APP_NAME.equals(project)) {
                    gradleAppFound = true;
                }
            }
            Assertions.assertTrue(gradleAppFound, () -> "The gradle test app was not found.");

        }
    }

    /**
     * Imports existing Gradle application projects into the workspace.
     */
    public static void importGradleApplications() {
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {

                Path projPath = Paths.get("resources", "applications", "gradle", GRADLE_APP_NAME);
                File project = projPath.toFile();

                try {
                    importProjects(project);
                } catch (Exception e) {
                    e.printStackTrace();
                    Assertions.fail();
                }
            }
        });
    }

    /**
     * Imports the specified list of projects.
     *
     * @param workspaceRoot The workspace root location.
     * @param folders The list of folders containing the projects to install.
     *
     * @throws InterruptedException
     * @throws CoreException
     */
    public static void importProjects(File projectFile) throws InterruptedException, CoreException {
        IPath projectLocation = org.eclipse.core.runtime.Path.fromOSString(Paths.get(projectFile.getPath()).toAbsolutePath().toString());

        // get an IProject instance and create the project
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IProjectDescription projectDescription = workspace.newProjectDescription(GRADLE_APP_NAME);
        projectDescription.setLocation(projectLocation);
        IProject project = workspace.getRoot().getProject(GRADLE_APP_NAME);
        project.create(projectDescription, new NullProgressMonitor());

        // open the project
        project.open(IResource.NONE, new NullProgressMonitor());
    }

    /**
     * Copies gradle build wrapper artifacts to the project.
     */
    public void copyWrapperArtifactsToProject() {
        Path sourceDirPath = Paths.get(Paths.get("").toAbsolutePath().toString(), "resources", "files", "apps", "gradle",
                "liberty-gradle-test-app", "wrapper");

        Path projectPath = Paths.get(Paths.get("").toAbsolutePath().toString(), "resources", "applications", "gradle",
                "liberty-gradle-test-app", "wrapper");
        try {
            Stream<Path> sourceFiles = Files.walk(sourceDirPath);
            Iterator<Path> iterator = sourceFiles.iterator();
            while (iterator.hasNext()) {
                Path sourceFile = iterator.next();
                Path destination = Paths.get(projectPath.toString(), sourceFile.toString().substring(sourceDirPath.toString().length()));
                try {
                    Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING);
                } catch (FileAlreadyExistsException | DirectoryNotEmptyException ee) {
                    // Ignore.
                }
            }
            sourceFiles.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes all wrapper related artifacts.
     */
    public void removeWrapperArtifactsFromProject() {
        Path projectPath = Paths.get(Paths.get("").toAbsolutePath().toString(), "resources", "applications", "gradle",
                "liberty-gradle-test-app", "wrapper");

        Path gradlew = Paths.get(projectPath.toString(), "gradlew");
        boolean gradlewDeleted = deleteFile(gradlew.toFile());
        Assertions.assertTrue(gradlewDeleted, () -> "File: " + gradlew + " was not be deleted.");

        Path gradlecmd = Paths.get(projectPath.toString(), "gradlew.cmd");
        boolean gradlecmdDeleted = deleteFile(gradlecmd.toFile());
        Assertions.assertTrue(gradlecmdDeleted, () -> "File: " + gradlecmd + " was not be deleted.");

        Path gradleDir = Paths.get(projectPath.toString(), "gradle");
        boolean gradleDirDeleted = deleteFile(gradleDir.toFile());
        Assertions.assertTrue(gradleDirDeleted, () -> "File: " + gradleDir + " was not be deleted.");
    }
}
