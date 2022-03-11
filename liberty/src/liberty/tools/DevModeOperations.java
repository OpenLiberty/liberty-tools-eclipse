package liberty.tools;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.tm.terminal.view.core.TerminalServiceFactory;
import org.eclipse.tm.terminal.view.core.interfaces.ITerminalService;
import org.eclipse.tm.terminal.view.core.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import liberty.tools.utils.Dialog;
import liberty.tools.utils.Project;

/**
 * Provides the implementation of all supported dev mode operations.
 */
public class DevModeOperations {
    // TODO: Dashboard display: Handle the case where the project is configured to be built/run by both
    // Gradle and Maven at the same time.

    // TODO: Establish a Maven/Gradle command precedence (i.e. gradlew -> gradle configured ->
    // gradle_home).

    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void start() {
        IProject project = Project.getSelected();
        String projectName = project.getName();

        try {
            // Get the absolute path to the application project.
            String projectPath = Project.getPath(project);
            if (projectPath == null) {
                throw new Exception("Unable to find the path to the selected project");
            }

            // Prepare the Liberty plugin development mode command.
            String cmd = "";
            if (Project.isMaven(project)) {
                cmd = getMavenCommand("mvn io.openliberty.tools:liberty-maven-plugin:dev -f " + projectPath);
            } else if (Project.isGradle(project)) {
                cmd = getGradleCommand("gradle libertyDev -p=" + projectPath);
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");

                return;
            }

            // Start a terminal and run the application in development mode.
            runCommand(cmd, project.getName());
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the start action on project " + projectName, e);
            return;
        }

    }

    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void startWithParms(String userParms) {
        IProject project = Project.getSelected();
        String projectName = project.getName();

        try {
            // Get the absolute path to the application project.
            String projectPath = Project.getPath(project);
            if (projectPath == null) {
                throw new Exception("Unable to find the path to the selected project");
            }

            // Prepare the Liberty plugin development mode command.
            String cmd = "";
            if (Project.isMaven(project)) {
                cmd = getMavenCommand("mvn io.openliberty.tools:liberty-maven-plugin:dev " + userParms + " -f " + projectPath);
            } else if (Project.isGradle(project)) {
                cmd = getGradleCommand("gradle libertyDev " + userParms + " -p=" + projectPath);
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");
                return;
            }

            // Start a terminal and run the application in development mode.
            runCommand(cmd, project.getName());
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the start... action on project " + projectName,
                    e);
            return;
        }
    }

    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void startInContainer() {
        IProject project = Project.getSelected();
        String projectName = project.getName();

        try {
            // Get the absolute path to the application project.
            String projectPath = Project.getPath(project);
            if (projectPath == null) {
                throw new Exception("Unable to find the path to the selected project");
            }

            // Prepare the Liberty plugin container development mode command.
            String cmd = "";
            if (Project.isMaven(project)) {
                cmd = getMavenCommand("io.openliberty.tools:liberty-maven-plugin:devc -f " + projectPath);
            } else if (Project.isGradle(project)) {
                cmd = getGradleCommand("gradle libertyDevc -p=" + projectPath);
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");
            }

            // Start a terminal and run the application in development mode.
            runCommand(cmd, project.getName());
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails(
                    "An error was detected while performing the start in container action on project " + projectName, e);
            return;
        }
    }

    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void stop() {
        IProject project = Project.getSelected();
        String projectName = project.getName();

        try {
            String cmd = "q";
            runCommand(cmd, projectName);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the stop action.", e);
            return;
        }
    }

    /**
     * Runs the tests provided by the application.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void runTests() {
        IProject project = Project.getSelected();
        String projectName = project.getName();
        String cmd = " ";
        try {
            runCommand(cmd, projectName);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the run tests action.", e);
            return;
        }
    }

    /**
     * Open Maven integration test report.
     */
    public void openMavenIntegrationTestReport() {
        IProject project = Project.getSelected();
        String projectName = project.getName();

        try {
            // Get the absolute path to the application project.
            String projectPath = Project.getPath(project);
            if (projectPath == null) {
                throw new Exception("Unable to find the path to the selected project");
            }

            // Get the path to the test report.
            Path path = Paths.get(projectPath, "target", "site", "failsafe-report.html");
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Integration test results are not available. Be sure to run the tests first.");
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserId = "maven.failsafe.integration.test.results";
            String name = "Maven Failsafe integration test results";
            openTestReport(project.getName(), path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening integration test report for project " + projectName,
                    e);
            return;
        }
    }

    /**
     * Open Maven unit test report.
     */
    public void openMavenUnitTestReport() {
        IProject project = Project.getSelected();
        String projectName = project.getName();

        try {
            // Get the absolute path to the application project.
            String projectPath = Project.getPath(project);
            if (projectPath == null) {
                throw new Exception("Unable to find the path to the selected project");
            }

            // Get the path to the test report.
            Path path = Paths.get(projectPath, "target", "site", "surefire-report.html");
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Unit test results are not available. Be sure to run the tests first.");
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserId = "maven.project.surefire.unit.test.results";
            String name = "Maven Surefire unit test results";
            openTestReport(project.getName(), path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening unit test report for project " + projectName, e);
            return;
        }
    }

    /**
     * Open Gradle test report.
     */
    public void openGradleTestReport() {
        IProject project = Project.getSelected();
        String projectName = project.getName();

        try {
            // Get the absolute path to the application project.
            String projectPath = Project.getPath(project);
            if (projectPath == null) {
                throw new Exception("Unable to find the path to the selected project");
            }

            // Get the path to the test report.
            Path path = getGradleTestReportPath(project, projectPath);
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Test results are not available. Be sure to run the tests first.");
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            String browserId = "gradle.project.test.results";
            String name = "Gradle project test results";
            openTestReport(projectName, path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening test report for project " + projectName, e);
            return;
        }
    }

    /**
     * Opens the specified report in a browser.
     *
     * @param projectName The application project name.
     * @param path The path to the HTML report file.
     * @param browserId The Id to use for the browser display.
     * @param name The name to use for the browser display.
     * @param toolTip The tool tip to use for the browser display.
     * 
     * @throws Exception If an error occurs while displaying the test report.
     */
    public void openTestReport(String projectName, Path path, String browserId, String name, String toolTip) throws Exception {
        URL url = path.toUri().toURL();
        IWorkbenchBrowserSupport bSupport = PlatformUI.getWorkbench().getBrowserSupport();
        IWebBrowser browser = null;
        if (bSupport.isInternalWebBrowserAvailable()) {
            browser = bSupport.createBrowser(IWorkbenchBrowserSupport.AS_EDITOR | IWorkbenchBrowserSupport.LOCATION_BAR
                    | IWorkbenchBrowserSupport.NAVIGATION_BAR | IWorkbenchBrowserSupport.STATUS, browserId, name, toolTip);
        } else {
            browser = bSupport.createBrowser(browserId);
        }

        browser.openURL(url);
    }

    /**
     * Runs the specified command on a terminal.
     * 
     * @param cmd The command to run.
     * @param projectName The name of the project currently being processed.
     * 
     * @throws Exception If an error occurs while running the specified command.
     */
    public void runCommand(String cmd, String projectName) throws Exception {
        List<String> envs = new ArrayList<String>(1);
        envs.add("JAVA_HOME=" + getJavaInstallHome());
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ITerminalsConnectorConstants.PROP_TITLE, projectName);
        properties.put(ITerminalsConnectorConstants.PROP_ENCODING, "UTF-8");
        properties.put(ITerminalsConnectorConstants.PROP_DELEGATE_ID, "org.eclipse.tm.terminal.connector.local.launcher.local");
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ARGS, cmd);
        properties.put(ITerminalsConnectorConstants.PROP_DATA, projectName);
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT, envs.toArray(new String[envs.size()]));
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_MERGE_ENVIRONMENT, Boolean.TRUE);
        properties.put(ITerminalsConnectorConstants.PROP_FORCE_NEW, Boolean.TRUE);
        properties.put(ITerminalsConnectorConstants.PROP_DATA_NO_RECONNECT, Boolean.TRUE);

        ITerminalService ts = TerminalServiceFactory.getService();
        ts.openConsole(properties, null);
    }

    /**
     * Returns the home path to the Java installation.
     * 
     * @return The home path to the Java installation, or null if not found.
     */
    private String getJavaInstallHome() {
        String javaHome = null;
        // TODO: 1. Find the eclipse->java configured install path

        // 2. Check for associated system properties.
        if (javaHome == null) {
            javaHome = System.getProperty("java.home");
        }

        // 3. Check for associated environment property.
        if (javaHome == null) {
            javaHome = System.getenv("JAVA_HOME");
        }

        return javaHome;
    }

    /**
     * Returns the home path to the Maven installation.
     * 
     * @return The home path to the Maven installation, or null if not found.
     */
    private String getMavenInstallHome() {
        String mvnInstall = null;
        // TODO: 1. Find the eclipse->maven configured install path

        // 2. Check for associated environment property.
        if (mvnInstall == null) {
            mvnInstall = System.getenv("MAVEN_HOME");

            if (mvnInstall == null) {
                mvnInstall = System.getenv("M2_MAVEN");
            }
        }

        return mvnInstall;
    }

    /**
     * Returns the home path to the Gradle installation.
     * 
     * @return The home path to the Gradle installation, or null if not found.
     */
    private String getGradleInstallHome() {
        // TODO: 1. Find the eclipse->gradle configured install path.

        // 2. Check for associated environment property.
        String gradleInstall = System.getenv("GRADLE_HOME");

        return gradleInstall;
    }

    /**
     * Returns the full Maven command to run on the terminal.
     * 
     * @param baseCommand The base development mode command.
     * 
     * @return The full Maven command to run on the terminal.
     */
    private String getMavenCommand(String baseCommand) {
        StringBuilder cmd = new StringBuilder(baseCommand);
        String mvnInstallPath = getMavenInstallHome();
        if (mvnInstallPath != null) {
            return Paths.get(mvnInstallPath, "bin", cmd.toString()).toString();
        }

        return cmd.insert(0, "mvn ").toString();
    }

    /**
     * Returns the full Gradle command to run on the terminal.
     * 
     * @param baseCommand The base development mode command.
     * 
     * @return The full Gradle command to run on the terminal.
     */
    private String getGradleCommand(String baseCommand) {
        StringBuilder cmd = new StringBuilder(baseCommand);
        String gradleInstallPath = getGradleInstallHome();
        if (gradleInstallPath != null) {
            return Paths.get(gradleInstallPath, "bin", cmd.toString()).toString();
        }

        return cmd.insert(0, "gradle ").toString();
    }

    /**
     * Returns the home path to the HTML test report.
     * 
     * @return The HTML default located in the configured in the build file or the default location.
     */
    private Path getGradleTestReportPath(IProject project, String projectPath) {
        // TODO: Look for custom dir entry in build.gradle:
        // "test.reports.html.destination". Need to handle a value like this:
        // reports.html.destination = file("$buildDir/edsTestReports/teststuff")
        // Notice the use of a variable: $buildDir.

        // If a custom path was not defined, use default value.
        Path path = Paths.get(projectPath, "build", "reports", "tests", "test", "index.html");

        return path;
    }
}