package liberty.tools.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jem.util.emf.workbench.ProjectUtilities;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import liberty.tools.LibertyNature;

public class Project {

    /**
     * Retrieves the project currently selected.
     * 
     * @return The project currently selected or null if one was not found.
     */
    public static IProject getSelected() {
        IProject project = null;
        IWorkbenchWindow w = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        ISelectionService selectionService = w.getSelectionService();
        ISelection selection = selectionService.getSelection();

        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object firstElement = structuredSelection.getFirstElement();
            project = ProjectUtilities.getProject(firstElement);
            if (project == null && (firstElement instanceof String)) {
                project = getByName((String) firstElement);
            }
            if (project == null && (firstElement instanceof IProject)) {
                project = ((IProject) firstElement);
            }
        }

        return project;
    }

    /**
     * Gets all open projects currently in the workspace.
     * 
     * @return All open projects currently in the workspace.
     */
    public static List<IProject> getOpenWokspaceProjects() {
        List<IProject> jProjects = new ArrayList<IProject>();

        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = workspaceRoot.getProjects();
        for (int i = 0; i < projects.length; i++) {
            IProject project = projects[i];

            if (project.isOpen()) {
                jProjects.add(project);
            }
        }

        return jProjects;
    }

    /**
     * Returns a list of projects configured to run on Liberty.
     * 
     * @return A list of projects configured to run on Liberty.
     * 
     * @throws Exception
     */
    public static List<String> getLibertyProjects(boolean refresh) throws Exception {
        ArrayList<String> libertyProjects = new ArrayList<String>();
        List<IProject> projectList = getOpenWokspaceProjects();
        Iterator<IProject> projects = projectList.iterator();
        while (projects.hasNext()) {
            IProject project = projects.next();
            if (isLiberty(project, refresh)) {
                libertyProjects.add(project.getName());
            }
        }

        return libertyProjects;
    }

    /**
     * Retrieves the IProject object associated with the input name.
     * 
     * @param name The name of the project.
     * 
     * @return The IProject object associated with the input name.
     */
    public static IProject getByName(String name) {

        try {
            IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();

            IProject[] projects = workspaceRoot.getProjects();
            for (int i = 0; i < projects.length; i++) {
                IProject project = projects[i];
                if (project.isOpen() && (project.getName().equals(name))) {
                    return project;
                }
            }
        } catch (Exception ce) {

        }
        return null;
    }

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
            // TODO: Log it somewhere (return false).
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
                isGradle = project.getFile("pom.xml").exists();
            }
        } catch (Exception e) {
            // TODO: Log it somewhere (return false).
        }

        return isGradle;
    }

    /**
     * Returns true if the input project is a Liberty configured project. False, otherwise. If the project is determined to
     * be Liberty project, the outcome is persisted by associating the project with a Liberty type/nature.
     * 
     * @param project The project to check.
     * @param refresh Defines whether or not this call is being done on behalf of a refresh action.
     * 
     * @return True if the input project is a Liberty configured project. False, otherwise.
     * 
     * @throws Exception
     */
    public static boolean isLiberty(IProject project, boolean refresh) throws Exception {
        // TODO: Use validation parser to find the Liberty entries in config files more accurately.
        // Perhaps check for other things that we may consider appropriate to check.

        // Check if the input project is already marked to be a liberty project.
        boolean isNatureLiberty = project.getDescription().hasNature(LibertyNature.NATURE_ID);
        
        if (isNatureLiberty && !refresh) {
            return isNatureLiberty;
        }

        boolean isLiberty = false;

        // If the project is not marked to be of type Liberty or the project list is refreshing, 
        // check is configured to run on Liberty.
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

        // If it is determined that the input project is a Liberty type project, persist the outcome (if not
        // done so already) by adding a Liberty type/nature marker to the project's metadata.
        if (!isNatureLiberty && isLiberty) {
            addLibertyNature(project);
        }

        // If it is determined that the input project is not a Liberty project, but it is marked as being one,
        // remove the Liberty type/nature marker from the project's metadata.
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
        IProjectDescription projectDesc = project.getDescription();
        String[] currentNatures = projectDesc.getNatureIds();
        String[] newNatures = new String[currentNatures.length + 1];
        System.arraycopy(currentNatures, 0, newNatures, 0, currentNatures.length);
        newNatures[currentNatures.length] = LibertyNature.NATURE_ID;
        projectDesc.setNatureIds(newNatures);
        project.setDescription(projectDesc, new NullProgressMonitor());
    }

    /**
     * Removes the Liberty type/nature entry from the project's description/metadata (.project).
     * 
     * @param project The project to process.
     * 
     * @throws Exception
     */
    public static void removeLibertyNature(IProject project) throws Exception {
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
    }

    /**
     * Returns true if the Maven project's pom.xml file is configured to use Liberty development mode. False, otherwise.
     * 
     * @param project The Maven project.
     * 
     * @return True if the Maven project's pom.xml file is configured to use Liberty development mode. False, otherwise.
     */
    public static boolean isMavenBuildFileValid(IProject project) {
        IFile file = project.getFile("pom.xml");

        // TODO: Implement. Check for Liberty Maven plugin and other needed definitions.
        // Need some parsing tool.

        return true;
    }

    /**
     * Returns true if the Gradle project's build file is configured to use Liberty development mode. False, otherwise.
     * 
     * @param project The Gradle project.
     * 
     * @return True if the Gradle project's build file is configured to use Liberty development mode. False, otherwise.
     */
    public static boolean isGradleBuildFileValid(IProject project) {
        IFile file = project.getFile("build.gradle");

        // TODO: Implement. Check for Liberty Gradle plugin and other needed
        // definitions. Need some xml parsing tool.

        return true;
    }
}
