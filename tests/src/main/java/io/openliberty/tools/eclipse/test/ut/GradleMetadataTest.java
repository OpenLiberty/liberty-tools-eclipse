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
package io.openliberty.tools.eclipse.test.ut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.model.GradleMetadata;

/**
 * Unit tests for GradleMetadata.
 *
 * All tests are pure file-system tests: they write synthetic Gradle project trees under
 * a temporary directory and verify that GradleMetadata parses them correctly.
 * No Eclipse runtime is required.
 */
public class GradleMetadataTest {

    static Path tempDir;

    /** Creates the shared temporary root directory used by all tests. */
    @BeforeAll
    public static void setup() throws IOException {
        tempDir = Files.createTempDirectory("GradleMetadataTest-");
    }

    /** Deletes all files written during a test, leaving the root directory intact for the next test. */
    @AfterEach
    public void tearDown() throws IOException {
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(tempDir)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    /** Deletes the shared temporary root directory after all tests have run. */
    @AfterAll
    public static void tearDownAll() throws IOException {
        if (tempDir != null) {
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Writes content to file, creating parent directories if needed.
     * Every Gradle build and settings file used by the tests passes through this method.
     */
    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * Verifies that all metadata properties are correctly parsed for a minimal
     * single-module Groovy DSL project.
     *
     * Project layout:
     *
     * settings.gradle:
     *   rootProject.name = 'my-app'
     *
     * build.gradle:
     *   apply plugin: 'liberty'
     *   apply plugin: 'war'
     *
     * Expected: projectName="my-app", libertyPlugin=true, aggregator=false,
     * subprojects=[], parentProjectName=null.
     */
    @Test
    public void singleModule_groovyDsl_allProperties() throws Exception {
        Path dir = tempDir.resolve("sg-groovy");
        write(dir.resolve("settings.gradle"), "rootProject.name = 'my-app'\n");
        write(dir.resolve("build.gradle"),
              "apply plugin: 'liberty'\n" +
                                           "apply plugin: 'war'\n");

        GradleMetadata meta = new GradleMetadata(dir.resolve("build.gradle").toString(), null);

        assertEquals("my-app", meta.getProjectName());
        assertTrue(meta.isLibertyPluginConfigured(), "Liberty plugin should be detected via apply plugin");
        assertFalse(meta.isAggregator());
        assertTrue(meta.getSubprojects().isEmpty());
        assertNull(meta.getParentProjectName());
    }

    /**
     * Verifies that the project name falls back to the directory name when no
     * settings.gradle is present.
     *
     * Project layout:
     *
     * build.gradle:
     *   apply plugin: 'war'
     *
     * Expected: projectName="sg-fallback" (directory name), libertyPlugin=false.
     */
    @Test
    public void singleModule_groovyDsl_nameFromDirectoryWhenNoSettings() throws Exception {
        Path dir = tempDir.resolve("sg-fallback");
        write(dir.resolve("build.gradle"), "apply plugin: 'war'\n");

        GradleMetadata meta = new GradleMetadata(dir.resolve("build.gradle").toString(), null);

        assertEquals("sg-fallback", meta.getProjectName());
        assertFalse(meta.isLibertyPluginConfigured());
    }

    /**
     * Verifies that the Liberty plugin is detected and the project name is read correctly
     * for a single-module Kotlin DSL project.
     *
     * Project layout:
     *
     * settings.gradle.kts:
     *   rootProject.name = "kotlin-app"
     *
     * build.gradle.kts:
     *   plugins {
     *       id("io.openliberty.tools.gradle.Liberty")
     *       war
     *   }
     *
     * Expected: projectName="kotlin-app", libertyPlugin=true, aggregator=false.
     */
    @Test
    public void singleModule_kotlinDsl_allProperties() throws Exception {
        Path dir = tempDir.resolve("sg-kotlin");
        write(dir.resolve("settings.gradle.kts"), "rootProject.name = \"kotlin-app\"\n");
        write(dir.resolve("build.gradle.kts"),
              "plugins {\n" +
                                               "    id(\"io.openliberty.tools.gradle.Liberty\")\n" +
                                               "    war\n" +
                                               "}\n");

        GradleMetadata meta = new GradleMetadata(dir.resolve("build.gradle.kts").toString(), null);

        assertEquals("kotlin-app", meta.getProjectName());
        assertTrue(meta.isLibertyPluginConfigured(), "Liberty plugin should be detected via Kotlin plugins block");
        assertFalse(meta.isAggregator());
    }

    /**
     * Verifies that the Liberty plugin is detected across the two syntactic forms
     * that appear in real projects.
     *
     * Form 1: plugins block with fully-qualified portal ID (Groovy DSL):
     *
     * build.gradle:
     *
     *   plugins {
     *       id 'io.openliberty.tools.gradle.Liberty'
     *   }
     *
     * This is the only valid form for a non-core plugin in a plugins block.
     * The shorthand 'liberty' is not a Gradle core plugin ID and cannot be resolved
     * without a full portal identifier.
     *
     * Form 2: buildscript classpath + apply plugin: shorthand (Groovy DSL):
     *
     * build.gradle:
     *
     *   buildscript {
     *       dependencies {
     *           classpath 'io.openliberty.tools:liberty-gradle-plugin:3.9'
     *       }
     *   }
     *   apply plugin: 'liberty'
     *
     * The apply plugin: shorthand resolves against the buildscript classpath, not the
     * Gradle Plugin Portal. The Liberty plugin JAR ships
     * META-INF/gradle-plugins/liberty.properties which registers the short ID
     * 'liberty'. The parser detects either line independently, so a file containing
     * both exercises both detection patterns at once.
     */
    @Test
    public void libertyPlugin_detectedInAllForms() throws Exception {
        // Form 1: plugins block, fully-qualified portal ID.
        Path d1 = tempDir.resolve("lp-id-full");
        write(d1.resolve("settings.gradle"), "rootProject.name = 'p'\n");
        write(d1.resolve("build.gradle"), "plugins {\n    id 'io.openliberty.tools.gradle.Liberty'\n}\n");
        assertTrue(new GradleMetadata(d1.resolve("build.gradle").toString(), null).isLibertyPluginConfigured());

        // Form 2: buildscript classpath + apply plugin: shorthand together, as they appear
        // in real projects. The classpath line makes the JAR available; apply plugin: 'liberty'
        // then activates it using the short ID registered in META-INF/gradle-plugins/liberty.properties.
        Path d2 = tempDir.resolve("lp-classpath-apply");
        write(d2.resolve("settings.gradle"), "rootProject.name = 'p'\n");
        write(d2.resolve("build.gradle"),
              "buildscript {\n" +
                                          "    dependencies {\n" +
                                          "        classpath 'io.openliberty.tools:liberty-gradle-plugin:3.9'\n" +
                                          "    }\n" +
                                          "}\n" +
                                          "apply plugin: 'liberty'\n");
        assertTrue(new GradleMetadata(d2.resolve("build.gradle").toString(), null).isLibertyPluginConfigured());
    }

    /**
     * Verifies that subprojects declared on a single include line without
     * parentheses are all parsed correctly.
     *
     * settings.gradle:
     *   rootProject.name = 'root'
     *   include 'web', 'ejb', 'ear'
     *
     * build.gradle:
     *   (empty)
     *
     * Expected: aggregator=true, subprojects=[web, ejb, ear].
     */
    @Test
    public void multiModule_includeNoParensCommaSeparated() throws Exception {
        Path root = tempDir.resolve("mm-noparen");
        write(root.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                               "include 'web', 'ejb', 'ear'\n");
        write(root.resolve("build.gradle"), "");

        GradleMetadata meta = new GradleMetadata(root.resolve("build.gradle").toString(), null);
        assertTrue(meta.isAggregator());
        List<String> subs = meta.getSubprojects();
        assertEquals(3, subs.size());
        assertTrue(subs.contains("web"));
        assertTrue(subs.contains("ejb"));
        assertTrue(subs.contains("ear"));
    }

    /**
     * Verifies that subprojects declared via include(...) with parentheses
     * are parsed correctly.
     *
     * settings.gradle:
     *   rootProject.name = 'root'
     *   include('web', 'ejb')
     *
     * build.gradle:
     *   (empty)
     *
     * Expected: subprojects=[web, ejb].
     */
    @Test
    public void multiModule_includeWithParens() throws Exception {
        Path root = tempDir.resolve("mm-paren");
        write(root.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                               "include('web', 'ejb')\n");
        write(root.resolve("build.gradle"), "");

        List<String> subs = new GradleMetadata(root.resolve("build.gradle").toString(), null).getSubprojects();
        assertEquals(2, subs.size());
        assertTrue(subs.contains("web"));
        assertTrue(subs.contains("ejb"));
    }

    /**
     * Verifies that leading colons on subproject names are stripped.
     * Colon-prefixed names are the Gradle project-path convention; only the
     * bare directory name should be returned.
     *
     * settings.gradle:
     *   rootProject.name = 'root'
     *   include ':web', ':ejb'
     *
     * build.gradle:
     *   (empty)
     *
     * Expected: subprojects=[web, ejb] (leading colon stripped).
     */
    @Test
    public void multiModule_includeColonPrefixedStripped() throws Exception {
        Path root = tempDir.resolve("mm-colon");
        write(root.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                               "include ':web', ':ejb'\n");
        write(root.resolve("build.gradle"), "");

        List<String> subs = new GradleMetadata(root.resolve("build.gradle").toString(), null).getSubprojects();
        assertEquals(2, subs.size());
        assertTrue(subs.contains("web"), "Leading colon should be stripped");
        assertTrue(subs.contains("ejb"), "Leading colon should be stripped");
    }

    /**
     * Verifies that subprojects are collected when each is declared on its own
     * separate include statement.
     *
     * settings.gradle:
     *   rootProject.name = 'root'
     *   include 'web'
     *   include 'ejb'
     *   include 'ear'
     *
     * build.gradle:
     *   (empty)
     *
     * Expected: subprojects size=3.
     */
    @Test
    public void multiModule_includeMultipleStatements() throws Exception {
        Path root = tempDir.resolve("mm-multi-stmt");
        write(root.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                               "include 'web'\n" +
                                               "include 'ejb'\n" +
                                               "include 'ear'\n");
        write(root.resolve("build.gradle"), "");

        assertEquals(3,
                     new GradleMetadata(root.resolve("build.gradle").toString(), null).getSubprojects().size());
    }

    /**
     * Verifies that subprojects are parsed correctly from a Kotlin DSL settings file.
     *
     * settings.gradle.kts:
     *   rootProject.name = "root"
     *   include(":web", ":ejb")
     *
     * build.gradle.kts:
     *   (empty)
     *
     * Expected: subprojects=[web, ejb] (leading colon stripped).
     */
    @Test
    public void multiModule_includeKotlinDsl() throws Exception {
        Path root = tempDir.resolve("mm-kotlin");
        write(root.resolve("settings.gradle.kts"),
              "rootProject.name = \"root\"\n" +
                                                   "include(\":web\", \":ejb\")\n");
        write(root.resolve("build.gradle.kts"), "");

        List<String> subs = new GradleMetadata(root.resolve("build.gradle.kts").toString(), null).getSubprojects();
        assertEquals(2, subs.size());
        assertTrue(subs.contains("web"));
        assertTrue(subs.contains("ejb"));
    }

    /**
     * Verifies that a multi-line include(...) call spread across several lines
     * is fully collected and parsed.
     *
     * settings.gradle:
     *   rootProject.name = 'root'
     *   include(
     *       'web',
     *       'ejb',
     *       'ear'
     *   )
     *
     * build.gradle:
     *   (empty)
     *
     * Expected: subprojects=[web, ejb, ear].
     */
    @Test
    public void multiModule_includeMultiLine() throws Exception {
        Path root = tempDir.resolve("mm-multiline");
        write(root.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                               "include(\n" +
                                               "    'web',\n" +
                                               "    'ejb',\n" +
                                               "    'ear'\n" +
                                               ")\n");
        write(root.resolve("build.gradle"), "");

        List<String> subs = new GradleMetadata(root.resolve("build.gradle").toString(), null).getSubprojects();
        assertEquals(3, subs.size());
        assertTrue(subs.contains("web"));
        assertTrue(subs.contains("ejb"));
        assertTrue(subs.contains("ear"));
    }

    /**
     * Verifies that a projectDir remapping in settings.gradle causes
     * the returned subproject name to be the filesystem directory name rather than the
     * Gradle project-path label.
     *
     * settings.gradle:
     *   rootProject.name = 'root'
     *   include ':web-module'
     *   project(':web-module').projectDir = new File('web')
     *
     * build.gradle:
     *   (empty)
     *
     * Expected: subprojects=[web] (label "web-module" replaced by directory "web").
     */
    @Test
    public void projectDirRemapping_replacesLabelWithDirectoryName() throws Exception {
        Path root = tempDir.resolve("remap");
        write(root.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                               "include ':web-module'\n" +
                                               "project(':web-module').projectDir = new File('web')\n");
        write(root.resolve("build.gradle"), "");

        List<String> subs = new GradleMetadata(root.resolve("build.gradle").toString(), null).getSubprojects();
        assertEquals(1, subs.size());
        assertEquals("web", subs.get(0), "projectDir remapping should replace the Gradle label with the directory name");
    }

    /**
     * Verifies two scenarios in one test setup:
     * 1. A submodule resolves its parent name from the root settings.gradle.
     * 2. A root project that has no parent settings above it returns null.
     *
     * Project layout:
     *
     * parent-root/settings.gradle:
     *   rootProject.name = 'myroot'
     *   include 'web', 'ejb'
     *
     * parent-root/build.gradle:
     *   (empty)
     *
     * parent-root/web/build.gradle:
     *   apply plugin: 'liberty'
     *   apply plugin: 'war'
     *
     * Expected for web submodule: projectName="web", parentProjectName="myroot", aggregator=false.
     * Expected for root: parentProjectName=null.
     */
    @Test
    public void parent_resolvedFromParentSettings_andNullForStandalone() throws Exception {
        Path root = tempDir.resolve("parent-root");
        write(root.resolve("settings.gradle"),
              "rootProject.name = 'myroot'\n" +
                                               "include 'web', 'ejb'\n");
        write(root.resolve("build.gradle"), "");

        Path web = root.resolve("web");
        write(web.resolve("build.gradle"),
              "apply plugin: 'liberty'\n" +
                                           "apply plugin: 'war'\n");

        GradleMetadata webMeta = new GradleMetadata(web.resolve("build.gradle").toString(), null);
        assertEquals("web", webMeta.getProjectName());
        assertEquals("myroot", webMeta.getParentProjectName(),
                     "Parent name should come from root settings.gradle");
        assertFalse(webMeta.isAggregator());

        GradleMetadata rootMeta = new GradleMetadata(root.resolve("build.gradle").toString(), null);
        assertNull(rootMeta.getParentProjectName());
    }

    /**
     * Verifies that the Liberty plugin is treated as configured for a submodule when it is
     * applied inside an allprojects or subprojects block in the parent
     * root build.gradle, rather than in the submodule's own build file.
     *
     * allprojects scenario:
     *
     * inherit-all/settings.gradle:
     *   rootProject.name = 'root'
     *   include 'web'
     *
     * inherit-all/build.gradle:
     *   allprojects {
     *       apply plugin: 'liberty'
     *   }
     *
     * inherit-all/web/build.gradle:
     *   apply plugin: 'war'
     *
     * subprojects scenario:
     *
     * inherit-sub/settings.gradle:
     *   rootProject.name = 'root'
     *   include 'ejb'
     *
     * inherit-sub/build.gradle:
     *   subprojects {
     *       apply plugin: 'liberty'
     *       apply plugin: 'java'
     *   }
     *
     * inherit-sub/ejb/build.gradle:
     *   dependencies {}
     *
     * Expected: libertyPlugin=true for both the web and ejb submodules.
     */
    @Test
    public void libertyPlugin_inheritedViaAllprojectsAndSubprojects() throws Exception {
        Path allRoot = tempDir.resolve("inherit-all");
        write(allRoot.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                                  "include 'web'\n");
        write(allRoot.resolve("build.gradle"),
              "allprojects {\n" +
                                               "    apply plugin: 'liberty'\n" +
                                               "}\n");
        Path allWeb = allRoot.resolve("web");
        write(allWeb.resolve("build.gradle"), "apply plugin: 'war'\n");

        assertTrue(new GradleMetadata(allWeb.resolve("build.gradle").toString(), null).isLibertyPluginConfigured(),
                   "Liberty plugin should be detected as inherited from parent allprojects block");

        Path subRoot = tempDir.resolve("inherit-sub");
        write(subRoot.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                                  "include 'ejb'\n");
        write(subRoot.resolve("build.gradle"),
              "subprojects {\n" +
                                               "    apply plugin: 'liberty'\n" +
                                               "    apply plugin: 'java'\n" +
                                               "}\n");
        Path ejb = subRoot.resolve("ejb");
        write(ejb.resolve("build.gradle"), "dependencies {}\n");

        assertTrue(new GradleMetadata(ejb.resolve("build.gradle").toString(), null).isLibertyPluginConfigured(),
                   "Liberty plugin should be detected as inherited from parent subprojects block");
    }

    /**
     * Verifies that the Liberty plugin is NOT inherited by a directory that is not listed
     * as a submodule in the parent settings.gradle, even though the parent's
     * allprojects block applies it.
     *
     * inherit-unrelated/settings.gradle:
     *   rootProject.name = 'root'
     *   include 'other'
     *
     * inherit-unrelated/build.gradle:
     *   allprojects {
     *       apply plugin: 'liberty'
     *   }
     *
     * inherit-unrelated/unrelated/build.gradle:
     *   apply plugin: 'war'
     *
     * Expected: libertyPlugin=false for the "unrelated" directory (not in include list).
     */
    @Test
    public void libertyPlugin_notInheritedWhenNotDeclaredSubmodule() throws Exception {
        Path root = tempDir.resolve("inherit-unrelated");
        write(root.resolve("settings.gradle"),
              "rootProject.name = 'root'\n" +
                                               "include 'other'\n");
        write(root.resolve("build.gradle"),
              "allprojects {\n" +
                                            "    apply plugin: 'liberty'\n" +
                                            "}\n");

        Path unrelated = root.resolve("unrelated");
        write(unrelated.resolve("build.gradle"), "apply plugin: 'war'\n");

        assertFalse(new GradleMetadata(unrelated.resolve("build.gradle").toString(), null).isLibertyPluginConfigured(),
                    "Liberty should NOT be inherited when the directory is not a declared submodule");
    }

    /**
     * Verifies that inter-module project dependencies declared with the standard
     * Groovy DSL configuration names are extracted correctly.
     *
     * settings.gradle:
     *   rootProject.name = 'war'
     *
     * build.gradle:
     *   dependencies {
     *       implementation project(':ejb')
     *       runtimeOnly project(':common')
     *   }
     *
     * Expected: projectDependencies=[ejb, common].
     */
    @Test
    public void projectDependencies_allForms() throws Exception {
        Path dir = tempDir.resolve("dep-groovy");
        write(dir.resolve("settings.gradle"), "rootProject.name = 'war'\n");
        write(dir.resolve("build.gradle"),
              "dependencies {\n" +
                                           "    implementation project(':ejb')\n" +
                                           "    runtimeOnly project(':common')\n" +
                                           "}\n");

        List<String> deps = new GradleMetadata(dir.resolve("build.gradle").toString(), null).getProjectDependencies();
        assertEquals(2, deps.size());
        assertTrue(deps.contains("ejb"));
        assertTrue(deps.contains("common"));
    }

    /**
     * Verifies project dependency extraction across three additional forms.
     *
     * Kotlin DSL:
     *
     * settings.gradle.kts:
     *   rootProject.name = "war"
     *
     * build.gradle.kts:
     *   dependencies {
     *       implementation(project(":ejb"))
     *       api(project(":common"))
     *   }
     *
     * Expected: projectDependencies=[ejb, common].
     *
     * Multi-segment path:
     *
     * build.gradle:
     *   dependencies {
     *       implementation project(':group:name')
     *   }
     *
     * Expected: projectDependencies=[name] (only the last colon-delimited segment).
     *
     * Map notation:
     *
     * build.gradle:
     *   dependencies {
     *       implementation project(path: ':web', configuration: 'default')
     *   }
     *
     * Expected: projectDependencies=[web].
     */
    @Test
    public void projectDependencies_kotlinDslAndMultiSegmentAndMapNotation() throws Exception {
        Path kts = tempDir.resolve("dep-kotlin");
        write(kts.resolve("settings.gradle.kts"), "rootProject.name = \"war\"\n");
        write(kts.resolve("build.gradle.kts"),
              "dependencies {\n" +
                                               "    implementation(project(\":ejb\"))\n" +
                                               "    api(project(\":common\"))\n" +
                                               "}\n");

        List<String> d1 = new GradleMetadata(kts.resolve("build.gradle.kts").toString(), null).getProjectDependencies();
        assertEquals(2, d1.size());
        assertTrue(d1.contains("ejb"));
        assertTrue(d1.contains("common"));

        Path seg = tempDir.resolve("dep-segment");
        write(seg.resolve("settings.gradle"), "rootProject.name = 'app'\n");
        write(seg.resolve("build.gradle"),
              "dependencies {\n" +
                                           "    implementation project(':group:name')\n" +
                                           "}\n");

        List<String> d2 = new GradleMetadata(seg.resolve("build.gradle").toString(), null).getProjectDependencies();
        assertEquals(1, d2.size());
        assertEquals("name", d2.get(0), "Only the last segment of a multi-part path should be returned");

        Path map = tempDir.resolve("dep-map");
        write(map.resolve("settings.gradle"), "rootProject.name = 'app'\n");
        write(map.resolve("build.gradle"),
              "dependencies {\n" +
                                           "    implementation project(path: ':web', configuration: 'default')\n" +
                                           "}\n");

        List<String> d3 = new GradleMetadata(map.resolve("build.gradle").toString(), null).getProjectDependencies();
        assertEquals(1, d3.size());
        assertEquals("web", d3.get(0));
    }

    /**
     * Verifies two de-duplication and scoping rules simultaneously:
     * - A project(...) reference appearing only in a comment or an unrelated
     *   block is not included.
     * - The same dependency declared under two configuration names inside the
     *   dependencies block is included only once.
     *
     * settings.gradle:
     *   rootProject.name = 'app'
     *
     * build.gradle:
     *   // project(':notAdep') - comment
     *   someOtherBlock {
     *       project(':alsoNotAdep')
     *   }
     *   dependencies {
     *       implementation project(':realdep')
     *       testImplementation project(':realdep')
     *   }
     *
     * Expected: projectDependencies=[realdep] (size=1, no duplicates, no false positives).
     */
    @Test
    public void projectDependencies_noDuplicatesAndNotOutsideDependenciesBlock() throws Exception {
        Path dir = tempDir.resolve("dep-dedup");
        write(dir.resolve("settings.gradle"), "rootProject.name = 'app'\n");
        write(dir.resolve("build.gradle"),
              "// project(':notAdep') - comment\n" +
                                           "someOtherBlock {\n" +
                                           "    project(':alsoNotAdep')\n" +
                                           "}\n" +
                                           "dependencies {\n" +
                                           "    implementation project(':realdep')\n" +
                                           "    testImplementation project(':realdep')\n" +
                                           "}\n");

        List<String> deps = new GradleMetadata(dir.resolve("build.gradle").toString(), null).getProjectDependencies();
        assertEquals(1, deps.size(), "Only deps inside dependencies{} block, de-duplicated");
        assertEquals("realdep", deps.get(0));
    }

    /**
     * Verifies that a settings-only aggregator root (a project that has a
     * settings.gradle but no build.gradle) is correctly identified
     * as an aggregator. The settings file path is passed explicitly and
     * getBuildFilePath() returns null for such projects,
     * which accurately reflects the absence of a build file.
     *
     * settings.gradle:
     *   rootProject.name = 'multi-root'
     *   include 'web', 'ejb'
     *
     * Expected: projectName="multi-root", aggregator=true, subprojects size=2,
     * libertyPlugin=false, parentProjectName=null, buildFilePath=null,
     * settingsFilePath non-null.
     */
    @Test
    public void aggregatorOnlyRoot_settingsWithoutBuildFile() throws Exception {
        Path root = tempDir.resolve("agg-only");
        Files.createDirectories(root);
        Path settingsFile = root.resolve("settings.gradle");
        write(settingsFile,
              "rootProject.name = 'multi-root'\n" +
                             "include 'web', 'ejb'\n");

        GradleMetadata meta = new GradleMetadata(null, settingsFile.toString());

        assertEquals("multi-root", meta.getProjectName());
        assertTrue(meta.isAggregator());
        assertEquals(2, meta.getSubprojects().size());
        assertFalse(meta.isLibertyPluginConfigured());
        assertNull(meta.getParentProjectName());
        assertNull(meta.getBuildFilePath(), "Settings-only aggregator root must report null build file path");
        assertNotNull(meta.getSettingsFilePath(), "Settings file path must be recorded");
    }

    /**
     * Verifies the file-preference and fallback logic of GradleMetadata.findBuildFile.
     *
     * - When both build.gradle and build.gradle.kts exist, the Groovy DSL file is preferred.
     * - When only build.gradle.kts exists, it is returned.
     * - When neither exists, null is returned.
     */
    @Test
    public void findBuildFile_behaviour() throws Exception {
        Path both = tempDir.resolve("fb-both");
        write(both.resolve("build.gradle"), "");
        write(both.resolve("build.gradle.kts"), "");
        Path found = GradleMetadata.findBuildFile(both);
        assertNotNull(found);
        assertEquals("build.gradle", found.getFileName().toString(),
                     "Groovy DSL build file should be preferred over Kotlin DSL");

        Path ktsOnly = tempDir.resolve("fb-kts");
        write(ktsOnly.resolve("build.gradle.kts"), "");
        Path foundKts = GradleMetadata.findBuildFile(ktsOnly);
        assertNotNull(foundKts);
        assertEquals("build.gradle.kts", foundKts.getFileName().toString());

        Path empty = tempDir.resolve("fb-empty");
        Files.createDirectories(empty);
        assertNull(GradleMetadata.findBuildFile(empty));
    }
}
