/*******************************************************************************
 * Copyright (c) 2022, 2023 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.test.it.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.junit.jupiter.api.Assertions;
import org.osgi.service.prefs.Preferences;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginTestUtils {

    /**
     * Validates the state of the application (active/inactive) based on the expectation of success (true/false).
     * 
     * @param ctxRoot The applications context root.
     * @param expectSuccess True for success. False for failure.
     * @param testAppPath The base path to the liberty installation.
     */
    public static void validateApplicationOutcome(String ctxRoot, boolean expectSuccess, String testAppPath) {
        String expectedResponse = "Hello! How are you today?";
        String appUrl = "http://localhost:9080/" + ctxRoot + "/servlet";
        validateApplicationOutcomeCustom(appUrl, expectSuccess, expectedResponse, testAppPath);
    }

    /**
     * Validates that the Liberty server is no longer running.
     * 
     * @param testAppPath The base path to the Liberty installation.
     */
    public static void validateLibertyServerStopped(String testAppPath) {
        String wlpMsgLogPath = testAppPath + "/wlp/usr/servers/defaultServer/logs/messages.log";
        int maxAttempts = 30;
        boolean foundStoppedMsg = false;

        // Find message CWWKE0036I: The server x stopped after y seconds
        for (int i = 0; i < maxAttempts; i++) {
            try (BufferedReader br = new BufferedReader(new FileReader(wlpMsgLogPath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("CWWKE0036I")) {
                        foundStoppedMsg = true;
                        break;
                    }
                }

                if (foundStoppedMsg) {
                    break;
                } else {
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                Assertions.fail("Caught exception waiting for stop message", e);
            }
        }

        if (!foundStoppedMsg) {
            // If we are here, the expected outcome was not found. Print the Liberty server's messages.log and fail.
            printLibertyMessagesLogFile(wlpMsgLogPath);
            Assertions.fail("Message CWWKE0036I not found in " + wlpMsgLogPath);
        }

    }

    /**
     * Validates the state of the application (active/inactive) based on the expectation of success (true/false).
     * 
     * @param appUrl The application URL.
     * @param expectSuccess True to check for success. False to check for failure.
     * @param expectedResponse The expected application response payload.
     * @param testAppPath The base path to the liberty installation.
     */
    public static void validateApplicationOutcomeCustom(String appUrl, boolean expectSuccess, String expectedResponse, String testAppPath) {
        int retryCountLimit = 60;
        int reryIntervalSecs = 3;
        int retryCount = 0;

        System.out.println("INFO: Entering validateApplicationOutcomeCustom, appUrl: " + appUrl);
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

                    if (!(content.toString().contains(expectedResponse))) {
                        Thread.sleep(reryIntervalSecs * 1000);
                        con.disconnect();
                        continue;
                    }

                } else {
                    if (status == HttpURLConnection.HTTP_OK) {
                        Thread.sleep(reryIntervalSecs * 1000);
                        con.disconnect();
                        continue;
                    } else {
                        // Giving the server a few secs to start if it is starting.
                        int counter = 0;
                        if (counter <= 5) {
                            counter++;
                            Thread.sleep(reryIntervalSecs * 1000);
                        }
                        con.disconnect();
                        continue;
                    }
                }

                System.out.println("INFO: Exiting normally validateApplicationOutcomeCustom, appUrl: " + appUrl);
                return;
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

                System.out.println("INFO: Exiting with exc validateApplicationOutcomeCustom, appUrl: " + appUrl);
                return;
            }
        }

        // If we are here, the expected outcome was not found. Print the Liberty server's messages.log and fail.
        String wlpMsgLogPath = testAppPath + "/wlp/usr/servers/defaultServer/logs/messages.log";
        printLibertyMessagesLogFile(wlpMsgLogPath);

        Assertions.fail("Timed out while waiting for application under URL: " + appUrl + " to become available.");
    }

    /**
     * Prints the Liberty server's messages.log identified by the input path.
     * 
     * @param wlpMsgLogPath The messages.log path to print.
     */
    public static void printLibertyMessagesLogFile(String wlpMsgLogPath) {
        System.out.println("--------------------------- messages.log ----------------------------");

        try (BufferedReader br = new BufferedReader(new FileReader(wlpMsgLogPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("---------------------------------------------------------------------");
    }

    /**
     * Validates that the test report represented by the input path exists.
     *
     * @param pathToTestReport The path to the report.
     */
    public static void validateTestReportExists(Path pathToTestReport) {
        int retryCountLimit = 100;
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

        throw new IllegalStateException("Timed out waiting for test report: " + pathToTestReport + " file to be created.");
    }

    /**
     * Validates that a wrapper is found at the the input path.
     *
     * @param isExpected to indicate the wrapper should or should not exist
     * @param pathToWrapper The path to the wrapper file in question.
     */
    public static void validateWrapperInProject(boolean isExpected, String pathToWrapper) {

        boolean wrapperFileExists = wrapperExists(pathToWrapper);
        if (!wrapperFileExists && isExpected) {
            Assertions.fail("Wrapper was expected to exisit. Wrapper: " + pathToWrapper + " not found");
        }
    }

    /**
     * Validates that a preference file associated with the Liberty Tools Plugin exists
     *
     * @param isExpected to indicate the preference file should or should not exist.
     */
    public static void validateLibertyToolsPreferencesSet() {
        // Preferences are stored in .metadata/.plugins/org.eclipse.core.runtime/.settings/<nodePath>.prefs.
        // By default, the <nodePath> is the Bundle-SymbolicName of the plug-in. In this case, the qualifier
        // needed to finding the Liberty Tools preference is the nodePath: io.openliberty.tools.eclipse.ui.
        Preferences preferences = InstanceScope.INSTANCE.getNode("io.openliberty.tools.eclipse.ui");
        if (preferences == null) {
            assertNotNull(preferences, "preferences file not found for Liberty Tools");
        }
    }

    /**
     * Returns true or false depending on if the input text is found in the target file
     * 
     * @throws IOException
     */
    public static boolean isTextInFile(String filePath, String text) throws IOException {

        List<String> lines = Files.readAllLines(Paths.get(filePath));
        for (String line : lines) {
            if (line.contains(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the current process is running on a windows environment. False, otherwise.
     *
     * @return True if the current process is running on a windows environment. False, otherwise.
     */
    public static boolean onWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    /**
     * Returns true if the file identified by the input path exists. False, otherwise.
     *
     * @param path The file's path.
     *
     * @return True if the file identified by the input path exists. False, otherwise.
     */
    private static boolean fileExists(Path filePath) {
        File f = new File(filePath.toString());
        boolean exists = f.exists();

        return exists;
    }

    /**
     * Returns true if the wrapper associated by the input path exists. False, otherwise.
     *
     * @param path The file's path.
     *
     * @return True if the file identified by the input path exists. False, otherwise.
     */
    private static boolean wrapperExists(String wrapperFilePathString) {
        File f = new File(wrapperFilePathString);
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
    public static boolean deleteFile(File file) {
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
     * @return Returns true if the directory identified by the input path was deleted. False, otherwise.
     */
    private static boolean deleteDirectory(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                deleteDirectory(files[i]);
            }
        }
        return file.delete();
    }

    /**
     * Updates browser configuration preferences.
     * 
     * @param useInternal Determines whether an internal or external browser setting is set. If true, the internal browser setting is
     *        set. If false the external browser setting is set.
     * 
     * @return True if the browser settings were updated successfully or if it already contains the desired value. False, otherwise.
     */
    public static boolean updateBrowserPreferences(boolean useInternal) {
        boolean success = false;
        // Preferences are stored in .metadata/.plugins/org.eclipse.core.runtime/.settings/<nodePath>.prefs.
        // By default, the <nodePath> is the Bundle-SymbolicName of the plug-in. In this case, the qualifier
        // needed to finding the browser preference is the nodePath: org.eclipse.ui.browser.
        Preferences preferences = InstanceScope.INSTANCE.getNode("org.eclipse.ui.browser");

        try {
            // Update the internal/external browser option.
            int inputChoice = (useInternal) ? 0 : 1;
            int cfgBrowserChoice = preferences.getInt("browser-choice", 1);

            if (cfgBrowserChoice != inputChoice) {
                preferences.putInt("browser-choice", inputChoice);
                preferences.flush();
            }

            success = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return success;
    }

    /**
     * Returns true if the Eclipse instance supports internal browsers. False, otherwise.
     *
     * @return True if the Eclipse instance supports internal browsers. False, otherwise.
     */
    public static boolean isInternalBrowserSupportAvailable() {
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
     * Returns the IProject object associated with the input project name.
     * 
     * @param projectName The project name.
     * 
     * @return The IProject object associated with the input project name.
     */
    public static IProject getProject(String projectName) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot root = workspace.getRoot();
        IProject iProject = root.getProject(projectName);
        return iProject;
    }

    /**
     * Returns the Java installation configured on the project's build path (.classpath file).
     * 
     * @param testAppPath The path to the application.
     * 
     * @return The Java installation configured on the project's build path (.classpath file).
     */
    public static String getJREFromBuildpath(String testAppPath) {
        String jre = null;
        File cpFile = new File(testAppPath + File.separator + ".classpath");

        try (Scanner scanner = new Scanner(cpFile).useDelimiter("\\n")) {
            while (scanner.hasNext()) {
                String line = scanner.next().trim();
                // Sample 1:
                // <classpathentry kind="con"
                // path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-11/"/>
                //
                // Sample 2: <classpathentry kind="con"
                // path="org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-11">
                if (line.contains(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH)) {
                    String[] jreParts = line.split("path=");
                    if (jreParts.length == 2) {
                        String jrePathRaw = jreParts[1];
                        String jrePath = (jrePathRaw.endsWith("/>")) ? jrePathRaw.substring(1, jrePathRaw.length() - 3)
                                : jrePathRaw.substring(1, jrePathRaw.length() - 2);
                        jrePath = (jrePath.endsWith("/")) ? jrePath.substring(0, jrePath.length() - 1) : jrePath;

                        String[] jrePathParts = jrePath.split("/");
                        if (jrePathParts.length == 3) {
                            jre = jrePathParts[2];
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return jre;
    }
}
