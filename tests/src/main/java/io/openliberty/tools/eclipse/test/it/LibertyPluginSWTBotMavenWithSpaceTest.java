/*******************************************************************************
* Copyright (c) 2025 IBM Corporation and others.
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

import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDashboardAction;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;

/**
 * Tests Open Liberty Eclipse plugin functions for the Maven project with spaces in the path
 */
public class LibertyPluginSWTBotMavenWithSpaceTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Application name.
     */
    static final String MVN_WRAPPER_APP_NAME = "liberty.maven.test.wrapper.app";
    static final String MVN_APP_NAME = "liberty.maven.test.app";

    /**
     * Test app relative path.
     */
    static final Path wrapperProjectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-wrapper-app");
    static final Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");
    static final Path projectPathCopy = Paths.get("resources", "applications", "maven", "maven withSpace", "liberty-maven-test-app");
    static final Path wrapperProjectPathCopy = Paths.get("resources", "applications", "maven","maven withSpace", "liberty-maven-test-wrapper-app");
    static final Path projectsPath = Paths.get("resources", "applications", "maven","maven withSpace");

    static ArrayList<String> projectPaths = new ArrayList<String>();

    /**
     * Setup.
     * 
     * @throws Exception
     */
    @BeforeAll
    public static void setup() throws Exception {

        commonSetup();
        copyDirectory(wrapperProjectPath,wrapperProjectPathCopy);
        copyDirectory(projectPath,projectPathCopy);

        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
        projectPaths.add(wrapperProjectPathCopy.toString());
        projectPaths.add(projectPathCopy.toString());

        // Maybe redundant but we really want to cleanup. We really want to
        // avoid wasting time debugging tricky differences in behavior because of a dirty re-run
        for (String p : projectPaths) {
            cleanupProject(p);
        }

        importMavenProjects(workspaceRoot, projectPaths);

        // Set the preferences
        setBuildCmdPathInPreferences(bot, "Maven");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();

    }

    @AfterAll
    public static void cleanup() throws IOException {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
        unsetBuildCmdPathInPreferences(bot, "Maven");
        deleteDirectory(projectsPath);
    }
    
    /**
     * Tests the start menu action on a dashboard listed application for wrapper project.
     */
    @Test
    public void testDashboardStartActionWithWrapper() {

        // Start dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_WRAPPER_APP_NAME, true,
        		wrapperProjectPathCopy.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(wrapperProjectPathCopy.toAbsolutePath().toString() + "/target/liberty");
    }
    
    /**
     * Tests the debug menu action on a dashboard listed application for wrapper project.
     */
    @Test
    public void testDashboardDebugActionWrapper() {
        // Start dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_WRAPPER_APP_NAME, true, wrapperProjectPathCopy.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(wrapperProjectPathCopy.toAbsolutePath().toString() + "/target/liberty");
    }
    
    
    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartActionProject() {

        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true,
        		projectPathCopy.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPathCopy.toAbsolutePath().toString() + "/target/liberty");
    }
    
    /**
     * Tests the debug menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardDebugActionProject() {
        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPathCopy.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPathCopy.toAbsolutePath().toString() + "/target/liberty");
    }

}
