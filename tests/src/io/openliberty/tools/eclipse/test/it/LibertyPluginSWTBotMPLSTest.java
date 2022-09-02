/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.test.it;

import org.junit.jupiter.api.Assertions;

import static io.openliberty.tools.eclipse.test.it.utils.LibertyPluginTestUtils.validateApplicationOutcome;

import java.io.File;

import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.bindings.keys.ParseException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import  org.eclipse.swt.widgets.Control;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.embedder.MavenModelManager;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.LocalProjectScanner;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.keyboard.KeyboardFactory;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.eclipse.test.it.utils.SWTPluginOperations;
import io.openliberty.tools.eclipse.test.it.utils.SWTTestCondition;

/**
 * Tests Open Liberty Eclipse plugin functions.
 */
public class LibertyPluginSWTBotMPLSTest {

    /**
     * Wokbench bot instance.
     */
    static SWTWorkbenchBot bot;

    /**
     * Dashboard instance.
     */
    static SWTBotView dashboard;
    
    /**
     * Application name.
     */
    static final String MVN_APP_NAME = "demo-service-a";

    /**
     * Test app relative path.
     */
    static final java.nio.file.Path projectPath = Paths.get("resources", "applications", "maven", "demo");
    
    static String appMsg = "Hello World";
    static String appURL = "http://localhost:9080/data/hello";
    
    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() {
        bot = new SWTWorkbenchBot();
        SWTPluginOperations.closeWelcomePage(bot);
        System.out.println("AJM: importing app");
        importMavenApplications();

        // need to start app first to get a target dir created
        // Start dev mode.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, MVN_APP_NAME);
        //bot.sleep(7000);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        bot.waitUntil(SWTTestCondition.isViewActive(terminal, "Terminal"));
        terminal.show();
                
        validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty", appMsg, appURL );
        
        
        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();
       
        validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty", appMsg, appURL );
        
        // Close the terminal.
        terminal.close();
        System.out.println("AJM: done importing");
    }

    /**
     * Cleanup.
     */
    @AfterAll
    public static void cleanup() {
        bot.closeAllEditors();
        bot.closeAllShells();
        bot.resetWorkbench();        
    }

    /**
     * Tests opening the dashboard using the main toolbar icon.
     */
    @Test
    public void testHoverOnConfig() {
    	// slow down tests
        //SWTBotPreferences.PLAYBACK_DELAY = 10;

    	SWTBotView projectExp = bot.viewByTitle("Project Explorer");
    	projectExp.show();
        bot.waitUntil(SWTTestCondition.isViewActive(projectExp, "Project Explorer"));
        SWTBotTreeItem[] topLevelProjects = projectExp.bot().tree().getAllItems();
        SWTBotTreeItem appProj = null;
        for (SWTBotTreeItem project : topLevelProjects) {
            if (project.getText().contains("demo-service-a")) {
            	appProj = project;
            }
        }
    	//projectExp.bot().tree().getTreeItem("demo-service-a (in service-a)").expand()
        appProj.expand()
    	       .getNode("src").expand()
    	       .getNode("main").expand()
    	       .getNode("java").expand()
    	       .getNode("com").expand()
    	       .getNode("example").expand()
    	       .getNode("demo").expand()
    	       .getNode("config").expand()
    	       .getNode("ConfigTestController.java").select().doubleClick();
        
        SWTBotEditor editor = bot.editorByTitle("ConfigTestController.java");
        
        SWTBotEclipseEditor javaEditor = editor.toTextEditor();
        javaEditor.show();

        javaEditor.navigateTo(9, 33);
        //bot.sleep(2000);
        
        try {
        	javaEditor.pressShortcut(KeyStroke.getInstance("F2"));
        	//bot.sleep(7000);
        }
        catch (ParseException e1) {
			// TODO Auto-generated catch block
        	e1.printStackTrace();
		} 
        
        final Control cntl = javaEditor.bot().getFocusedWidget();
        bot.waitUntil(SWTTestCondition.isControlActive(cntl, "mp-config hover"), 2000);
        //bot.sleep(7000);
		
		Matcher<? extends Browser> matcher = WidgetMatcherFactory.widgetOfType(Browser.class);
		List<? extends Browser> widgets = new SWTBot().widgets(matcher);
		String styledText = null;
		final String[] textStr = new String[1];
		
		for (Browser b : widgets) {
		  // Create a copy to work with to avoid Invalid Thread exception
			bot.waitUntil(SWTTestCondition.isBrowserActive(b, "mp-config hover"), 2000);
	        Display.getDefault().syncExec(new Runnable() {
	        	public void run() {
	        		textStr[0] = b.getText();
	        	}
	        });
		  styledText = textStr[0];
		  System.out.println("AJM: text -> " + styledText);
		  Assertions.assertTrue(styledText.contains("injected.value=my val"));
		}
		
     // set to the default speed
        //SWTBotPreferences.PLAYBACK_DELAY = 0;
		
    }

    /**
     * Tests opening the dashboard using the main toolbar icon.
     */
    //@Test
    public void testSnippets() {
    	// slow down tests
        SWTBotPreferences.PLAYBACK_DELAY = 10;
        
        SWTBotTreeItem appProj = null;
        bot.viewByTitle("Project Explorer").show();
        bot.sleep(7000);
        SWTBotTreeItem[] appProjects = bot.tree().getAllItems();
        bot.sleep(7000);
        for (int i = 0; i < appProjects.length; i++) {
        	System.out.println("AJM: contains -> " + appProjects[i].getText());
            if (appProjects[i].getText().contains("demo")) {
                appProj = appProjects[i];
                break;
            }
        }
        
        bot.sleep(10000);
        // need to start app first to get a target dir created
        // Start dev mode.
        SWTPluginOperations.launchAppMenuStartAction(bot, dashboard, MVN_APP_NAME);
        bot.sleep(7000);
        SWTBotView terminal = bot.viewByTitle("Terminal");
        bot.sleep(7000);
        terminal.show();
        bot.sleep(7000);

        // Validate application is up and running.
        //validateApplicationOutcome(MVN_APP_NAME, true, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Stop dev mode.
        SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        terminal.show();

        // Validate application stopped.
        //validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty");

        // Close the terminal.
        terminal.close();
        
        // now check hover
        appProj.select();
        appProj.expand();
        SWTBotTreeItem dir = appProj.getNode("src/main/java");
        dir.select();
        SWTBotTreeItem filedir = dir.expand().getNode("com.example.demo.health").select().expand();
        SWTBotTreeItem[] subtrees = filedir.getItems();
        
        SWTBotTreeItem file = filedir.select().expand().getNode("MyHealth.java");
        
        file.select();
        file.doubleClick();
        bot.sleep(4000);
        
        SWTBotEditor editor = bot.editorByTitle("MyHealth.java");
        System.out.println("editor: " + editor);
        editor.show();
        editor.setFocus();
        //editor = searchForEditor(bot, "ConfigTestController.java");
        //System.out.println("editor2: " + editor);
        //editor.show();
        
        
        
        
        //SWTBotEditor editor = searchForEditor(bot, "ConfigTestController.java");
        //editor.show();
        SWTBotEclipseEditor javaEditor = editor.toTextEditor();
        javaEditor.navigateTo(1, 1);
        bot.sleep(2000);
        //javaEditor.navigateTo(10, 45);
        
        String text = null;
        SWTBotShell [] shells;
        KeyStroke ctrl = KeyStroke.getInstance(SWT.CTRL, 0);
        KeyStroke space = KeyStroke.getInstance(SWT.SPACE, 0);
        KeyboardFactory.getSWTKeyboard().pressShortcut(Keystrokes.CTRL, Keystrokes.SPACE);
        bot.sleep(5000);
        javaEditor.bot().table().select("mpliveness");
        javaEditor.bot().table().getTableItem("mpliveness").doubleClick();

        //javaEditor.pressShortcut(ctrl , space);
        
		bot.sleep(5000);
		//shells = bot.shells();
        
		/*
        final Control cntl = javaEditor.bot().getFocusedWidget();
        bot.sleep(5000);
        
		Shell popupWindow = syncExec(new WidgetResult<Shell>() {
			public Shell run() {
				return cntl.getShell();
			}
		});
		
		Matcher<? extends Browser> matcher = WidgetMatcherFactory.widgetOfType(Browser.class);
		List<? extends Browser> widgets = new SWTBot().widgets(matcher);
		String styledText = null;
		final String[] textStr = new String[1];
		
		for (Browser b : widgets) {
		  // Create a copy to work with to avoid Invalid Thread exception
		  SWTBotBrowser browserWindow = new SWTBotBrowser(b);
	        Display.getDefault().syncExec(new Runnable() {
	        	public void run() {
	        		browserWindow.widget.setFocus();
	        		textStr[0] = browserWindow.widget.getText();
	        	}
	        });
		  styledText = textStr[0];
		  System.out.println("AJM: text -> " + styledText);
		  // Do stuff...
		}
		
		/*
		Matcher<? extends Composite> compMatcher = WidgetMatcherFactory.widgetOfType(Composite.class);
		List<? extends Composite> compWidgets = new SWTBot(popupWindow).widgets(matcher);
		//String styledText = null;
		
		for (Composite c : compWidgets) {
			//Control[] children = c.getChildren();
		  // Create a copy to work with to avoid Invalid Thread exception
		  //SWTBotCCombo compWindow = new SWTBotComposite(c);
		  //styledText = styledTextWindow.getText();
		  // Do stuff...
		}
		*/
		//try {
		//	bot.wait(5000);
		//} catch (InterruptedException e) {
			// TODO Auto-generated catch block
		//	e.printStackTrace();
		//}
		/*
		bot.sleep(5000);
        //String cntlText = popupWindow.getText();
        final String[] textStr = new String[1];
        Display.getDefault().syncExec(new Runnable() {
        	public void run() {
        		textStr[0] = popupWindow.getS;
        	}
        });
        System.out.println("AJM: text = " + textStr[0]);
			/*final SWTBotStyledText styledText = javaEditor.getStyledText();
			Shell mainWindow = syncExec(new WidgetResult<Shell>() {
				public Shell run() {
					return styledText.widget.getShell();
				}
			});
			SWTBotShell shell = bot.shell("", mainWindow); //$NON-NLS-1$
			//shell.activate();
			//bot.sleep(6000);
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					shell.setFocus();
					String tooltiptext=shell.widget.getText();
					String tooltipexttext = shell.getToolTipText();
					System.out.println("Here " + shell.widget.getText() + "extra " + shell.getToolTipText());
				}}); */
		
        
        bot.sleep(4000);
     // set to the default speed
        SWTBotPreferences.PLAYBACK_DELAY = 0;
		
    }

    /**
     * Imports existing Maven application projects into the workspace.
     */
    public static void importMavenApplications() {
        Display.getDefault().syncExec(new Runnable() {

            @Override
            public void run() {
                File workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile();
                ArrayList<String> projectPaths = new ArrayList<String>();
                projectPaths.add(projectPath.toString());

                try {
                    importProjects(workspaceRoot, projectPaths);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    
    /**
     * Imports the specified list of projects.
     *
     * @param workspaceRoot The workspace root location.
     * @param folders The list of folders containing the projects to install.
     *
     * @throws InterruptedException
     * @throws CoreException
     */
    public static void importProjects(File workspaceRoot, List<String> folders) throws InterruptedException, CoreException {
        // Get the list of projects to install.
        MavenModelManager modelManager = MavenPlugin.getMavenModelManager();
        LocalProjectScanner lps = new LocalProjectScanner(workspaceRoot, folders, false, modelManager);
        lps.run(new NullProgressMonitor());
        List<MavenProjectInfo> projects = lps.getProjects();

        // Import the projects.
        ProjectImportConfiguration projectImportConfig = new ProjectImportConfiguration();
        IProjectConfigurationManager projectConfigurationManager = MavenPlugin.getProjectConfigurationManager();
        projectConfigurationManager.importProjects(projects, projectImportConfig, new NullProgressMonitor());
    }
    
    /**
     * Searches for the text editor that contains the complete or partial input title name.
     *
     * @param bot The SWTWorkbenchBot instance.
     * @param titleContent The complete or partial title name.
     *
     * @return The text editor object associated with input title name.
     */
    public static SWTBotEditor searchForEditor(SWTWorkbenchBot bot, String titleContent) {
        Iterator<? extends SWTBotEditor> editors = bot.editors().iterator();
        SWTBotEditor editor = null;
        while (editors.hasNext()) {
            editor = editors.next();
            if (editor.getTitle().contains(titleContent)) {
                editor.show();
                break;
            }
        }
        return editor;
    }
}
