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

    // TODO: Figure out if there are any special needs for Windows/Linux.
    // TODO: Establish a Maven/Gradle command precedence (i.e. gradlew ->
    // gradle_home).

    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void start() {
        IProject project = Project.getSelected();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the path to selected project: " + project.getName());
            return;
        }
        String cmd = "";
        if (Project.isMaven(project)) {
            if (!Project.isMavenBuildFileValid(project)) {
                System.out.println("Maven build file on project" + project.getName() + " is not valid..");
            }
            cmd += getMavenInstallPath() + "/";
            cmd += "mvn io.openliberty.tools:liberty-maven-plugin:dev -f " + projectPath;
        } else if (Project.isGradle(project)) {
            if (!Project.isGradleBuildFileValid(project)) {
                System.out.println("Build file on project" + project.getName() + " is not valid.");
            }
            cmd += getGradleInstallPath() + "/";
            cmd += "gradle libertyDev -b=" + projectPath;
        } else {
            Dialog.displayErrorMessage("Project" + project.getName() + "is not a Gradle or Maven project.");
            return;
        }

        try {
            runCommand(cmd);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the start action.", e);
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
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the path to selected project: " + project.getName());
            return;
        }
        String cmd = "";
        if (Project.isMaven(project)) {
            cmd += getMavenInstallPath() + "/";
            cmd += "mvn io.openliberty.tools:liberty-maven-plugin:dev " + userParms + " -f " + projectPath;
        } else if (Project.isGradle(project)) {
            cmd += getGradleInstallPath() + "/";
            cmd += "gradle libertyDev " + userParms + " -b=" + projectPath;
        } else {
            Dialog.displayErrorMessage("Project" + project.getName() + "is not a Gradle or Maven project.");
            return;
        }

        try {
            runCommand(cmd);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the start... action.", e);
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
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the path to selected project: " + project.getName());
            return;
        }

        String cmd = "";
        if (Project.isMaven(project)) {
            cmd += getMavenInstallPath() + "/";
            cmd += "mvn io.openliberty.tools:liberty-maven-plugin:devc -f " + projectPath;
        } else if (Project.isGradle(project)) {
            cmd += getGradleInstallPath() + "/";
            cmd += "gradle libertyDevc -b=" + projectPath;
        } else {
            Dialog.displayErrorMessage("Project" + project.getName() + "is not a Gradle or Maven project.");
        }

        try {
            runCommand(cmd);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails(
                    "An error was detected while performing the start in container action.", e);
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
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the path to selected project: " + project.getName());
            return;
        }

        String browserId = "maven.failsafe.integration.test.results";
        String name = "Maven Failsafe integration test results";
        Path path = Paths.get(projectPath, "target", "site", "failsafe-report.html");

        openTestReport(project.getName(), path, browserId, name, name);
    }

    /**
     * Open Maven unit test report.
     */
    public void openMavenUnitTestReport() {
        IProject project = Project.getSelected();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the path to selected project: " + project.getName());
            return;
        }

        String browserId = "maven.project.surefire.unit.test.results";
        String name = "Maven Surefire unit test results";
        Path path = Paths.get(projectPath, "target", "site", "surefire-report.html");
        openTestReport(project.getName(), path, browserId, name, name);
    }

    /**
     * Open Gradle test report.
     */
    public void openGradleTestReport() {
        IProject project = Project.getSelected();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the path to selected project: " + project.getName());
            return;
        }

        String browserId = "gradle.project.test.results";
        String name = "Gradle project test results";

        openTestReport(project.getName(), getGradleTestReportPath(projectPath), browserId, name, name);
    }

    /**
     * Opens the specified report in a browser.
     * 
     * @param reportRelPath
     * @param browserId
     * @param name
     * @param toolTip
     */
    public void openTestReport(String projName, Path path, String browserId, String name, String toolTip) {
        try {
            URL url = path.toUri().toURL();
            IWorkbenchBrowserSupport bSupport = PlatformUI.getWorkbench().getBrowserSupport();
            IWebBrowser browser = null;
            if (bSupport.isInternalWebBrowserAvailable()) {
                browser = bSupport.createBrowser(
                        IWorkbenchBrowserSupport.AS_EDITOR | IWorkbenchBrowserSupport.LOCATION_BAR
                                | IWorkbenchBrowserSupport.NAVIGATION_BAR | IWorkbenchBrowserSupport.STATUS,
                        browserId, name, toolTip);
            } else {
                browser = bSupport.createBrowser(browserId);
            }

            browser.openURL(url);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails(
                    "An error was detected while retrieving " + name + " for project " + projName, e);
            return;
        }
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
            public void done(IStatus done) {
            }
        };

        List<String> envs = new ArrayList<String>(1);
        envs.add("JAVA_HOME=" + getJavaInstallPath());

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(ITerminalsConnectorConstants.PROP_TITLE, "Liberty DevMode");
        properties.put(ITerminalsConnectorConstants.PROP_ENCODING, "UTF-8");
        properties.put(ITerminalsConnectorConstants.PROP_DELEGATE_ID,
                "org.eclipse.tm.terminal.connector.local.launcher.local");
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_PATH, cmd);
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT, envs.toArray(new String[envs.size()]));
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_MERGE_ENVIRONMENT, true);
        ITerminalService ts = TerminalServiceFactory.getService();
        ts.openConsole(properties, done);
    }

    /**
     * Returns the path to the Java installation.
     * 
     * @return The path to the Java installation.
     */
    private String getJavaInstallPath() {
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
     * Returns the path to the Maven installation.
     * 
     * @return The path to the Maven installation.
     */
    private String getMavenInstallPath() {
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
     * Returns the path to the Gradle installation.
     * 
     * @return The path to the Gradle installation.
     */
    private String getGradleInstallPath() {
        // TODO: 1. Find the eclipse->gradle configured install path.

        // 2. Check for associated environment property.
        String mvnInstall = System.getenv("GRADLE_HOME");

        return mvnInstall;
    }

    /**
     * Returns the path to the HTML test report.
     * 
     * @return The HTML default located in the configured in the build file or the default location.
     */
    private Path getGradleTestReportPath(String projectPath) {
        // TODO: Look for custom dir entry in build.gradle:
        // "test.reports.html.destination". Need to handle a value like this:
        // reports.html.destination = file("$buildDir/edsTestReports/teststuff")
        // Notice the use of a variable: $buildDir.

        // If a custom path was not defined, use default value.
        Path path = Paths.get(projectPath, "build", "reports", "tests", "test", "index.html");

        return path;
    }
}