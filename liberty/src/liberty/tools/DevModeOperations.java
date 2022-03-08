package liberty.tools;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.buildship.core.internal.configuration.ProjectConfiguration;
import org.eclipse.buildship.core.internal.launch.GradleRunConfigurationAttributes;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;

import liberty.tools.utils.Dialog;
import liberty.tools.utils.Project;

/**
 * Provides the implementation of all supported dev mode operations.
 */
public class DevModeOperations {
    // TODO: Dashboard display: Handle the case where the project is configured to be built/run by both
    // Gradle and Maven at the same time.

    // TODO: Establish a Maven/Gradle command precedence (i.e. gradlew -> gradle configured ->
    // gradle_home).
	
    private Map<String, ILaunch> activeLaunches;
	
	public DevModeOperations() {
		activeLaunches = new HashMap<String, ILaunch>();
	}
	
    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void start() {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
        String projectPath = Project.getPath(project);
        
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projectName);
            return;
        }
        
        try {
            if (Project.isMaven(project)) {
                if (!Project.isMavenBuildFileValid(project)) {
                    System.out.println("Maven build file on project" + projectName + " is not valid..");
                }               
                runMavenStart("liberty:dev");
                
            } else if (Project.isGradle(project)) {
                if (!Project.isGradleBuildFileValid(project)) {
                    System.out.println("Build file on project" + projectName + " is not valid.");
                }
                runGradleStart("libertyDev", null);
                
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");

                return;
            }
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the start action on project " + projectName, e);
            return;
        }

    }

    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void startWithParms(String userParms) {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
        String projectPath = Project.getPath(project);
        
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projectName);
            return;
        }

        try {
            if (Project.isMaven(project)) {
            	if (!Project.isMavenBuildFileValid(project)) {
                    System.out.println("Maven build file on project" + projectName + " is not valid..");
                }               
                runMavenStart("liberty:dev " + userParms);
                
            } else if (Project.isGradle(project)) {
            	if (!Project.isGradleBuildFileValid(project)) {
                    System.out.println("Build file on project" + projectName + " is not valid.");
                }
                runGradleStart("libertyDev", userParms);
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");
                return;
            }
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while performing the start... action on project " + projectName, e);
            return;
        }
    }

    /**
     * Starts the server in development mode.
     * 
     * @return An error message or null if the command was processed successfully.
     */
    public void startInContainer() {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
        String projectPath = Project.getPath(project);
        
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projectName);
            return;
        }
        
        try {
            if (Project.isMaven(project)) {
            	if (!Project.isMavenBuildFileValid(project)) {
                    System.out.println("Maven build file on project" + projectName + " is not valid..");
                }               
                runMavenStart("liberty:devc");
                
            } else if (Project.isGradle(project)) {
            	if (!Project.isGradleBuildFileValid(project)) {
                    System.out.println("Build file on project" + projectName + " is not valid.");
                }
                runGradleStart("libertyDevc", null);
                
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");
            }
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails(
                    "An error was detected while performing the start in container action on project " + projectName, e);
            return;
        }
    }
    
    /**
     * Stops the server
     * 
     */
    public void stop() {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
        String projectPath = Project.getPath(project);
        
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projectName);
            return;
        }
        
        try {
            if (Project.isMaven(project)) {
            	if (!Project.isMavenBuildFileValid(project)) {
                    System.out.println("Maven build file on project" + projectName + " is not valid..");
                }               
                stopMaven();
                
            } else if (Project.isGradle(project)) {
            	if (!Project.isGradleBuildFileValid(project)) {
                    System.out.println("Build file on project" + projectName + " is not valid.");
                }
                stopGradle();
                
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");
            }
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails(
                    "An error was detected while performing the stop action on project " + projectName, e);
            return;
        }
        
    }
    
    /**
     * Runs the tests provided by the application.
     * 
     */
    public void runTests() {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
        String projectPath = Project.getPath(project);
        
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projectName);
            return;
        }
        
        try {
            if (Project.isMaven(project)) {
            	if (!Project.isMavenBuildFileValid(project)) {
                    System.out.println("Maven build file on project" + projectName + " is not valid..");
                }               
                runMavenTests();
                
            } else if (Project.isGradle(project)) {
            	if (!Project.isGradleBuildFileValid(project)) {
                    System.out.println("Build file on project" + projectName + " is not valid.");
                }
                runGradleTests();
                
            } else {
                Dialog.displayErrorMessage("Project" + projectName + "is not a Gradle or Maven project.");
            }
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails(
                    "An error was detected while performing the run tests action on project " + projectName, e);
            return;
        }
    }
    
    /**
     * Launch a new process using the m2e launch configuration type.
     * 
     * @param goal - The command to run.
     * 
     * @throws Exception If an error occurs while running the specified command.
     */
    public void runMavenStart(String goal) throws Exception {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
    	IPath workingDir = project.getLocation();
    	
        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType launchConfigurationType = launchManager.getLaunchConfigurationType("org.eclipse.m2e.Maven2LaunchConfigurationType");
        ILaunchConfigurationWorkingCopy workingCopy = launchConfigurationType.newInstance((IContainer) null, projectName);

        workingCopy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_WORKING_DIRECTORY, workingDir.toOSString());
        workingCopy.setAttribute("M2_GOALS", goal);

        ILaunchConfiguration launchConfig = workingCopy.doSave();

        ILaunch launch = launchConfig.launch("run", new NullProgressMonitor(), false, true);
        
        // Save launch
        activeLaunches.put(projectName, launch);
    }
    
    /**
     * Run the specified Gradle start task using the Gradle Buildship type configuration.
     * 
     * @param task - The Gradle task to run.
     * @param parms - The users parms to add to the task
     * 
     * @throws Exception If an error occurs while running the specified command.
     */
    public void runGradleStart(String task, String parms) throws Exception {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
    	
    	// The buildship launch configuration type does not launch the process in the background. 
    	// We need to do this manually with a Job.
	    Job job = new Job("Run Gradle task") {
	        @SuppressWarnings("restriction")
			protected IStatus run(IProgressMonitor monitor) {
    	
	        	try {
	        		// Initialize task and arguments
	        		List<String> tasks = new ArrayList<String>();
			    	tasks.add(task);
			    	
			    	List<String> arguments;
			    	if (parms != null) {
			    	    arguments = new ArrayList<String>();
			    	    arguments.add(parms);
			    	} else {
			    		arguments = Collections.emptyList();
			    	}
			    	
			        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			        ILaunchConfigurationType launchConfigurationType = launchManager.getLaunchConfigurationType("org.eclipse.buildship.core.launch.runconfiguration");
			        ILaunchConfigurationWorkingCopy workingCopy = launchConfigurationType.newInstance((IContainer) null, projectName);
			        
			        // Load project config and override
			        ProjectConfiguration configuration = CorePlugin.configurationManager().loadProjectConfiguration(project);
			        GradleRunConfigurationAttributes attributes = new GradleRunConfigurationAttributes(tasks,
	        				"${workspace_loc:/" + project.getName() + "}",
	        				configuration.getBuildConfiguration().getGradleDistribution().toString(),
	        				emptyOrValue(configuration.getBuildConfiguration().getGradleUserHome()),
	        				emptyOrValue(configuration.getBuildConfiguration().getJavaHome()),
	        				configuration.getBuildConfiguration().getJvmArguments(),
	        				arguments,
	        				false,
	        				configuration.getBuildConfiguration().isShowConsoleView(),
	        				true,
	        				configuration.getBuildConfiguration().isOfflineMode(),
	        				configuration.getBuildConfiguration().isBuildScansEnabled());
	        		attributes.apply(workingCopy);
			
			        ILaunchConfiguration launchConfig = workingCopy.doSave();
			
			        launchConfig.launch("run", new NullProgressMonitor(), false, true);
			        
	        	} catch (Exception e) {
	        		return Status.error(e.getMessage());
	        	}
	        	
	        	return Status.OK_STATUS;
	        }
        };
        job.setPriority(Job.SHORT);
        job.schedule(); 
    }
    
    private void stopGradle() throws Exception {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
    	IPath workingDir = project.getLocation();
    	
    	// The buildship launch configuration type does not launch the process in the background. 
    	// We need to do this manually with a Job.
	    Job job = new Job("Run Gradle task") {
	        @SuppressWarnings("restriction")
			protected IStatus run(IProgressMonitor monitor) {
    	
	        	try {
	        		
			    	NullProgressMonitor nullMonitor = new NullProgressMonitor();
			    	
			        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			        ILaunchConfigurationType launchConfigurationType = launchManager.getLaunchConfigurationType("org.eclipse.buildship.core.launch.runconfiguration");
			        ILaunchConfigurationWorkingCopy workingCopy = launchConfigurationType.newInstance((IContainer) null, projectName);
			
			        // Load project config and override
			        ProjectConfiguration configuration = CorePlugin.configurationManager().loadProjectConfiguration(project);
			        GradleRunConfigurationAttributes attributes = new GradleRunConfigurationAttributes(Collections.emptyList(),
	        				"${workspace_loc:/" + project.getName() + "}",
	        				configuration.getBuildConfiguration().getGradleDistribution().toString(),
	        				emptyOrValue(configuration.getBuildConfiguration().getGradleUserHome()),
	        				emptyOrValue(configuration.getBuildConfiguration().getJavaHome()),
	        				configuration.getBuildConfiguration().getJvmArguments(),
	        				configuration.getBuildConfiguration().getArguments(),
	        				false,
	        				false,
	        				true,
	        				configuration.getBuildConfiguration().isOfflineMode(),
	        				configuration.getBuildConfiguration().isBuildScansEnabled());
	        		attributes.apply(workingCopy);
	        		
			    	List<String> tasks = new ArrayList<String>();
			    	tasks.add("libertyStop");
			    	
			    	
			        workingCopy.setAttribute("working_dir", workingDir.toOSString());
			        workingCopy.setAttribute("tasks", tasks);
			
			        ILaunchConfiguration launchConfig = workingCopy.doSave();
			
			        launchConfig.launch("run", nullMonitor, false, true);
			        
	        	} catch (Exception e) {
	        		return Status.error(e.getMessage());
	        	}
	        	
	        	return Status.OK_STATUS;
	        }
        };
        
        job.setPriority(Job.SHORT);
        job.schedule(); 
        
        
    }
    
    private void stopMaven() throws CoreException {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
		
    	if (activeLaunches.containsKey(projectName)) {
    		try {
    			ILaunch launch = activeLaunches.get(projectName);
    			if (!launch.isTerminated()) {
    				Dialog.displayWarningMessage("Number of processes: " + launch.getProcesses().length);
    				
    				launch.getProcesses()[0].getStreamsProxy().write("q" + System.lineSeparator());
    			} else {
    				Dialog.displayWarningMessage("The application is not currently running");
    			}
            } catch (Exception e) {
                Dialog.displayErrorMessageWithDetails("An error was detected while performing the stop action.", e);
            }
    	} else {
    		Dialog.displayWarningMessage("The application is not currently running");
    	}
    }

    /**
     * Runs the tests provided by the application.
     * @throws CoreException 
     * 
     */
    public void runMavenTests() throws CoreException {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
		
		if (activeLaunches.containsKey(projectName)) {
    		try {
    			ILaunch launch = activeLaunches.get(projectName);
    			if (!launch.isTerminated()) {
    				launch.getProcesses()[0].getStreamsProxy().write(System.lineSeparator());
    			} else {
    				Dialog.displayWarningMessage("The application is not currently running");
    			}
            } catch (Exception e) {
                Dialog.displayErrorMessageWithDetails("An error was detected while performing the stop action.", e);
            }
    	} else {
    		Dialog.displayWarningMessage("The application is not currently running");
    	}
    }
    
    /**
     * Runs the tests provided by the application.
     * 
     */
    public void runGradleTests() {
    	IProject project = Project.getSelected();
		String projectName = project.getName();
    	IPath workingDir = project.getLocation();
    	
    	// The buildship launch configuration type does not launch the process in the background. 
    	// We need to do this manually with a Job.
	    Job job = new Job("Run Gradle task") {
	        @SuppressWarnings("restriction")
			protected IStatus run(IProgressMonitor monitor) {
    	
	        	try {
	        		
			    	NullProgressMonitor nullMonitor = new NullProgressMonitor();
			    	
			        ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
			        ILaunchConfigurationType launchConfigurationType = launchManager.getLaunchConfigurationType("org.eclipse.buildship.core.launch.runconfiguration");
			        ILaunchConfigurationWorkingCopy workingCopy = launchConfigurationType.newInstance((IContainer) null, projectName);
			        
			        // Load project config and override
			        ProjectConfiguration configuration = CorePlugin.configurationManager().loadProjectConfiguration(project);
			        GradleRunConfigurationAttributes attributes = new GradleRunConfigurationAttributes(Collections.emptyList(),
	        				"${workspace_loc:/" + project.getName() + "}",
	        				configuration.getBuildConfiguration().getGradleDistribution().toString(),
	        				emptyOrValue(configuration.getBuildConfiguration().getGradleUserHome()),
	        				emptyOrValue(configuration.getBuildConfiguration().getJavaHome()),
	        				configuration.getBuildConfiguration().getJvmArguments(),
	        				configuration.getBuildConfiguration().getArguments(),
	        				false,
	        				configuration.getBuildConfiguration().isShowConsoleView(),
	        				true,
	        				configuration.getBuildConfiguration().isOfflineMode(),
	        				configuration.getBuildConfiguration().isBuildScansEnabled());
	        		attributes.apply(workingCopy);
			
			    	List<String> tasks = new ArrayList<String>();
			    	tasks.add("cleanTest");
			    	tasks.add("test");
			    	
			        workingCopy.setAttribute("working_dir", workingDir.toOSString());
			        workingCopy.setAttribute("tasks", tasks);
			
			        ILaunchConfiguration launchConfig = workingCopy.doSave();
			
			        launchConfig.launch("run", nullMonitor, false, true);
			        
	        	} catch (Exception e) {
	        		return Status.error(e.getMessage());
	        	}
	        	
	        	return Status.OK_STATUS;
	        }
        };
        
        job.setPriority(Job.SHORT);
        job.schedule(); 
    }

    /**
     * Open Maven integration test report.
     */
    public void openMavenIntegrationTestReport() {
        IProject project = Project.getSelected();
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projName);
            return;
        }

        try {
            String browserId = "maven.failsafe.integration.test.results";
            String name = "Maven Failsafe integration test results";
            Path path = Paths.get(projectPath, "target", "site", "failsafe-report.html");
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Integration test results are not available. Be sure to run the tests first.");
                return;
            }

            openTestReport(project.getName(), path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening integration test report for project " + projName, e);
            return;
        }
    }

    /**
     * Open Maven unit test report.
     */
    public void openMavenUnitTestReport() {
        IProject project = Project.getSelected();
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + projName);
            return;
        }

        try {
            String browserId = "maven.project.surefire.unit.test.results";
            String name = "Maven Surefire unit test results";
            Path path = Paths.get(projectPath, "target", "site", "surefire-report.html");
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Unit test results are not available. Be sure to run the tests first.");
                return;
            }

            openTestReport(project.getName(), path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening unit test report for project " + projName, e);
            return;
        }
    }

    /**
     * Open Gradle test report.
     */
    public void openGradleTestReport() {
        IProject project = Project.getSelected();
        String projName = project.getName();
        String projectPath = Project.getPath(project);
        if (projectPath == null) {
            Dialog.displayErrorMessage("Unable to find the home path to the selected project: " + project.getName());
            return;
        }

        try {
            String browserId = "gradle.project.test.results";
            String name = "Gradle project test results";
            Path path = getGradleTestReportPath(project, projectPath);
            if (!path.toFile().exists()) {
                Dialog.displayWarningMessage("Test results are not available. Be sure to run the tests first.");
                return;
            }

            openTestReport(projName, path, browserId, name, name);
        } catch (Exception e) {
            Dialog.displayErrorMessageWithDetails("An error was detected while opening test report for project " + projName, e);
            return;
        }
    }

    /**
     * Opens the specified report in a browser.
     *
     * @param projName The application project name.
     * @param path The path to the HTML report file.
     * @param browserId The Id to use for the browser display.
     * @param name The name to use for the browser display.
     * @param toolTip The tool tip to use for the browser display.
     * 
     * @throws Exception If an error occurs while displaying the test report.
     */
    public void openTestReport(String projName, Path path, String browserId, String name, String toolTip) throws Exception {
        URL url = path.toUri().toURL();
        IWorkbenchBrowserSupport bSupport = PlatformUI.getWorkbench().getBrowserSupport();
        IWebBrowser browser = null;
        if (bSupport.isInternalWebBrowserAvailable()) {
            browser = bSupport.createBrowser(IWorkbenchBrowserSupport.AS_EDITOR | IWorkbenchBrowserSupport.LOCATION_BAR
                    | IWorkbenchBrowserSupport.NAVIGATION_BAR | IWorkbenchBrowserSupport.STATUS, browserId, name, toolTip);
        } else {
            browser = bSupport.createBrowser(browserId);
        }

        browser.openURL(url);
    }

    /**
     * Returns the home path to the HTML test report.
     * 
     * @return The HTML default located in the configured in the build file or the default location.
     */
    private Path getGradleTestReportPath(IProject project, String projectPath) {
        // TODO: Look for custom dir entry in build.gradle:
        // "test.reports.html.destination". Need to handle a value like this:
        // reports.html.destination = file("$buildDir/edsTestReports/teststuff")
        // Notice the use of a variable: $buildDir.

        // If a custom path was not defined, use default value.
        Path path = Paths.get(projectPath, "build", "reports", "tests", "test", "index.html");

        return path;
    }
    
    private String emptyOrValue(Object src) {
		return src!=null?src.toString():"";
	}
}