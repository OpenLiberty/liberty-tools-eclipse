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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

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
     * Build complete workspace project model. Do classify projects (add Liberty nature if conditions warrant)
     * Should only be called
     * on UI thread
     */
    public void createNewCompleteWorkspaceModelWithClassify() {
        createNewCompleteWorkspaceModel(true);
    }

    /**
     * Discard previous model and build new model from open projects
     * 
     * @param whether to classify or not
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
                                     + projectModel.getPath();
                        if (Trace.isEnabled()) {
                            Trace.getTracer().trace(Trace.TRACE_TOOLS, msg, e);
                        }
                    }
                }
            }
        }

        // Step 2: Build parent-child relationships using the project configuration.
        for (IProject iProject : projectsToScan) {
            if (iProject.isOpen()) {
                String projectLocation = iProject.getLocation().toOSString();
                ProjectModel projectModel = projectsByLocation.get(projectLocation);
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
                                        java.io.File parentDir = new java.io.File(projectLocation);
                                        java.io.File childDir = new java.io.File(parentDir, subprojectPath);
                                        String resolvedPath = childDir.getCanonicalPath();

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

        // Step 3: Extract project/module dependencies.
        for (IProject iProject : projectsToScan) {
            if (iProject.isOpen()) {
                String projectLocation = iProject.getLocation().toOSString();
                ProjectModel projectModel = projectsByLocation.get(projectLocation);
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

        // Step 4: Now that we have established parent and child relationships, classify with Liberty nature.
        if (classify) {
            for (IProject iProject : projectsToScan) {
                if (iProject.isOpen()) {
                    String projectLocation = iProject.getLocation().toOSString();
                    ProjectModel projectModel = projectsByLocation.get(projectLocation);
                    projectModel.classifyAsLibertyNature();
                }
            }
        }

        // Step 5: Identify root projects (no parent or parent not in workspace).
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

        // Step 6. Sort root projects alphabetically. Maven projects are shown first and Gradle projects second.
        rootProjects.sort((p1, p2) -> {
            if (p1.getBuildType() != p2.getBuildType()) {
                return p1.getBuildType() == BuildType.MAVEN ? -1 : 1;
            }
            return p1.getName().compareTo(p2.getName());
        });

        if (Trace.isEnabled()) {
            Trace.getTracer().trace(Trace.TRACE_TOOLS, "Projects: " + projectsByLocation.values());
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

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, name);
        }

        ProjectModel retVal = projectsByName.get(name);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, retVal);
        }

        return retVal;
    }

    /**
     * Returns the Liberty server project associated with the input project location.
     * 
     * @param String The location of the project.
     * 
     * @return The project model associated with the input project location or null if none is found in
     *         the list of projects with Liberty server configuration.
     */
    public ProjectModel getProjectByLocation(String location) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, location);
        }

        ProjectModel retVal = projectsByLocation.get(location);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, retVal);
        }

        return retVal;
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
        String projectDir = iProject.getLocation().toOSString();
        Metadata metadata = null;

        if (buildType == BuildType.MAVEN) {
            File pomFile = new File(projectDir, "pom.xml");
            String buildFilePath = pomFile.getAbsolutePath();

            metadata = new MavenMetadata(buildFilePath);

        } else if (buildType == BuildType.GRADLE) {
            File pomFile = new File(projectDir, "build.gradle");
            String buildFilePath = pomFile.getAbsolutePath();

            metadata = new GradleMetadata(buildFilePath);

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

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS);
        }

        List<String> mavenDashboardProjects = new ArrayList<String>();
        List<String> gradleDashboardProjects = new ArrayList<String>();
        List<String> retVal = new ArrayList<String>();

        for (ProjectModel p : projectsByName.values()) {
            if (p.isLibertyServerModule() || p.isParentOfServerModule() || p.hasLibertyNature()) {
                if (p.getBuildType() == ProjectModel.BuildType.MAVEN) {
                    mavenDashboardProjects.add(p.getName());
                } else if (p.getBuildType() == ProjectModel.BuildType.GRADLE) {
                    gradleDashboardProjects.add(p.getName());
                } else {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_TOOLS,
                                                "Project " + p.getIProject().getName() + " could not be identified as being a Maven or Gradle project.");
                    }
                }
            }
        }
        Collections.sort(mavenDashboardProjects);
        Collections.sort(gradleDashboardProjects);

        retVal.addAll(mavenDashboardProjects);
        retVal.addAll(gradleDashboardProjects);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, retVal);
        }

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
}
