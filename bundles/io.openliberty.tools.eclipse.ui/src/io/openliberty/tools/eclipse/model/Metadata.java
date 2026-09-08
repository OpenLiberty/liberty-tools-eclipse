/*******************************************************************************
* Copyright (c) 2026 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.model;

import java.util.List;

/**
 * Abstracts build tool configuration data for the supported build tools.
 *
 * <p>This interface provides a common contract for extracting metadata from different
 * build systems (i.e. Maven, Gradle). It supports both single-module and multi-module
 * project structures, enabling the Liberty Tools Eclipse plugin to work uniformly
 * with different build tools.</p>
 */
public interface Metadata {

    /**
     * Returns the name of the project as defined in the build configuration.
     *
     * @return The project name.
     */
    public String getProjectName();

    /**
     * Returns the name of the parent project in a multi-module build structure.
     *
     * @return The parent project name, or {@code null} if this is a standalone project
     *         or root aggregator project
     */
    public String getParentProjectName();

    /**
     * Returns the list of subproject (child module) names defined in this project.
     *
     * @return A list of subproject names, or an empty list if this is not an aggregator project.
     */
    public List<String> getSubprojects();

    /**
     * Indicates whether this project has the Liberty Plugin configured.
     *
     * @return {@code true} if the Liberty plugin is configured, {@code false} otherwise.
     */
    public boolean isLibertyPluginConfigured();

    /**
     * Indicates whether this project is an aggregator (parent/multi-module) project.
     *
     * @return {@code true} if this project aggregates child modules, {@code false} otherwise.
     */
    public boolean isAggregator();

    /**
     * Returns the absolute file system path to the build configuration file.
     *
     * @return The absolute path to the build file.
     */
    public String getBuildFilePath();

    /**
     * Indicates whether this project has been disabled to run on a Liberty server.
     *
     * @return {@code true} if this project is disabled to run on a Liberty server, {@code false} otherwise.
     */
    public boolean isModuleDisabled();

    /**
     * Returns the list of project dependencies declared in the build configuration.
     * For Maven, this includes dependencies with type "pom" or dependencies that reference
     * other workspace projects. For Gradle, this includes project dependencies.
     *
     * @return A list of dependency artifact IDs that may correspond to workspace projects,
     *         or an empty list if no project dependencies are found.
     */
    public List<String> getProjectDependencies();
}
