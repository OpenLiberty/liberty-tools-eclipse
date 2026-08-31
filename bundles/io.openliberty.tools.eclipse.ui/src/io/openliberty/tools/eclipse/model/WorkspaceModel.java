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

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.model.ProjectModel.BuildType;

/**
 * Represents the project model informing the Liberty tools dashboard and the Run Configurations
 */
public class WorkspaceModel {

    private Map<String, ProjectModel> projectsByLocation;
    private Map<String, ProjectModel> projectsByName;
    private List<ProjectModel> rootProjects;

    /**
     * Constructor.
     */
    public WorkspaceModel() {
        initProjectModels();
    }

    /**
     * Builds a complete workspace project model. Classifies projects (adds Liberty nature if conditions warrant).
     * Should only be called on the UI thread.
     */
    public void createNewCompleteWorkspaceModelWithClassify() {
        createNewCompleteWorkspaceModel(true);
    }

    /**
     * Discards the previous model and builds a new model from open projects.
     *
     * @param classify Whether to classify projects or not.
     */
    private void createNewCompleteWorkspaceModel(boolean classify) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { classify });
        }

        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] iProjects = workspaceRoot.getProjects();

        List<IProject> openProjects = Arrays.stream(iProjects).filter(project -> project.isOpen()).collect(Collectors.toList());

        initProjectModels();
        buildMultiProjectModel(openProjects, classify);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS);
        }
    }

    private void initProjectModels() {
        // Start over. Throw away existing model
        projectsByLocation = new ConcurrentHashMap<String, ProjectModel>();
        projectsByName = new ConcurrentHashMap<String, ProjectModel>();
        rootProjects = new ArrayList<ProjectModel>();
    }

    /**
     * @param projectsToScan Projects to include in model update
     * @param classify       Whether to classify
     */
    private void buildMultiProjectModel(List<IProject> projectsToScan, boolean classify) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { classify, projectsToScan });
        }

        // Step 1: Classify as server module and detect tests
        for (IProject iProject : projectsToScan) {
            if (iProject.isOpen()) {
                ProjectModel projectModel = projectsByLocation.get(iProject.getLocation().toOSString());
                if (projectModel == null) {
                    projectModel = new ProjectModel(iProject);
                    Metadata metadata;
                    try {
                        metadata = getBuildConfigMetadata(projectModel);
                        projectModel.setBuildConfigMetadata(metadata);
                        projectsByLocation.put(iProject.getLocation().toOSString(), projectModel);
                        projectsByName.put(metadata.getProjectName(), projectModel);

                        // Classify this project as a Liberty project.
                        if (classify) {
                            projectModel.classifyAsLibertyServerModule();
                        }

                        // Determine if the project has any tests.
                        projectModel.classifyAsHavingTests();
                    } catch (Exception e) {
                        // Log it and continue.
                        String msg = "The build metadata associated with project " + projectModel.getName() + " of build type: " + projectModel.getBuildType()
                                     + " was not found or could not be read. Project Path: "
                                     + projectModel.getPath() + ". This project is not being tracked.";
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
                        }

                    }
                }
            }
        }

        // Step 1b: For Gradle aggregator projects imported via "General → Project from Folder",
        // submodule directories are not yet Eclipse projects. Register any missing submodules so
        // that subsequent steps can see and classify them.
        ensureGradleSubmodulesRegistered(classify);

        // Step 2: Build parent-child relationships using the project configuration.
        for (IProject iProject : projectsToScan) {
            if (iProject.isOpen()) {
                String projectLocation = iProject.getLocation().toOSString();
                ProjectModel projectModel = projectsByLocation.get(projectLocation);

                // An error happened in step one. This project is not being tracked.
                if (projectModel == null) {
                    continue;
                }

                Metadata metadata = projectModel.getBuildConfigMetadata();

                if (metadata != null) {
                    // Handle the case where child declares parent (Maven <parent>, Gradle parent reference).
                    String parentProjectName = metadata.getParentProjectName();
                    if (parentProjectName != null) {
                        ProjectModel parentProjectModel = projectsByName.get(parentProjectName);
                        if (parentProjectModel != null && projectModel.getParentProjectModel() == null) {
                            projectModel.setParentProjectModel(parentProjectModel);
                            parentProjectModel.addChildDirProject(projectModel);
                        }
                    } else {
                        // Handle the case where the parent of a child project could not be determined
                        // through the existing configuration. For example, in the case where a Maven parent
                        // project defines who its children are, but the children do not define who their
                        // parent is.
                        if (metadata.isAggregator()) {
                            List<String> subprojects = metadata.getSubprojects();
                            if (subprojects != null && !subprojects.isEmpty()) {
                                for (String subprojectPath : subprojects) {
                                    ProjectModel childProjectModel = null;

                                    try {
                                        // Resolve the module path relative to the parent project location
                                        // The module path can be:
                                        // - Simple name: "module1" (nested under parent)
                                        // - Relative path: "../ear" (peer or other location)
                                        File parentDir = new File(projectLocation);
                                        File childDir = new File(parentDir, subprojectPath);
                                        String resolvedPath = childDir.getAbsolutePath();

                                        childProjectModel = projectsByLocation.get(resolvedPath);
                                    } catch (Exception e) {
                                        // If path resolution fails, skip this module
                                        if (Trace.isEnabled()) {
                                            Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                                                    "Failed to resolve module path: " + subprojectPath + " for parent: " + projectLocation, e);
                                        }
                                    }

                                    if (childProjectModel != null && childProjectModel.getParentProjectModel() == null) {
                                        childProjectModel.setParentProjectModel(projectModel);
                                        projectModel.addChildDirProject(childProjectModel);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Step 3: Set peer projects for each child within the same parent.
        for (ProjectModel projectModel : projectsByLocation.values()) {
            List<ProjectModel> children = projectModel.getChildProjects();
            if (!children.isEmpty()) {
                for (ProjectModel child : children) {
                    child.setPeerDirProjects(children);
                }
            }
        }

        // Step 4: Extract project/module dependencies.
        for (IProject iProject : projectsToScan) {
            if (iProject.isOpen()) {
                String projectLocation = iProject.getLocation().toOSString();
                ProjectModel projectModel = projectsByLocation.get(projectLocation);

                // An error happened in step one. This project is not being tracked.
                if (projectModel == null) {
                    continue;
                }

                Metadata metadata = projectModel.getBuildConfigMetadata();

                if (metadata != null) {
                    List<String> dependencies = metadata.getProjectDependencies();
                    if (dependencies != null && !dependencies.isEmpty()) {
                        // Match each dependency against workspace projects
                        for (String depArtifactId : dependencies) {
                            // Try to find a workspace project with this artifactId
                            ProjectModel depProject = projectsByName.get(depArtifactId);
                            if (depProject != null) {
                                // This is a workspace project dependency
                                projectModel.addDependentProject(depProject);
                            }
                        }
                    }
                }
            }
        }

        // Step 5: Now that we have established parent and child relationships, classify with Liberty nature.
        if (classify) {
            for (IProject iProject : projectsToScan) {
                if (iProject.isOpen()) {
                    String projectLocation = iProject.getLocation().toOSString();
                    ProjectModel projectModel = projectsByLocation.get(projectLocation);

                    // An error happened in step one. This project is not being tracked.
                    if (projectModel == null) {
                        continue;
                    }

                    projectModel.classifyAsLibertyNature();
                }
            }
        }

        // Step 6: Identify root projects (no parent or parent not in workspace).
        rootProjects.clear();
        for (ProjectModel projectModel : projectsByLocation.values()) {
            if (projectModel.getParentProjectModel() == null) {
                // A project is the root if:
                // 1. It is Liberty-enabled itself.
                // 2. It has Liberty-enabled descendants.
                boolean isLibertyEnabled = projectModel.isLibertyServerModule();
                boolean hasLibertyDescendants = !findLibertyDescendants(projectModel).isEmpty();

                if (isLibertyEnabled || hasLibertyDescendants) {
                    rootProjects.add(projectModel);
                }
            }
        }

        // Step 7. Sort root projects alphabetically. Maven projects are shown first and Gradle projects second.
        rootProjects.sort((p1, p2) -> {
            if (p1.getBuildType() != p2.getBuildType()) {
                return p1.getBuildType() == BuildType.Maven ? -1 : 1;
            }
            return p1.getName().compareTo(p2.getName());
        });

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, projectsByLocation.values());
        }
    }

    /**
     * Returns the Liberty server project associated with the input name or null if none is found.
     * 
     * @param name The name of the project (not the location)
     * 
     * @return The project associated with the input name or null if none is found. (Note that 'null' may be returned because this is
     *         not a server project).
     */
    public ProjectModel getProjectByName(String name) {
        return projectsByName.get(name);
    }

    /**
     * Returns the Liberty server project associated with the input project location.
     * 
     * @param location The location of the project.
     * 
     * @return The project model associated with the input project location or null if none is found in
     *         the list of projects with Liberty server configuration.
     */
    public ProjectModel getProjectByLocation(String location) {
        return projectsByLocation.get(location);
    }

    /**
     * Returns the object representing the build metadata associated with the input project.
     * 
     * @param projectModel The project model containing information about the project being processed.
     * 
     * @return The object representing the build metadata associated with the input project.
     */
    private Metadata getBuildConfigMetadata(ProjectModel projectModel) throws Exception {
        BuildType buildType = projectModel.getBuildType();
        IProject iProject = projectModel.getIProject();
        String projectLocation = iProject.getLocation().toOSString();
        Path projectPath = Paths.get(projectLocation);
        Metadata metadata = null;

        if (buildType == BuildType.Maven) {
            File pomFile = new File(projectLocation, "pom.xml");
            if (!pomFile.exists()) {
                throw new FileNotFoundException("pom.xml not found for Maven project '" + iProject.getName() + "' at: " + pomFile.getAbsolutePath());
            }
            metadata = new MavenMetadata(pomFile.getAbsolutePath());

        } else if (buildType == BuildType.Gradle) {
            Path buildFile = GradleMetadata.findBuildFile(projectPath);
            Path settingsFile = GradleMetadata.findSettingsFile(projectPath);

            if (buildFile == null && settingsFile == null) {
                throw new FileNotFoundException("Neither a build file nor a settings file "
                                                + "was found for Gradle project: '"
                                                + iProject.getName() + "'. Project location: " + projectLocation);
            }

            if (buildFile == null) {
                // There is a settings file only. However, this scenario is only meaningful
                // if the project being analyzed is a multi-module aggregator root.
                GradleMetadata settingsOnlyMetadata = new GradleMetadata(null, settingsFile.toAbsolutePath().toString());
                if (!settingsOnlyMetadata.isAggregator()) {
                    throw new IllegalStateException("Gradle project '" + iProject.getName()
                                                    + "' has a settings file but no build file and declares no submodules. "
                                                    + "Project location: " + projectLocation);
                }
                metadata = settingsOnlyMetadata;
            } else {
                metadata = new GradleMetadata(buildFile.toAbsolutePath().toString(), settingsFile != null ? settingsFile.toAbsolutePath().toString() : null);
            }
        } else {
            throw new IllegalStateException("Build type: " + buildType.name() + " is not supported.");
        }

        return metadata;
    }

    /**
     * Returns Liberty server modules grouped into two groups: Maven, then Gradle. Within each of
     * the two groups, modules of that group will be sorted in alphabetic order by project name.
     * So you will get the sorted list of Maven Liberty server project names followed by the sorted
     * list of Gradle Liberty server project names.
     * 
     * @return Liberty server project names sorted and grouped.
     */
    public List<String> getSortedDashboardProjectList() {
        List<String> mavenDashboardProjects = new ArrayList<String>();
        List<String> gradleDashboardProjects = new ArrayList<String>();
        List<String> retVal = new ArrayList<String>();

        for (ProjectModel p : projectsByName.values()) {
            if (p.isLibertyServerModule() || p.isParentOfServerModule() || p.hasLibertyNature()) {
                if (p.getBuildType() == ProjectModel.BuildType.Maven) {
                    mavenDashboardProjects.add(p.getName());
                } else if (p.getBuildType() == ProjectModel.BuildType.Gradle) {
                    gradleDashboardProjects.add(p.getName());
                }
            }
        }
        Collections.sort(mavenDashboardProjects);
        Collections.sort(gradleDashboardProjects);

        retVal.addAll(mavenDashboardProjects);
        retVal.addAll(gradleDashboardProjects);

        return retVal;
    }

    /**
     * Returns the list of root projects.
     * 
     * @return The list of root projects.
     */
    public List<ProjectModel> getRootProjects() {
        return rootProjects;
    }

    /**
     * Find all Liberty-enabled descendants of a project.
     *
     * @param project The project to search
     * @return List of Liberty-enabled descendant projects
     */
    public List<ProjectModel> findLibertyDescendants(ProjectModel project) {
        List<ProjectModel> descendants = new ArrayList<>();

        // Only search children - don't include the project itself
        for (ProjectModel child : project.getChildProjects()) {
            // Add the child if it's Liberty-enabled
            if (child.isLibertyServerModule()) {
                descendants.add(child);
            }

            // Recursively search the child's descendants
            descendants.addAll(findLibertyDescendants(child));
        }

        return descendants;
    }

    /**
     * Ensures that all submodule directories declared by Gradle aggregator projects are
     * registered as Eclipse {@link IProject} instances in the workspace.
     *
     * <p>When a multi-module Gradle project is imported via <em>File → General → Project from
     * Folder and Archive</em>, only the root directory becomes an Eclipse project. Submodule
     * directories are not registered, so they are invisible to the workspace model and cannot
     * receive the Liberty nature. This method detects that situation and programmatically
     * registers any missing submodule directories as Eclipse projects.</p>
     *
     * <p>After registration the new projects are also parsed for metadata and added to the
     * internal maps ({@code projectsByLocation} and {@code projectsByName}) so that the
     * parent-child relationship steps that follow can wire them up correctly.</p>
     *
     * <p>The method is idempotent: if a submodule directory is already registered as an Eclipse
     * project it is left untouched.</p>
     *
     * @param classify whether to classify newly registered projects as Liberty server modules
     */
    private void ensureGradleSubmodulesRegistered(boolean classify) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();

        // Snapshot the currently known project models to avoid modifying the map
        // while iterating over it.
        List<ProjectModel> currentModels = new ArrayList<>(projectsByLocation.values());

        for (ProjectModel projectModel : currentModels) {
            // Only process Gradle aggregator projects.
            if (projectModel.getBuildType() != BuildType.Gradle) {
                continue;
            }
            Metadata metadata = projectModel.getBuildConfigMetadata();
            if (metadata == null || !metadata.isAggregator()) {
                continue;
            }

            String aggregatorLocation = projectModel.getPath();
            if (aggregatorLocation == null) {
                continue;
            }

            List<String> subprojectNames = metadata.getSubprojects();
            if (subprojectNames == null || subprojectNames.isEmpty()) {
                continue;
            }

            for (String subprojectName : subprojectNames) {
                File submoduleDir = new File(aggregatorLocation, subprojectName);
                if (!submoduleDir.exists() || !submoduleDir.isDirectory()) {
                    // Directory does not exist on disk; skip.
                    continue;
                }

                String submodulePath = submoduleDir.getAbsolutePath();

                // Already registered as an Eclipse project; nothing to do.
                if (projectsByLocation.containsKey(submodulePath)) {
                    continue;
                }

                // Register the submodule directory as a new Eclipse project.
                try {
                    IPath location = new org.eclipse.core.runtime.Path(submodulePath);
                    IProjectDescription desc = workspace.newProjectDescription(subprojectName);
                    desc.setLocation(location);

                    IProject newProject = workspace.getRoot().getProject(subprojectName);
                    if (!newProject.exists()) {
                        newProject.create(desc, new NullProgressMonitor());
                    }
                    if (!newProject.isOpen()) {
                        newProject.open(new NullProgressMonitor());
                    }

                    // Immediately parse and register the new project so that it is available
                    // for the parent-child relationship steps that follow in this same model build.
                    ProjectModel newModel = new ProjectModel(newProject);
                    try {
                        Metadata newMetadata = getBuildConfigMetadata(newModel);
                        newModel.setBuildConfigMetadata(newMetadata);
                        projectsByLocation.put(submodulePath, newModel);
                        projectsByName.put(newMetadata.getProjectName(), newModel);

                        if (classify) {
                            newModel.classifyAsLibertyServerModule();
                        }
                        newModel.classifyAsHavingTests();
                    } catch (Exception e) {
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                                    "Failed to build metadata for auto-registered submodule: " + submodulePath, e);
                        }
                    }

                } catch (Exception e) {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                                "Failed to auto-register Gradle submodule as Eclipse project: " + submodulePath, e);
                    }
                }
            }
        }
    }
}
