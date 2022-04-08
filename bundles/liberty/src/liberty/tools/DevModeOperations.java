package liberty.tools;

import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tm.internal.terminal.provisional.api.ITerminalConnector;
import org.eclipse.tm.terminal.view.core.TerminalServiceFactory;
import org.eclipse.tm.terminal.view.core.interfaces.ITerminalService;
import org.eclipse.tm.terminal.view.core.interfaces.constants.ITerminalsConnectorConstants;
import org.eclipse.tm.terminal.view.ui.manager.ConsoleManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;

import liberty.tools.ui.terminal.LocalDevModeLauncherDelegate;
import liberty.tools.ui.terminal.TerminalTabListenerImpl;
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

    public static final String DEVMODE_START_PARMS_DIALOG_TITLE = "Liberty Development Mode";
    public static final String DEVMODE_START_PARMS_DIALOG_MSG = "Specify custom parameters for the liberty dev command.";

    public static final String BROWSER_MVN_IT_RESULT_ID = "maven.failsafe.integration.test.results";
    public static final String BROWSER_MVN_IT_RESULT_NAME = "Maven Failsafe integration test results";

    public static final String BROWSER_MVN_UT_RESULT_ID = "maven.project.surefire.unit.test.results";
    public static final String BROWSER_MVN_UT_RESULT_NAME = "Maven Surefire unit test results";

    public static final String BROWSER_GRADLE_TEST_RESULT_ID = "gradle.project.test.results";
    public static final String BROWSER_GRADLE_TEST_RESULT_NAME = "Gradle project test results";

    /**
     * Returns true if the underlying OS is windows. False, otherwise.
     *
     * @return True if the underlying OS is windows. False, otherwise.
     */
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
        String projectName = project.getName();

        // Check if the application has already been started.
        if (isStarted(projectName)) {
            return;
        }

        try {
            // Get the absolute path to the application project.
            String projectPath = Project.getPath(project);
            if (projectPath == null) {
                throw new Exception("Unable to find the path to the selected project");
            }

            // Prepare the Liberty plugin development mode command.
            String cmd = "";
            if (Project.isMaven(project)) {
                cmd = getMavenCommand("io.openliberty.tools:liberty-maven-plugin:dev -f " + projectPath);
            } else if (Project.isGradle(project)) {
                cmd = getGradleCommand("libertyDev -p=" + projectPath);
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
    public void startWithParms() {
        IProject project = Project.getSelected();
        String projectName = project.getName();

        // Check if the application has already been started.
        if (isStarted(projectName)) {
            return;
        }

        try {
            // Get start parameters from the user. If the user cancelled or closed the parameter dialog,
            // take that as indication that no action should take place.
            String userParms = getStartParms();
            if (userParms == null) {
                return;
            }

            // Get the absolute path to the application project.
            String projectPath = Project.getPath(project);
            if (projectPath == null) {
                throw new Exception("Unable to find the path to the selected project");
            }

            // Prepare the Liberty plugin development mode command.
            String cmd = "";
            if (Project.isMaven(project)) {
                cmd = getMavenCommand("io.openliberty.tools:liberty-maven-plugin:dev " + userParms + " -f " + projectPath);
            } else if (Project.isGradle(project)) {
                cmd = getGradleCommand("libertyDev " + userParms + " -p=" + projectPath);
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

        // Check if the application has already been started.
        if (isStarted(projectName)) {
            return;
        }

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
                cmd = getGradleCommand("libertyDevc -p=" + projectPath);
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
            // Validate that we can get to the respective terminal's output stream.
            LocalDevModeLauncherDelegate delegate = LocalDevModeLauncherDelegate.getInstance();
            if (delegate == null) {
                throw new Exception("Unable to find the development mode launcher delegate. Be sure to run the start action first.");
            }

            ITerminalConnector terminalConnector = delegate.getConnector(projectName);
            if (terminalConnector == null) {
                throw new Exception(
                        "Unable to find terminal connector. Be sure to run the start action first. Note that attempting to stop orphaned processes with this action in not valid. Orphaned processes require manual intervention.");
            }

            OutputStream terminalStream = terminalConnector.getTerminalToRemoteStream();
            if (terminalStream == null) {
                throw new Exception(
                        "Unable to find terminal remote stream. The terminal might not be active. Be sure to run the start action first.  Note that attempting to stop orphaned processes with this action in not valid. Orphaned processes require manual intervention.");
            }

            // Prepare the development mode command to stop the server.
            String cmd = "exit" + System.lineSeparator();

            // Issue the command on the terminal.
            terminalStream.write(cmd.getBytes());
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the stop action on project " + projectName, e);
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

        try {
            // Validate that we can get to the respective terminal's output stream.
            LocalDevModeLauncherDelegate delegate = LocalDevModeLauncherDelegate.getInstance();
            if (delegate == null) {
                throw new Exception("Unable to find the development mode launcher delegate. Be sure to run the start action first.");
            }

            ITerminalConnector terminalConnector = delegate.getConnector(projectName);
            if (terminalConnector == null) {
                throw new Exception("Unable to find terminal connector. Be sure to run the start action first.");
            }

            OutputStream terminalStream = terminalConnector.getTerminalToRemoteStream();
            if (terminalStream == null) {
                throw new Exception(
                        "Unable to find terminal remote stream. The terminal might not be active. Be sure to run the start action first.");
            }

            // Prepare the development mode command to run a test.
            String cmd = System.lineSeparator();

            // Issue the command on the terminal
            terminalStream.write(cmd.getBytes());
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the run tests action on project " + projectName,
                    e);
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
            Path path = getMavenIntegrationTestReportPath(projectPath);
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Integration test results are not available. Be sure to run the tests first.");
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            openTestReport(project.getName(), path, BROWSER_MVN_IT_RESULT_ID, BROWSER_MVN_IT_RESULT_NAME, BROWSER_MVN_IT_RESULT_NAME);
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
            Path path = getMavenUnitTestReportPath(projectPath);
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Unit test results are not available. Be sure to run the tests first.");
                return;
            }

            // Display the report on the browser. Browser display is based on eclipse configuration preferences.
            openTestReport(project.getName(), path, BROWSER_MVN_UT_RESULT_ID, BROWSER_MVN_UT_RESULT_NAME, BROWSER_MVN_UT_RESULT_NAME);
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
            openTestReport(projectName, path, BROWSER_GRADLE_TEST_RESULT_ID, BROWSER_GRADLE_TEST_RESULT_NAME,
                    BROWSER_GRADLE_TEST_RESULT_NAME);
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
        properties.put(ITerminalsConnectorConstants.PROP_DELEGATE_ID, LocalDevModeLauncherDelegate.id);
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ARGS, cmd);
        properties.put(ITerminalsConnectorConstants.PROP_DATA, projectName);
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_ENVIRONMENT, envs.toArray(new String[envs.size()]));
        properties.put(ITerminalsConnectorConstants.PROP_PROCESS_MERGE_ENVIRONMENT, Boolean.TRUE);
        properties.put(ITerminalsConnectorConstants.PROP_FORCE_NEW, Boolean.TRUE);
        properties.put(ITerminalsConnectorConstants.PROP_DATA_NO_RECONNECT, Boolean.TRUE);

        ITerminalService ts = TerminalServiceFactory.getService();

        ITerminalService.Done done = new ITerminalService.Done() {
            @Override
            public void done(IStatus status) {
                if (status.getCode() == IStatus.OK) {
                    TerminalTabListenerImpl tabListener = new TerminalTabListenerImpl(ts, projectName);
                    ts.addTerminalTabListener(tabListener);
                }
            }
        };

        ts.openConsole(properties, done);
    }

    /**
     * Returns the list of parameters if the user presses OK, null otherwise.
     *
     * @return The list of parameters if the user presses OK, null otherwise.
     */
    public String getStartParms() {
        String dInitValue = "";
        IInputValidator iValidator = getParmListValidator();
        Shell shell = Display.getCurrent().getActiveShell();
        InputDialog iDialog = new InputDialog(shell, DEVMODE_START_PARMS_DIALOG_TITLE, DEVMODE_START_PARMS_DIALOG_MSG, dInitValue,
                iValidator) {
        };

        String userInput = null;

        if (iDialog.open() == Window.OK) {
            userInput = iDialog.getValue().trim();
        }

        return userInput;
    }

    /**
     * Creates a validation object for user provided parameters.
     *
     * @return A validation object for user provided parameters.
     */
    public IInputValidator getParmListValidator() {
        return new IInputValidator() {

            @Override
            public String isValid(String text) {
                String[] parmSegments = text.split(" ");
                for (int i = 0; i < parmSegments.length; i++) {
                    if (parmSegments[i] != null && !parmSegments[i].isEmpty() && !parmSegments[i].startsWith("-")) {
                        return "Parameters must start with -";
                    }
                }
                return null;
            }
        };
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
     * @param cmdArgs The mvn command args
     *
     * @return The full Maven command to run on the terminal.
     */
    private String getMavenCommand(String cmdArgs) {
        StringBuilder sb = new StringBuilder();

        String baseCmd = isWindows() ? "mvn.cmd" : "mvn";
        String mvnCmd = null;

        String mvnInstallPath = getMavenInstallHome();
        if (mvnInstallPath != null) {
            mvnCmd = Paths.get(mvnInstallPath, "bin", baseCmd).toString();
        } else {
            mvnCmd = baseCmd;
        }

        sb.append(mvnCmd).append(" ").append(cmdArgs);

        if (isWindows()) {
            // Include trailing space for separation
            sb.insert(0, "/c ");
        }

        return sb.toString();
    }

    /**
     * Returns the full Gradle command to run on the terminal.
     *
     * @param cmdArgs The Gradle command args.
     *
     * @return The full Gradle command to run on the terminal.
     */
    private String getGradleCommand(String cmdArgs) {
        StringBuilder sb = new StringBuilder();

        String baseCmd = isWindows() ? "gradle.bat" : "gradle";
        String gradleCmd = null;

        String gradleInstallPath = getGradleInstallHome();
        if (gradleInstallPath != null) {
            gradleCmd = Paths.get(gradleInstallPath, "bin", baseCmd).toString();
        } else {
            gradleCmd = baseCmd;
        }

        sb.append(gradleCmd).append(" ").append(cmdArgs);

        if (isWindows()) {
            // Include trailing space for separation
            sb.insert(0, "/c ");
        }

        return sb.toString();
    }

    /**
     * Returns the path of the HTML file containing the integration test report.
     *
     * @return The path of the HTML file containing the integration test report.
     */
    public static Path getMavenIntegrationTestReportPath(String projectPath) {
        Path path = Paths.get(projectPath, "target", "site", "failsafe-report.html");

        return path;
    }

    /**
     * Returns the path of the HTML file containing the unit test report.
     *
     * @return The path of the HTML file containing the unit test report.
     */
    public static Path getMavenUnitTestReportPath(String projectPath) {
        Path path = Paths.get(projectPath, "target", "site", "surefire-report.html");

        return path;
    }

    /**
     * Returns the path of the HTML file containing the test report.
     *
     * @return The custom path of the HTML file containing the or the default location.
     */
    public static Path getGradleTestReportPath(IProject project, String projectPath) {
        // TODO: Look for custom dir entry in build.gradle:
        // "test.reports.html.destination". Need to handle a value like this:
        // reports.html.destination = file("$buildDir/edsTestReports/teststuff")
        // Notice the use of a variable: $buildDir.

        // If a custom path was not defined, use default value.
        Path path = Paths.get(projectPath, "build", "reports", "tests", "test", "index.html");

        return path;
    }

    /**
     * Returns true if the input project has already been started. False, otherwise.
     *
     * @param projectName The project name to check.
     *
     * @return True if the input project has already been started. False, otherwise
     */
    private boolean isStarted(String projectName) {
        // Find if there is a connector already associated with the project. If there is one, make sure
        // that associated terminal was terminated.
        LocalDevModeLauncherDelegate delegate = LocalDevModeLauncherDelegate.getInstance();
        if (delegate != null) {
            ITerminalConnector connector = delegate.getConnector(projectName);

            if (connector != null) {
                ConsoleManager consoleMgr = ConsoleManager.getInstance();
                CTabItem item = consoleMgr.findConsole(null, null, projectName, connector, null);
                if (item != null) {
                    if (!item.getText().contains("<Closed>")) {
                        Dialog.displayWarningMessage("Application project " + projectName + " is already running.");
                        return true;
                    } else {
                        // There is no easy way to get notified when the terminal is disconnected, so proactively close the
                        // terminal as it would on normal restart (PROP_FORCE_NEW=TRUE) in order to cleanup. This action
                        // triggers the registered tab listeners to be called.
                        item.dispose();
                    }
                }
            }
        }

        return false;
    }
}