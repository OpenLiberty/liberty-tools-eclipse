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

package io.openliberty.tools.eclipse.test.ut;

import static io.openliberty.tools.eclipse.test.it.utils.SWTBotPluginOperations.unsetBuildCmdPathInPreferences;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.CommandBuilder;

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
		String retVal = CommandBuilder.getMavenCommandLine(projectPath.toString(), "-a 123", obfuscatedPath(), false);
		assertEquals(projectPath.resolve(mvnwName()) + " -a 123", retVal, "Wrong cmd line");
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
		String pathEnv= obfuscatedPath() + File.pathSeparator + mvnPath.toAbsolutePath().toString();
		String retVal = CommandBuilder.getMavenCommandLine(projectPath.toString(), "-a 123", pathEnv, false);
		assertEquals(mvnPath.toAbsolutePath().resolve(mvnName()) + " -a 123", retVal, "Wrong cmd line");
	}

	/**
	 * @return 	A platform-dependent path very unlikely to be used, with an empty element
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

	private String mvnwName() {
		if (System.getProperty("os.name").contains("Windows")) {
			return "mvnw.cmd";
		} else {
			return "mvnw";
		}
	}

}
