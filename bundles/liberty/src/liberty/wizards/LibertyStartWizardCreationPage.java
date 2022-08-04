/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Jakub Jurkiewicz <jakub.jurkiewicz@gmail.com> - Fix for Bug 174737
 *     [IDE] New Plug-in Project wizard status handling is inconsistent
 *     Oakland Software Incorporated (Francis Upton) <francisu@ieee.org>
 *		    Bug 224997 [Workbench] Impossible to copy project
 *******************************************************************************/
package liberty.wizards;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.*;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.util.BidiUtils;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.WorkingSetGroup;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.eclipse.ui.internal.ide.IIDEHelpContextIds;
import org.eclipse.ui.internal.ide.dialogs.ProjectContentsLocationArea;
import org.eclipse.ui.internal.ide.dialogs.ProjectContentsLocationArea.IErrorMessageReporter;

/**
 * Standard main page for a wizard that is creates a project resource.
 * <p>
 * This page may be used by clients as-is; it may be also be subclassed to suit.
 * </p>
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * mainPage = new WizardNewProjectCreationPage("basicNewProjectPage");
 * mainPage.setTitle("Project");
 * mainPage.setDescription("Create a new project resource.");
 * </pre>
 */
public class LibertyStartWizardCreationPage extends WizardPage {

	// initial value stores
	private String initialProjectFieldValue;
	private String initialProjectGroupFieldValue = "com.demo";
	Combo microProfile;
	Combo javaVersionEE;
	
	String[] microProfileVersions;
	String[] javaVersionsEE;
	HashMap<String, JSONArray> dependenciesEE2MP;
	HashMap<String, JSONArray> dependenciesMP2EE;
	
    JSONArray optionsBuild;
    JSONArray optionsSE;
    JSONArray optionsEE;
    JSONArray optionsMP;
    
    String selectionBuild;
    String selectionSE;
    String selectionEE;
    String selectionMP;
    
    String defaultBuild;
    String defaultSE;

	// widgets
	Text projectNameField;
	Text projectGroupField;
	Label infoLabel;

	private Listener nameModifyListener = e -> {
		setLocationForSelection();
		boolean valid = validatePage();
		setPageComplete(valid);

	};

	private ProjectContentsLocationArea locationArea;

	private WorkingSetGroup workingSetGroup;

	// constants
	private static final int SIZING_TEXT_FIELD_WIDTH = 250;

	/**
	 * Creates a new project creation wizard page.
	 *
	 * @param pageName the name of this page
	 */
	public LibertyStartWizardCreationPage(String pageName) {
		super(pageName);
		setPageComplete(false);	
		
	}

	/**
	 * Creates a new project creation wizard page.
	 *
	 * @param pageName        the name of this page
	 * @param selection       the current workbench selection
	 * @param workingSetTypes an array of working set type IDs that will restrict
	 *                        what types of working sets can be chosen in this group
	 *
	 * @deprecated default placement of the working set group has been removed. If
	 *             you wish to use the working set block please call
	 *             {@link #createWorkingSetGroup(Composite, IStructuredSelection, String[])}
	 *             in your overridden {@link #createControl(Composite)}
	 *             implementation.
	 * @since 3.4
	 */
	@Deprecated
	public LibertyStartWizardCreationPage(String pageName, IStructuredSelection selection, String[] workingSetTypes) {
		this(pageName);
	}

	@Override
	public void createControl(Composite parent) {
		
		
        HttpClient client = HttpClient.newHttpClient();
        try {
			HttpRequest request = HttpRequest.newBuilder()
					  .uri(new URI("https://start.openliberty.io/api/start/info"))
					  .GET()
					  .build();
			
		    HttpResponse<String> response =
		            client.send(request, BodyHandlers.ofString());

		    System.out.println(response.body());

		    try {
		        JSONObject jsonObject = new JSONObject(response.body());
		        setInitialProjectName(jsonObject.getJSONObject("a").get("default").toString());
		        System.out.println(jsonObject.getJSONObject("a").get("default").toString());
		        optionsBuild = jsonObject.getJSONObject("b").getJSONArray("options");
		        optionsSE = jsonObject.getJSONObject("j").getJSONArray("options");
		        optionsEE = jsonObject.getJSONObject("e").getJSONArray("options");
		        optionsMP = jsonObject.getJSONObject("m").getJSONArray("options");
		        System.out.println(optionsSE);
		        System.out.println(optionsBuild);
		        System.out.println(optionsEE);
		        System.out.println(optionsMP);
		        
		    	dependenciesEE2MP = new HashMap<String, JSONArray>();
		    	dependenciesMP2EE = new HashMap<String, JSONArray>();
		        
		        for(int i = 0; i < optionsEE.length(); i++) {
		        	JSONArray validMPVersions = jsonObject.getJSONObject("e").getJSONObject("constraints").getJSONObject(optionsEE.getString(i)).getJSONArray("m");
		        	System.out.println(optionsEE.getString(i));
		        	System.out.println(validMPVersions.toString());
		        	dependenciesEE2MP.put(optionsEE.getString(i), validMPVersions);
		        	
		        	for(int x = 0; x < validMPVersions.length(); x++) {
			        	if(!dependenciesMP2EE.containsKey(validMPVersions.getString(x))) {
			        		JSONArray arr = new JSONArray();
			        		dependenciesMP2EE.put(validMPVersions.getString(x), arr);
			        	}
			        	dependenciesMP2EE.get(validMPVersions.getString(x)).put(optionsEE.getString(i));
			        	System.out.println(validMPVersions.getString(x) + " : " + dependenciesMP2EE.get(validMPVersions.getString(x)));
		        	}
		        	
		        }
		        
		        defaultBuild = jsonObject.getJSONObject("b").get("default").toString();
		        defaultSE = jsonObject.getJSONObject("j").get("default").toString();

		    }catch (JSONException err){
		        System.out.println(err.toString());
		    }
		    
			
		} catch (URISyntaxException e) {
			System.out.println("uhoh");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("uhoh2");
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.out.println("uhoh3");
			e.printStackTrace();
		}
        
		Composite composite = new Composite(parent, SWT.NULL);

		initializeDialogUnits(parent);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IIDEHelpContextIds.NEW_PROJECT_WIZARD_PAGE);

		composite.setLayout(new GridLayout());
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		createProjectNameGroup(composite);
		locationArea = new ProjectContentsLocationArea(getErrorReporter(), composite);
		if (initialProjectFieldValue != null) {
			locationArea.updateProjectName(initialProjectFieldValue);
		}
		
		createProjectGroup(composite);

		// Scale the button based on the rest of the dialog
		setButtonLayoutData(locationArea.getBrowseButton());		
		
		Label label = new Label(composite, SWT.NULL);
		label.setText("&Build Tool:");

	    Combo buildTool = new Combo(composite, SWT.BORDER | SWT.READ_ONLY);
	    //String buildTools[] = { "Maven", "Gradle" };
	    
	    String[] buildTools = new String[optionsBuild.length()];
	    for(int i=0; i< buildTools.length; i++) {
	    	buildTools[i]= optionsBuild.optString(i);
	    }
	    
  	    int indexBuild = 0;
  	    //TURN THIS INTO A METHOD
	  	for(int i=0; i< buildTools.length; i++) {
	  		if(buildTools[i].equals(defaultBuild)) {
	  			indexBuild = i;
	  			System.out.println("DEFAULT BUILD: " + buildTools[i]);
	  		}
		}
	    
	    buildTool.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	    buildTool.setItems(buildTools);
	    buildTool.select(indexBuild);
	    selectionBuild = buildTools[indexBuild];

	    buildTool.addSelectionListener(new SelectionAdapter() {
	      public void widgetSelected(SelectionEvent e) {
	        System.out.println(buildTool.getText());
	      }
	    });

		Label label1 = new Label(composite, SWT.NULL);
		label1.setText("&Java SE Version:");

	    Combo javaVersionSE = new Combo(composite, SWT.BORDER | SWT.READ_ONLY);
	    //String javaVersionsSE[] = { "17", "11", "8" };
	    
	    String[] javaVersionsSE = new String[optionsSE.length()];
	    for(int i=0; i< javaVersionsSE.length; i++) {
	    	javaVersionsSE[i]= optionsSE.optString(i);
	    }
	    
  	    int indexSE = 0;
  	    //TURN THIS INTO A METHOD
	  	for(int i=0; i< javaVersionsSE.length; i++) {
	  		if(javaVersionsSE[i].equals(defaultSE)) {
	  			indexSE = i;
	  			System.out.println("DEFAULT SE: " + javaVersionsSE[i]);
	  		}
		}
	    
	    javaVersionSE.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	    javaVersionSE.setItems(javaVersionsSE);
	    javaVersionSE.select(indexSE);
	    selectionSE = javaVersionsSE[indexSE];
	    
	    javaVersionSE.addSelectionListener(new SelectionAdapter() {
		      public void widgetSelected(SelectionEvent e) {
			        System.out.println(javaVersionSE.getText());
			  }
		});
	    
		Label label2 = new Label(composite, SWT.NULL);
		label2.setText("&Java EE/Jakarta EE Version:");
	    
	    javaVersionEE = new Combo(composite, SWT.BORDER | SWT.READ_ONLY);
	    //String javaVersionsEE[] = { "9.1", "8.0", "7.0", "None" };
	    
	    javaVersionsEE = new String[optionsEE.length()];
	    for(int i=0; i< javaVersionsEE.length; i++) {
	    	javaVersionsEE[i]= optionsEE.optString(i);
	    }
	    javaVersionEE.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	    javaVersionEE.setItems(javaVersionsEE);
	    javaVersionEE.select(javaVersionsEE.length-1);
	    selectionEE = javaVersionsEE[javaVersionsEE.length-1];
	    
	    javaVersionEE.addSelectionListener(new SelectionAdapter() {
		      public void widgetSelected(SelectionEvent e) {
		    	  selectionEE = javaVersionEE.getText();			        
		    	  System.out.println("Java EE Version: " + selectionEE);
		    	  System.out.println("Dependencies: " + dependenciesEE2MP.get(selectionEE));
		    	  //String[] microProfileVersions = new String[dependenciesEE2MP.get(selectionEE).length()];
		    	  JSONArray arr = dependenciesEE2MP.get(selectionEE);
		    	  String toFind = arr.getString(arr.length()-1);
		    	  System.out.println(toFind);
		    	  int index = 0;
			  	  for(int i=0; i< microProfileVersions.length; i++) {
			  		  if(microProfileVersions[i].equals(toFind)) {
			  			  index = i;
			  			System.out.println("MATCH: " + microProfileVersions[i]);
			  		  }
				  }
				  //microProfile.setItems(microProfileVersions);
				  if(!selectionMP.equals(microProfileVersions[index])) {
					  infoLabel.setText("MicroProfile Version has been automatically updated from "+ selectionMP +" to "+ microProfileVersions[index] +" for compatibility with Java EE / Jakarta EE Version.");
				  } else {
					  infoLabel.setText("");
				  }
				  microProfile.select(index);
				  selectionMP = microProfileVersions[index];
			  }
		});
	   
		Label label3 = new Label(composite, SWT.NULL);
		label3.setText("&MicroProfile Version:");
	    
	    microProfile = new Combo(composite, SWT.BORDER | SWT.READ_ONLY);
	    //String microProfileVersions[] = { "5.0", "4.0", "3.3", "2.2", "1.4", "none" };
	    
	    microProfileVersions = new String[optionsMP.length()];
	    for(int i=0; i< microProfileVersions.length; i++) {
	    	microProfileVersions[i]= optionsMP.optString(i);
	    }
	    microProfile.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	    microProfile.setItems(microProfileVersions);
	    microProfile.select(microProfileVersions.length-1);
	    selectionMP = microProfileVersions[microProfileVersions.length-1];
	    
	    microProfile.addSelectionListener(new SelectionAdapter() {
		      public void widgetSelected(SelectionEvent e) {
		    	  selectionMP = microProfile.getText();	
			      System.out.println("Micro Profile Version: " + selectionMP);
			      System.out.println("Dependencies: " + dependenciesMP2EE.get(selectionMP));
			      //javaVersionsEE = new String[dependenciesMP2EE.get(selectionMP).length()];
		    	  JSONArray arr = dependenciesMP2EE.get(selectionMP);
		    	  String toFind = arr.getString(arr.length()-1);
		    	  System.out.println(toFind);
			      int index = 0;
				  for(int i=0; i< javaVersionsEE.length; i++) {
					  if(javaVersionsEE[i].equals(toFind)) {
						  index = i;
						  System.out.println("MATCH: " + javaVersionsEE[i]);
					  }
				  }
				  //javaVersionEE.setItems(javaVersionsEE);
				  
				  if(!selectionEE.equals(javaVersionsEE[index])) {
					  infoLabel.setText("Java EE / Jakarta EE Version has been automatically updated from "+ selectionEE +" to "+ javaVersionsEE[index] +" for compatibility with MicroProfile Version.");
				  } else {
					  infoLabel.setText("");
				  }
				  javaVersionEE.select(index);
				  selectionEE = javaVersionsEE[index];
				  System.out.println(javaVersionsEE.length-1 + " : " + selectionEE);
			   }
		});
		
	    infoLabel = new Label(composite, SWT.NULL);
	    infoLabel.setText("___________________________________________________________________________________________________________________________");
	    
		setPageComplete(validatePage());
		// Show description on opening
		setErrorMessage(null);
		setMessage(null);
		setControl(composite);
		Dialog.applyDialogFont(composite);
		
	}

	/**
	 * Create a working set group for this page. This method can only be called
	 * once.
	 *
	 * @param composite                the composite in which to create the group
	 * @param selection                the current workbench selection
	 * @param supportedWorkingSetTypes an array of working set type IDs that will
	 *                                 restrict what types of working sets can be
	 *                                 chosen in this group
	 * @return the created group. If this method has been called previously the
	 *         original group will be returned.
	 * @since 3.4
	 */
	public WorkingSetGroup createWorkingSetGroup(Composite composite, IStructuredSelection selection,
			String[] supportedWorkingSetTypes) {
		if (workingSetGroup != null)
			return workingSetGroup;
		workingSetGroup = new WorkingSetGroup(composite, selection, supportedWorkingSetTypes);
		return workingSetGroup;
	}

	/**
	 * Get an error reporter for the receiver.
	 *
	 * @return IErrorMessageReporter
	 */
	private IErrorMessageReporter getErrorReporter() {
		return (errorMessage, infoOnly) -> {
			if (infoOnly) {
				setMessage(errorMessage, IStatus.INFO);
				setErrorMessage(null);
			} else
				setErrorMessage(errorMessage);
			boolean valid = errorMessage == null;
			if (valid) {
				valid = validatePage();
			}

			setPageComplete(valid);
		};
	}

	/**
	 * Creates the project name specification controls.
	 *
	 * @param parent the parent composite
	 */
	private final void createProjectNameGroup(Composite parent) {
		// project specification group
		Composite projectGroup = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		projectGroup.setLayout(layout);
		projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// new project label
		Label projectLabel = new Label(projectGroup, SWT.NONE);
		projectLabel.setText("Project Name/ Artifact:");
		projectLabel.setFont(parent.getFont());

		// new project name entry field
		projectNameField = new Text(projectGroup, SWT.BORDER);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = SIZING_TEXT_FIELD_WIDTH;
		projectNameField.setLayoutData(data);
		projectNameField.setFont(parent.getFont());

		// Set the initial value first before listener
		// to avoid handling an event during the creation.
		if (initialProjectFieldValue != null) {
			projectNameField.setText(initialProjectFieldValue);
		}
		projectNameField.addListener(SWT.Modify, nameModifyListener);
		BidiUtils.applyBidiProcessing(projectNameField, BidiUtils.BTD_DEFAULT);
	}
	
	/**
	 * Creates the project name specification controls.
	 *
	 * @param parent the parent composite
	 */
	private final void createProjectGroup(Composite parent) {
		// project specification group
		Composite projectGroup = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		projectGroup.setLayout(layout);
		projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// new project label
		Label projectLabel = new Label(projectGroup, SWT.NONE);
		projectLabel.setText("Group:");
		projectLabel.setFont(parent.getFont());

		// new project name entry field
		projectGroupField = new Text(projectGroup, SWT.BORDER);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = SIZING_TEXT_FIELD_WIDTH;
		projectGroupField.setLayoutData(data);
		projectGroupField.setFont(parent.getFont());

		// Set the initial value first before listener
		// to avoid handling an event during the creation.
		if (initialProjectGroupFieldValue != null) {
			projectGroupField.setText(initialProjectGroupFieldValue);
		}
		projectGroupField.addListener(SWT.Modify, nameModifyListener);
		//BidiUtils.applyBidiProcessing(projectNameField, BidiUtils.BTD_DEFAULT);
	}

	/**
	 * Returns the current project location path as entered by the user, or its
	 * anticipated initial value. Note that if the default has been returned the
	 * path in a project description used to create a project should not be set.
	 *
	 * @return the project location path or its anticipated initial value.
	 */
	public IPath getLocationPath() {
		return new Path(locationArea.getProjectLocation());
	}

	/**
	 * /** Returns the current project location URI as entered by the user, or
	 * <code>null</code> if a valid project location has not been entered.
	 *
	 * @return the project location URI, or <code>null</code>
	 * @since 3.2
	 */
	public URI getLocationURI() {
		return locationArea.getProjectLocationURI();
	}

	/**
	 * Creates a project resource handle for the current project name field value.
	 * The project handle is created relative to the workspace root.
	 * <p>
	 * This method does not create the project resource; this is the responsibility
	 * of <code>IProject::create</code> invoked by the new project resource wizard.
	 * </p>
	 *
	 * @return the new project resource handle
	 */
	public IProject getProjectHandle() {
		return ResourcesPlugin.getWorkspace().getRoot().getProject(getProjectName());
	}

	/**
	 * Returns the current project name as entered by the user, or its anticipated
	 * initial value.
	 *
	 * @return the project name, its anticipated initial value, or <code>null</code>
	 *         if no project name is known
	 */
	public String getProjectName() {
		if (projectNameField == null) {
			return initialProjectFieldValue;
		}

		return getProjectNameFieldValue();
	}

	/**
	 * Returns the value of the project name field with leading and trailing spaces
	 * removed.
	 *
	 * @return the project name in the field
	 */
	private String getProjectNameFieldValue() {
		if (projectNameField == null) {
			return ""; //$NON-NLS-1$
		}

		return projectNameField.getText().trim();
	}

	/**
	 * Returns the current project name as entered by the user, or its anticipated
	 * initial value.
	 *
	 * @return the project name, its anticipated initial value, or <code>null</code>
	 *         if no project name is known
	 */
	public String getGroupName() {
		if (projectGroupField == null) {
			return initialProjectGroupFieldValue;
		}

		return getGroupNameFieldValue();
	}

	/**
	 * Returns the value of the project name field with leading and trailing spaces
	 * removed.
	 *
	 * @return the project name in the field
	 */
	private String getGroupNameFieldValue() {
		if (projectGroupField == null) {
			return ""; //$NON-NLS-1$
		}

		return projectGroupField.getText().trim();
	}
	
	/**
	 * Sets the initial project name that this page will use when created. The name
	 * is ignored if the createControl(Composite) method has already been called.
	 * Leading and trailing spaces in the name are ignored. Providing the name of an
	 * existing project will not necessarily cause the wizard to warn the user.
	 * Callers of this method should first check if the project name passed already
	 * exists in the workspace.
	 *
	 * @param name initial project name for this page
	 *
	 * @see IWorkspace#validateName(String, int)
	 *
	 */
	public void setInitialProjectName(String name) {
		if (name == null) {
			initialProjectFieldValue = null;
		} else {
			initialProjectFieldValue = name.trim();
			if (locationArea != null) {
				locationArea.updateProjectName(name.trim());
			}
		}
	}
	
	/**
	 * Set the location to the default location if we are set to useDefaults.
	 */
	void setLocationForSelection() {
		locationArea.updateProjectName(getProjectNameFieldValue());
	}

	/**
	 * Returns whether this page's controls currently all contain valid values.
	 *
	 * @return <code>true</code> if all controls are valid, and <code>false</code>
	 *         if at least one is invalid
	 */
	protected boolean validatePage() {
		IWorkspace workspace = IDEWorkbenchPlugin.getPluginWorkspace();

		String projectFieldContents = getProjectNameFieldValue();
		String projectGroupContents = getGroupName();

		if (projectFieldContents.isEmpty()) {
			setErrorMessage(null);
			setMessage(IDEWorkbenchMessages.WizardNewProjectCreationPage_projectNameEmpty);
			return false;
		}
		
		if (!projectFieldContents.matches("^([a-z]+-)*[a-z]+$")) {
			System.out.println(projectFieldContents);
			setErrorMessage("App name must be a-z characters separated by dashes (-)");
			return false;
		} 
		
		if (!projectGroupContents.matches("^([a-z]+\\.)*[a-z]+$")) {
			System.out.println(projectGroupContents);
			setErrorMessage("Group name must be a-z separated by periods (.)");
			return false;
		}
		
		IStatus nameStatus = workspace.validateName(projectFieldContents, IResource.PROJECT);
		if (!nameStatus.isOK()) {
			setErrorMessage(nameStatus.getMessage());
			return false;
		}

		IProject handle = getProjectHandle();
		if (handle.exists()) {
			setErrorMessage(IDEWorkbenchMessages.WizardNewProjectCreationPage_projectExistsMessage);
			return false;
		}

		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(getProjectNameFieldValue());
		locationArea.setExistingProject(project);

		String validLocationMessage = locationArea.checkValidLocation();
		if (validLocationMessage != null) { // there is no destination location given
			setErrorMessage(validLocationMessage);
			return false;
		}

		setErrorMessage(null);
		setMessage(null);
		return true;
	}

	/*
	 * see @DialogPage.setVisible(boolean)
	 */
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			projectNameField.setFocus();
		}
	}

	/**
	 * Returns the useDefaults.
	 *
	 * @return boolean
	 */
	public boolean useDefaults() {
		return locationArea.isDefault();
	}

	/**
	 * Return the selected working sets, if any. If this page is not configured to
	 * interact with working sets this will be an empty array.
	 *
	 * @return the selected working sets
	 * @since 3.4
	 */
	public IWorkingSet[] getSelectedWorkingSets() {
		return workingSetGroup == null ? new IWorkingSet[0] : workingSetGroup.getSelectedWorkingSets();
	}
}
