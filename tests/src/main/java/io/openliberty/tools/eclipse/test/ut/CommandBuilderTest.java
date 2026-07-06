/*******************************************************************************
* Copyright (c) 2025, 2026 IBM Corporation and others.
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

import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.CommandBuilder;
import io.openliberty.tools.eclipse.model.ProjectModel;

public class CommandBuilderTest {

    /**
     * Tests the CommandBuilder builds a mvn invocation using the mvnw wrapper found in the project, even when the preference
     * is unset
     * 
     * @throws Exception
     */
    @Test
    public void testCmdBuilderMvnWrapper() throws Exception {
        // This allows the test to prove the wrapper is in use even when the preference is unset, since an empty string preference would
        // resolve to "" + "/bin/mvn" = "/bin/mvn" and would typically be an actual path on Unix/Mac
        unsetBuildCmdPathInPreferences(new SWTWorkbenchBot(), "Maven");
        Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-wrapper-app");
        ProjectModel projectModel = createMockProjectModel(projectPath);
        String retVal = CommandBuilder.constructMavenCommand(projectModel, "-a 123", obfuscatedPath()).getCommand();
        assertEquals(projectPath.toAbsolutePath().resolve(wrapperName()) + " -a 123", retVal, "Wrong cmd line");
    }

    /**
     * Tests the CommandBuilder builds a mvn invocation using the mvn found in the path, even when there is an empty path element
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderMvn() throws Exception {
        Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");
        Path mvnPath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + mvnPath.toAbsolutePath().toString();
        ProjectModel projectModel = createMockProjectModel(projectPath);
        String retVal = CommandBuilder.constructMavenCommand(projectModel, "-a 123", pathEnv).getCommand();
        assertEquals(mvnPath.toAbsolutePath().resolve(mvnName()) + " -a 123", retVal, "Wrong cmd line");
    }

    /**
     * @return A platform-dependent path very unlikely to be used, with an empty element
     */
    private String obfuscatedPath() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "C:\\abc\\xyz\\123\456;;C:\\xyz\\abc\\456\\123";
        } else {
            return "/a/b/c/d1/e/f/g::/x/ya/b2/saa/";
        }
    }

    private String mvnName() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "mvn.cmd";
        } else {
            return "mvn";
        }
    }

    private String wrapperName() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "mvnw.cmd";
        } else {
            return "mvnw";
        }
    }

    /**
     * Creates a mock ProjectModel for testing purposes
     */
    private ProjectModel createMockProjectModel(Path projectPath) throws Exception {
        IProject mockProject = mock(IProject.class);
        IPath mockIPath = mock(IPath.class);
        IFile mockPomFile = mock(IFile.class);
        IFile mockBuildGradleFile = mock(IFile.class);
        IProjectDescription mockProjectDescription = mock(IProjectDescription.class);

        when(mockProject.getLocation()).thenReturn(mockIPath);
        when(mockIPath.toOSString()).thenReturn(projectPath.toAbsolutePath().toString());

        // Mock getFile() to return mock IFile objects instead of null
        when(mockProject.getFile("pom.xml")).thenReturn(mockPomFile);
        when(mockProject.getFile("build.gradle")).thenReturn(mockBuildGradleFile);

        // Mock the exists() method based on actual file existence
        File pomFile = projectPath.resolve("pom.xml").toFile();
        File buildGradleFile = projectPath.resolve("build.gradle").toFile();
        when(mockPomFile.exists()).thenReturn(pomFile.exists());
        when(mockBuildGradleFile.exists()).thenReturn(buildGradleFile.exists());

        // Mock getDescription() to avoid NullPointerException
        when(mockProject.getDescription()).thenReturn(mockProjectDescription);
        when(mockProjectDescription.hasNature(anyString())).thenReturn(false);

        return new ProjectModel(mockProject);
    }

}