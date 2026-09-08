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
import io.openliberty.tools.eclipse.CommandBuilder.CommandData;
import io.openliberty.tools.eclipse.model.ProjectModel;

public class CommandBuilderTest {

    /**
     * Tests that the CommandBuilder builds a Maven invocation using the mvnw wrapper found in the project, even when
     * the preference is unset.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderMvnWrapper() throws Exception {
        // This allows the test to prove the wrapper is in use even when the preference is unset, since an empty string preference would
        // resolve to "" + "/bin/mvn" = "/bin/mvn" and would typically be an actual path on Unix/Mac.
        unsetBuildCmdPathInPreferences(new SWTWorkbenchBot(), "Maven");
        Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-wrapper-app");
        ProjectModel projectModel = createMockProjectModel(projectPath, null);
        String retVal = CommandBuilder.constructMavenCommand(projectModel, "io.openliberty.tools:liberty-maven-plugin:dev", false, "-a 123", obfuscatedPath()).getCommand();
        assertEquals(projectPath.toAbsolutePath().resolve(mvnwName()) + " io.openliberty.tools:liberty-maven-plugin:dev -a 123", retVal, "Wrong cmd line.");
    }

    /**
     * Tests that the CommandBuilder builds a Maven invocation using the mvn found in the path, even when there is an
     * empty path element.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderMvn() throws Exception {
        Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");
        Path mvnPath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + mvnPath.toAbsolutePath().toString();
        ProjectModel projectModel = createMockProjectModel(projectPath, null);
        String retVal = CommandBuilder.constructMavenCommand(projectModel, "io.openliberty.tools:liberty-maven-plugin:dev", false, "-a 123", pathEnv).getCommand();
        assertEquals(mvnPath.toAbsolutePath().resolve(mvnName()) + " io.openliberty.tools:liberty-maven-plugin:dev -a 123", retVal, "Wrong cmd line.");
    }

    /**
     * Tests that the CommandBuilder prepends the clean phase before the goal when runClean is true for a standalone
     * Maven project.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderMvnWithClean() throws Exception {
        Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");
        Path mvnPath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + mvnPath.toAbsolutePath().toString();
        ProjectModel projectModel = createMockProjectModel(projectPath, null);
        String retVal = CommandBuilder.constructMavenCommand(projectModel, "io.openliberty.tools:liberty-maven-plugin:dev", true, "-a 123", pathEnv).getCommand();
        assertEquals(mvnPath.toAbsolutePath().resolve(mvnName()) + " clean io.openliberty.tools:liberty-maven-plugin:dev -a 123", retVal, "Wrong cmd line.");
    }

    /**
     * Tests that the CommandBuilder accepts null userParms and omits any trailing whitespace or extra token from the
     * assembled command line.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderMvnNullUserParms() throws Exception {
        Path projectPath = Paths.get("resources", "applications", "maven", "liberty-maven-test-app");
        Path mvnPath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + mvnPath.toAbsolutePath().toString();
        ProjectModel projectModel = createMockProjectModel(projectPath, null);
        String retVal = CommandBuilder.constructMavenCommand(projectModel, "io.openliberty.tools:liberty-maven-plugin:dev", false, null, pathEnv).getCommand();
        assertEquals(mvnPath.toAbsolutePath().resolve(mvnName()) + " io.openliberty.tools:liberty-maven-plugin:dev", retVal, "Wrong cmd line.");
    }

    /**
     * Tests that the CommandBuilder appends -pl and -am flags for a child module in a multi-module Maven build, and
     * that user-supplied parameters appear after those flags.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderMvnMultiModuleChild() throws Exception {
        Path rootPath = Paths.get("resources", "applications", "maven", "maven-multi-module", "typeJ", "pom");
        Path childPath = Paths.get("resources", "applications", "maven", "maven-multi-module", "typeJ", "war1");
        Path mvnPath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + mvnPath.toAbsolutePath().toString();
        ProjectModel parentModel = createMockProjectModel(rootPath, null);
        ProjectModel childModel = createMockProjectModel(childPath, parentModel);
        CommandData result = CommandBuilder.constructMavenCommand(childModel, "io.openliberty.tools:liberty-maven-plugin:dev", false, "-a 123", pathEnv);
        assertEquals(mvnPath.toAbsolutePath().resolve(mvnName()) + " io.openliberty.tools:liberty-maven-plugin:dev -pl :war1 -am -a 123", result.getCommand(), "Wrong cmd line.");
        assertEquals(rootPath.toAbsolutePath().toString(), result.getExecutionPath(), "Wrong execution path.");
    }

    /**
     * Tests that the CommandBuilder prepends the clean phase and appends -pl and -am flags with user-supplied
     * parameters when runClean is true for a child module in a multi-module Maven build.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderMvnMultiModuleChildWithClean() throws Exception {
        Path rootPath = Paths.get("resources", "applications", "maven", "maven-multi-module", "typeJ", "pom");
        Path childPath = Paths.get("resources", "applications", "maven", "maven-multi-module", "typeJ", "war1");
        Path mvnPath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + mvnPath.toAbsolutePath().toString();
        ProjectModel parentModel = createMockProjectModel(rootPath, null);
        ProjectModel childModel = createMockProjectModel(childPath, parentModel);
        CommandData result = CommandBuilder.constructMavenCommand(childModel, "io.openliberty.tools:liberty-maven-plugin:dev", true, "-a 123", pathEnv);
        assertEquals(mvnPath.toAbsolutePath().resolve(mvnName()) + " clean io.openliberty.tools:liberty-maven-plugin:dev -pl :war1 -am -a 123", result.getCommand(), "Wrong cmd line.");
        assertEquals(rootPath.toAbsolutePath().toString(), result.getExecutionPath(), "Wrong execution path.");
    }

    /**
     * Tests that the CommandBuilder builds a Gradle invocation using the gradlew wrapper found in the project.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderGradleWrapper() throws Exception {
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-wrapper-app");
        ProjectModel projectModel = createMockProjectModel(projectPath, null);
        CommandData result = CommandBuilder.constructGradleCommand(projectModel, "libertyDev", false, null, obfuscatedPath());
        String expectedCmd = projectPath.toAbsolutePath().resolve(gradlewName()) + " libertyDev";
        assertEquals(expectedCmd, result.getCommand(), "Wrong cmd line.");
        assertEquals(projectPath.toAbsolutePath().toString(), result.getExecutionPath(), "Wrong execution path.");
    }

    /**
     * Tests that the CommandBuilder builds a standalone Gradle invocation using the gradle executable found in the
     * path, with no subproject qualification.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderGradleStandalone() throws Exception {
        Path projectPath = Paths.get("resources", "applications", "gradle", "liberty-gradle-test-app");
        Path gradlePath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + gradlePath.toAbsolutePath().toString();
        ProjectModel projectModel = createMockProjectModel(projectPath, null);
        CommandData result = CommandBuilder.constructGradleCommand(projectModel, "libertyDev", false, null, pathEnv);
        assertEquals(gradlePath.toAbsolutePath().resolve(gradleName()) + " libertyDev", result.getCommand(), "Wrong cmd line.");
        assertEquals(projectPath.toAbsolutePath().toString(), result.getExecutionPath(), "Wrong execution path.");
    }

    /**
     * Tests that the CommandBuilder qualifies the task name with the subproject path when the target is a child module
     * in a multi-module Gradle build.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderGradleMultiModuleChildTask() throws Exception {
        Path rootPath = Paths.get("resources", "applications", "gradle", "multi-liberty-module-gradle-app");
        Path childPath = rootPath.resolve("ear1");
        ProjectModel parentModel = createMockProjectModel(rootPath, null);
        ProjectModel childModel = createMockProjectModel(childPath, parentModel);
        Path gradlePath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + gradlePath.toAbsolutePath().toString();
        CommandData result = CommandBuilder.constructGradleCommand(childModel, "libertyDev", false, null, pathEnv);
        assertEquals(gradlePath.toAbsolutePath().resolve(gradleName()) + " :ear1:libertyDev", result.getCommand(), "Wrong cmd line.");
        assertEquals(rootPath.toAbsolutePath().toString(), result.getExecutionPath(), "Wrong execution path.");
    }

    /**
     * Tests that the CommandBuilder prepends a qualified clean task before the qualified Liberty task when runClean is
     * true for a child module in a multi-module Gradle build.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderGradleMultiModuleChildWithClean() throws Exception {
        Path rootPath = Paths.get("resources", "applications", "gradle", "multi-liberty-module-gradle-app");
        Path childPath = rootPath.resolve("ear1");
        ProjectModel parentModel = createMockProjectModel(rootPath, null);
        ProjectModel childModel = createMockProjectModel(childPath, parentModel);
        Path gradlePath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + gradlePath.toAbsolutePath().toString();
        CommandData result = CommandBuilder.constructGradleCommand(childModel, "libertyDev", true, null, pathEnv);
        assertEquals(gradlePath.toAbsolutePath().resolve(gradleName()) + " :ear1:clean :ear1:libertyDev", result.getCommand(), "Wrong cmd line.");
        assertEquals(rootPath.toAbsolutePath().toString(), result.getExecutionPath(), "Wrong execution path.");
    }

    /**
     * Tests that user-supplied parameters are appended verbatim after the qualified task name and are not given a
     * subproject prefix.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderGradleMultiModuleChildWithUserParms() throws Exception {
        Path rootPath = Paths.get("resources", "applications", "gradle", "multi-liberty-module-gradle-app");
        Path childPath = rootPath.resolve("ear1");
        ProjectModel parentModel = createMockProjectModel(rootPath, null);
        ProjectModel childModel = createMockProjectModel(childPath, parentModel);
        Path gradlePath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + gradlePath.toAbsolutePath().toString();
        CommandData result = CommandBuilder.constructGradleCommand(childModel, "libertyDev", false, "--hotTests --debug-jvm", pathEnv);
        assertEquals(gradlePath.toAbsolutePath().resolve(gradleName()) + " :ear1:libertyDev --hotTests --debug-jvm", result.getCommand(), "Wrong cmd line.");
        assertEquals(rootPath.toAbsolutePath().toString(), result.getExecutionPath(), "Wrong execution path.");
    }

    /**
     * Tests that the Gradle daemon stop command uses the child project's own directory as the execution path and that
     * the task is not qualified with a subproject prefix.
     *
     * @throws Exception
     */
    @Test
    public void testCmdBuilderGradleStopDaemon() throws Exception {
        Path rootPath = Paths.get("resources", "applications", "gradle", "multi-liberty-module-gradle-app");
        Path childPath = rootPath.resolve("ear1");
        ProjectModel parentModel = createMockProjectModel(rootPath, null);
        ProjectModel childModel = createMockProjectModel(childPath, parentModel);
        Path gradlePath = Paths.get("resources", "execs");
        String pathEnv = obfuscatedPath() + File.pathSeparator + gradlePath.toAbsolutePath().toString();
        CommandData result = CommandBuilder.constructGradleStopDaemonCommand(childModel, pathEnv);
        assertEquals(gradlePath.toAbsolutePath().resolve(gradleName()) + " --stop", result.getCommand(), "Wrong cmd line.");
        assertEquals(childPath.toAbsolutePath().toString(), result.getExecutionPath(), "Wrong execution path.");
    }

    /**
     * Returns a platform-dependent PATH string that is very unlikely to contain any real executables, including an
     * empty element.
     *
     * @return The obfuscated PATH string.
     */
    private String obfuscatedPath() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "C:\\abc\\xyz\\123\\456;;C:\\xyz\\abc\\456\\123";
        } else {
            return "/a/b/c/d1/e/f/g::/x/ya/b2/saa/";
        }
    }

    /**
     * Returns the platform-dependent Maven executable name.
     *
     * @return The Maven executable name.
     */
    private String mvnName() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "mvn.cmd";
        } else {
            return "mvn";
        }
    }

    /**
     * Returns the platform-dependent Maven wrapper script name.
     *
     * @return The Maven wrapper script name.
     */
    private String mvnwName() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "mvnw.cmd";
        } else {
            return "mvnw";
        }
    }

    /**
     * Returns the platform-dependent Gradle executable name.
     *
     * @return The Gradle executable name.
     */
    private String gradleName() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "gradle.bat";
        } else {
            return "gradle";
        }
    }

    /**
     * Returns the platform-dependent Gradle wrapper script name.
     *
     * @return The Gradle wrapper script name.
     */
    private String gradlewName() {
        if (System.getProperty("os.name").contains("Windows")) {
            return "gradlew.bat";
        } else {
            return "gradlew";
        }
    }

    /**
     * Creates a mock ProjectModel with an optional parent model, for testing purposes.
     *
     * @param projectPath The path of the project to create the mock model for.
     * @param parentModel The parent model, or null if the project has no parent.
     *
     * @return A mock ProjectModel for the given project path.
     *
     * @throws Exception
     */
    private ProjectModel createMockProjectModel(Path projectPath, ProjectModel parentModel) throws Exception {
        IProject mockProject = mock(IProject.class);
        IPath mockIPath = mock(IPath.class);
        IFile mockPomFile = mock(IFile.class);
        IFile mockBuildGradleFile = mock(IFile.class);
        IFile mockBuildGradleKtsFile = mock(IFile.class);
        IProjectDescription mockProjectDescription = mock(IProjectDescription.class);

        when(mockProject.getLocation()).thenReturn(mockIPath);
        when(mockIPath.toOSString()).thenReturn(projectPath.toAbsolutePath().toString());
        when(mockProject.getName()).thenReturn(projectPath.getFileName().toString());

        // Mock getFile() to return mock IFile objects instead of null.
        when(mockProject.getFile("pom.xml")).thenReturn(mockPomFile);
        when(mockProject.getFile("build.gradle")).thenReturn(mockBuildGradleFile);
        when(mockProject.getFile("build.gradle.kts")).thenReturn(mockBuildGradleKtsFile);

        // Mock the exists() method based on actual file existence.
        when(mockPomFile.exists()).thenReturn(projectPath.resolve("pom.xml").toFile().exists());
        when(mockBuildGradleFile.exists()).thenReturn(projectPath.resolve("build.gradle").toFile().exists());
        when(mockBuildGradleKtsFile.exists()).thenReturn(projectPath.resolve("build.gradle.kts").toFile().exists());

        // Mock getDescription() to avoid NullPointerException.
        when(mockProject.getDescription()).thenReturn(mockProjectDescription);
        when(mockProjectDescription.hasNature(anyString())).thenReturn(false);

        ProjectModel model = new ProjectModel(mockProject);
        if (parentModel != null) {
            model.setParentProjectModel(parentModel);
        }
        return model;
    }

}