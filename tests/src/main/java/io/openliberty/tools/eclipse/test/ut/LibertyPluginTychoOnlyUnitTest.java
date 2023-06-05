/*******************************************************************************
* Copyright (c) 2023 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.test.ut;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.MockedStatic;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.WorkspaceProjectsModel;
import io.openliberty.tools.eclipse.ui.launch.JRETab;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationDelegateLauncher.RuntimeEnv;
import io.openliberty.tools.eclipse.ui.launch.LaunchConfigurationHelper;
import io.openliberty.tools.eclipse.ui.launch.StartTab;

/**
 * Unit tests. The class name refers to the fact that
 * these cannot currently be run in Eclipse as "JUnit Plug-in Test"
 * due to the issue loading the mockito-extensions/org.mockito.plugins.MockMaker
 * to enable static mocks.
 */
public class LibertyPluginTychoOnlyUnitTest {

    /**
     * Runs before each test.
     */
    @BeforeEach
    public void beforeEach(TestInfo info) {
        System.out.println("INFO: Test " + info.getDisplayName() + " entry: " + java.time.LocalDateTime.now());
    }

    /**
     * Runs after each test.
     */
    @AfterEach
    public void afterEach(TestInfo info) {
        System.out.println("INFO: Test " + info.getDisplayName() + " exit: " + java.time.LocalDateTime.now());
    }


    
    /**
     * Test that run configs with similar attributes (project name, local vs. container), are reused by {@link LaunchConfigurationHelper#getLaunchConfiguration(IProject, String, RuntimeEnv)}
     * 
     * Perhaps ideally the filter method called within would be separately tested.   But this test would've been enough to catch https://github.com/OpenLiberty/liberty-tools-eclipse/issues/357
     * 
     * @throws Exception
     */
    @Test
    public void testGetLaunchConfiguration() throws Exception {
        
        DevModeOperations devModeOps = mock(DevModeOperations.class);
        WorkspaceProjectsModel projModel = mock(WorkspaceProjectsModel.class);
        try( MockedStatic<DevModeOperations> devModeOpsMock = mockStatic(DevModeOperations.class);
             MockedStatic<JRETab> jreTabMock = mockStatic(JRETab.class)) {

            devModeOpsMock.when(DevModeOperations::getInstance).thenReturn(devModeOps);
            jreTabMock.when(()-> JRETab.getDefaultJavaFromBuildPath(any())).thenReturn("mock-build-path");

            when(devModeOps.getProjectModel()).thenReturn(projModel);
            when (projModel.getDefaultStartParameters(any())).thenReturn("");

            LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
            ILaunchConfiguration cfg1 = launchConfigHelper.getLaunchConfiguration(mockIProject("getLaunchConfiguration"), "run", RuntimeEnv.LOCAL);
            ILaunchConfiguration cfg2 = launchConfigHelper.getLaunchConfiguration(mockIProject("getLaunchConfiguration"), "run", RuntimeEnv.CONTAINER);
            ILaunchConfiguration cfg3 = launchConfigHelper.getLaunchConfiguration(mockIProject("getLaunchConfiguration"), "run", RuntimeEnv.LOCAL);
            ILaunchConfiguration cfg4 = launchConfigHelper.getLaunchConfiguration(mockIProject("getLaunchConfiguration"), "run", RuntimeEnv.LOCAL);
            ILaunchConfiguration cfg5 = launchConfigHelper.getLaunchConfiguration(mockIProject("getLaunchConfiguration"), "run", RuntimeEnv.CONTAINER);
            ILaunchConfiguration cfg6 = launchConfigHelper.getLaunchConfiguration(mockIProject("getLaunchConfiguration"), "run", RuntimeEnv.CONTAINER);
            ILaunchConfiguration cfg7 = launchConfigHelper.getLaunchConfiguration(mockIProject("getLaunchConfiguration"), "run", RuntimeEnv.LOCAL);
            Set<String> uniqueConfigNames  = new HashSet<String>();
            ILaunchConfiguration[] configs = {cfg1, cfg2, cfg3, cfg4, cfg5, cfg6, cfg7};
            for (ILaunchConfiguration config : configs) {
                uniqueConfigNames.add(config.getName());
            }
            
            Assertions.assertFalse(cfg1.getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER,(boolean)true), "Expecting local config for cfg1");
            Assertions.assertTrue(cfg2.getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, (boolean)false), "Expecting container config for cfg2");

            Assertions.assertEquals(2, uniqueConfigNames.size(),
                    "Expecting only two unique configs, one for local, one for container");
        }
    }
                
    public static IProject mockIProject(String projectName) throws CoreException {
        IProject mockProject = mock(IProject.class);
        when (mockProject.getName()).thenReturn(projectName);
        return mockProject;
    }
}
