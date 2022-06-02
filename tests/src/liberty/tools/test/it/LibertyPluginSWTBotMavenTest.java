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
package liberty.tools.test.it;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import liberty.tools.DevModeOperations;
import liberty.tools.test.it.utils.SWTPluginOperations;
import liberty.tools.ui.DashboardView;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginSWTBotMavenTest {

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
    static final String MVN_APP_NAME = "liberty.maven.test.app";

    /**
     * Test app relative path.
     */
    static final Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");

    /**
     * Expected menu items.
     */
    static String[] mvnMenuItems = new String[] { DashboardView.APP_MENU_ACTION_START, DashboardView.APP_MENU_ACTION_START_PARMS,
            DashboardView.APP_MENU_ACTION_START_IN_CONTAINER, DashboardView.APP_MENU_ACTION_STOP, DashboardView.APP_MENU_ACTION_RUN_TESTS,
            DashboardView.APP_MENU_ACTION_VIEW_MVN_IT_REPORT, DashboardView.APP_MENU_ACTION_VIEW_MVN_UT_REPORT };

    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() {
        bot = new SWTWorkbenchBot();
        SWTPluginOperations.closeWelcomePage(bot);
        importMavenApplications();
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

        dashboard = SWTPluginOperations.openDashboardUsingMenu(bot);

        // Check that the dashboard can be opened and its content retrieved.
        String[] dashboardContent = SWTPluginOperations.getDashboardContent(bot, dashboard);

        // Check that dashboard contains the expected applications.
        boolean foundApp = false;
        for (int i = 0; i < dashboardContent.length; i++) {
            if (dashboardContent[i].equals(MVN_APP_NAME)) {
                foundApp = true;
                break;
            }
        }
        Assertions.assertTrue(foundApp, () -> "The dashboard does not contain expected application: " + MVN_APP_NAME);

        // Check that the menu for the expected application contains the required actions.
        List<String> menuItems = SWTPluginOperations.getDashboardItemMenuActions(bot, dashboard, MVN_APP_NAME);
        Assertions.assertTrue(menuItems.size() == mvnMenuItems.length,
                () -> "Maven application " + MVN_APP_NAME + " does not contain the expected number of menu items: " + mvnMenuItems.length);
        Assertions.assertTrue(menuItems.containsAll(Arrays.asList(mvnMenuItems)),
                () -> "Maven application " + MVN_APP_NAME + " does not contain the expected menu items: " + mvnMenuItems);
    }

    /**
     * Tests opening the dashboard using the main toolbar icon.
     */
    @Test
    public void testOpenDashboardWithToolbarIcon() {
        // Close the dashboard view if it is open.
        SWTPluginOperations.closeDashboardView(bot);

        // Open the dashboard view.
        SWTPluginOperations.openDashboardUsingToolbar(bot);
    }

    /**
     * Tests opening the dashboard using the Liberty menu.
     */
    @Test
    public void testOpenDashboardUsingMenu() {
        // Close the dashboard view if it is open.
        SWTPluginOperations.closeDashboardView(bot);

        // Open the dashboard view.
        SWTPluginOperations.openDashboardUsingMenu(bot);
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testStartWithWrapper() {
        String invalidMvnHomePath = "INVALID";
        String originalEnvVariable = System.getenv("MAVEN_HOME");

        // Update the MAVEN_HOME environment variable with an invalid value
        try {
            updateJVMEnvVariableCache("MAVEN_HOME", invalidMvnHomePath);
            String updatedValue = System.getenv("MAVEN_HOME");
            Assertions.assertTrue(updatedValue.equals(invalidMvnHomePath),
                    () -> "The updated value of " + updatedValue + " does not match the expected value of " + invalidMvnHomePath);
            System.out.println("INFO: MAVEN_HOME updated to invalid value: " + updatedValue);
        } catch (Exception e) {
            Assertions.fail("Unable to update the value of environment variable MAVEN_HOME. Error: " + e.getMessage());
        }

        // Call the start action. This is expected to fail because there is an invalid MAVEN_HOME value set.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();
        validateApplicationOutcome(false);
        terminal.close();

        // Add wrapper artifacts to the project.
        copyWrapperArtifactsToProject();

        // Call the start/stop actions. This is expected to work because, currently, the plugin's order of precedence when
        // selecting the maven command gives preference to the mvn wrapper command over the maven command installation
        // pointed to by the MAVEN_HOME environment variable.
        try {
            // Start dev mode.
            SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, MVN_APP_NAME);
            terminal = bot.viewByTitle("Terminal");
            terminal.show();
            validateApplicationOutcome(true);

            // Stop dev mode.
            SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
            terminal.show();
            validateApplicationOutcome(false);
            terminal.close();
        } finally {
            // Update the MAVEN_HOME environment variable with the original value.
            try {
                updateJVMEnvVariableCache("MAVEN_HOME", originalEnvVariable);
                String updatedValue = System.getenv("MAVEN_HOME");
                Assertions.assertTrue(updatedValue.equals(originalEnvVariable),
                        () -> "The updated value of " + updatedValue + " does not match the expected value of " + originalEnvVariable);
                System.out.println("INFO: MAVEN_HOME reset to its original value: " + updatedValue);
            } catch (Exception e) {
                Assertions.fail("Unable to update the value of environment variable MAVEN_HOME. Error: " + e.getMessage());
            }

            // Remove all wrapper artifacts.
            removeWrapperArtifactsFromProject();
        }
    }

    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testStart() {
        // Start dev mode.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(true);

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(false);

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the start with parameters menu action on a dashboard listed application.
     */
    @Test
    public void testStartWithParms() {
        Path pathToITReport = DevModeOperations.getMavenIntegrationTestReportPath(projectPath.toString());
        boolean testReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(testReportDeleted, () -> "File: " + pathToITReport + " was not be deleted.");

        // Start dev mode with parms.
        SWTPluginOperations.launchAppMenuStartWithParmsAction(bot, dashboard, MVN_APP_NAME, "-DhotTests=true");
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(true);

        // Validate that the test reports were generated.
        validateTestReportExists(pathToITReport);

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(false);

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the "Run Tests" menu action and test report view actions if internal browser support is available.
     */
    @Test
    public void testRunTests() {
        // Delete the test report files before we start this test.
        Path pathToITReport = DevModeOperations.getMavenIntegrationTestReportPath(projectPath.toString());
        boolean itReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(itReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        Path pathToUTReport = DevModeOperations.getMavenUnitTestReportPath(projectPath.toString());
        boolean utReportDeleted = deleteFile(pathToITReport.toFile());
        Assertions.assertTrue(utReportDeleted, () -> "Test report file: " + pathToITReport + " was not be deleted.");

        // Start dev mode.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, MVN_APP_NAME);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        terminal.show();

        // Validate application is up and running.
        validateApplicationOutcome(true);

        // Run Tests.
        SWTPluginOperations.launchAppMenuRunTestsAction(bot, dashboard, MVN_APP_NAME);

        // Validate that the reports were generated and the the browser editor was launched.
        validateTestReportExists(pathToITReport);
        if (isInternalBrowserSupportAvailable()) {
            SWTPluginOperations.launchAppMenuViewMavenITReportAction(bot, dashboard, MVN_APP_NAME);
        }

        validateTestReportExists(pathToUTReport);
        if (isInternalBrowserSupportAvailable()) {
            SWTPluginOperations.launchAppMenuViewMavenUTReportAction(bot, dashboard, MVN_APP_NAME);
        }

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        validateApplicationOutcome(false);

        // Close the terminal.
        terminal.close();
    }

    /**
     * Tests the refresh action on the dashboard's toolbar.
     */
    @Test
    public void testRefresh() {
        final String fileName = "pom.xml";
        // Get the list of entries on the dashboard and verify the expected number found.
        String[] dashboardContent = SWTPluginOperations.getDashboardContent(bot, dashboard);
        Assertions.assertTrue(dashboardContent.length == 1, () -> "The dashboard did not display the expected number of applications: 1");

        String originalContent = SWTPluginOperations.getAppFileContent(bot, "Project Explorer", MVN_APP_NAME, fileName);
        Path noOLPluginPom = Paths.get("resources", "files", "apps", "maven", "liberty-maven-test-app", "pom.xml");
        String pomEditorTitle = Paths.get(MVN_APP_NAME, fileName).toString();

        try {
            // Modify the application metadata to make it not capable of using Liberty's dev mode.
            StringBuilder newPomContent = new StringBuilder();
            try {

                BufferedReader br = new BufferedReader(new FileReader(noOLPluginPom.toString()));
                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null) {
                    newPomContent.append(sCurrentLine).append(System.lineSeparator());
                }
            } catch (IOException e) {
                Assertions.fail("Error while reading file: " + noOLPluginPom.toString() + "Error: " + e.getMessage());
            }

            SWTPluginOperations.setEditorText(bot, pomEditorTitle, newPomContent.toString());

            // Validate that the editor was correctly updated.
            validateEditorContent(pomEditorTitle, "liberty-maven-plugin", "</project>", false);

            // Refresh
            SWTPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is found.
            dashboardContent = SWTPluginOperations.getDashboardContent(bot, dashboard);
            Assertions.assertTrue(dashboardContent.length == 0,
                    () -> "The dashboard did not display the expected number of applications: 0");

        } finally {
            // Update the application metadata to make it capable of using Liberty's dev mode.
            SWTPluginOperations.setEditorText(bot, pomEditorTitle, originalContent);

            // Validate that the editor was correctly updated.
            validateEditorContent(pomEditorTitle, "liberty-maven-plugin", null, true);

            // Refresh
            SWTPluginOperations.refreshDashboard(bot);

            // Get the list of entries on the dashboard and verify the expected number is found.
            dashboardContent = SWTPluginOperations.getDashboardContent(bot, dashboard);
            Assertions.assertTrue(dashboardContent.length == 1,
                    () -> "The dashboard did not display the expected number of applications: 1");
        }
    }

    /**
     * Imports existing Maven application projects into the workspace.
     */
    public static void importMavenApplications() {
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
                ArrayList<String> projectPaths = new ArrayList<String>();
                projectPaths.add(projectPath.toString());

                try {
                    importProjects(workspaceRoot, projectPaths);
                } catch (Exception e) {
                    e.printStackTrace();
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
    public static void importProjects(File workspaceRoot, List<String> folders) throws InterruptedException, CoreException {
        // Get the list of projects to install.
        MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
        LocalProjectScanner lps = new LocalProjectScanner(workspaceRoot, folders, false, modelManager);
        lps.run(new NullProgressMonitor());
        List<MavenProjectInfo> projects = lps.getProjects();

        // Import the projects.
        ProjectImportConfiguration projectImportConfig = new ProjectImportConfiguration();
        IProjectConfigurationManager projectConfigurationManager = MavenPlugin.getProjectConfigurationManager();
        projectConfigurationManager.importProjects(projects, projectImportConfig, new NullProgressMonitor());
    }

    /**
     * Validates that the deployed application is active.
     *
     * @param expectSuccess True if the validation is expected to be successful. False, otherwise.
     */
    private void validateApplicationOutcome(boolean expectSuccess) {
        String expectedMvnAppResp = "Hello! How are you today?";
        String appUrl = "http://localhost:9080/liberty.maven.test.app/servlet";
        int retryCountLimit = 20;
        int reryIntervalSecs = 3;
        int retryCount = 0;

        while (retryCount < retryCountLimit) {
            retryCount++;
            int status = 0;
            try {
                URL url = new URL(appUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");

                // Possible error: java.net.ConnectException: Connection refused
                con.connect();
                status = con.getResponseCode();

                if (expectSuccess) {
                    if (status != HttpURLConnection.HTTP_OK) {
                        Thread.sleep(reryIntervalSecs * 1000);
                        con.disconnect();
                        continue;
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    String responseLine = "";
                    StringBuffer content = new StringBuffer();
                    while ((responseLine = br.readLine()) != null) {
                        content.append(responseLine).append(System.lineSeparator());
                    }

                    if (!(content.toString().contains(expectedMvnAppResp))) {
                        Thread.sleep(reryIntervalSecs * 1000);
                        con.disconnect();
                        continue;
                    }

                    return;
                } else {
                    if (status == HttpURLConnection.HTTP_OK) {
                        Thread.sleep(reryIntervalSecs * 1000);
                        con.disconnect();
                        continue;
                    }

                    return;
                }
            } catch (Exception e) {
                if (expectSuccess) {
                    System.out.println(
                            "INFO: Retrying application connection: Response code: " + status + ". Error message: " + e.getMessage());
                    try {
                        Thread.sleep(reryIntervalSecs * 1000);
                    } catch (Exception ee) {
                        ee.printStackTrace(System.out);
                    }
                    continue;
                }

                return;
            }
        }

        // If we are here, the expected outcome was not found.
        Assertions.fail("Timed out while waiting for application under URL: " + appUrl + " to become available.");
    }

    /**
     * Validates that the test report represented by the input path exists.
     *
     * @param pathToTestReport The path to the report.
     */
    public void validateTestReportExists(Path pathToTestReport) {
        int retryCountLimit = 50;
        int reryIntervalSecs = 1;
        int retryCount = 0;

        while (retryCount < retryCountLimit) {
            retryCount++;

            boolean fileExists = fileExists(pathToTestReport.toAbsolutePath());
            if (!fileExists) {
                try {
                    Thread.sleep(reryIntervalSecs * 1000);
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    continue;
                }
                continue;
            }

            return;
        }

        // If we are here, the expected outcome was not found.
        Assertions.fail("Timed out while waiting for test report: " + pathToTestReport + " to become available.");
    }

    /**
     * Validates the content of an editor based on the input validation text.
     *
     * @param editorTitle The editor title of the editor to check.
     * @param validationText The text to use for validation.
     * @param contains The validation type. If true, the code checks that the editor contains the validation text. If false,
     *        the code checks that the editor does not contain the validation text.
     * @param eofMarker A unique string that represents the EOF. It is used when 'contains' is set to false to validate that
     *        the entire file was read. This parameter is a no-op when 'contains' is set to true.
     */
    public void validateEditorContent(String editorTitle, String validationText, String eofMarker, boolean contains) {
        int retryCountLimit = 20;
        int reryIntervalSecs = 1;
        int retryCount = 0;
        String editorContent = "";
        while (retryCount < retryCountLimit) {
            retryCount++;
            editorContent = SWTPluginOperations.getEditorText(bot, editorTitle);

            if ((!contains && (!editorContent.contains(eofMarker) || editorContent.contains(validationText)))
                    || (contains && !editorContent.contains(validationText))) {
                try {
                    Thread.sleep(reryIntervalSecs * 1000);
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                    continue;
                }
                continue;
            }

            return;
        }

        // If we are here, the expected outcome was not found.
        Assertions.fail("Timed out while waiting for the correct editor content to become available. Editor content:\n" + editorContent);

    }

    /**
     * Returns true if the Eclipse instance supports internal browsers. False, otherwise.
     *
     * @return True if the Eclipse instance supports internal browsers. False, otherwise.
     */
    public boolean isInternalBrowserSupportAvailable() {
        final String availableKey = "available";
        final Map<String, Boolean> results = new HashMap<String, Boolean>();

        Display.getDefault().syncExec(new Runnable() {
            @Override
            public void run() {
                IWorkbenchBrowserSupport bSupport = PlatformUI.getWorkbench().getBrowserSupport();
                if (bSupport.isInternalWebBrowserAvailable()) {
                    results.put(availableKey, Boolean.TRUE);
                } else {
                    results.put(availableKey, Boolean.FALSE);
                }
            }
        });

        return results.get(availableKey);
    }

    /**
     * Returns true if the file identified by the input path exists. False, otherwise.
     *
     * @param path The file's path.
     *
     * @return True if the file identified by the input path exists. False, otherwise.
     */
    public boolean fileExists(Path filePath) {
        File f = new File(filePath.toString());
        boolean exists = f.exists();

        return exists;
    }

    /**
     * Deletes file identified by the input path. If the file is a directory, it must be empty.
     *
     * @param path The file's path.
     *
     * @return Returns true if the file identified by the input path was deleted. False, otherwise.
     */
    public boolean deleteFile(File file) {
        boolean deleted = true;

        if (file.exists()) {
            if (!file.isDirectory()) {
                deleted = file.delete();
            } else {
                deleted = deleteDirectory(file);
            }
        }

        return deleted;
    }

    /**
     * Recursively deletes the input file directory.
     *
     * @param filePath The directory path.
     *
     * @return
     */
    private boolean deleteDirectory(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                deleteDirectory(files[i]);
            }
        }
        return file.delete();
    }

    /**
     * Adds or updates the JVM copy of the environment variables using the key and value inputs.
     *
     * @param key The environment variable to add or update.
     * @param value The value associated with the input key.
     *
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void updateJVMEnvVariableCache(String key, String value) throws Exception {
        Map<String, String> jvmEnvVars = null;
        if (onWindows()) {
            Class<?> pec = Class.forName("java.lang.ProcessEnvironment");
            Field field = pec.getDeclaredField("theCaseInsensitiveEnvironment");
            field.setAccessible(true);
            jvmEnvVars = (Map<String, String>) field.get(null);
        } else {
            Map<String, String> envVariables = System.getenv();
            Field field = envVariables.getClass().getDeclaredField("m");
            field.setAccessible(true);
            jvmEnvVars = ((Map<String, String>) field.get(envVariables));
        }

        jvmEnvVars.put(key, value);
    }

    /**
     * Returns true if the current process is running on a windows environment. False, otherwise.
     *
     * @return True if the current process is running on a windows environment. False, otherwise.
     */
    private boolean onWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    /**
     * Copies maven build wrapper artifacts to the project.
     */
    public void copyWrapperArtifactsToProject() {
        Path sourceDirPath = Paths.get(Paths.get("").toAbsolutePath().toString(), "resources", "files", "apps", "maven",
                "liberty-maven-test-app", "wrapper");
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
        Path mvnw = Paths.get(projectPath.toString(), "mvnw");
        boolean mvnwDeleted = deleteFile(mvnw.toFile());
        Assertions.assertTrue(mvnwDeleted, () -> "File: " + mvnw + " was not be deleted.");

        Path mvncmd = Paths.get(projectPath.toString(), "mvnw.cmd");
        boolean mvncmdDeleted = deleteFile(mvncmd.toFile());
        Assertions.assertTrue(mvncmdDeleted, () -> "File: " + mvncmd + " was not be deleted.");

        Path mvnDir = Paths.get(projectPath.toString(), ".mvn");
        boolean mvnDirDeleted = deleteFile(mvnDir.toFile());
        Assertions.assertTrue(mvnDirDeleted, () -> "File: " + mvnDir + " was not be deleted.");
    }
}
