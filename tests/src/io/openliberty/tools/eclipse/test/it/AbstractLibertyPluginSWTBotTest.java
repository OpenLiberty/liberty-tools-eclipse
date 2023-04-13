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
package io.openliberty.tools.eclipse.test.it;

import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.isInternalBrowserSupportAvailable;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import io.openliberty.tools.eclipse.test.it.utils.ActiveShellUtil;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations;
import io.openliberty.tools.eclipse.test.it.utils.SWTBotTestCondition;

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
        SWTBotPluginOperations.closeWelcomePage(bot);
        // Update browser preferences.
        if (isInternalBrowserSupportAvailable()) {
            boolean success = LibertyPluginTestUtils.updateBrowserPreferences(true);
            Assertions.assertTrue(success, () -> "Unable to update browser preferences.");
        }

        ActiveShellUtil.startActiveShellThread();

    }

    public AbstractLibertyPluginSWTBotTest() {
        super();
    }

    @BeforeEach
    public void beforeEach(TestInfo info) {
        System.out.println(
                "INFO: Test " + this.getClass().getSimpleName() + "#" + info.getDisplayName() + " entry: " + java.time.LocalDateTime.now());
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        System.out.println(
                "INFO: Test " + this.getClass().getSimpleName() + "#" + info.getDisplayName() + " exit: " + java.time.LocalDateTime.now());
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
    public static void importMavenProjects(File workspaceRoot, List<String> folders) {
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                try {
                    // Get the list of projects to install.
                    MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
                    LocalProjectScanner lps = new LocalProjectScanner(folders, false, modelManager);
                    lps.run(new NullProgressMonitor());
                    List<MavenProjectInfo> projects = lps.getProjects();

                    // Import the projects.
                    ProjectImportConfiguration projectImportConfig = new ProjectImportConfiguration();
                    IProjectConfigurationManager projectConfigurationManager = MavenPlugin.getProjectConfigurationManager();
                    projectConfigurationManager.importProjects(projects, projectImportConfig, new NullProgressMonitor());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
    }

    /**
     * Imports the specified list of projects.
     *
     * @param projectsToInstall The list of File objects representing the location of the projects to install.
     *
     * @throws InterruptedException
     * @throws CoreException
     */
    public static void importGradleApplications(ArrayList<File> projectsToInstall) {
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                try {
                    for (File projectFile : projectsToInstall) {
                        IPath projectLocation = org.eclipse.core.runtime.Path
                                .fromOSString(Paths.get(projectFile.getPath()).toAbsolutePath().toString());
                        BuildConfiguration configuration = BuildConfiguration.forRootProjectDirectory(projectLocation.toFile()).build();
                        GradleWorkspace workspace = GradleCore.getWorkspace();
                        GradleBuild newBuild = workspace.createBuild(configuration);
                        newBuild.synchronize(new NullProgressMonitor());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
    }

    /**
     * Validates if a Remote Java configuration was created. If it was created, it means that the debugger successfully attached to
     * the Liberty server.
     * 
     * @param projectName The project name..
     */
    public void validateRemoteJavaAppCreation(String projectName) {
        SWTBotPluginOperations.launchConfigurationsDialog(bot, projectName, "debug");
        SWTBotTreeItem remoteJavaAppEntry = SWTBotPluginOperations.getRemoteJavaAppConfigMenuItem(bot);
        Assertions.assertTrue((remoteJavaAppEntry != null),
                () -> "The " + SWTBotPluginOperations.LAUNCH_CONFIG_REMOTE_JAVA_APP + " entry was not found in run Configurations dialog.");

        List<String> configs = remoteJavaAppEntry.getNodes();
        for (String config : configs) {
            SWTBotTreeItem configEntry = remoteJavaAppEntry.getNode(config);
            bot.waitUntil(SWTBotTestCondition.isTreeItemEnabled(configEntry), 10000);
            configEntry.select().setFocus();

            if (config.startsWith(projectName)) {
                SWTBotButton closeButton = bot.button("Close");
                closeButton.setFocus();
                closeButton.click();
                return;
            }
        }

        Assertions.fail("The remote java application configuration did not contain project name " + projectName);
    }
}