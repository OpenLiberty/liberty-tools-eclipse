package liberty.tools.utils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jem.util.emf.workbench.ProjectUtilities;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

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
            if (firstElement instanceof IResource) {
                project = ((IResource) firstElement).getProject();
            }
        }

        return project;
    }

    /**
     * Gets all open projects currently in the workspace.
     * 
     * @return All open projects currently in the workspace.
     */
    public static List<String> getOpenWokspaceProjects() {
        List<String> jProjects = new ArrayList<String>();

        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        IProject[] projects = workspaceRoot.getProjects();
        for (int i = 0; i < projects.length; i++) {
            IProject project = projects[i];

            if (project.isOpen()) {
                jProjects.add(project.getName());
            }
        }

        return jProjects;
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
                return path.toPortableString();
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
        // TODO: Handle cases where pom.xml is not in the root dir.
        IFile file = project.getFile("pom.xml");
        return file.exists();
    }

    /**
     * Returns true if the input project is a Gradle project. False, otherwise.
     * 
     * @param project The project to check.
     * 
     * @return True if the input project is a Gradle project. False otherwise.
     */
    public static boolean isGradle(IProject project) {
        // TODO: Handle cases where build.gradle is not in the root dir.
        IFile file = project.getFile("build.gradle");
        return file.exists();
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
