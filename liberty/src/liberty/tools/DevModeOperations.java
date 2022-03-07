package liberty.tools;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
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

	private boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
	}
	
    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void start() {
        IProject project = Project.getSelected();
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projName);
            return;
        }
        try {
            String cmd = "";
            if (Project.isMaven(project)) {
                if (!Project.isMavenBuildFileValid(project)) {
                    System.out.println("Maven build file on project" + projName + " is not valid..");
                }

                cmd = isWindows() ? "mvn.cmd" : "mvn";

                cmd = Paths.get(getMavenInstallHome(), "bin", cmd).toString();
                cmd = cmd + " io.openliberty.tools:liberty-maven-plugin:dev -f " + projectPath;
                                
            } else if (Project.isGradle(project)) {
                if (!Project.isGradleBuildFileValid(project)) {
                    System.out.println("Build file on project" + projName + " is not valid.");
                }

                cmd += "gradle libertyDev -p=" + projectPath;
                cmd = Paths.get(getGradleInstallHome(), "bin", cmd).toString();
            } else {
                Dialog.displayErrorMessage("Project" + projName + "is not a Gradle or Maven project.");

                return;
            }

            runCommand(cmd);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the start action on project " + projName, e);
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
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projName);
            return;
        }

        try {
            String cmd = "";
            if (Project.isMaven(project)) {
                cmd += "mvn io.openliberty.tools:liberty-maven-plugin:dev " + userParms + " -f " + projectPath;
                cmd = Paths.get(getMavenInstallHome(), "bin", cmd).toString();
            } else if (Project.isGradle(project)) {
                cmd += "gradle libertyDev " + userParms + " -p=" + projectPath;
                cmd = Paths.get(getGradleInstallHome(), "bin", cmd).toString();
            } else {
                Dialog.displayErrorMessage("Project" + projName + "is not a Gradle or Maven project.");
                return;
            }

            runCommand(cmd);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the start... action on project " + projName, e);
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
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projName);
            return;
        }

        try {
            String cmd = "";
            if (Project.isMaven(project)) {
                cmd += "mvn io.openliberty.tools:liberty-maven-plugin:devc -f " + projectPath;
                cmd = Paths.get(getMavenInstallHome(), "bin", cmd).toString();
            } else if (Project.isGradle(project)) {
                cmd += "gradle libertyDevc -p=" + projectPath;
                cmd = Paths.get(getGradleInstallHome(), "bin", cmd).toString();
            } else {
                Dialog.displayErrorMessage("Project" + projName + "is not a Gradle or Maven project.");
            }

            runCommand(cmd);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails(
                    "An error was detected while performing the start in container action on project " + projName, e);
            return;
        }
    }

    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void stop() {
        String cmd = "q";
        try {
            runCommand(cmd);
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
        String cmd = " ";
        try {
            runCommand(cmd);
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
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projName);
            return;
        }

        try {
            String browserId = "maven.failsafe.integration.test.results";
            String name = "Maven Failsafe integration test results";
            Path path = Paths.get(projectPath, "target", "site", "failsafe-report.html");
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Integration test results are not available. Be sure to run the tests first.");
                return;
            }

            openTestReport(project.getName(), path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening integration test report for project " + projName, e);
            return;
        }
    }

    /**
     * Open Maven unit test report.
     */
    public void openMavenUnitTestReport() {
        IProject project = Project.getSelected();
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projName);
            return;
        }

        try {
            String browserId = "maven.project.surefire.unit.test.results";
            String name = "Maven Surefire unit test results";
            Path path = Paths.get(projectPath, "target", "site", "surefire-report.html");
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Unit test results are not available. Be sure to run the tests first.");
                return;
            }

            openTestReport(project.getName(), path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening unit test report for project " + projName, e);
            return;
        }
    }

    /**
     * Open Gradle test report.
     */
    public void openGradleTestReport() {
        IProject project = Project.getSelected();
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + project.getName());
            return;
        }

        try {
            String browserId = "gradle.project.test.results";
            String name = "Gradle project test results";
            Path path = getGradleTestReportPath(project, projectPath);
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Test results are not available. Be sure to run the tests first.");
                return;
            }

            openTestReport(projName, path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening test report for project " + projName, e);
            return;
        }
    }

    /**
     * Opens the specified report in a browser.
     *
     * @param projName The application project name.
     * @param path The path to the HTML report file.
     * @param browserId The Id to use for the browser display.
     * @param name The name to use for the browser display.
     * @param toolTip The tool tip to use for the browser display.
     * 
     * @throws Exception If an error occurs while displaying the test report.
     */
    public void openTestReport(String projName, Path path, String browserId, String name, String toolTip) throws Exception {
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
     * 
     * @throws Exception If an error occurs while running the specified command.
     */
    public void runCommand(String cmd) throws Exception {
        ITerminalService.Done done = new ITerminalService.Done() {
            @Override
            public void done(IStatus done) {
            }
        };

        List<String> envs = new ArrayList<String>(1);
        envs.add("JAVA_HOME=" + getJavaInstallHome());
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ITerminalsConnectorConstants.PROP_TITLE, "Liberty DevMode");
        properties.put(ITerminalsConnectorConstants.PROP_ENCODING, "UTF-8");
        properties.put(ITerminalsConnectorConstants.PROP_DELEGATE_ID, "org.eclipse.tm.terminal.connector.local.launcher.local");
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_PATH, cmd);
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT, envs.toArray(new String[envs.size()]));
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_MERGE_ENVIRONMENT, true);

        ITerminalService ts = TerminalServiceFactory.getService();
        ts.openConsole(properties, done);
    }

    /**
     * Returns the home path to the Java installation.
     * 
     * @return The home path to the Java installation.
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
     * @return The home path to the Maven installation.
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
     * @return The home path to the Gradle installation.
     */
    private String getGradleInstallHome() {
        // TODO: 1. Find the eclipse->gradle configured install path.

        // 2. Check for associated environment property.
        String gradleInstall = System.getenv("GRADLE_HOME");

        return gradleInstall;
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