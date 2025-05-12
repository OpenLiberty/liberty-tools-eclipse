/*******************************************************************************
* Copyright (c) 2022, 2024 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import io.openliberty.tools.eclipse.DevModeOperations;
import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.Project;
import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Provides a set of utility methods.
 */
public class Utils {

    /**
     * Returns true if the underlying OS is windows. False, otherwise.
     *
     * @return True if the underlying OS is windows. False, otherwise.
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    /**
     * Returns a org.eclipse.swt.graphics.Image object representing the image under the input path.
     * 
     * @param device The device display.
     * @param path The path to the image.
     * 
     * @return A org.eclipse.swt.graphics.Image object representing the Open Liberty image.
     */
    public static Image getImage(Device device, String path) {
        URL url = LibertyDevPlugin.getDefault().getBundle().getResource(path);
        Image image = null;
        if (url != null) {
            InputStream stream = null;
            try {
                stream = url.openStream();
                image = new Image(device, stream);
            } catch (Exception e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_UI, "Error encountered while retrieving image.", e);
                }
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Exception e) {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_UI, "Error encountered while closing image stream.", e);
                    }
                }
            }
        }
        return image;
    }

    /**
     * Returns an org.eclipse.core.resources.IProject object associated with the input active part.
     * 
     * @param part The active workbench part.
     * 
     * @return An org.eclipse.core.resources.IProject object associated with the input active part.
     */
    public static IProject getProjectFromPart(IWorkbenchPart part) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { part });
        }

        IProject iProject = null;
        if (part != null && part instanceof IEditorPart) {
            IEditorPart editorPart = (IEditorPart) part;
            IEditorInput input = editorPart.getEditorInput();
            IResource resource = (IResource) input.getAdapter(IResource.class);
            if (resource != null) {
                iProject = resource.getProject();
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, iProject);
        }

        return iProject;
    }

    /**
     * Returns an org.eclipse.core.resources.IProject object associated with the input selection.
     * 
     * @param selection The active selection.
     * 
     * @return An org.eclipse.core.resources.IProject object associated with the input selection.
     */
    public static IProject getProjectFromSelection(ISelection selection) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { "Selection of type: " + selection.getClass() });
        }

        IProject iProject = null;
        if (selection != null && (selection instanceof IStructuredSelection)) {

            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object firstElement = structuredSelection.getFirstElement();
            if (firstElement instanceof IProject) {
                iProject = (IProject) firstElement;
            } else if (firstElement instanceof IResource) {
                iProject = ((IResource) firstElement).getProject();
            } else if (firstElement instanceof IAdaptable) {
                IResource adapter = (IResource) ((IAdaptable) firstElement).getAdapter(IResource.class);
                if (adapter != null) {
                    iProject = adapter.getProject();
                }
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, iProject);
        }

        return iProject;
    }

    /**
     * Returns an org.eclipse.core.resources.IProject objects associated with the input selection.
     * 
     * @param selection The active selection.
     * 
     * @return An org.eclipse.core.resources.IProject objects associated with the input selection.
     */
    public static List<IProject> getProjectFromSelections(ISelection selection) {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { selection });
        }

        List<IProject> selectedProjects = new ArrayList<IProject>();
        if (selection != null && (selection instanceof IStructuredSelection)) {

            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            for (Object element : structuredSelection.toList()) {
                IProject iProject = null;
                if (element instanceof IProject) {
                    iProject = (IProject) element;
                } else if (element instanceof IResource) {
                    iProject = ((IResource) element).getProject();
                } else if (element instanceof IAdaptable) {
                    iProject = ((IResource) ((IAdaptable) element).getAdapter(IResource.class)).getProject();
                }

                if (iProject != null) {
                    selectedProjects.add(iProject);
                }
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, selectedProjects);
        }

        return selectedProjects;
    }

    /**
     * Returns the project instance associated with the currently selected view or editor.
     *
     * @return The project instance associated with the currently selected view or editor. If the project is not found, null is
     *         returned.
     */
    public static IProject getActiveProject() {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS);
        }

        IProject iProject = null;
        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow activeWindow = workbench.getActiveWorkbenchWindow();

        if (activeWindow != null) {
            // Find project based on the current selection.
            ISelectionService selectionService = activeWindow.getSelectionService();
            ISelection selection = selectionService.getSelection();
            iProject = Utils.getProjectFromSelection(selection);

            // If nothing is currently selected, find the project based on the currently open editor.
            if (iProject == null) {
                IWorkbenchPage page = activeWindow.getActivePage();
                if (page != null) {
                    IWorkbenchPart part = page.getActivePart();
                    iProject = Utils.getProjectFromPart(part);
                }
            }
        }

        if (iProject == null) {
            iProject = DevModeOperations.getInstance().getSelectedDashboardProject();
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, iProject);
        }

        return iProject;
    }

    /**
     * Returns a formatted string with the list of given objects. The format follows DebugTrace.traceEntry object list formatting.
     * 
     * @param objects The list of objects to format.
     * 
     * @return A formatted string with the list of given objects. The format follows DebugTrace.traceEntry object list formatting.
     */
    public static String objectsToString(Object... objects) {
        StringBuffer sb = new StringBuffer("(");
        for (Object o : objects) {
            sb.append(o).append(" ");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");

        return sb.toString();
    }
    
    /**
     * Returns the input throwable's root throwable cause.
     * 
     * @param t The parent throwable.
     * 
     * @return The input throwable's root throwable cause.
     */
    public static Throwable findRootCause(Throwable t) {
        if (t == null) {
            return null;
        }

        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        return cause;
    }
    
	public static boolean validateLibertyServerStopped(Project project) {
		String wlpMsgLogPath = getLogFilePath(project);
		int maxAttempts = 30;
		boolean foundStoppedMsg = false;

		// Find message CWWKE0036I: The server x stopped after y seconds
		for (int i = 0; i < maxAttempts; i++) {
			try (BufferedReader br = new BufferedReader(new FileReader(wlpMsgLogPath))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.contains("CWWKE0036I")) {
						foundStoppedMsg = true;
						break;
					}
				}

				if (foundStoppedMsg) {
					break;
				} else {
					Thread.sleep(3000);
				}
			} catch (Exception e) {
				if (Trace.isEnabled()) {
					Trace.getTracer().trace(Trace.TRACE_UI, "Caught exception waiting for stop message", e);
				}
			}
		}
		return foundStoppedMsg;
	}

    // Get the usr directory path from the maven/gradle output folder.
    private static String getLogFilePath(Project project) {
    	if (project.getBuildType() == Project.BuildType.MAVEN) {
    		return Paths.get(project.getPath(), "target", "liberty", "wlp", "usr", "servers", "defaultServer", "logs",
    				"messages.log").toString();
    	} else {
    		return Paths
    				.get(project.getPath(), "build", "wlp", "usr", "servers", "defaultServer", "logs", "messages.log")
    				.toString();
    	}
    }
}
