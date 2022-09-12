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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import  org.eclipse.swt.widgets.Control;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
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
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotMultiPageEditor;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.keyboard.KeyboardFactory;
import org.eclipse.swtbot.swt.finder.keyboard.Keystrokes;
import org.eclipse.swtbot.swt.finder.matchers.WidgetMatcherFactory;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTable;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotText;
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
    
    static SWTBotView projectExp;
    static SWTBotTreeItem appProj;
    
    /**
     * Setup.
     */
    @BeforeAll
    public static void setup() {
        bot = new SWTWorkbenchBot();
        SWTPluginOperations.closeWelcomePage(bot);
        System.out.println("AJM: importing app");
        importMavenApplications();
        dashboard = SWTPluginOperations.openDashboardUsingMenu(bot);
        
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
        
        projectExp = bot.viewByTitle("Project Explorer");
    	projectExp.show();
        bot.waitUntil(SWTTestCondition.isViewActive(projectExp, "Project Explorer"));
        SWTBotTreeItem[] topLevelProjects = projectExp.bot().tree().getAllItems();
        appProj = null;
        for (SWTBotTreeItem project : topLevelProjects) {
            if (project.getText().contains("demo-service-a")) {
            	appProj = project;
            }
        }
        System.out.println("AJM: after loading the proj explorer and app");
    }

    /**
     * Cleanup.
     */
    @AfterAll
    public static void cleanup() {
        // Stop dev mode.
        //SWTPluginOperations.launchAppMenuStopAction(bot, dashboard, MVN_APP_NAME);
        //terminal.show();
       
        //validateApplicationOutcome(MVN_APP_NAME, false, projectPath.toAbsolutePath().toString() + "/target/liberty", appMsg, appURL );
        
        // Close the terminal.
        //terminal.close();
        
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
        /*
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
        do the above in setup now? */
    	
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
        
        String expectedHealthText = "package com.example.demo.health;\r\n"
        		+ "\r\n"
        		+ "import org.eclipse.microprofile.health.HealthCheck;\r\n"
        		+ "import org.eclipse.microprofile.health.HealthCheckResponse;\r\n"
        		+ "import org.eclipse.microprofile.health.Liveness;\r\n"
        		+ "\r\n"
        		+ "import javax.enterprise.context.ApplicationScoped;\r\n"
        		+ "\r\n"
        		+ "@Liveness\r\n"
        		+ "@ApplicationScoped\r\n"
        		+ "public class ${TM_FILENAME_BASE} implements HealthCheck {\r\n"
        		+ "\r\n"
        		+ "	@Override\r\n"
        		+ "	public HealthCheckResponse call() {\r\n"
        		+ "		return HealthCheckResponse.named(${TM_FILENAME_BASE}.class.getSimpleName()).withData(\"live\",true).up().build();\r\n"
        		+ "	}\r\n"
        		+ "}";
        
        appProj.expand()
	       .getNode("src").expand()
	       .getNode("main").expand()
	       .getNode("java").expand()
	       .getNode("com").expand()
	       .getNode("example").expand()
	       .getNode("demo").expand()
	       .getNode("health").expand()
	       .getNode("MyHealth.java").select().doubleClick();
 
		 SWTBotEditor editor = bot.editorByTitle("MyHealth.java");
		 
		 SWTBotEclipseEditor javaEditor = editor.toTextEditor();
		 javaEditor.show();
		 javaEditor.setFocus();
		
		 javaEditor.navigateTo(1, 1);
		   
			        //KeyboardFactory.getSWTKeyboard().pressShortcut(Keystrokes.CTRL, Keystrokes.SPACE);
			        //KeyboardFactory.getSWTKeyboard().typeText("mplive");
			        //KeyboardFactory.getSWTKeyboard().pressShortcut(Keystrokes.CR, Keystrokes.LF);
	        		//javaEditor.autoCompleteProposal("mplive", "mpliveness");
		 			javaEditor.setFocus();
		 			javaEditor.bot().activeShell().activate();
			        javaEditor.pressShortcut(Keystrokes.CTRL, Keystrokes.SPACE);
			        javaEditor.typeText("mplive");
			        javaEditor.pressShortcut(Keystrokes.CR, Keystrokes.LF);
			        javaEditor.save();	
	        
	        String gotText = javaEditor.getText();
	        System.out.println("AJM: retrieved health code: " + gotText);
	        
	        Assertions.assertTrue(gotText.contains(expectedHealthText));


	        bot.sleep(5000);
        
     // set to the default speed
        SWTBotPreferences.PLAYBACK_DELAY = 0;
		
    }

    /**
     * Tests opening the dashboard using the main toolbar icon.
     */
    @Test
    public void testServerXMLAddFeature() {
    	// slow down tests
        SWTBotPreferences.PLAYBACK_DELAY = 10;
        
        appProj.expand()
	       .getNode("src").expand()
	       .getNode("main").expand()
	       .getNode("liberty").expand()
	       .getNode("config").expand()
	       .getNode("server.xml").select().doubleClick();
 
        SWTBotEditor editor = bot.editorByTitle("server.xml");
		 
		 SWTBotEclipseEditor xmlEditor = editor.toTextEditor();
		 
		 xmlEditor.show();
		 xmlEditor.setFocus();
		 
		 String serverXMLBeforeText = xmlEditor.getText();
		 Assertions.assertFalse(serverXMLBeforeText.contains("<feature>el-3.0</feature>"));
		
		 xmlEditor.navigateTo(4, 43);
		 
		 xmlEditor.pressShortcut(Keystrokes.CR, Keystrokes.LF);
		 
			xmlEditor.pressShortcut(Keystrokes.CTRL, Keystrokes.SPACE);
			xmlEditor.typeText("feat");
			bot.sleep(5000);
			xmlEditor.pressShortcut(Keystrokes.CR, Keystrokes.LF);

			xmlEditor.pressShortcut(Keystrokes.CTRL, Keystrokes.SPACE);
			xmlEditor.typeText("el-3");
			bot.sleep(5000);
			xmlEditor.pressShortcut(Keystrokes.CR, Keystrokes.LF);
			//xmlEditor.pressShortcut(Keystrokes.CR, Keystrokes.LF);

			//xmlEditor.selectCurrentLine();
			//bot.sleep(5000);
			//String addedFeature = xmlEditor.getSelection();
			
			//xmlEditor.save();
			String serverXMLAfterText = xmlEditor.getText();
			 Assertions.assertTrue(serverXMLAfterText.contains("<feature>el-3.0</feature>"));

	        //bot.sleep(5000);
        
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
