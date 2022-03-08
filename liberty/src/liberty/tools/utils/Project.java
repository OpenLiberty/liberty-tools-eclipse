package liberty.tools.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jem.util.emf.workbench.ProjectUtilities;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.xml.sax.SAXException;

import liberty.tools.LibertyNature;

public class Project {
	
	public static final String LIBERTY_MAVEN_PLUGIN_CONTAINER_VERSION = "3.3-M1";
    public static final String LIBERTY_GRADLE_PLUGIN_CONTAINER_VERSION = "3.1-M1";

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
            isLiberty = isMavenBuildFileValid(project);
        } else if (isGradle(project)) {
            isLiberty = isGradleBuildFileValid(project);
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
    public static boolean isMavenBuildFileValid(IProject project) throws ParserConfigurationException, SAXException, IOException, CoreException {
    	IFile file = project.getFile("pom.xml");
        
        MavenModelManager mavenModelManager = MavenPlugin.getMavenModelManager();
        Model model = mavenModelManager.readMavenModel(file.getContents());
        
        // Check for Liberty Maven plugin in plugins
        if (isLibertyMavenPluginDetected(model.getBuild().getPlugins())) {
        	return true;
        }
        
        // Check for Liberty Maven plugin in profiles
        List<Profile> profiles = model.getProfiles();
        for (Profile profile : profiles) {
        	if (isLibertyMavenPluginDetected(profile.getBuild().getPlugins())) {
        		return true;
        	}
        }
        
        // Check for Liberty Maven plugin in pluginManagement
        PluginManagement pluginManagement = model.getBuild().getPluginManagement();
        if (pluginManagement != null) {
            if (isLibertyMavenPluginDetected(pluginManagement.getPlugins())) {
        	    return true;
            }
        }
        
        return false;
	}

	private static boolean isLibertyMavenPluginDetected(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
        	
        	String groupId = plugin.getGroupId();
        	String artifactId = plugin.getArtifactId();
        	String version = plugin.getVersion();
        	
        	if (groupId.equals("io.openliberty.tools") && artifactId.equals("liberty-maven-plugin")){
                if (containerVersion(version, LIBERTY_MAVEN_PLUGIN_CONTAINER_VERSION)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Given liberty-maven-plugin version, determine if it is compatible for dev mode with containers
     *
     * @param version plugin version
     * @return true if valid for dev mode in contianers
     */
    private static boolean containerVersion(String version, String minimumVersion){
    	
        if (version == null) {
            return false;
        }
        try {
            ComparableVersion pluginVersion = new ComparableVersion(version);
            ComparableVersion containerVersion = new ComparableVersion(minimumVersion);
            if (pluginVersion.compareTo(containerVersion) >= 0) {
                return true;
            }
            return false;
        } catch (ClassCastException e) {
            return false;
        }
    }

	public static boolean isGradleBuildFileValid(IProject project) throws CoreException, IOException {
		IFile file = project.getFile("build.gradle");

		// Read build.gradle file to String
		BufferedInputStream bis = new BufferedInputStream(file.getContents());
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		for (int result = bis.read(); result != -1; result = bis.read()) {
		    buf.write((byte) result);
		}

		String buildFileText = buf.toString("UTF-8");

         if (buildFileText.isEmpty()) { 
        	 return false; 
         }

         // Filter commented out lines in build.gradle
         String partialFiltered = buildFileText.replaceAll("/\\*.*\\*/", "");
         String buildFileTextFiltered = partialFiltered.replaceAll("//.*(?=\\n)", "");

         // Check if "apply plugin: 'liberty'" is specified in the build.gradle
         boolean libertyPlugin = false;

         String applyPluginRegex = "(?<=apply plugin:)(\\s*)('|\")liberty";
         Pattern applyPluginPattern = Pattern.compile(applyPluginRegex);
         Matcher applyPluginMatcher = applyPluginPattern.matcher(buildFileTextFiltered);
         while (applyPluginMatcher.find()) {
             libertyPlugin = true;
         }

         // Check if liberty is in the plugins block
         if (libertyPlugin) {
             // check if group matches io.openliberty.tools and name matches liberty-gradle-plugin
             String depRegex = "(?<=dependencies)(\\s*\\{)([^\\}]+)(?=\\})";
             String pluginRegex = "(.*\\bio\\.openliberty\\.tools\\b.*)(.*\\bliberty-gradle-plugin\\b.*)";
             String versionRegex = "(?<=:liberty-gradle-plugin:).*(?=\')";

             Pattern depPattern = Pattern.compile(depRegex);
             Matcher depMatcher = depPattern.matcher(buildFileTextFiltered);

             while (depMatcher.find()) {
            	 String deps = buildFileTextFiltered.substring(depMatcher.start(), depMatcher.end());
                 Pattern pluginPattern = Pattern.compile(pluginRegex);
                 Matcher pluginMatcher = pluginPattern.matcher(deps);
                 
                 while (pluginMatcher.find()) {
                     String plugin = deps.substring(pluginMatcher.start(), pluginMatcher.end());
                     
                     Pattern versionPattern = Pattern.compile(versionRegex);
                     Matcher versionMatcher = versionPattern.matcher(plugin);
                     
                     while (versionMatcher.find()) {
                         String version = plugin.substring(versionMatcher.start(), versionMatcher.end());
                         if (containerVersion(version, LIBERTY_GRADLE_PLUGIN_CONTAINER_VERSION)) {
                        	 return true;
                         }
                     }
                 }
             }
         }

		return false;
	}
}
