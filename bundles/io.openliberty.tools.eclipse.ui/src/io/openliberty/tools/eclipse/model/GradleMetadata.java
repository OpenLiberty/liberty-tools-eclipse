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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Represents metadata extracted from a Gradle build file.
 */
public class GradleMetadata implements Metadata {

    private String projectName;
    private String parentProjectName;
    private List<String> subprojects;
    private List<String> projectDependencies;
    private boolean hasLibertyPlugin;
    private boolean isAggregator;
    private String buildFilePath;

    /**
     * Constructor.
     *
     * @param buildGradlePath
     */
    public GradleMetadata(String buildGradlePath) throws Exception {
        extract(buildGradlePath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProjectName() {
        return projectName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getParentProjectName() {
        return parentProjectName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSubprojects() {
        return subprojects;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLibertyPluginConfigured() {
        return hasLibertyPlugin;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAggregator() {
        return isAggregator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFilePath() {
        return buildFilePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isModuleDisabled() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getProjectDependencies() {
        if (projectDependencies == null) {
            return new ArrayList<>();
        }
        return projectDependencies;
    }

    /**
     * Extracts metadata from a Gradle build file.
     *
     * @param buildGradlePath The path to the build.gradle file
     * 
     * @return The GradleProjectMetadata object containing extracted information.
     * 
     * @throws Exception if parsing fails,
     */
    public void extract(String buildGradlePath) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, buildGradlePath);
        }

        buildFilePath = buildGradlePath;

        // Extract project name from settings.gradle or directory name
        projectName = getProjectName(buildGradlePath);

        // Extract subprojects from settings.gradle
        subprojects = getSubprojects(buildGradlePath);
        isAggregator = !subprojects.isEmpty();

        // Determine parent project name
        parentProjectName = getParentProjectName(buildGradlePath);

        // Extract project dependencies from build.gradle
        projectDependencies = extractProjectDependencies(buildGradlePath);

        // Check for Liberty plugin
        hasLibertyPlugin = isLibertyPluginInConfig(buildGradlePath);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, this);
        }
    }

    /**
     * Extracts project name from settings.gradle or use directory name.
     * 
     * @param buildGradlePath The path to build.gradle.
     * @return The project name.
     */
    private String getProjectName(String buildGradlePath) {
        Path buildPath = Paths.get(buildGradlePath);
        Path projectDir = buildPath.getParent();

        // Try to get name from settings.gradle
        Path settingsPath = projectDir.resolve("settings.gradle");
        if (Files.exists(settingsPath)) {
            try (BufferedReader reader = new BufferedReader(new FileReader(settingsPath.toFile()))) {
                String line;
                Pattern pattern = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        return matcher.group(1);
                    }
                }
            } catch (Exception e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "Error reading settings.gradle", e);
                }
            }
        }

        // Fall back to directory name
        return projectDir.getFileName().toString();
    }

    /**
     * Extracts subproject names from settings.gradle.
     * 
     * @param The buildGradlePath Path to build.gradle.
     * 
     * @return The list of subproject names.
     */
    private List<String> getSubprojects(String buildGradlePath) throws Exception {
        List<String> subprojects = new ArrayList<>();
        Path buildPath = Paths.get(buildGradlePath);
        Path projectDir = buildPath.getParent();
        Path settingsPath = projectDir.resolve("settings.gradle");

        if (!Files.exists(settingsPath)) {
            return subprojects;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(settingsPath.toFile()))) {
            String line;
            StringBuilder includeBlock = new StringBuilder();
            boolean inInclude = false;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Check for include statement
                if (line.startsWith("include")) {
                    inInclude = true;
                    includeBlock.append(line);

                    // Check if it's a single-line include
                    if (line.contains(")")) {
                        parseIncludeStatement(includeBlock.toString(), subprojects);
                        includeBlock.setLength(0);
                        inInclude = false;
                    }
                } else if (inInclude) {
                    includeBlock.append(" ").append(line);
                    if (line.contains(")")) {
                        parseIncludeStatement(includeBlock.toString(), subprojects);
                        includeBlock.setLength(0);
                        inInclude = false;
                    }
                }
            }
        }

        return subprojects;
    }

    /**
     * Parses an include statement to extract module names.
     * 
     * @param includeStatement The include statement.
     * 
     * @param subprojects      The List to add subprojects to.
     */
    private void parseIncludeStatement(String includeStatement, List<String> subprojects) {
        // Remove 'include' keyword and parentheses
        String content = includeStatement.replaceAll("include\\s*\\(", "").replaceAll("\\)", "");

        // Split by comma and extract module names
        String[] parts = content.split(",");
        for (String part : parts) {
            // Remove quotes and colons, trim whitespace
            String moduleName = part.replaceAll("['\"]", "").replaceAll(":", "").trim();
            if (!moduleName.isEmpty()) {
                subprojects.add(moduleName);
            }
        }
    }

    /**
     * Returns the parent project name by checking parent directory's settings.gradle.
     * 
     * @param The buildGradlePath Path to build.gradle.
     * 
     * @return The parent project name or null.
     */
    private String getParentProjectName(String buildGradlePath) throws Exception {
        Path buildPath = Paths.get(buildGradlePath);
        Path projectDir = buildPath.getParent();
        String currentDirName = projectDir.getFileName().toString();
        Path parentDir = projectDir.getParent();

        if (parentDir == null) {
            return null;
        }

        Path parentSettings = parentDir.resolve("settings.gradle");
        if (!Files.exists(parentSettings)) {
            return null;
        }

        // Check if current directory is in parent's includes
        List<String> parentSubprojects = getSubprojects(parentDir.resolve("build.gradle").toString());
        if (parentSubprojects.contains(currentDirName)) {
            // Get parent project name
            return getProjectName(parentDir.resolve("build.gradle").toString());
        }

        return null;
    }

    /**
     * Checks if build.gradle contains Liberty plugin.
     * 
     * @param buildGradlePath The path to build.gradle.
     * 
     * @return true if Liberty plugin is found.
     */
    private boolean isLibertyPluginInConfig(String buildGradlePath) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(buildGradlePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Check for Liberty plugin application
                if (line.contains("io.openliberty.tools.gradle.Liberty") ||
                    line.contains("liberty-gradle-plugin") ||
                    line.contains("apply plugin: 'liberty'") ||
                    line.contains("id 'liberty'")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Extracts project dependencies from build.gradle.
     * Looks for project() dependencies ONLY within dependencies {} blocks.
     * Inter-module dependencies are declared in each module's own dependencies block,
     * not in parent-level allprojects/subprojects blocks.
     *
     * @param buildGradlePath The path to build.gradle.
     * 
     * @return The list of project names from dependencies.
     */
    private List<String> extractProjectDependencies(String buildGradlePath) throws Exception {
        List<String> dependencies = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(buildGradlePath))) {
            String line;
            int braceDepth = 0;
            boolean inDependenciesBlock = false;

            // Pattern for project dependencies: implementation project(':module-name')
            Pattern projectPattern = Pattern.compile("\\s*(implementation|compile|api|testImplementation|testCompile|runtimeOnly|compileOnly)\\s+project\\s*\\(['\"]:(\\S+)['\"]\\)");

            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();

                // Check if entering dependencies block
                if (trimmedLine.startsWith("dependencies") && trimmedLine.contains("{")) {
                    inDependenciesBlock = true;
                    braceDepth = 1; // Start counting from the dependencies block
                    continue;
                }

                // Track brace depth only when inside dependencies block
                if (inDependenciesBlock) {
                    braceDepth += countChar(trimmedLine, '{') - countChar(trimmedLine, '}');

                    // Exit dependencies block when braces are balanced
                    if (braceDepth == 0) {
                        inDependenciesBlock = false;
                        continue;
                    }

                    // Look for project dependencies only inside dependencies block
                    Matcher matcher = projectPattern.matcher(trimmedLine);
                    if (matcher.find()) {
                        String projectName = matcher.group(2);
                        if (!dependencies.contains(projectName)) {
                            dependencies.add(projectName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Error extracting project dependencies from build.gradle", e);
            }
        }

        return dependencies;
    }

    /**
     * Counts occurrences of a character in a string.
     *
     * @param str The string to search.
     * @param ch  The character to count.
     * 
     * @return The number of occurrences.
     */
    private int countChar(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks if a file is a valid Gradle build file.
     *
     * @param filePath The ath to the file.
     * @return true if it's a valid build.gradle.
     */
    public boolean isValidBuildFile(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.getName().equals("build.gradle");
    }
}