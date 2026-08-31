/*******************************************************************************
* Copyright (c) 2022, 2026 IBM Corporation and others.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;

import io.openliberty.tools.eclipse.LibertyNature;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;
import io.openliberty.tools.eclipse.utils.ErrorHandler;

/**
 * This class models Eclipse workspace projects and their relationships in the context
 * of Liberty development. It tracks build types (Maven/Gradle), multi-module project
 * hierarchies, and Liberty server configuration presence.
 *
 * @see WorkspaceModel
 */
public class ProjectModel {

    /** Maven project nature identifier used by Eclipse M2E plugin. */
    public static final String MAVEN_NATURE = "org.eclipse.m2e.core.maven2Nature";

    /** Gradle project nature identifier used by Eclipse Buildship plugin. */
    public static final String GRADLE_NATURE = "org.eclipse.buildship.core.gradleprojectnature";

    /** Java project nature identifier used by Eclipse JDT. */
    public static final String JAVA_NATURE_ID = "org.eclipse.jdt.core.javanature";

    /** Enumeration of supported build types. */
    public static enum BuildType {
        Unknown,
        Gradle,
        Maven
    };

    /**
     * Enumeration of the lifecycle states that a Liberty module (leaf) can be in.
     * The parent's visual state is derived dynamically from its children.
     */
    public enum AppState {
        /** Dev mode process has started but the Liberty server has not yet reported ready. */
        STARTING,
        /** Liberty server has reported {@code CWWKZ0001I:} — the application is fully started. */
        RUNNING,
        /** A stop has been requested but the Liberty server has not yet confirmed shutdown. */
        STOPPING,
        /** Dev mode process is not running (initial state). */
        STOPPED
    }

    /** The Eclipse project reference. */
    private IProject iProject;

    /** Build type associated with this project (Maven, Gradle, or Unknown). */
    private BuildType type;

    /** The parent of this project in a multi-module structure, or {@code null} if standalone. */
    private ProjectModel parentProjectModel;

    /**
     * Indicates whether this module has Liberty server configuration files such as
     * server.xml, bootstrap.properties, server.env, or the Liberty plugin configuration.
     */
    private boolean libertyServerModule;

    /** Indicates whether this project is a parent/aggregator of modules with Liberty configuration. */
    private boolean isParentOfServerModule;

    /** Indicates whether this module has test source files. */
    private boolean hasTests;

    /** The metadata associated with the project's build configuration. */
    private Metadata buildConfigMetadata;

    /** Current dev mode / application lifecycle state for this module. */
    private volatile AppState appState = AppState.STOPPED;

    /** Indicates whether or not this project was started as part of a batch start. */
    private boolean batchStarted = false;

    /**
     * The child projects associated with this project in a multi-module structure.
     * Thread-safe set to support concurrent access during workspace model building.
     */
    private Set<ProjectModel> childDirProjects = ConcurrentHashMap.newKeySet();

    /**
     * The set of peer projects (siblings in the same parent directory).
     */
    private Set<ProjectModel> peerDirProjects = ConcurrentHashMap.newKeySet();

    /**
     * The set of dependent projects explicitly declared in the build configuration
     * (Maven modules or Gradle subprojects). This is a subset of childDirProjects
     * that represents actual build dependencies.
     */
    private Set<ProjectModel> dependentProjects = ConcurrentHashMap.newKeySet();

    /**
     * Constructor.
     * 
     * @param project The Eclipse project reference.
     */
    public ProjectModel(IProject project) {
        this.iProject = project;
        this.type = findBuildType();
    }

    /**
     * Checks whether this project has the Liberty nature.
     *
     * The Liberty nature is added to projects that have Liberty server configuration
     * or are parents of modules with Liberty configuration. This nature enables
     * Liberty-specific UI elements and commands in Eclipse.
     *
     * @return True if the project has the Liberty nature. False otherwise.
     */
    public boolean hasLibertyNature() {
        try {
            return iProject.getDescription().hasNature(LibertyNature.NATURE_ID);
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                        "An error occurred while attempting to find the nature of project " + iProject.getName(), e);
            }
            return false;
        }
    }

    /**
     * Returns the build type associated with this project.
     * 
     * @return The build type associated with this project.
     */
    public BuildType getBuildType() {
        return type;
    }

    /**
     * Returns the build configuration metadata for this project.
     *
     * @return The build configuration metadata.
     */
    public Metadata getBuildConfigMetadata() {
        return buildConfigMetadata;
    }

    /**
     * Finds the build type to be associated with this project.
     *
     * <p>The detection strategy is:</p>
     * <ol>
     * <li>Check for Maven or Gradle nature in the project description</li>
     * <li>If no nature found, check for build files (pom.xml or build.gradle)</li>
     * <li>If both Maven and Gradle are present, Maven takes precedence</li>
     * </ol>
     *
     * @return the detected build type (MAVEN, GRADLE, or UNKNOWN)
     */
    private BuildType findBuildType() {

        // Check the installed project's nature, but only trust it when the
        // corresponding build file is also present on disk.  A nature can be
        // stale — left behind from a previous import — while the actual build
        // file no longer exists at the project location.
        try {
            if (iProject.getDescription().hasNature(MAVEN_NATURE)
                && iProject.getFile("pom.xml").exists()) {
                return BuildType.Maven;
            } else if (iProject.getDescription().hasNature(GRADLE_NATURE)
                       && (iProject.getFile("build.gradle").exists()
                           || iProject.getFile("build.gradle.kts").exists())) {
                return BuildType.Gradle;
            }
        } catch (Exception e) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                        "An error occurred while attempting to find the nature of project " + iProject.getName(), e);
            }
        }

        // Fall back to build file existence only.
        if (iProject.getFile("pom.xml").exists()) {
            return BuildType.Maven;
        } else if (iProject.getFile("build.gradle").exists() || iProject.getFile("build.gradle.kts").exists()) {
            return BuildType.Gradle;
        }

        return BuildType.Unknown;

    }

    /**
     * Gets the associated Eclipse project reference.
     * 
     * @return The associated Eclipse project reference.
     */
    public IProject getIProject() {
        return iProject;
    }

    /**
     * Retrieves the absolute path of this project.
     *
     * @return The absolute path of this project, or null if the path could not be obtained.
     */
    public String getPath() {
        if (iProject != null) {
            IPath path = iProject.getLocation();
            if (path != null) {
                return path.toOSString();
            }
        }

        return null;
    }

    /**
     * Returns the list of child projects that contain Liberty server configuration.
     *
     * @return The list of child projects that contain Liberty server configuration.
     */
    public List<ProjectModel> getChildLibertyServerProjects() {
        ArrayList<ProjectModel> clsps = new ArrayList<ProjectModel>();

        for (ProjectModel child : childDirProjects) {
            if (child.isLibertyServerModule()) {
                clsps.add(child);
            }
        }

        return clsps;
    }

    /**
     * Returns the list of child projects.
     *
     * @return The list of child projects.
     */
    public List<ProjectModel> getChildProjects() {
        return new ArrayList<>(childDirProjects);
    }

    /**
     * Returns the list of child projects that contain the java nature.
     *
     * @return The list of child projects that contain the java nature.
     */
    public List<ProjectModel> getChildJavaProjects() {
        return filterJavaProjects(childDirProjects);
    }

    /**
     * Returns the list of peer projects that contain the java nature.
     * 
     * @return The list of peer projects that contain the java nature.
     */
    public List<ProjectModel> getPeerJavaProjects() {
        return filterJavaProjects(peerDirProjects);
    }

    /**
     * Returns the list of projects that contain the Java nature from the input set.
     * 
     * @param projects The set of projects to filter.
     * 
     * @return The list of projects that contain the Java nature from the input set.
     */
    public List<ProjectModel> filterJavaProjects(Set<ProjectModel> projects) {
        ArrayList<ProjectModel> javaProjects = new ArrayList<ProjectModel>();
        for (ProjectModel child : projects) {
            try {
                if (child.getIProject().hasNature(JAVA_NATURE_ID)) {
                    javaProjects.add(child);
                }
            } catch (CoreException e) {
                ErrorHandler.processWarningMessage(Messages.getMessage("determine_java_project_error", child.getName()), e, false);
            }
        }
        return javaProjects;
    }

    /**
     * Returns a Java project that is a peer or child of the input project.
     *
     * @param project The project to search for.
     *
     * @return A Java project that is a peer or child of the input project.
     *
     * @throws Exception if none of the associated projects is a Java project.
     */
    public ProjectModel getAssociatedJavaProject(ProjectModel project) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, project);
        }

        ProjectModel aJProject = null;

        // Find a child java project.
        List<ProjectModel> jProjects = project.getChildJavaProjects();
        if (!jProjects.isEmpty()) {
            aJProject = jProjects.get(0);
        }

        // find a peer Java project.
        if (aJProject == null) {
            jProjects = project.getPeerJavaProjects();
            if (!jProjects.isEmpty()) {
                aJProject = jProjects.get(0);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, aJProject);
        }

        return aJProject;
    }

    /**
     * Classifies this project as a project able to run on a Liberty server.
     */
    public void classifyAsLibertyServerModule() {
        try {
            IFile serverxml = iProject.getFile(new org.eclipse.core.runtime.Path("src/main/liberty/config/server.xml"));
            IFile bootstrapProps = iProject.getFile(new org.eclipse.core.runtime.Path("src/main/liberty/config/bootstrap.properties"));
            IFile serverenv = iProject.getFile(new org.eclipse.core.runtime.Path("src/main/liberty/config/server.env"));
            boolean isLibertyPluginConfigured = (buildConfigMetadata != null) ? buildConfigMetadata.isLibertyPluginConfigured() : false;
            boolean isModuleDisabled = (buildConfigMetadata != null) ? buildConfigMetadata.isModuleDisabled() : false;

            if (!isModuleDisabled && (serverxml.exists() || bootstrapProps.exists() || serverenv.exists() || isLibertyPluginConfigured)) {
                libertyServerModule = true;
            } else {
                libertyServerModule = false;
            }
        } catch (Exception e) {
            ErrorHandler.processWarningMessage(Messages.getMessage("liberty_nature_add_error"), e, false);
        }
    }

    /**
     * Adds the Liberty nature to the project if it is not already present.
     */
    public void classifyAsLibertyNature() {
        try {
            if (libertyServerModule) {
                ProjectModel.addNature(iProject, LibertyNature.NATURE_ID);
            }

            for (ProjectModel child : childDirProjects) {
                if (child.isLibertyServerModule()) {
                    ProjectModel.addNature(iProject, LibertyNature.NATURE_ID);
                    isParentOfServerModule = true;
                    break;
                }
            }
        } catch (Exception e) {
            ErrorHandler.processWarningMessage(Messages.getMessage("liberty_nature_add_error"), e, false);
        }
    }

    /**
     * Adds the specified nature ID to the project's description/metadata (.project).
     * 
     * @param project  The project to process.
     * @param natureId The nature ID to add.
     * 
     * @throws Exception
     */
    public static void addNature(IProject project, String natureId) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { project, natureId });
        }

        if (project.getDescription().hasNature(natureId)) {
            return;
        }

        IPath projectPath = project.getLocation().addTrailingSeparator().append(".project");

        IProjectDescription projectDesc = ResourcesPlugin.getWorkspace().loadProjectDescription(projectPath);
        String[] currentNatures = projectDesc.getNatureIds();
        String[] newNatures = new String[currentNatures.length + 1];
        System.arraycopy(currentNatures, 0, newNatures, 0, currentNatures.length);
        newNatures[currentNatures.length] = natureId;
        projectDesc.setNatureIds(newNatures);
        project.setDescription(projectDesc, new NullProgressMonitor());

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, newNatures);
        }
    }

    /**
     * Removes the specified nature ID from the project's description/metadata (.project).
     *
     * @param project  The project to process.
     * @param natureId The nature ID to remove.
     *
     * @throws Exception
     */
    public static void removeNature(IProject project, String natureId) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, project);
        }

        IProjectDescription projectDesc = project.getDescription();
        String[] currentNatures = projectDesc.getNatureIds();
        ArrayList<String> newNatures = new ArrayList<String>(currentNatures.length - 1);

        for (int i = 0; i < currentNatures.length; i++) {
            if (currentNatures[i].equals(natureId)) {
                continue;
            }
            newNatures.add(currentNatures[i]);
        }

        projectDesc.setNatureIds(newNatures.toArray(new String[newNatures.size()]));
        project.setDescription(projectDesc, new NullProgressMonitor());

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, newNatures);
        }
    }

    /**
     * Returns true if the project has the specified nature, false otherwise.
     *
     * @param nature The nature to check for.
     *
     * @return true if the project has the specified nature, false otherwise.
     *
     * @throws CoreException if the nature check fails.
     */
    public boolean hasNature(String nature) throws CoreException {
        return iProject.hasNature(nature);
    }

    /**
     * Filters and saves the list of peer projects associated with this project.
     * 
     * @param peerProjects The raw list of peer projects.
     */
    public void setPeerDirProjects(List<ProjectModel> peerProjects) {
        this.peerDirProjects.addAll(peerProjects);
    }

    /**
     * Sets the build configuration metadata for this project.
     *
     * @param buildConfigMetadata The build configuration metadata to set.
     */
    public void setBuildConfigMetadata(Metadata buildConfigMetadata) {
        this.buildConfigMetadata = buildConfigMetadata;
    }

    /**
     * Returns the names of the projects in the input projectModel collection.
     *
     * @param projects the collection of project models.
     * 
     * @return a list of project names.
     */
    private static List<String> toProjectNames(java.util.Collection<ProjectModel> projects) {
        List<String> names = new ArrayList<>();
        for (ProjectModel p : projects) {
            names.add(p.getName());
        }
        return names;
    }

    /**
     * Checks whether this project is classified as a Liberty server module.
     *
     * A project is a Liberty server module if it contains Liberty configuration files
     * such as server.xml, bootstrap.properties, or server.env in the standard location.
     *
     * @return True if this is a Liberty server module. False otherwise.
     */
    public boolean isLibertyServerModule() {
        return libertyServerModule;
    }

    /**
     * Sets the parent project for this project in a multi-module structure.
     *
     * @param parent the parent project model
     */
    public void setParentProjectModel(ProjectModel parent) {
        this.parentProjectModel = parent;
    }

    /**
     * Get the parent project model.
     *
     * @return The parent project
     */
    public ProjectModel getParentProjectModel() {
        return parentProjectModel;
    }

    /**
     * Adds a child project to this project's set of child projects.
     *
     * @param child the child project model to add
     */
    public void addChildDirProject(ProjectModel child) {
        this.childDirProjects.add(child);
    }

    /**
     * Returns the name of the Eclipse project.
     *
     * @return the project name
     */
    public String getName() {
        return (buildConfigMetadata != null) ? buildConfigMetadata.getProjectName() : iProject.getName();
    }

    /**
     * Checks whether this project is part of a multi-module structure (has a parent).
     *
     * @return True if this project has a parent project. False otherwise.
     */
    public boolean isAggregated() {
        return parentProjectModel != null;
    }

    /**
     * Checks whether this project is a parent of a module with Liberty server configuration.
     *
     * This is used to expose parent/aggregator projects in the Liberty Dashboard
     * even when they do not have Liberty configuration themselves.
     *
     * @return true if this is a parent of a Liberty server module, false otherwise.
     */
    public boolean isParentOfServerModule() {
        return isParentOfServerModule;
    }

    /**
     * Returns whether this project has test source files.
     *
     * @return True if test source directories exist. False otherwise.
     */
    public boolean hasTests() {
        return hasTests;
    }

    /**
     * Classifies this project as having tests by checking for the src/test directory with content.
     * Should be called during workspace model building.
     *
     * Checks for the standard Maven/Gradle test directory (src/test) and verifies it contains files.
     * This approach supports any language (Java, Kotlin, Groovy, Scala, etc.) and any custom
     * directory structure under src/test.
     */
    public void classifyAsHavingTests() {
        String projectPath = getPath();
        if (projectPath == null) {
            this.hasTests = false;
            return;
        }

        // Check for standard test directory (Maven and Gradle convention)
        Path testDir = Paths.get(projectPath, "src", "test");
        this.hasTests = hasTestFiles(testDir);
    }

    /**
     * Recursively checks if a directory contains any files.
     *
     * @param dir The directory to check
     * @return true if the directory contains files (indicating tests exist), false otherwise
     */
    private boolean hasTestFiles(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return false;
        }

        try (Stream<Path> paths = Files.walk(dir, 20)) {
            // Check if there are any regular files in the directory tree.
            // This indicates the directory is not empty and likely contains test source files.
            return paths.anyMatch(path -> Files.isRegularFile(path));
        } catch (Exception e) {
            // If we can't read the directory, assume no tests
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                        "Error checking for test files in directory: " + dir, e);
            }
            return false;
        }
    }

    /**
     * Adds a dependent project (one declared in build configuration) to this project.
     *
     * @param dependent the dependent project model to add
     */
    public void addDependentProject(ProjectModel dependent) {
        this.dependentProjects.add(dependent);
    }

    /**
     * Returns all modules that belong to the same multi-module project as this module.
     * It walks up to the root of the multi-module hierarchy, then collects the root and
     * every descendant. For a standalone (single-module) project the returned set
     * contains only this module.
     *
     * @return The set of all modules in the same multi-module build.
     */
    public Set<ProjectModel> getAllModulesInSameMultiModuleBuild() {
        // Walk up to the root of this multi-module build.
        ProjectModel root = this;
        while (root.parentProjectModel != null) {
            root = root.parentProjectModel;
        }

        Set<ProjectModel> all = ConcurrentHashMap.newKeySet();
        collectDescendants(root, all);
        return all;
    }

    /**
     * Recursively collects nodes and all of its descendants into the input set collection.
     *
     * @param node   The starting node.
     * @param result The set to populate.
     */
    private static void collectDescendants(ProjectModel node, Set<ProjectModel> result) {
        result.add(node);
        for (ProjectModel child : node.childDirProjects) {
            collectDescendants(child, result);
        }
    }

    /**
     * Returns the set of direct and transitive dependent modules within the same
     * multi-module project. The traversal only looks at the dependencies that stay
     * within the same multi-module project. Third-party library dependencies are not
     * included.
     *
     * @return The set of direct and transitive dependent modules scoped to the same
     *         multi-module project.
     */
    public Set<ProjectModel> getTransitiveDependentModules() {
        Set<ProjectModel> siblings = getAllModulesInSameMultiModuleBuild();

        Set<ProjectModel> visited = ConcurrentHashMap.newKeySet();
        Deque<ProjectModel> queue = new ArrayDeque<>(dependentProjects);

        while (!queue.isEmpty()) {
            ProjectModel current = queue.poll();
            // Only follow edges to sibling modules; never include self.
            if (current != this && siblings.contains(current) && visited.add(current)) {
                queue.addAll(current.dependentProjects);
            }
        }
        return visited;
    }

    /**
     * Returns the current lifecycle state of this module.
     *
     * @return The current {@link AppState}.
     */
    public AppState getAppState() {
        return appState;
    }

    /**
     * Sets the lifecycle state of this module.
     *
     * @param appState The new AppState to apply.
     */
    public void setAppState(AppState appState) {
        this.appState = appState;
    }

    /**
     * Returns true if this project was started as part of a multi-module batch start.
     *
     * @return True if this project was started as part of a multi-module batch start.
     */
    public boolean isBatchStarted() {
        return batchStarted;
    }

    /**
     * Sets the batch started indicator for this project.
     *
     * @param batchStarted The batch started indicator.
     */
    public void setBatchStarted(boolean batchStarted) {
        this.batchStarted = batchStarted;
    }
    
    /**
     * Returns a string representation of this project model for debugging.
     *
     * @return a detailed string containing project information including build type,
     *         Liberty configuration status, and parent/child relationships
     */
    @Override
    public String toString() {
        return String.format("ProjectModel{name=%s, path=%s, buildType=%s, appState=%s"
                             + ", libertyServerModule=%b, parentOfServerModule=%b, libertyNature=%b"
                             + ", hasTests=%b, batchStarted=%b, parent=%s"
                             + ", children=%s, peers=%s, dependents=%s}",
                             getName(), getPath(), type, appState,
                             libertyServerModule, isParentOfServerModule, hasLibertyNature(),
                             hasTests, batchStarted,
                             (parentProjectModel != null ? parentProjectModel.getName() : null),
                             toProjectNames(childDirProjects), toProjectNames(peerDirProjects),
                             toProjectNames(dependentProjects));
    }
}