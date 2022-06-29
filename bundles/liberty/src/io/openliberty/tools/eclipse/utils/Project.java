/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;

import io.openliberty.tools.eclipse.LibertyNature;
import io.openliberty.tools.eclipse.logging.Trace;

public class Project {

    /**
     * Retrieves the absolute path of the currently selected project.
     *
     * @param selectedProject The project object
     *
     * @return The absolute path of the currently selected project or null if the path could not be obtained.
     */
    public static String getPath(IProject project) {
        if (project != null) {
            IPath path = project.getLocation();
            if (path != null) {
                return path.toOSString();
            }
        }

        return null;
    }

    /**
     * Returns true if the input project is a Maven project. False otherwise.
     *
     * @param project The project to check.
     *
     * @return True if the input project is a Maven project. False, otherwise.
     */
    public static boolean isMaven(IProject project) {
        // TODO: Handle cases where pom.xml is not in the root dir or if it has a different name.

        boolean isMaven = false;

        try {
            isMaven = project.getDescription().hasNature("org.eclipse.m2e.core.maven2Nature");
            if (!isMaven) {
                isMaven = project.getFile("pom.xml").exists();
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UTILS, "An error occurred while checking if project " + project + "is a Maven project",
                        e);
            }
        }

        return isMaven;
    }

    /**
     * Returns true if the input project is a Gradle project. False, otherwise.
     *
     * @param project The project to check.
     *
     * @return True if the input project is a Gradle project. False otherwise.
     */
    public static boolean isGradle(IProject project) {
        // TODO: Handle cases where build.gradle is not in the root dir or if it has a different name.

        boolean isGradle = false;

        try {
            isGradle = project.getDescription().hasNature("org.eclipse.buildship.core.gradleprojectnature");
            if (!isGradle) {
                isGradle = project.getFile("build.gradle").exists();
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UTILS, "An error occurred while checking if project " + project + "is a Gradle project",
                        e);
            }
        }

        return isGradle;
    }

    /**
     * Returns true if the input project is configured to run in Liberty's dev mode. False, otherwise. If it is
     * determined
     * that the project can run in Liberty's dev mode, the outcome is persisted by associating the project with a
     * Liberty type/nature.
     *
     * @param project The project to check.
     * @param refresh Defines whether or not this call is being done on behalf of a refresh action.
     *
     * @return True if the input project is configured to run in Liberty's dev mode. False, otherwise.
     *
     * @throws Exception
     */
    public static boolean isLiberty(IProject project, boolean refresh) throws Exception {
        // TODO: Use validation parser to find the Liberty entries in config files more accurately.
        // Perhaps check for other things that we may consider appropriate to check.

        // Check if the input project is already marked as being able to run in Liberty's dev mode.
        boolean isNatureLiberty = project.getDescription().hasNature(LibertyNature.NATURE_ID);

        if (isNatureLiberty && !refresh) {
            return isNatureLiberty;
        }

        // If we are here, the project is not marked as being able to run in Liberty' dev mode or
        // knowledge of this project needs to be refreshed.
        boolean isLiberty = false;

        // Check if the project configured to run in Liberty's dev mode.
        if (isMaven(project)) {
            IFile file = project.getFile("pom.xml");
            BufferedReader br = new BufferedReader(new InputStreamReader(file.getContents()));

            boolean foundLibertyGroupId = false;
            boolean foundLibertyArtifactId = false;
            String line = br.readLine();
            while (line != null) {
                if (line.contains("io.openliberty.tools")) {
                    foundLibertyGroupId = true;
                }
                if (line.contains("liberty-maven-plugin")) {
                    foundLibertyArtifactId = true;
                }
                if (foundLibertyGroupId && foundLibertyArtifactId) {
                    isLiberty = true;
                    break;
                }
                line = br.readLine();
            }
        } else if (isGradle(project)) {
            IFile file = project.getFile("build.gradle");
            BufferedReader br = new BufferedReader(new InputStreamReader(file.getContents()));

            boolean foundLibertyDependency = false;
            boolean foundLibertyPlugin = false;
            String line = br.readLine();
            while (line != null) {
                if (line.matches(".*classpath.*io.openliberty.tools:liberty-gradle-plugin.*")
                        || line.matches(".*classpath.*io.openliberty.tools:liberty-ant-tasks.*")) {
                    foundLibertyDependency = true;
                }
                if (line.matches(".*apply plugin:.*liberty.*") || line.matches(".*id.*io.openliberty.tools.gradle.Liberty.*")) {
                    foundLibertyPlugin = true;
                }
                if (foundLibertyDependency && foundLibertyPlugin) {
                    isLiberty = true;
                    break;
                }
                line = br.readLine();
            }
        }

        // If it is determined that the input project can run in Liberty's dev mode, persist the outcome (if not
        // done so already) by adding a Liberty type/nature marker to the project's metadata.
        if (!isNatureLiberty && isLiberty) {
            addLibertyNature(project);
        }

        // If it is determined that the input project cannot run in Liberty's dev mode, but it is marked as being able
        // to do so, remove the Liberty type/nature marker from the project's metadata.
        if (isNatureLiberty && !isLiberty) {
            removeLibertyNature(project);
        }

        return isLiberty;
    }

    /**
     * Adds the Liberty type/nature entry to the project's description/metadata (.project).
     *
     * @param project The project to process.
     *
     * @throws Exception
     */
    public static void addLibertyNature(IProject project) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, project);
        }

        IProjectDescription projectDesc = project.getDescription();
        String[] currentNatures = projectDesc.getNatureIds();
        String[] newNatures = new String[currentNatures.length + 1];
        System.arraycopy(currentNatures, 0, newNatures, 0, currentNatures.length);
        newNatures[currentNatures.length] = LibertyNature.NATURE_ID;
        projectDesc.setNatureIds(newNatures);
        project.setDescription(projectDesc, new NullProgressMonitor());

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, new Object[] { project, newNatures });
        }
    }

    /**
     * Removes the Liberty type/nature entry from the project's description/metadata (.project).
     *
     * @param project The project to process.
     *
     * @throws Exception
     */
    public static void removeLibertyNature(IProject project) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UTILS, project);
        }

        IProjectDescription projectDesc = project.getDescription();
        String[] currentNatures = projectDesc.getNatureIds();
        ArrayList<String> newNatures = new ArrayList<String>(currentNatures.length - 1);

        for (int i = 0; i < currentNatures.length; i++) {
            if (currentNatures[i].equals(LibertyNature.NATURE_ID)) {
                continue;
            }
            newNatures.add(currentNatures[i]);
        }

        projectDesc.setNatureIds(newNatures.toArray(new String[newNatures.size()]));
        project.setDescription(projectDesc, new NullProgressMonitor());

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UTILS, new Object[] { project, newNatures });
        }
    }

    /**
     * Returns true if the Maven project's pom.xml file is configured to use Liberty dev mode. False, otherwise.
     *
     * @param project The Maven project.
     *
     * @return True if the Maven project's pom.xml file is configured to use Liberty dev mode. False, otherwise.
     */
    public static boolean isMavenBuildFileValid(IProject project) {
        IFile file = project.getFile("pom.xml");

        // TODO: Implement. Check for Liberty Maven plugin and other needed definitions.
        // Need some parsing tool.

        return true;
    }

    /**
     * Returns true if the Gradle project's build file is configured to use Liberty dev mode. False, otherwise.
     *
     * @param project The Gradle project.
     *
     * @return True if the Gradle project's build file is configured to use Liberty dev mode. False, otherwise.
     */
    public static boolean isGradleBuildFileValid(IProject project) {
        IFile file = project.getFile("build.gradle");

        // TODO: Implement. Check for Liberty Gradle plugin and other needed
        // definitions. Need some xml parsing tool.

        return true;
    }
}
