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

import io.openliberty.tools.eclipse.model.MavenMetadata;

/**
 * Unit tests for MavenMetadata.
 */
public class MavenMetadataTest {

    static Path tempDir;

    /**
     * Creates the shared temporary root directory used by all tests.
     */
    @BeforeAll
    public static void setup() throws IOException {
        tempDir = Files.createTempDirectory("MavenMetadataTest-");
    }

    /**
     * Deletes all files written during a test, leaving the root directory
     * intact for the next test.
     */
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

    /**
     * Deletes the shared temporary root directory after all tests have run.
     */
    @AfterAll
    public static void tearDownAll() throws IOException {
        if (tempDir != null) {
            Files.deleteIfExists(tempDir);
        }
    }

    /**
     * Writes content to a pom.xml inside subdir,
     * creating parent directories if needed, and returns the file path.
     * Every POM used by the tests passes through this method.
     */
    private Path pom(String subdir, String content) throws IOException {
        Path dir = tempDir.resolve(subdir);
        Files.createDirectories(dir);
        Path file = dir.resolve("pom.xml");
        Files.writeString(file, content);
        return file;
    }

    /**
     * Verifies that the project name is read from the artifactId element
     * directly under project.
     *
     * pom.xml:
     *
     *   <project>
     *     <groupId>com.example</groupId>
     *     <artifactId>my-app</artifactId>
     *     <version>1.0</version>
     *     <packaging>war</packaging>
     *   </project>
     *
     * Expected: projectName="my-app".
     */
    @Test
    public void projectName_fromArtifactId() throws Exception {
        Path p = pom("pn-simple",
                     "<project>" +
                                  "  <groupId>com.example</groupId>" +
                                  "  <artifactId>my-app</artifactId>" +
                                  "  <version>1.0</version>" +
                                  "  <packaging>war</packaging>" +
                                  "</project>");

        assertEquals("my-app", new MavenMetadata(p.toString()).getProjectName());
    }

    /**
     * Verifies that the project artifactId is read from the project element and not from
     * a nested parent or dependency element. Also verifies that the parent name is
     * resolved from the parent block in the same parse.
     *
     * pom.xml:
     *
     *   <project>
     *     <parent>
     *       <groupId>com.example</groupId>
     *       <artifactId>parent-id</artifactId>
     *       <version>1.0</version>
     *     </parent>
     *     <artifactId>child-id</artifactId>
     *     <version>1.0</version>
     *     <dependencies>
     *       <dependency>
     *         <groupId>x</groupId>
     *         <artifactId>dep-id</artifactId>
     *         <version>1.0</version>
     *       </dependency>
     *     </dependencies>
     *   </project>
     *
     * Expected: projectName="child-id", parentProjectName="parent-id".
     */
    @Test
    public void projectName_usesProjectArtifactId_andParentNameResolved() throws Exception {
        Path p = pom("pn-child",
                     "<project>" +
                                 "  <parent>" +
                                 "    <groupId>com.example</groupId>" +
                                 "    <artifactId>parent-id</artifactId>" +
                                 "    <version>1.0</version>" +
                                 "  </parent>" +
                                 "  <artifactId>child-id</artifactId>" +
                                 "  <version>1.0</version>" +
                                 "  <dependencies>" +
                                 "    <dependency>" +
                                 "      <groupId>x</groupId>" +
                                 "      <artifactId>dep-id</artifactId>" +
                                 "      <version>1.0</version>" +
                                 "    </dependency>" +
                                 "  </dependencies>" +
                                 "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertEquals("child-id", meta.getProjectName());
        assertEquals("parent-id", meta.getParentProjectName());
    }

    /**
     * Verifies that getParentProjectName() returns null when the POM
     * has no parent element.
     *
     * pom.xml:
     *
     *   <project>
     *     <groupId>com.example</groupId>
     *     <artifactId>standalone</artifactId>
     *     <version>1.0</version>
     *   </project>
     *
     * Expected: parentProjectName=null.
     */
    @Test
    public void parentProjectName_nullWhenNoParentElement() throws Exception {
        Path p = pom("ppn-standalone",
                     "<project>" +
                                       "  <groupId>com.example</groupId>" +
                                       "  <artifactId>standalone</artifactId>" +
                                       "  <version>1.0</version>" +
                                       "</project>");

        assertNull(new MavenMetadata(p.toString()).getParentProjectName());
    }

    /**
     * Verifies that a POM with packaging=pom and a non-empty modules block
     * is recognised as an aggregator.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>root</artifactId>
     *     <version>1.0</version>
     *     <packaging>pom</packaging>
     *     <modules>
     *       <module>web</module>
     *       <module>ejb</module>
     *     </modules>
     *   </project>
     *
     * Expected: aggregator=true, subprojects=[web, ejb].
     */
    @Test
    public void aggregator_trueWhenPomPackagingAndModulesPresent() throws Exception {
        Path p = pom("agg-root",
                     "<project>" +
                                 "  <artifactId>root</artifactId>" +
                                 "  <version>1.0</version>" +
                                 "  <packaging>pom</packaging>" +
                                 "  <modules>" +
                                 "    <module>web</module>" +
                                 "    <module>ejb</module>" +
                                 "  </modules>" +
                                 "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertTrue(meta.isAggregator());
        List<String> subs = meta.getSubprojects();
        assertEquals(2, subs.size());
        assertTrue(subs.contains("web"));
        assertTrue(subs.contains("ejb"));
    }

    /**
     * Verifies that a POM with modules but non-pom packaging is not recognised as an
     * aggregator. Both conditions are required.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>root</artifactId>
     *     <version>1.0</version>
     *     <packaging>jar</packaging>
     *     <modules>
     *       <module>child</module>
     *     </modules>
     *   </project>
     *
     * Expected: aggregator=false.
     */
    @Test
    public void aggregator_falseWhenModulesPresentButPackagingIsNotPom() throws Exception {
        Path p = pom("agg-bad-packaging",
                     "<project>" +
                                          "  <artifactId>root</artifactId>" +
                                          "  <version>1.0</version>" +
                                          "  <packaging>jar</packaging>" +
                                          "  <modules>" +
                                          "    <module>child</module>" +
                                          "  </modules>" +
                                          "</project>");

        assertFalse(new MavenMetadata(p.toString()).isAggregator(),
                    "isAggregator must be false when packaging is not 'pom'");
    }

    /**
     * Verifies that a BOM or parent-only POM (packaging=pom but no modules) is not
     * recognised as an aggregator, and that getSubprojects() returns null or an empty list.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>my-bom</artifactId>
     *     <version>1.0</version>
     *     <packaging>pom</packaging>
     *   </project>
     *
     * Expected: aggregator=false, subprojects=null or empty.
     */
    @Test
    public void aggregator_falseWhenPomPackagingButNoModules() throws Exception {
        Path p = pom("agg-bom",
                     "<project>" +
                                "  <artifactId>my-bom</artifactId>" +
                                "  <version>1.0</version>" +
                                "  <packaging>pom</packaging>" +
                                "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertFalse(meta.isAggregator());
        assertTrue(meta.getSubprojects() == null || meta.getSubprojects().isEmpty());
    }

    /**
     * Verifies that getSubprojects() returns null or an empty list when no
     * modules element is present.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>app</artifactId>
     *     <version>1.0</version>
     *     <packaging>war</packaging>
     *   </project>
     *
     * Expected: subprojects=null or empty.
     */
    @Test
    public void subprojects_emptyListWhenNoneDeclared() throws Exception {
        Path p = pom("subs-none",
                     "<project>" +
                                  "  <artifactId>app</artifactId>" +
                                  "  <version>1.0</version>" +
                                  "  <packaging>war</packaging>" +
                                  "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertTrue(meta.getSubprojects() == null || meta.getSubprojects().isEmpty());
    }

    /**
     * Verifies that the Liberty Maven plugin is detected when declared in the
     * standard build/plugins section.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>app</artifactId>
     *     <version>1.0</version>
     *     <build>
     *       <plugins>
     *         <plugin>
     *           <groupId>io.openliberty.tools</groupId>
     *           <artifactId>liberty-maven-plugin</artifactId>
     *           <version>3.10.3</version>
     *         </plugin>
     *       </plugins>
     *     </build>
     *   </project>
     *
     * Expected: libertyPlugin=true, moduleDisabled=false.
     */
    @Test
    public void libertyPlugin_detectedInBuildSection() throws Exception {
        Path p = pom("lp-build",
                     "<project>" +
                                 "  <artifactId>app</artifactId>" +
                                 "  <version>1.0</version>" +
                                 "  <build>" +
                                 "    <plugins>" +
                                 "      <plugin>" +
                                 "        <groupId>io.openliberty.tools</groupId>" +
                                 "        <artifactId>liberty-maven-plugin</artifactId>" +
                                 "        <version>3.10.3</version>" +
                                 "      </plugin>" +
                                 "    </plugins>" +
                                 "  </build>" +
                                 "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertTrue(meta.isLibertyPluginConfigured());
        assertFalse(meta.isModuleDisabled());
    }

    /**
     * Verifies that the Liberty Maven plugin is not detected when only an unrelated
     * plugin is present in build/plugins.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>app</artifactId>
     *     <version>1.0</version>
     *     <build>
     *       <plugins>
     *         <plugin>
     *           <groupId>org.apache.maven.plugins</groupId>
     *           <artifactId>maven-war-plugin</artifactId>
     *           <version>3.3.1</version>
     *         </plugin>
     *       </plugins>
     *     </build>
     *   </project>
     *
     * Expected: libertyPlugin=false.
     */
    @Test
    public void libertyPlugin_notDetectedWhenAbsent() throws Exception {
        Path p = pom("lp-absent",
                     "<project>" +
                                  "  <artifactId>app</artifactId>" +
                                  "  <version>1.0</version>" +
                                  "  <build>" +
                                  "    <plugins>" +
                                  "      <plugin>" +
                                  "        <groupId>org.apache.maven.plugins</groupId>" +
                                  "        <artifactId>maven-war-plugin</artifactId>" +
                                  "        <version>3.3.1</version>" +
                                  "      </plugin>" +
                                  "    </plugins>" +
                                  "  </build>" +
                                  "</project>");

        assertFalse(new MavenMetadata(p.toString()).isLibertyPluginConfigured());
    }

    /**
     * Verifies that the Liberty Maven plugin is detected when it is declared inside
     * a profiles/profile/build/plugins section rather than the top-level build section.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>app</artifactId>
     *     <version>1.0</version>
     *     <profiles>
     *       <profile>
     *         <id>liberty-profile</id>
     *         <build>
     *           <plugins>
     *             <plugin>
     *               <groupId>io.openliberty.tools</groupId>
     *               <artifactId>liberty-maven-plugin</artifactId>
     *               <version>3.10.3</version>
     *             </plugin>
     *           </plugins>
     *         </build>
     *       </profile>
     *     </profiles>
     *   </project>
     *
     * Expected: libertyPlugin=true.
     */
    @Test
    public void libertyPlugin_detectedInProfileBuildSection() throws Exception {
        Path p = pom("lp-profile",
                     "<project>" +
                                   "  <artifactId>app</artifactId>" +
                                   "  <version>1.0</version>" +
                                   "  <profiles>" +
                                   "    <profile>" +
                                   "      <id>liberty-profile</id>" +
                                   "      <build>" +
                                   "        <plugins>" +
                                   "          <plugin>" +
                                   "            <groupId>io.openliberty.tools</groupId>" +
                                   "            <artifactId>liberty-maven-plugin</artifactId>" +
                                   "            <version>3.10.3</version>" +
                                   "          </plugin>" +
                                   "        </plugins>" +
                                   "      </build>" +
                                   "    </profile>" +
                                   "  </profiles>" +
                                   "</project>");

        assertTrue(new MavenMetadata(p.toString()).isLibertyPluginConfigured(),
                   "Liberty plugin should be detected when declared inside a <profile><build> section");
    }

    /**
     * Verifies that the Liberty Maven plugin is detected when it is declared inside
     * build/pluginManagement/plugins, as is common in parent POMs that centralise
     * plugin version management.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>parent</artifactId>
     *     <version>1.0</version>
     *     <packaging>pom</packaging>
     *     <build>
     *       <pluginManagement>
     *         <plugins>
     *           <plugin>
     *             <groupId>io.openliberty.tools</groupId>
     *             <artifactId>liberty-maven-plugin</artifactId>
     *             <version>3.10.3</version>
     *           </plugin>
     *         </plugins>
     *       </pluginManagement>
     *     </build>
     *   </project>
     *
     * Expected: libertyPlugin=true.
     */
    @Test
    public void libertyPlugin_detectedInPluginManagement() throws Exception {
        Path p = pom("lp-pluginmgmt",
                     "<project>" +
                                      "  <artifactId>parent</artifactId>" +
                                      "  <version>1.0</version>" +
                                      "  <packaging>pom</packaging>" +
                                      "  <build>" +
                                      "    <pluginManagement>" +
                                      "      <plugins>" +
                                      "        <plugin>" +
                                      "          <groupId>io.openliberty.tools</groupId>" +
                                      "          <artifactId>liberty-maven-plugin</artifactId>" +
                                      "          <version>3.10.3</version>" +
                                      "        </plugin>" +
                                      "      </plugins>" +
                                      "    </pluginManagement>" +
                                      "  </build>" +
                                      "</project>");

        assertTrue(new MavenMetadata(p.toString()).isLibertyPluginConfigured(),
                   "Liberty plugin should be detected when declared inside <pluginManagement>");
    }

    /**
     * Verifies that isModuleDisabled() returns true when skip=true is set in the
     * Liberty plugin configuration, and that the plugin itself is still detected as configured.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>app</artifactId>
     *     <version>1.0</version>
     *     <build>
     *       <plugins>
     *         <plugin>
     *           <groupId>io.openliberty.tools</groupId>
     *           <artifactId>liberty-maven-plugin</artifactId>
     *           <version>3.10.3</version>
     *           <configuration>
     *             <skip>true</skip>
     *           </configuration>
     *         </plugin>
     *       </plugins>
     *     </build>
     *   </project>
     *
     * Expected: libertyPlugin=true, moduleDisabled=true.
     */
    @Test
    public void moduleDisabled_trueWhenSkipIsTrue() throws Exception {
        Path p = pom("skip-true",
                     "<project>" +
                                  "  <artifactId>app</artifactId>" +
                                  "  <version>1.0</version>" +
                                  "  <build>" +
                                  "    <plugins>" +
                                  "      <plugin>" +
                                  "        <groupId>io.openliberty.tools</groupId>" +
                                  "        <artifactId>liberty-maven-plugin</artifactId>" +
                                  "        <version>3.10.3</version>" +
                                  "        <configuration>" +
                                  "          <skip>true</skip>" +
                                  "        </configuration>" +
                                  "      </plugin>" +
                                  "    </plugins>" +
                                  "  </build>" +
                                  "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertTrue(meta.isLibertyPluginConfigured(), "Plugin should still be detected even when skip=true");
        assertTrue(meta.isModuleDisabled(), "isModuleDisabled should be true when <skip>true</skip>");
    }

    /**
     * Verifies three variants where {@code isModuleDisabled()} must return {@code false}:
     */
    @Test
    public void moduleDisabled_falseVariants() throws Exception {
        Path skipFalse = pom("skip-false",
                             "<project>" +
                                           "  <artifactId>app</artifactId>" +
                                           "  <version>1.0</version>" +
                                           "  <build>" +
                                           "    <plugins>" +
                                           "      <plugin>" +
                                           "        <groupId>io.openliberty.tools</groupId>" +
                                           "        <artifactId>liberty-maven-plugin</artifactId>" +
                                           "        <version>3.10.3</version>" +
                                           "        <configuration><skip>false</skip></configuration>" +
                                           "      </plugin>" +
                                           "    </plugins>" +
                                           "  </build>" +
                                           "</project>");

        MavenMetadata metaFalse = new MavenMetadata(skipFalse.toString());
        assertTrue(metaFalse.isLibertyPluginConfigured());
        assertFalse(metaFalse.isModuleDisabled());

        Path noSkip = pom("skip-absent",
                          "<project>" +
                                         "  <artifactId>app</artifactId>" +
                                         "  <version>1.0</version>" +
                                         "  <build>" +
                                         "    <plugins>" +
                                         "      <plugin>" +
                                         "        <groupId>io.openliberty.tools</groupId>" +
                                         "        <artifactId>liberty-maven-plugin</artifactId>" +
                                         "        <version>3.10.3</version>" +
                                         "      </plugin>" +
                                         "    </plugins>" +
                                         "  </build>" +
                                         "</project>");

        assertFalse(new MavenMetadata(noSkip.toString()).isModuleDisabled());

        Path noPlugin = pom("skip-no-plugin",
                            "<project>" +
                                              "  <artifactId>app</artifactId>" +
                                              "  <version>1.0</version>" +
                                              "</project>");

        assertFalse(new MavenMetadata(noPlugin.toString()).isModuleDisabled());
    }

    /**
     * Verifies that dependencies are collected from all three locations where Maven
     * allows them: the top-level dependencies section, dependencyManagement, and a
     * profile's dependencies section.
     *
     * Scenario 1: regular dependencies (all scopes collected):
     *
     *   <dependencies>
     *     <dependency>
     *       <artifactId>jar-module</artifactId>
     *       ...
     *     </dependency>
     *     <dependency>
     *       <artifactId>junit-jupiter</artifactId>
     *       <scope>test</scope>
     *       ...
     *     </dependency>
     *   </dependencies>
     *
     * Expected: [jar-module, junit-jupiter].
     *
     * Scenario 2: dependency management:
     *
     *   <dependencyManagement>
     *     <dependencies>
     *       <dependency>
     *         <artifactId>managed-dep</artifactId>
     *         ...
     *       </dependency>
     *     </dependencies>
     *   </dependencyManagement>
     *
     * Expected: contains "managed-dep".
     *
     * Scenario 3: profile dependencies:
     *
     *   <profiles>
     *     <profile>
     *       <id>special</id>
     *       <dependencies>
     *         <dependency>
     *           <artifactId>profile-dep</artifactId>
     *           ...
     *         </dependency>
     *       </dependencies>
     *     </profile>
     *   </profiles>
     *
     * Expected: contains "profile-dep".
     */
    @Test
    public void projectDependencies_fromAllSections() throws Exception {
        Path deps = pom("dep-regular",
                        "<project>" +
                                       "  <artifactId>war-module</artifactId>" +
                                       "  <version>1.0</version>" +
                                       "  <dependencies>" +
                                       "    <dependency>" +
                                       "      <groupId>io.openliberty.guides</groupId>" +
                                       "      <artifactId>jar-module</artifactId>" +
                                       "      <version>1.0</version>" +
                                       "    </dependency>" +
                                       "    <dependency>" +
                                       "      <groupId>org.junit.jupiter</groupId>" +
                                       "      <artifactId>junit-jupiter</artifactId>" +
                                       "      <version>5.8.1</version>" +
                                       "      <scope>test</scope>" +
                                       "    </dependency>" +
                                       "  </dependencies>" +
                                       "</project>");

        List<String> d1 = new MavenMetadata(deps.toString()).getProjectDependencies();
        assertEquals(2, d1.size());
        assertTrue(d1.contains("jar-module"));
        assertTrue(d1.contains("junit-jupiter"));

        Path mgmt = pom("dep-mgmt",
                        "<project>" +
                                    "  <artifactId>parent</artifactId>" +
                                    "  <version>1.0</version>" +
                                    "  <packaging>pom</packaging>" +
                                    "  <dependencyManagement>" +
                                    "    <dependencies>" +
                                    "      <dependency>" +
                                    "        <groupId>io.openliberty.guides</groupId>" +
                                    "        <artifactId>managed-dep</artifactId>" +
                                    "        <version>1.0</version>" +
                                    "      </dependency>" +
                                    "    </dependencies>" +
                                    "  </dependencyManagement>" +
                                    "</project>");

        assertTrue(new MavenMetadata(mgmt.toString()).getProjectDependencies().contains("managed-dep"),
                   "Dependencies in <dependencyManagement> should be included");

        Path profile = pom("dep-profile",
                           "<project>" +
                                          "  <artifactId>app</artifactId>" +
                                          "  <version>1.0</version>" +
                                          "  <profiles>" +
                                          "    <profile>" +
                                          "      <id>special</id>" +
                                          "      <dependencies>" +
                                          "        <dependency>" +
                                          "          <groupId>io.openliberty.guides</groupId>" +
                                          "          <artifactId>profile-dep</artifactId>" +
                                          "          <version>1.0</version>" +
                                          "        </dependency>" +
                                          "      </dependencies>" +
                                          "    </profile>" +
                                          "  </profiles>" +
                                          "</project>");

        assertTrue(new MavenMetadata(profile.toString()).getProjectDependencies().contains("profile-dep"),
                   "Dependencies inside a <profile> should be included");
    }

    /**
     * Verifies that the same artifactId appearing in both dependencies and
     * dependencyManagement is returned only once.
     *
     * pom.xml:
     *
     *   <dependencies>
     *     <dependency>
     *       <artifactId>shared</artifactId>
     *       ...
     *     </dependency>
     *   </dependencies>
     *   <dependencyManagement>
     *     <dependencies>
     *       <dependency>
     *         <artifactId>shared</artifactId>
     *         ...
     *       </dependency>
     *     </dependencies>
     *   </dependencyManagement>
     *
     * Expected: projectDependencies=[shared] (size=1, no duplicate).
     */
    @Test
    public void projectDependencies_noDuplicates() throws Exception {
        Path p = pom("dep-dedup",
                     "<project>" +
                                  "  <artifactId>app</artifactId>" +
                                  "  <version>1.0</version>" +
                                  "  <dependencies>" +
                                  "    <dependency>" +
                                  "      <groupId>com.example</groupId>" +
                                  "      <artifactId>shared</artifactId>" +
                                  "      <version>1.0</version>" +
                                  "    </dependency>" +
                                  "  </dependencies>" +
                                  "  <dependencyManagement>" +
                                  "    <dependencies>" +
                                  "      <dependency>" +
                                  "        <groupId>com.example</groupId>" +
                                  "        <artifactId>shared</artifactId>" +
                                  "        <version>1.0</version>" +
                                  "      </dependency>" +
                                  "    </dependencies>" +
                                  "  </dependencyManagement>" +
                                  "</project>");

        List<String> deps = new MavenMetadata(p.toString()).getProjectDependencies();
        assertEquals(1, deps.size(), "Duplicate artifactIds should appear only once");
        assertEquals("shared", deps.get(0));
    }

    /**
     * Verifies that getProjectDependencies() returns an empty list when the
     * POM declares no dependencies at all.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>app</artifactId>
     *     <version>1.0</version>
     *   </project>
     *
     * Expected: projectDependencies=[] (empty).
     */
    @Test
    public void projectDependencies_emptyWhenNoneDeclared() throws Exception {
        Path p = pom("dep-none",
                     "<project>" +
                                 "  <artifactId>app</artifactId>" +
                                 "  <version>1.0</version>" +
                                 "</project>");

        assertTrue(new MavenMetadata(p.toString()).getProjectDependencies().isEmpty());
    }

    /**
     * Verifies that getBuildFilePath() returns the exact path string that was
     * passed to the constructor.
     *
     * pom.xml:
     *
     *   <project>
     *     <artifactId>app</artifactId>
     *     <version>1.0</version>
     *   </project>
     *
     * Expected: buildFilePath equals the absolute path of the written pom.xml.
     */
    @Test
    public void buildFilePath_matchesConstructorArg() throws Exception {
        Path p = pom("bfp",
                     "<project>" +
                            "  <artifactId>app</artifactId>" +
                            "  <version>1.0</version>" +
                            "</project>");

        assertEquals(p.toString(), new MavenMetadata(p.toString()).getBuildFilePath());
    }

    /**
     * Mirrors the root aggregator POM of the guide-maven-multimodules project.
     *
     * pom.xml:
     *
     *   <project xmlns="http://maven.apache.org/POM/4.0.0">
     *     <groupId>io.openliberty.guides</groupId>
     *     <artifactId>guide-maven-multimodules</artifactId>
     *     <version>1.0-SNAPSHOT</version>
     *     <packaging>pom</packaging>
     *     <modules>
     *       <module>jar</module>
     *       <module>war1</module>
     *       <module>war2</module>
     *       <module>pom</module>
     *     </modules>
     *   </project>
     *
     * Expected: projectName="guide-maven-multimodules", parentProjectName=null,
     * aggregator=true, subprojects=[jar, war1, war2, pom],
     * libertyPlugin=false, moduleDisabled=false.
     */
    @Test
    public void realWorld_rootAggregatorPom() throws Exception {
        Path p = pom("rw-root",
                     "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">" +
                                "  <modelVersion>4.0.0</modelVersion>" +
                                "  <groupId>io.openliberty.guides</groupId>" +
                                "  <artifactId>guide-maven-multimodules</artifactId>" +
                                "  <version>1.0-SNAPSHOT</version>" +
                                "  <packaging>pom</packaging>" +
                                "  <modules>" +
                                "    <module>jar</module>" +
                                "    <module>war1</module>" +
                                "    <module>war2</module>" +
                                "    <module>pom</module>" +
                                "  </modules>" +
                                "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertEquals("guide-maven-multimodules", meta.getProjectName());
        assertNull(meta.getParentProjectName());
        assertTrue(meta.isAggregator());
        List<String> subs = meta.getSubprojects();
        assertEquals(4, subs.size());
        assertTrue(subs.contains("jar"));
        assertTrue(subs.contains("war1"));
        assertTrue(subs.contains("war2"));
        assertTrue(subs.contains("pom"));
        assertFalse(meta.isLibertyPluginConfigured());
        assertFalse(meta.isModuleDisabled());
    }

    /**
     * Mirrors a Liberty server submodule POM (guide-maven-multimodules-pom/pom.xml).
     * This submodule has packaging=pom, declares war dependencies,
     * and configures the Liberty Maven plugin directly.
     *
     * pom.xml:
     *
     *   <project xmlns="http://maven.apache.org/POM/4.0.0">
     *     <artifactId>guide-maven-multimodules-pom</artifactId>
     *     <version>1.0-SNAPSHOT</version>
     *     <packaging>pom</packaging>
     *     <dependencies>
     *       <dependency>
     *         <artifactId>guide-maven-multimodules-war1</artifactId>
     *         <type>war</type>
     *       </dependency>
     *       <dependency>
     *         <artifactId>guide-maven-multimodules-war2</artifactId>
     *         <type>war</type>
     *       </dependency>
     *     </dependencies>
     *     <build>
     *       <plugins>
     *         <plugin>
     *           <groupId>io.openliberty.tools</groupId>
     *           <artifactId>liberty-maven-plugin</artifactId>
     *           <version>3.10.3</version>
     *         </plugin>
     *       </plugins>
     *     </build>
     *   </project>
     *
     * Expected: projectName="guide-maven-multimodules-pom", libertyPlugin=true,
     * moduleDisabled=false, deps contain war1 and war2 artifactIds.
     */
    @Test
    public void realWorld_libertyServerSubmodule() throws Exception {
        Path p = pom("rw-liberty-sub",
                     "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                                       "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">" +
                                       "  <modelVersion>4.0.0</modelVersion>" +
                                       "  <groupId>io.openliberty.guides</groupId>" +
                                       "  <artifactId>guide-maven-multimodules-pom</artifactId>" +
                                       "  <version>1.0-SNAPSHOT</version>" +
                                       "  <packaging>pom</packaging>" +
                                       "  <dependencies>" +
                                       "    <dependency>" +
                                       "      <groupId>io.openliberty.guides</groupId>" +
                                       "      <artifactId>guide-maven-multimodules-war1</artifactId>" +
                                       "      <version>1.0-SNAPSHOT</version>" +
                                       "      <type>war</type>" +
                                       "    </dependency>" +
                                       "    <dependency>" +
                                       "      <groupId>io.openliberty.guides</groupId>" +
                                       "      <artifactId>guide-maven-multimodules-war2</artifactId>" +
                                       "      <version>1.0-SNAPSHOT</version>" +
                                       "      <type>war</type>" +
                                       "    </dependency>" +
                                       "  </dependencies>" +
                                       "  <build>" +
                                       "    <plugins>" +
                                       "      <plugin>" +
                                       "        <groupId>io.openliberty.tools</groupId>" +
                                       "        <artifactId>liberty-maven-plugin</artifactId>" +
                                       "        <version>3.10.3</version>" +
                                       "      </plugin>" +
                                       "    </plugins>" +
                                       "  </build>" +
                                       "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertEquals("guide-maven-multimodules-pom", meta.getProjectName());
        assertTrue(meta.isLibertyPluginConfigured());
        assertFalse(meta.isModuleDisabled());
        List<String> deps = meta.getProjectDependencies();
        assertTrue(deps.contains("guide-maven-multimodules-war1"));
        assertTrue(deps.contains("guide-maven-multimodules-war2"));
    }

    /**
     * Mirrors a pure jar submodule (guide-maven-multimodules-jar/pom.xml) - no Liberty plugin,
     * no parent declaration, no dependencies.
     *
     * pom.xml:
     *
     *   <project xmlns="http://maven.apache.org/POM/4.0.0">
     *     <groupId>io.openliberty.guides</groupId>
     *     <artifactId>guide-maven-multimodules-jar</artifactId>
     *     <version>1.0-SNAPSHOT</version>
     *   </project>
     *
     * Expected: projectName="guide-maven-multimodules-jar", parentProjectName=null,
     * aggregator=false, libertyPlugin=false, moduleDisabled=false, dependencies=[].
     */
    @Test
    public void realWorld_pureJarSubmodule() throws Exception {
        Path p = pom("rw-jar",
                     "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                               "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">" +
                               "  <modelVersion>4.0.0</modelVersion>" +
                               "  <groupId>io.openliberty.guides</groupId>" +
                               "  <artifactId>guide-maven-multimodules-jar</artifactId>" +
                               "  <version>1.0-SNAPSHOT</version>" +
                               "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertEquals("guide-maven-multimodules-jar", meta.getProjectName());
        assertNull(meta.getParentProjectName());
        assertFalse(meta.isAggregator());
        assertFalse(meta.isLibertyPluginConfigured());
        assertFalse(meta.isModuleDisabled());
        assertTrue(meta.getProjectDependencies().isEmpty());
    }

    /**
     * Mirrors a single-module Liberty WAR application (liberty-maven-test-app/pom.xml)
     * with a servlet dependency, a shared library dependency, and the Liberty Maven plugin
     * configured with a custom serverStartTimeout.
     *
     * pom.xml:
     *
     *   <project xmlns="http://maven.apache.org/POM/4.0.0">
     *     <groupId>test</groupId>
     *     <artifactId>liberty.maven.test.app</artifactId>
     *     <packaging>war</packaging>
     *     <version>0.0.1-SNAPSHOT</version>
     *     <dependencies>
     *       <dependency>
     *         <artifactId>javax.servlet-api</artifactId>
     *         <scope>provided</scope>
     *       </dependency>
     *       <dependency>
     *         <artifactId>shared-lib</artifactId>
     *       </dependency>
     *     </dependencies>
     *     <build>
     *       <plugins>
     *         <plugin>
     *           <groupId>io.openliberty.tools</groupId>
     *           <artifactId>liberty-maven-plugin</artifactId>
     *           <version>3.10.3</version>
     *           <configuration>
     *             <serverStartTimeout>120</serverStartTimeout>
     *           </configuration>
     *         </plugin>
     *       </plugins>
     *     </build>
     *   </project>
     *
     * Expected: projectName="liberty.maven.test.app", parentProjectName=null,
     * aggregator=false, libertyPlugin=true, moduleDisabled=false,
     * deps contain javax.servlet-api and shared-lib.
     */
    @Test
    public void realWorld_singleModuleLibertyWarApp() throws Exception {
        Path p = pom("rw-single-war",
                     "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                                      "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">" +
                                      "  <modelVersion>4.0.0</modelVersion>" +
                                      "  <groupId>test</groupId>" +
                                      "  <artifactId>liberty.maven.test.app</artifactId>" +
                                      "  <packaging>war</packaging>" +
                                      "  <version>0.0.1-SNAPSHOT</version>" +
                                      "  <dependencies>" +
                                      "    <dependency>" +
                                      "      <groupId>javax.servlet</groupId>" +
                                      "      <artifactId>javax.servlet-api</artifactId>" +
                                      "      <version>3.1.0</version>" +
                                      "      <scope>provided</scope>" +
                                      "    </dependency>" +
                                      "    <dependency>" +
                                      "      <groupId>test</groupId>" +
                                      "      <artifactId>shared-lib</artifactId>" +
                                      "      <version>1.0-SNAPSHOT</version>" +
                                      "    </dependency>" +
                                      "  </dependencies>" +
                                      "  <build>" +
                                      "    <plugins>" +
                                      "      <plugin>" +
                                      "        <groupId>io.openliberty.tools</groupId>" +
                                      "        <artifactId>liberty-maven-plugin</artifactId>" +
                                      "        <version>3.10.3</version>" +
                                      "        <configuration>" +
                                      "          <serverStartTimeout>120</serverStartTimeout>" +
                                      "        </configuration>" +
                                      "      </plugin>" +
                                      "    </plugins>" +
                                      "  </build>" +
                                      "</project>");

        MavenMetadata meta = new MavenMetadata(p.toString());
        assertEquals("liberty.maven.test.app", meta.getProjectName());
        assertNull(meta.getParentProjectName());
        assertFalse(meta.isAggregator());
        assertTrue(meta.isLibertyPluginConfigured());
        assertFalse(meta.isModuleDisabled());
        List<String> deps = meta.getProjectDependencies();
        assertTrue(deps.contains("javax.servlet-api"));
        assertTrue(deps.contains("shared-lib"));
    }
}
