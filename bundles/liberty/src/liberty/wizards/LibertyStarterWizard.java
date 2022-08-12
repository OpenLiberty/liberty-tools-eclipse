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

package liberty.wizards;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;

import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.eclipse.ui.dialogs.WizardNewProjectReferencePage;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.operation.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import java.io.*;
import org.eclipse.ui.*;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.wizards.datatransfer.ZipLeveledStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;

/**
 * This is a Liberty Starter wizard. Its role is to call the Liberty Starter API
 * resource in the provided container. 
 */

public class LibertyStarterWizard extends Wizard implements INewWizard {
	private LibertyStartWizardCreationPage mainPage;
	private WizardNewProjectReferencePage testPage;
	private ISelection selection;
	
    /**
     * The default buffer size for output stream
     */
	public static final int DEFAULT_BUFFER_SIZE = 8192;
	
	/**
	 * Constructor for LibertyStarterWizard.
	 */
	public LibertyStarterWizard() {
		super();
		setNeedsProgressMonitor(true);
	}
	
	/**
	 * Adding the page to the wizard.
	 */
	@Override
	public void addPages() {
		mainPage = new LibertyStartWizardCreationPage("libertyStarterProjectPage");
		mainPage.setTitle("Liberty Starter Project");
		mainPage.setDescription("Create a new liberty project resource.");
		addPage(mainPage);
	}

	/**
	 * This method is called when 'Finish' button is pressed in
	 * the wizard. We will create an operation and run it
	 * using wizard as execution context.
	 */
	@Override
	public boolean performFinish() {
		//Take input from the LibertyStartWizardCreationPage form after user clicks 'finish'
        String appName = mainPage.getProjectName();
        String groupName = mainPage.getGroupName();
        String build = mainPage.selectionBuild;
        String SE = mainPage.selectionSE;
        String EE = mainPage.selectionEE;
        String MP = mainPage.selectionMP;
        
		IRunnableWithProgress op = monitor -> {
			//Send API request to Liberty Starter endpoint and stream to zip file in downloads
	        HttpClient client = HttpClient.newHttpClient();
	        try {
				HttpRequest request = HttpRequest.newBuilder()
						  .uri(new URI("https://start.openliberty.io/api/start?a=" + appName + "&b=" + build + "&e="+ EE +"&g=" + groupName + "&j="+ SE +"&m=" +MP))
						  .GET()
						  .build();
				
			    HttpResponse<InputStream> response =
			            client.send(request, BodyHandlers.ofInputStream());

			    InputStream in = new BufferedInputStream(response.body());

			    String home = System.getProperty("user.home");
			    File file = new File(new File(home, "Downloads"), appName + ".zip");

		        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
		            int read;
		            byte[] bytes = new byte[DEFAULT_BUFFER_SIZE];
		            while ((read = in.read(bytes)) != -1) {
		                outputStream.write(bytes, 0, read);
		            }
		        }
				
			} catch (URISyntaxException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		};
		
		try {
			getContainer().run(true, false, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			Throwable realException = e.getTargetException();
			MessageDialog.openError(getShell(), "Error", realException.getMessage());
			return false;
		}
		return true;
	}
	
	/**
	 * We will accept the selection in the workbench to see if
	 * we can initialize from it.
	 * @see IWorkbenchWizard#init(IWorkbench, IStructuredSelection)
	 */
	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.selection = selection;
	}
}