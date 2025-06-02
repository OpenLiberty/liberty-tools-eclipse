/*******************************************************************************
* Copyright (c) 2022, 2025 IBM Corporation and others.
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

import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.disconnectDebugTarget;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getDebuggerConnectMenuForDebugObject;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.getObjectInDebugView;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.launchDashboardAction;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.pressWorkspaceErrorDialogProceedButton;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.setBuildCmdPathInPreferences;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.terminateLaunch;
import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotMenu;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils;
import io.openliberty.tools.eclipse.ui.dashboard.DashboardView;

/**
 * Tests Open Liberty Eclipse plugin functions for the Debugger
 */
public class LibertyPluginSWTBotDebuggerTest extends AbstractLibertyPluginSWTBotTest {

    /**
     * Application name.
     */
    static final String MVN_APP_NAME = "liberty.maven.test.wrapper.app";

    /**
     * Test app relative path.
     */
    static final Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-wrapper-app");

    static ArrayList<String> projectPaths = new ArrayList<String>();

    /**
     * Setup.
     * 
     * @throws Exception
     */
    @BeforeAll
    public static void setup() throws Exception {

        commonSetup();

        File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
        projectPaths.add(projectPath.toString());

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

    @AfterEach
    public void afterEach(TestInfo info) {
        terminateLaunch();

        // Validate that launch has been removed
        Object launch = getObjectInDebugView("[Liberty]");
        Assertions.assertNull(launch);

        super.afterEach(info);
    }

    @AfterAll
    public static void cleanup() {
        for (String p : projectPaths) {
            cleanupProject(p);
        }
        unsetBuildCmdPathInPreferences(bot, "Maven");
    }

    /**
     * Tests the "Connect Liberty Debugger" menu command
     */
    @Test
    public void testConnectDebuggerMenuCommand() {
        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Verify button is enabled
        Object launch = getObjectInDebugView(MVN_APP_NAME + " [Liberty]");
        Assertions.assertNotNull(launch);
        SWTBotMenu connectDebuggerMenu = getDebuggerConnectMenuForDebugObject(launch);
        Assertions.assertTrue(connectDebuggerMenu.isEnabled());

        // Select button
        connectDebuggerMenu.click();

        // Verify debugger connected
        Object debugTarget = getObjectInDebugView("Liberty Application Debug");
        Assertions.assertNotNull(debugTarget);

        // Validate button disabled
        Assertions.assertFalse(getDebuggerConnectMenuForDebugObject(launch).isEnabled());

    }

    /**
     * Tests the "Connect Liberty Debugger" menu command is disabled after terminate
     */
    @Test
    public void testConnectDebuggerMenuCommand_disabledAfterTerminate() {
        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_START);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Verify button is enabled
        Object launch = getObjectInDebugView(MVN_APP_NAME + " [Liberty]");
        SWTBotMenu connectDebuggerMenu = getDebuggerConnectMenuForDebugObject(launch);
        Assertions.assertTrue(connectDebuggerMenu.isEnabled());

        // Stop dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

        // Validate application stopped.
        LibertyPluginTestUtils.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Validate button disabled
        Assertions.assertFalse(getDebuggerConnectMenuForDebugObject(launch).isEnabled());
    }

    /**
     * Tests the "Connect Liberty Debugger" menu command is enabled after disconnect of debugger
     */
    @Test
    public void testConnectDebuggerMenuCommand_enabledAfterDebuggerDisconnect() {
        // Start dev mode.
        launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

        // Validate application is up and running.
        LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() +
                "/target/liberty");

        // If there are issues with the workspace, close the error dialog.
        pressWorkspaceErrorDialogProceedButton(bot);

        // Verify button is disabled
        Object launch = getObjectInDebugView(MVN_APP_NAME + " [Liberty]");
        SWTBotMenu connectDebuggerMenu = getDebuggerConnectMenuForDebugObject(launch);
        Assertions.assertFalse(connectDebuggerMenu.isEnabled());

        // Disconnected Debugger
        Object debugTarget = getObjectInDebugView("Liberty Application Debug");
        disconnectDebugTarget(debugTarget);

        // Validate button enabled
        Assertions.assertTrue(getDebuggerConnectMenuForDebugObject(launch).isEnabled());
    }
    
    /**
     * Tests the "Enhanced debug monitoring", that the XML file is added in the
     * overrides directory during the debug mode.
     * 
     */
    @Test
    public void testEnhancedDebugMode_configXmlFilePresentOnDebugMode() {
    	// Start dev mode.
    	launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

    	// Validate application is up and running.
    	LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true,
    			projectPath.toAbsolutePath().toString() + "/target/liberty");

    	// If there are issues with the workspace, close the error dialog.
    	pressWorkspaceErrorDialogProceedButton(bot);

    	// Validate app monitoring is disabled by checking the xml file is present in
    	// the overrides directory.
    	Path pathToXmlFile = LibertyPluginTestUtils.getMavenXmlFilePathInOverridesDirectory(projectPath.toString());
    	boolean isExist = LibertyPluginTestUtils.appMonitorDisabledXmlExists(pathToXmlFile);

    	if (!isExist) {
    		Assertions.fail("Xml file not found on " + pathToXmlFile + ".");
    	}
    	// Stop dev mode.
    	launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

    	// Validate application stopped.
    	LibertyPluginTestUtils
    	.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");

    }

    /**
     * Tests the "Enhanced debug monitoring", that the XML file is added in the
     * overrides directory during the debug mode.
     * 
     */
    @Test
    public void testEnhancedDebugMode_onStopRemoveXmlFile() {
    	boolean isExist = false;
    	// Start dev mode.
    	launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

    	// Validate application is up and running.
    	LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true,
    			projectPath.toAbsolutePath().toString() + "/target/liberty");

    	// If there are issues with the workspace, close the error dialog.
    	pressWorkspaceErrorDialogProceedButton(bot);

    	// Validate app monitoring is disabled by checking the xml file is present in
    	// the overrides directory.
    	Path pathToXmlFile = LibertyPluginTestUtils.getMavenXmlFilePathInOverridesDirectory(projectPath.toString());
    	isExist = LibertyPluginTestUtils.appMonitorDisabledXmlExists(pathToXmlFile);

    	if (!isExist) {
    		Assertions.fail("Xml file not found on " + pathToXmlFile + ".");
    	}
    	// Stop dev mode.
    	launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

    	// Validate application stopped.
    	LibertyPluginTestUtils
    	.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");

    	isExist = LibertyPluginTestUtils.appMonitorDisabledXmlExists(pathToXmlFile);
    	if (isExist) {
    		Assertions.fail("Xml file " + pathToXmlFile
    				+ " is not removed from overrides directory on disconnecting debugger.");
    	}
    }

    /**
     * Tests that the XML file is removed from the overrides directory when the
     * debugger is disconnected.
     */
    @Test
    public void testEnhancedDebugMode_disconnectDebuggerRemoveXmlFile() {
    	boolean isExist = false;

    	// Start dev mode.
    	launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_DEBUG);

    	// Validate application is up and running.
    	LibertyPluginTestUtils.validateApplicationOutcome(MVN_APP_NAME, true,
    			projectPath.toAbsolutePath().toString() + "/target/liberty");

    	// If there are issues with the workspace, close the error dialog.
    	pressWorkspaceErrorDialogProceedButton(bot);

    	// Validate app monitoring is disabled by checking the xml file is present in
    	// the overrides directory.
    	Path pathToXmlFile = LibertyPluginTestUtils.getMavenXmlFilePathInOverridesDirectory(projectPath.toString());
    	isExist = LibertyPluginTestUtils.appMonitorDisabledXmlExists(pathToXmlFile);
    	if (!isExist) {
    		Assertions.fail("Xml file not found on " + pathToXmlFile + ".");
    	}

    	// Verify button is disabled
    	Object launch = getObjectInDebugView(MVN_APP_NAME + " [Liberty]");
    	SWTBotMenu connectDebuggerMenu = getDebuggerConnectMenuForDebugObject(launch);
    	Assertions.assertFalse(connectDebuggerMenu.isEnabled());

    	// Disconnected Debugger
    	Object debugTarget = getObjectInDebugView("Liberty Application Debug");
    	disconnectDebugTarget(debugTarget);

    	isExist = LibertyPluginTestUtils.appMonitorDisabledXmlExists(pathToXmlFile);
    	if (isExist) {
    		Assertions.fail("Xml file " + pathToXmlFile
    				+ " is not removed from overrides directory on disconnecting debugger.");
    	}
    	// Stop dev mode.
    	launchDashboardAction(MVN_APP_NAME, DashboardView.APP_MENU_ACTION_STOP);

    	// Validate application stopped.
    	LibertyPluginTestUtils
    	.validateLibertyServerStopped(projectPath.toAbsolutePath().toString() + "/target/liberty");

    }
}
