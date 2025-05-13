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

import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getObjectInDebugView;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDashboardAction;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.terminateLaunch;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;

/**
 * Tests Open Liberty Eclipse plugin functions for the Gradle project with spaces in the path
 */
public class LibertyPluginSWTBotGradleWithSpaceTest extends AbstractLibertyPluginSWTBotTest {

	/**
     * Application name.
     */
    static final String GRADLE_APP_NAME = "liberty-gradle-test-app-with-spaces";

    /**
     * Application name.
     */
    static final String GRADLE_WRAPPER_APP_NAME = "liberty-gradle-test-wrapper-app-with-spaces";

    static ArrayList<File> projectsToInstall = new ArrayList<File>();

    /**
     * Test app relative path.
     */
    
    static final Path projectPath = Paths.get("resources", "applications", "apps with spaces", "liberty-gradle-test-app-with-spaces");
    static final Path wrapperProjectPath = Paths.get("resources", "applications", "apps with spaces", "liberty-gradle-test-wrapper-app-with-spaces");
    static ArrayList<String> projectPaths = new ArrayList<String>();
    
    /**
     * Setup.
     * 
     * @throws Exception
     */
    @BeforeAll
    public static void setup() throws Exception {

        commonSetup();
        File mainProject = projectPath.toFile();
        File wrapperProject = wrapperProjectPath.toFile();
        
        projectsToInstall.add(mainProject);
        projectsToInstall.add(wrapperProject);

        // Maybe redundant but we really want to cleanup. We really want to
        // avoid wasting time debugging tricky differences in behavior because of a dirty re-run
        for (File p : projectsToInstall) {
            cleanupProject(p.toString());
        }

        importGradleApplications(projectsToInstall);


        // set the preferences
        setBuildCmdPathInPreferences(bot, "Gradle");
        LibertyPluginTestUtils.validateLibertyToolsPreferencesSet();

    }

    @AfterEach
    public void afterEach(TestInfo info) {
        terminateLaunch();

        // Validate that launch has been removed
        Object launch = getObjectInDebugView("[Liberty]");
        Assertions.assertNull(launch);

        super.afterEach(info);
    }
    
    @AfterAll
    public static void cleanup() throws IOException {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
        unsetBuildCmdPathInPreferences(bot, "Gradle");
    }
    
    
    /**
     * Tests the start menu action on a dashboard listed application for wrapper project.
     */
    @Test
    public void testDashboardStartActionWithWrapper() {

        // Start dev mode.
        launchDashboardAction(GRADLE_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_WRAPPER_APP_NAME, true,
        		wrapperProjectPath.toAbsolutePath().toString() + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(GRADLE_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(wrapperProjectPath.toAbsolutePath().toString() + "/build");
    }
    
    /**
     * Tests the debug menu action on a dashboard listed application for wrapper project.
     */
    @Test
    public void testDashboardDebugActionWrapper() {
        // Start dev mode.
        launchDashboardAction(GRADLE_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_WRAPPER_APP_NAME, true, wrapperProjectPath.toAbsolutePath().toString() + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(GRADLE_WRAPPER_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(wrapperProjectPath.toAbsolutePath().toString() + "/build");
    }
    
    
    /**
     * Tests the start menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardStartActionProject() {

        // Start dev mode.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true,
        		projectPath.toAbsolutePath().toString() + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

     // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/build");
    }
    
    /**
     * Tests the debug menu action on a dashboard listed application.
     */
    @Test
    public void testDashboardDebugActionProject() {
        // Start dev mode.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(GRADLE_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/build");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Stop dev mode.
        launchDashboardAction(GRADLE_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

     // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/build");
    }

}
