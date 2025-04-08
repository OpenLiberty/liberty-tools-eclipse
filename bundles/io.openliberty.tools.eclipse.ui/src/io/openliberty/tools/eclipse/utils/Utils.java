/*******************************************************************************
* Copyright (c) 2022, 2025 IBM Corporation and others.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

	/**
	 * Disable app monitoring by placing an XML file containing the app monitoring
	 * configuration into the 'configDropins/overrides' folder inside the target
	 * directory.
	 * 
	 * 
	 * @param project a project in the Liberty dashboard.
	 * 
	 */
	public static void disableAppMonitoring(Project project) {

		String serverFolderName = "servers";
		String fileNameTofind = "server.xml";
		String fileContent = "<server> <applicationMonitor updateTrigger=\"disabled\"/> </server>";

		try {
			validateProjectIsGradleOrMaven(project);			
			File projectDir = new File(getUsrDirPath(project).toString());
			File serverDirectory = findFolder(projectDir, serverFolderName);

			if (serverDirectory != null) {
				File serverXmlFile = findFileInFolder(serverDirectory, fileNameTofind);
				if (serverXmlFile != null) {
					File disableAppMonitorXmlFile = new File(getXmlFilePath(serverXmlFile));
					createDirectoryStructure(disableAppMonitorXmlFile, fileContent);
				} else {
					if (Trace.isEnabled()) {
						Trace.getTracer().trace(Trace.TRACE_UI,
								"File '" + fileNameTofind + "' not found in folder '" + serverFolderName + "'.");
					}
				}
			} else {
				if (Trace.isEnabled()) {
					Trace.getTracer().trace(Trace.TRACE_UI,
							"Folder '" + serverFolderName + "' not found in the project.");
				}
			}
		} catch (Exception e) {
			if (Trace.isEnabled()) {
				Trace.getTracer().trace(Trace.TRACE_UI, "Error encountered while adding xml file in the configDropins.",
						e);
			}
		}
	}

	/**
	 * Re-enable app monitoring by removing the XML file containing the app
	 * monitoring configuration from the 'configDropins/overrides' folder inside the
	 * target directory.
	 * 
	 * 
	 * @param project a project in the Liberty dashboard.
	 * 
	 */
	public static void reEnableAppMonitoring(Project project) {

		try {
			validateProjectIsGradleOrMaven(project);
			String dirNameTofind = "disableApplicationMonitor.xml";
			File configDropins = findFileInFolder(getUsrDirPath(project), dirNameTofind);

			// Delete the directory if exists.
			if (configDropins != null) {
				deleteDirectory(configDropins);
			}

		} catch (Exception e) {
			if (Trace.isEnabled()) {
				Trace.getTracer().trace(Trace.TRACE_UI, "Error encountered while removing xml file from configDropins.",
						e);
			}
		}
	}

	// Method to recursively find a folder by name.
	private static File findFolder(File rootDir, String targetFolderName) {
		if (rootDir == null || !rootDir.isDirectory()) {
			return null;
		}

		if (rootDir.getName().equals(targetFolderName)) {
			return rootDir; // Found the folder
		}

		File[] subDirs = rootDir.listFiles();
		if (subDirs != null) {
			for (File subDir : subDirs) {
				if (subDir.isDirectory()) {
					File found = findFolder(subDir, targetFolderName);
					if (found != null) {
						return found;
					}
				}
			}
		}
		return null; // Folder not found
	}

	// Method to find a specific file in a folder
	private static File findFileInFolder(File folder, String fileName) {
		if (folder == null || !folder.isDirectory()) {
			return null;
		}

		File[] files = folder.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.isFile() && file.getName().equals(fileName)) {
					return file; // Found the file
				} else if (file.isDirectory()) {
					File found = findFileInFolder(file, fileName);
					if (found != null) {
						return found;
					}
				}
			}
		}
		return null; // File not found
	}

	// Create a directory and a file containing the specified content.
	private static void createDirectoryStructure(File file, String content) {
		try {
			if (file.getParentFile() != null && !file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}

			if (file.createNewFile()) {
				try (FileWriter writer = new FileWriter(file)) {
					writer.write(content);
				}
			} else {
				if (Trace.isEnabled()) {
					Trace.getTracer().trace(Trace.TRACE_UI, "File already exists: " + file.getAbsolutePath());
				}
			}
		} catch (IOException e) {
			if (Trace.isEnabled()) {
				Trace.getTracer().trace(Trace.TRACE_UI, "An error occurred while creating the file: " + e.getMessage());
			}
		}
	}

	// Method to delete a directory and its contents
	private static boolean deleteDirectory(File directory) {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File file : files) {
					deleteDirectory(file); // Recursively delete files and subdirectories
				}
			}
		}
		return directory.delete(); // Delete the directory (or file)
	}

	private static void validateProjectIsGradleOrMaven(Project project) throws Exception {

		if (project == null) {
			throw new Exception("Unable to find internal instance of project.");
		}

		// Get the absolute path to the application project.
		if (project.getPath() == null) {
			throw new Exception("Unable to find the path to selected project.");
		}

		if (project.getBuildType() == Project.BuildType.UNKNOWN) {
			if (Trace.isEnabled()) {
				Trace.getTracer().trace(Trace.TRACE_UI, "Unexpected project build type: " + project.getBuildType()
						+ ". " + "Project does not appear to be a Maven or Gradle built project.");
				return;
			}
		}
	}

	private static File getUsrDirPath(Project project) {
		if (project.getBuildType()  == Project.BuildType.MAVEN) {
			return Paths.get(project.getPath(), "target", "liberty", "wlp", "usr").toFile();
		} else {
			return Paths.get(project.getPath(), "target", "wlp", "usr").toFile();
		}
	}

	private static String getXmlFilePath(File serverXmlFile) {
        return Paths.get(serverXmlFile.getParent(), "configDropins", "overrides", "disableApplicationMonitor.xml").toString();
	}
	
}
