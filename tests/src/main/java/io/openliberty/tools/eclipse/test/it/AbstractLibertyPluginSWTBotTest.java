/*******************************************************************************
 * Copyright (c) 2022, 2024 IBM Corporation and others.
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

import static io.openliberty.tools.eclipse.DevModeOperations.MVN_RUN_APP_LOG_FILE;
import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.isInternalBrowserSupportAvailable;
import static io.openliberty.tools.eclipse.test.it.utils.MagicWidgetFinder.go;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.closeWelcomePage;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getLibertyToolsConfigMenuItem;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDebugConfigurationsDialogFromAppRunAs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.GradleWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;

public abstract class AbstractLibertyPluginSWTBotTest {

    /**
     * Wokbench bot instance.
     */
    static SWTWorkbenchBot bot;

    /**
     * Dashboard instance.
     */
    static SWTBotView dashboard;

    /**
     * Gradle distribution that supports Java 21.
     * Gradle version 8.4+ supports Java 21.
     */
    private static String GRADLE_DISTRIBUTION_VERISION = "8.8";

    protected static String getMvnCmdFilename() {
        return LibertyPluginTestUtils.onWindows() ? "mvn.cmd" : "mvn";
    }

    public static String getMvnCmd() {
        return getMvnCmdPath() + File.separator + "bin" + File.separator + getMvnCmdFilename();
    }

    public static boolean isMvnLogFile() {
        return Boolean.getBoolean("io.liberty.tools.eclipse.tests.mvn.logfile");
    }

    public static String getMvnCmdPath() {
        String pathVal = System.getProperty("io.liberty.tools.eclipse.tests.mvnexecutable.path");
        // Tycho "helpfully" converts empty/absent props to 'null' but we'd rather have an empty so we can use /
        if (pathVal.equals("null")) {
            pathVal = "";
        }
        return pathVal;
    }

    public static String getGradleCmdPath() {
        String pathVal = System.getProperty("io.liberty.tools.eclipse.tests.gradleexecutable.path");
        // Tycho "helpfully" converts empty/absent props to 'null' but we'd rather have an empty so we can use /
        if (pathVal.equals("null")) {
            pathVal = "";
        }
        return pathVal;
    }

    /**
     * Cleanup.
     */
    @AfterAll
    public static void commonCleanup() {
        bot.closeAllEditors();
        bot.closeAllShells();
        bot.resetWorkbench();
    }

    protected static void commonSetup() {
        bot = new SWTWorkbenchBot();
        closeWelcomePage(bot);
        // Update browser preferences.
        if (isInternalBrowserSupportAvailable()) {
            boolean success = LibertyPluginTestUtils.updateBrowserPreferences(true);
            Assertions.assertTrue(success, () -> "Unable to update browser preferences.");
        }

    }

    public AbstractLibertyPluginSWTBotTest() {
        super();
    }

    @BeforeEach
    public void beforeEach(TestInfo info) {
        System.out.println(
                           "INFO: Test " + this.getClass().getSimpleName() + "#" + info.getDisplayName() + " entry: " + java.time.LocalDateTime.now());

        if (isMvnLogFile()) {
            // Turn on config to log dev mode output to file
            System.setProperty(MVN_RUN_APP_LOG_FILE, "lte-dev-mode-output-" + getTimestamp() + ".log");
        }
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        System.out.println(
                           "INFO: Test " + this.getClass().getSimpleName() + "#" + info.getDisplayName() + " exit: " + java.time.LocalDateTime.now());
    }

    protected static void cleanupProject(String projectPathStr) {
        // Problems on Windows deleting .settings directory so giving up for now
        // String[] extensions = { ".project", ".classpath", ".settings" };
        String[] extensions = { ".project", ".classpath" };
        for (String ext : extensions) {
            try {
                Files.delete(Paths.get(projectPathStr, ext));
            } catch (IOException e) {
            }
        }
    }

    /**
     * Imports the specified list of projects.
     *
     * @param workspaceRoot The workspace root location.
     * @param folders       The list of folders containing the projects to install.
     *
     * @throws InterruptedException
     * @throws CoreException
     */
    public static void importMavenProjects(File workspaceRoot, List<String> folders) throws Exception {

        // Get the list of projects to install.
        MavenModelManager modelManager = MavenPlugin.getMavenModelManager();

        for (String folder : folders) {
            ArrayList<String> folderList = new ArrayList<String>();
            folderList.add(folder);

            LocalProjectScanner lps = new LocalProjectScanner(folderList, false, modelManager);
            lps.run(new NullProgressMonitor());
            List<MavenProjectInfo> projects = lps.getProjects();

            try {
                // Import the projects.
                ProjectImportConfiguration projectImportConfig = new ProjectImportConfiguration();
                IProjectConfigurationManager projectConfigurationManager = MavenPlugin.getProjectConfigurationManager();
                projectConfigurationManager.importProjects(projects, projectImportConfig, new NullProgressMonitor());
            } catch (Exception e) {
                System.out.println("Exception in importMavenProjects importing project = " + folder);
                e.printStackTrace();
            }
        }
    }

    /**
     * Imports the specified list of projects.
     *
     * @param projectsToInstall The list of File objects representing the location of the projects to install.
     *
     * @throws InterruptedException
     * @throws CoreException
     */
    public static void importGradleApplications(ArrayList<File> projectsToInstall) throws Exception {
        // When using Eclipse IDE 2024-06, this exception could have been caused by the 
        // Gradle tooling API using a Gradle distribution that does not support Java 21.
        //
        // Buildship 3.1.9 uses org.gradle.toolingapi 8.1.1. If no Gradle version is defined for the  
        // build (Gradle wrapper properties file), the connection will use the tooling API's 
        // version as the Gradle version to run the build.
        // Therefore, if a Gradle version is not defined for the build and given that the 
        // tooling version currently being used is 8.1.1, Gradle 8.1.1 
        // is downloaded and used by the Gradle build. Gradle 8.1.1 does not support Java 21.
        // This causes runtime issues during the synchronization step (Unsupported class file major 
        // version 65), which are not reported back to the caller. 
        // To workaround this issue, specify a Java 21 compatible Gradle version that the
        // tooling can use (i.e. 8.4+). Note that since it is preferable to use the default version 
        // provided by the tooling API, setting the version can be revised at a later time.
        for (File projectFile : projectsToInstall) {
            IPath projectLocation = org.eclipse.core.runtime.Path.fromOSString(Paths.get(projectFile.getPath()).toAbsolutePath().toString());
            BuildConfiguration configuration = BuildConfiguration.forRootProjectDirectory(projectLocation.toFile()).gradleDistribution(GradleDistribution.forVersion(GRADLE_DISTRIBUTION_VERISION)).overrideWorkspaceConfiguration(true).build();
            GradleWorkspace workspace = GradleCore.getWorkspace();
            GradleBuild newBuild = workspace.createBuild(configuration);
            newBuild.synchronize(new NullProgressMonitor());
        }
    }

    /**
     * Validates if a Liberty Tools debug configuration was created. Assumes it will be the first configuration named accordingly.
     * TODO - make this a parameter
     * 
     * @param projectName The project name
     */
    public void validateDebugConfigCreation(String projectName, String configName) {
        Shell configShell = launchDebugConfigurationsDialogFromAppRunAs(projectName);
        SWTBotTreeItem libertyToolsEntry = getLibertyToolsConfigMenuItem(configShell);
        Assertions.assertTrue((libertyToolsEntry != null), () -> "The Liberty entry was not found in run Configurations dialog.");

        Object debugConfig = null;
        for (SWTBotTreeItem item : libertyToolsEntry.getItems()) {
            if (item.getText().equals(configName)) {
                debugConfig = item;
                break;
            }
        }

        if (debugConfig != null) {
            go("Close", configShell);
            return;
        }

        Assertions.fail("The debug configuration: " + configName + " was not found.");
    }

    private String getTimestamp() {
        long currentTime = System.currentTimeMillis();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd.HH-mm-ss.SSS");
        return formatter.format(currentTime);
    }
}