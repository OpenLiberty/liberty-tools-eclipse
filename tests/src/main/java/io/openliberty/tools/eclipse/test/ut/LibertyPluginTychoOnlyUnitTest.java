/*******************************************************************************
* Copyright (c) 2023, 2026 IBM Corporation and others.
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.mockito.MockedStatic;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.model.Metadata;
import io.openliberty.tools.eclipse.model.ProjectModel;
import io.openliberty.tools.eclipse.model.WorkspaceModel;
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
     * Test that run configs with similar attributes (project name, local vs. container), are reused by
     * {@link LaunchConfigurationHelper#getLaunchConfiguration(IProject, String, RuntimeEnv)}
     * 
     * Perhaps ideally the filter method called within would be separately tested. But this test would've been enough to catch
     * https://github.com/OpenLiberty/liberty-tools-eclipse/issues/357
     * 
     * @throws Exception
     */
    @Test
    public void testGetLaunchConfiguration() throws Exception {

        DevModeOperations devModeOps = mock(DevModeOperations.class);
        WorkspaceModel projModel = mock(WorkspaceModel.class);
        try (MockedStatic<DevModeOperations> devModeOpsMock = mockStatic(DevModeOperations.class);
                        MockedStatic<JRETab> jreTabMock = mockStatic(JRETab.class)) {

            devModeOpsMock.when(DevModeOperations::getInstance).thenReturn(devModeOps);
            jreTabMock.when(() -> JRETab.getDefaultJavaFromBuildPath(any())).thenReturn("mock-build-path");

            when(devModeOps.getWorkspaceModel()).thenReturn(projModel);

            LaunchConfigurationHelper launchConfigHelper = LaunchConfigurationHelper.getInstance();
            ILaunchConfiguration cfg1 = launchConfigHelper.getLaunchConfiguration(mockProjectModel("getLaunchConfiguration"), "run", RuntimeEnv.LOCAL);
            ILaunchConfiguration cfg2 = launchConfigHelper.getLaunchConfiguration(mockProjectModel("getLaunchConfiguration"), "run", RuntimeEnv.CONTAINER);
            ILaunchConfiguration cfg3 = launchConfigHelper.getLaunchConfiguration(mockProjectModel("getLaunchConfiguration"), "run", RuntimeEnv.LOCAL);
            ILaunchConfiguration cfg4 = launchConfigHelper.getLaunchConfiguration(mockProjectModel("getLaunchConfiguration"), "run", RuntimeEnv.LOCAL);
            ILaunchConfiguration cfg5 = launchConfigHelper.getLaunchConfiguration(mockProjectModel("getLaunchConfiguration"), "run", RuntimeEnv.CONTAINER);
            ILaunchConfiguration cfg6 = launchConfigHelper.getLaunchConfiguration(mockProjectModel("getLaunchConfiguration"), "run", RuntimeEnv.CONTAINER);
            ILaunchConfiguration cfg7 = launchConfigHelper.getLaunchConfiguration(mockProjectModel("getLaunchConfiguration"), "run", RuntimeEnv.LOCAL);
            Set<String> uniqueConfigNames = new HashSet<String>();
            ILaunchConfiguration[] configs = { cfg1, cfg2, cfg3, cfg4, cfg5, cfg6, cfg7 };
            for (ILaunchConfiguration config : configs) {
                uniqueConfigNames.add(config.getName());
            }

            Assertions.assertFalse(cfg1.getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, (boolean) true), "Expecting local config for cfg1");
            Assertions.assertTrue(cfg2.getAttribute(StartTab.PROJECT_RUN_IN_CONTAINER, (boolean) false), "Expecting container config for cfg2");

            Assertions.assertEquals(2, uniqueConfigNames.size(),
                                    "Expecting only two unique configs, one for local, one for container");
        }
    }

    public static ProjectModel mockProjectModel(String projectName) throws Exception {
        IProject mockProject = mock(IProject.class);
        IFile mockPomFile = mock(IFile.class);
        IFile mockBuildGradleFile = mock(IFile.class);
        IFile mockBuildGradleKtsFile = mock(IFile.class);
        IProjectDescription mockProjectDescription = mock(IProjectDescription.class);

        when(mockProject.getName()).thenReturn(projectName);

        // Mock getFile() to return mock IFile objects.
        when(mockProject.getFile("pom.xml")).thenReturn(mockPomFile);
        when(mockProject.getFile("build.gradle")).thenReturn(mockBuildGradleFile);
        when(mockProject.getFile("build.gradle.kts")).thenReturn(mockBuildGradleKtsFile);

        // Mock the exists() method to return false (no build files).
        when(mockPomFile.exists()).thenReturn(false);
        when(mockBuildGradleFile.exists()).thenReturn(false);
        when(mockBuildGradleKtsFile.exists()).thenReturn(false);

        // Mock getDescription().
        when(mockProject.getDescription()).thenReturn(mockProjectDescription);
        when(mockProjectDescription.hasNature(anyString())).thenReturn(false);

        // Mock getLocation() to return null (not needed for this test).
        when(mockProject.getLocation()).thenReturn(null);

        ProjectModel projectModel = new ProjectModel(mockProject);
        Metadata metadata = mock(Metadata.class);
        when(metadata.getProjectName()).thenReturn(projectName);
        projectModel.setBuildConfigMetadata(metadata);
        return projectModel;
    }
}