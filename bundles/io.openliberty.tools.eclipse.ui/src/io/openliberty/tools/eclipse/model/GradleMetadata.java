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
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Extracts build metadata from a Gradle project directory.
 * It supports both Groovy DSL (build.gradle, settings.gradle) and
 * Kotlin DSL (build.gradle.kts, settings.gradle.kts).
 */
public class GradleMetadata implements Metadata {

    /** Matches: rootProject.name = 'value' or rootProject.name = "value" */
    private static final Pattern ROOT_NAME_PATTERN = Pattern.compile("rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']");

    /**
     * Matches an include statement opener; handles:
     * include 'a', 'b'
     * include('a', 'b')
     * include ':a', ':b'
     * include(":a")
     * The capture group is everything after the keyword on the same line.
     */
    private static final Pattern INCLUDE_START_PATTERN = Pattern.compile("^\\s*include\\s*\\(?(.*)");

    /**
     * Matches an optional projectDir remapping:
     * project(':name').projectDir = new File('dir')
     * project(":name").projectDir = new File("dir")
     */
    private static final Pattern PROJECT_DIR_REMAP_PATTERN = Pattern.compile("project\\s*\\([\"']:(\\w[\\w/-]*)['\"']\\)\\.projectDir\\s*=\\s*new\\s+File\\s*\\([\"']([^\"']+)[\"']\\)");

    /**
     * Matches a project(...) dependency inside a dependencies {} block.
     * Handles:
     * project(':name')
     * project(":group:name")
     * project(path: ':name')
     * project(path: ':name', ...)
     */
    private static final Pattern PROJECT_DEP_PATTERN = Pattern.compile("project\\s*\\(\\s*(?:path\\s*:\\s*)?[\"'](:(?:[\\w/-]+:)*[\\w/-]+)[\"']");

    /**
     * Liberty plugin detection patterns (covers both DSLs and all application styles)
     */
    private static final Pattern[] LIBERTY_PLUGIN_PATTERNS = {
                                                               // plugins { id 'io.openliberty.tools.gradle.Liberty' }  (Groovy)
                                                               // plugins { id("io.openliberty.tools.gradle.Liberty") } (Kotlin)
                                                               Pattern.compile("id\\s*\\(?\\s*[\"']io\\.openliberty\\.tools\\.gradle\\.Liberty[\"']\\s*\\)?"),
                                                               // apply plugin: 'liberty'  /  apply plugin: "liberty"
                                                               Pattern.compile("apply\\s+plugin\\s*:\\s*[\"']liberty[\"']"),
                                                               // apply plugin: 'io.openliberty.tools.gradle.Liberty'
                                                               Pattern.compile("apply\\s+plugin\\s*:\\s*[\"']io\\.openliberty\\.tools\\.gradle\\.Liberty[\"']"),
                                                               // classpath 'io.openliberty.tools:liberty-gradle-plugin:...'
                                                               Pattern.compile("classpath\\s+[\"']io\\.openliberty\\.tools:liberty-gradle-plugin"),
    };

    /**
     * Matches the opening of an {@code allprojects} or {@code subprojects} block.
     * Used when scanning the parent build file for inherited Liberty plugin application.
     */
    private static final Pattern ALL_OR_SUB_PROJECTS_BLOCK_PATTERN = Pattern.compile("^\\s*(allprojects|subprojects)\\s*\\{");

    private String projectName;
    private String parentProjectName;
    private List<String> subprojects;
    private List<String> projectDependencies;
    private boolean hasLibertyPlugin;
    private boolean isAggregator;
    private String buildFilePath;
    private String settingsFilePath;

    /**
     * Constructs Gradle metadata from the paths to the build file and/or the settings file.
     *
     * @param buildFilePath    The absolute path to the build file, or null.
     * @param settingsFilePath The absolute path to the settings file, or null.
     * 
     * @throws Exception If required files cannot be read
     */
    public GradleMetadata(String buildFilePath, String settingsFilePath) throws Exception {
        this.buildFilePath = buildFilePath;
        this.settingsFilePath = settingsFilePath;
        extract();
    }

    /** {@inheritDoc} */
    @Override
    public String getProjectName() {
        return projectName;
    }

    /** {@inheritDoc} */
    @Override
    public String getParentProjectName() {
        return parentProjectName;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getSubprojects() {
        return subprojects != null ? subprojects : new ArrayList<>();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isLibertyPluginConfigured() {
        return hasLibertyPlugin;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAggregator() {
        return isAggregator;
    }

    /** {@inheritDoc} */
    @Override
    public String getBuildFilePath() {
        return buildFilePath;
    }

    /**
     * Returns the absolute path to the Gradle settings file (settings.gradle or
     * settings.gradle.kts), or null if no settings file was found.
     *
     * @return The absolute path to the settings file, or null.
     */
    public String getSettingsFilePath() {
        return settingsFilePath;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isModuleDisabled() {
        // The Liberty Gradle plugin (LGP) has no skip mechanism like there is with LMP.
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getProjectDependencies() {
        return projectDependencies != null ? projectDependencies : new ArrayList<>();
    }

    /**
     * Populates all fields. The project directory is derived from whichever path was
     * saved by the constructor: the build file parent is preferred; the settings file
     * parent is used when no build file is present.
     *
     * @throws Exception if required files cannot be read.
     */
    private void extract() throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, buildFilePath);
        }

        // Derive project directory from whichever path is available.
        Path projectDir = buildFilePath != null ? Paths.get(buildFilePath).getParent() : Paths.get(settingsFilePath).getParent();

        projectName = resolveProjectName(projectDir);
        subprojects = resolveSubprojects(projectDir);
        isAggregator = !subprojects.isEmpty();
        parentProjectName = resolveParentProjectName(projectDir);

        hasLibertyPlugin = (buildFilePath != null && isLibertyPluginInBuildFile(buildFilePath))
                           || isLibertyPluginInheritedFromParent(projectDir);

        projectDependencies = buildFilePath != null ? resolveProjectDependencies(buildFilePath) : new ArrayList<>();

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, this);
        }
    }

    /**
     * Returns the project name.
     */
    private String resolveProjectName(Path projectDir) {
        if (projectDir == null) {
            return null;
        }

        // Read the name from settings.
        Path settingsFile = findSettingsFile(projectDir);
        if (settingsFile != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher m = ROOT_NAME_PATTERN.matcher(line);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            } catch (IOException e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "Error reading settings file for project name", e);
                }
            }
        }

        // If the name is not present in settings, default to the directory name.
        return projectDir.getFileName().toString();
    }

    /**
     * Parses the settings file in projectDir and returns the list of subproject
     * directory names declared via include statements.
     *
     * All of the following forms are handled:
     *
     * include 'web', 'ejb' // Groovy, no parens
     * include('web', 'ejb') // Groovy, with parens
     * include ':web', ':ejb' // colon-prefixed, no parens
     * include(':web') // colon-prefixed, with parens
     * include("web") // Kotlin DSL
     * include(":web", ":ejb") // Kotlin DSL, colon-prefixed
     *
     * Custom projectDir remappings are applied so the returned names are actual
     * filesystem directory names, not Gradle project-path labels.
     */
    private List<String> resolveSubprojects(Path projectDir) {
        List<String> result = new ArrayList<>();
        if (projectDir == null) {
            return result;
        }

        Path settingsFile = findSettingsFile(projectDir);
        if (settingsFile == null) {
            return result;
        }

        // Read the entire settings file and collect projectDir remappings alongside includes.
        Map<String, String> projectDirRemappings = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile.toFile()))) {
            StringBuilder currentStatement = new StringBuilder();
            boolean collectingInclude = false;
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                // Skip blank lines and comment lines.
                if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
                    if (collectingInclude) {
                        // A blank line terminates a multi-line include that had no closing paren.
                        parseIncludeContent(currentStatement.toString(), result);
                        currentStatement.setLength(0);
                        collectingInclude = false;
                    }
                    continue;
                }

                // Check for projectDir remapping before include processing.
                Matcher remapMatcher = PROJECT_DIR_REMAP_PATTERN.matcher(trimmed);
                if (remapMatcher.find()) {
                    // key = bare module name (without colon), value = actual directory name
                    projectDirRemappings.put(remapMatcher.group(1), remapMatcher.group(2));
                }

                if (collectingInclude) {
                    currentStatement.append(" ").append(trimmed);
                    // The statement ends when we see a closing paren, or when the line does
                    // not end with a comma (meaning no more arguments follow on the next line).
                    if (trimmed.contains(")") || !trimmed.endsWith(",")) {
                        parseIncludeContent(currentStatement.toString(), result);
                        currentStatement.setLength(0);
                        collectingInclude = false;
                    }
                } else {
                    Matcher includeMatcher = INCLUDE_START_PATTERN.matcher(trimmed);
                    if (includeMatcher.matches()) {
                        String rest = includeMatcher.group(1).trim();
                        currentStatement.append(rest);
                        // If the rest already has a closing paren or does not continue with
                        // a comma, the statement is complete on this line.
                        if (rest.contains(")") || (!rest.isEmpty() && !rest.endsWith(","))) {
                            parseIncludeContent(currentStatement.toString(), result);
                            currentStatement.setLength(0);
                        } else {
                            // Multi-line include: keep collecting (rest may be empty when
                            // the opening paren is alone on the line, e.g. "include(").
                            collectingInclude = true;
                        }
                    }
                }
            }

            // Flush any remaining collected content.
            if (collectingInclude && currentStatement.length() > 0) {
                parseIncludeContent(currentStatement.toString(), result);
            }

        } catch (IOException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Error reading settings file for subprojects", e);
            }
        }

        // Apply projectDir remappings: replace the Gradle project-path label with the
        // actual filesystem directory name where they differ.
        for (int i = 0; i < result.size(); i++) {
            String name = result.get(i);
            if (projectDirRemappings.containsKey(name)) {
                result.set(i, projectDirRemappings.get(name));
            }
        }

        return result;
    }

    /**
     * Parses the content portion of an include statement (everything after the
     * include keyword and optional opening paren) and adds bare module names to
     * result.
     *
     * Input examples (the raw string passed to this method after stripping
     * the include keyword):
     *
     * 'web', 'ejb')
     * "web", "ejb"
     * ':web', ':ejb')
     * ":web"
     *
     * These correspond to the full include statements:
     *
     * include('web', 'ejb')
     * include("web", "ejb")
     * include(':web', ':ejb')
     * include(":web")
     */
    private void parseIncludeContent(String content, List<String> result) {
        // Strip trailing closing paren if present.
        String cleaned = content.replaceAll("\\)\\s*$", "");

        // Split on commas, then clean each token.
        String[] parts = cleaned.split(",");
        for (String part : parts) {
            // Remove quotes, colons, parens, and whitespace.
            String name = part.replaceAll("[\"'()\\s]", "").replaceAll("^:+", "").trim();
            if (!name.isEmpty()) {
                result.add(name);
            }
        }
    }

    /**
     * Determines the parent project name by checking whether the parent directory contains
     * a settings file that includes the current directory as a submodule.
     *
     * @return the parent project name, or null if not in a multi-module build
     */
    private String resolveParentProjectName(Path projectDir) {
        if (projectDir == null) {
            return null;
        }

        String currentDirName = projectDir.getFileName().toString();
        Path parentDir = projectDir.getParent();
        if (parentDir == null) {
            return null;
        }

        // The parent must have a settings file.
        if (findSettingsFile(parentDir) == null) {
            return null;
        }

        // Check whether the parent's settings file includes the current directory.
        List<String> parentSubprojects = resolveSubprojects(parentDir);
        if (!parentSubprojects.contains(currentDirName)) {
            return null;
        }

        // Get the parent project name.
        return resolveProjectName(parentDir);
    }

    /**
     * Returns true if the given build file directly declares the Liberty Gradle
     * plugin in any of the recognised forms (both Groovy and Kotlin DSL).
     */
    private boolean isLibertyPluginInBuildFile(String buildFilePathStr) {
        if (buildFilePathStr == null) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(buildFilePathStr))) {
            String line;
            while ((line = reader.readLine()) != null) {
                for (Pattern p : LIBERTY_PLUGIN_PATTERNS) {
                    if (p.matcher(line).find()) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Error reading build file for Liberty plugin detection", e);
            }
        }
        return false;
    }

    /**
     * Returns true if the Liberty plugin is applied to this module indirectly via
     * an allprojects or subprojects block in the parent root build file.
     *
     * This covers the very common pattern where the root build.gradle contains:
     *
     * allprojects {
     * apply plugin: 'liberty'
     * }
     *
     * In that case each child module does not redeclare the plugin, but it is still active.
     */
    private boolean isLibertyPluginInheritedFromParent(Path projectDir) {
        if (projectDir == null) {
            return false;
        }

        Path parentDir = projectDir.getParent();
        if (parentDir == null) {
            return false;
        }

        // Parent must have a settings file.
        if (findSettingsFile(parentDir) == null) {
            return false;
        }

        // Current directory must be listed in the parent's subprojects.
        String currentDirName = projectDir.getFileName().toString();
        List<String> parentSubprojects = resolveSubprojects(parentDir);
        if (!parentSubprojects.contains(currentDirName)) {
            return false;
        }

        // Scan the parent build file for allprojects/subprojects blocks that apply Liberty.
        Path parentBuildFile = findBuildFile(parentDir);
        if (parentBuildFile == null) {
            return false;
        }

        return isLibertyPluginInAllOrSubprojectsBlock(parentBuildFile.toString());
    }

    /**
     * Scans a build file for an allprojects or subprojects block that
     * contains a Liberty plugin application pattern.
     *
     * Uses simple brace-depth tracking rather than full AST parsing; tolerant of the
     * common formatting styles used in real-world projects.
     */
    private boolean isLibertyPluginInAllOrSubprojectsBlock(String buildFilePathStr) {
        try (BufferedReader reader = new BufferedReader(new FileReader(buildFilePathStr))) {
            String line;
            boolean inTargetBlock = false;
            int braceDepth = 0;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (!inTargetBlock) {
                    Matcher m = ALL_OR_SUB_PROJECTS_BLOCK_PATTERN.matcher(trimmed);
                    if (m.find()) {
                        inTargetBlock = true;
                        braceDepth = countChar(trimmed, '{') - countChar(trimmed, '}');
                        // braceDepth == 0 means the block opened and closed on the same line
                        // (unlikely but defensive).
                        if (braceDepth <= 0) {
                            inTargetBlock = false;
                        }
                    }
                } else {
                    braceDepth += countChar(trimmed, '{') - countChar(trimmed, '}');
                    if (braceDepth <= 0) {
                        inTargetBlock = false;
                        braceDepth = 0;
                        continue;
                    }
                    // Check for any Liberty plugin pattern inside the block.
                    for (Pattern p : LIBERTY_PLUGIN_PATTERNS) {
                        if (p.matcher(trimmed).find()) {
                            return true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Error reading parent build file for inherited Liberty plugin detection", e);
            }
        }
        return false;
    }

    /**
     * Extracts inter-module project dependencies from the build file.
     *
     * Only dependencies declared inside a dependencies block are considered.
     * The following forms are recognised:
     *
     * dependencies {
     * implementation project(':web')
     * implementation(project(":web"))
     * api project(':group:name')
     * implementation project(path: ':web', configuration: 'default')
     * }
     *
     * The returned names are the last segment of the colon-delimited Gradle project
     * path (e.g. web from :web or name from :group:name), matched against
     * projectsByName in WorkspaceModel.
     */
    private List<String> resolveProjectDependencies(String buildFilePathStr) {
        List<String> result = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(buildFilePathStr))) {
            String line;
            boolean inDependenciesBlock = false;
            int braceDepth = 0;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();

                if (!inDependenciesBlock) {
                    // Detect start of a dependencies { } block.
                    // The '{' may be on the same line as 'dependencies' or on the next line.
                    if (trimmed.startsWith("dependencies")) {
                        if (trimmed.contains("{")) {
                            inDependenciesBlock = true;
                            braceDepth = countChar(trimmed, '{') - countChar(trimmed, '}');
                        } else {
                            // '{' is on the next line — peek ahead by setting a flag handled
                            // by the check below. Use a simple state: treat the next '{' as
                            // the block opener.
                            inDependenciesBlock = true;
                            braceDepth = 0; // will be incremented when '{' is seen
                        }
                    }
                } else {
                    if (braceDepth == 0 && trimmed.equals("{")) {
                        // Opening brace was on its own line after 'dependencies'.
                        braceDepth = 1;
                        continue;
                    }

                    braceDepth += countChar(trimmed, '{') - countChar(trimmed, '}');

                    if (braceDepth <= 0) {
                        inDependenciesBlock = false;
                        braceDepth = 0;
                        continue;
                    }

                    // Look for project(...) dependency references.
                    Matcher m = PROJECT_DEP_PATTERN.matcher(trimmed);
                    while (m.find()) {
                        String path = m.group(1); // e.g. ":web" or ":group:name"
                        String bare = lastSegment(path);
                        if (!bare.isEmpty() && !result.contains(bare)) {
                            result.add(bare);
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Error reading build file for project dependencies", e);
            }
        }

        return result;
    }

    /**
     * Returns the settings file settings.gradle preferred over settings.gradle.kts
     * if both files exist in the given directory. Null if neither exists.
     */
    public static Path findSettingsFile(Path dir) {
        if (dir == null) {
            return null;
        }
        Path groovy = dir.resolve("settings.gradle");
        if (Files.exists(groovy)) {
            return groovy;
        }
        Path kotlin = dir.resolve("settings.gradle.kts");
        if (Files.exists(kotlin)) {
            return kotlin;
        }
        return null;
    }

    /**
     * Returns the build file build.gradle preferred over build.gradle.kts
     * if both files exist in the given directory. Null, if neither exists.
     */
    public static Path findBuildFile(Path dir) {
        if (dir == null) {
            return null;
        }
        Path groovy = dir.resolve("build.gradle");
        if (Files.exists(groovy)) {
            return groovy;
        }
        Path kotlin = dir.resolve("build.gradle.kts");
        if (Files.exists(kotlin)) {
            return kotlin;
        }
        return null;
    }

    /**
     * Returns the last colon-delimited segment of a Gradle project path.
     * For example :group:name -> name, :web -> web.
     */
    private static String lastSegment(String gradlePath) {
        // Strip leading colons then split by colon.
        String stripped = gradlePath.replaceAll("^:+", "");
        int lastColon = stripped.lastIndexOf(':');
        return lastColon >= 0 ? stripped.substring(lastColon + 1) : stripped;
    }

    /**
     * Returns the number of occurrences of the input character in input string.
     * 
     * @return The number of occurrences of the input character in input string.
     */
    private static int countChar(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "GradleMetadata{name=" + projectName
               + ", parent=" + parentProjectName
               + ", subprojects=" + subprojects
               + ", aggregator=" + isAggregator
               + ", libertyPlugin=" + hasLibertyPlugin
               + ", dependencies=" + projectDependencies
               + ", buildFile=" + buildFilePath
               + ", settingsFile=" + settingsFilePath + "}";
    }
}
