/*******************************************************************************
* Copyright (c) 2022, 2023 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.launch;

import java.io.File;
import java.nio.file.Paths;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaJRETab;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.utils.ErrorHandler;
import io.openliberty.tools.eclipse.utils.Utils;

public class JRETab extends JavaJRETab {

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isValid(ILaunchConfiguration config) {
        if (!super.isValid(config)) {
            return false;
        }

        setErrorMessage(null);

        // Issue a warning if we detect that the java installation is not a JDK.
        String javaHome = resolveJavaHome(config);
        java.nio.file.Path javacPath = Paths.get(javaHome, "bin", (Utils.isWindows() ? "javac.exe" : "javac"));
        File javacFile = javacPath.toFile();
        if (!javacFile.exists()) {
            super.setErrorMessage("A Java Development Kit (JDK) is required to use Liberty dev mode.");
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
        String javaInstallation = null;
        IProject activeProject = Utils.getActiveProject();

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_UI, new Object[] { activeProject, configuration });
        }

        try {
            if (activeProject != null) {
                javaInstallation = getDefaulJavaFromBuildPath(activeProject);
                if (javaInstallation != null) {
                    configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH, javaInstallation);
                }
            }
        } catch (Exception e) {
            ErrorHandler.processWarningMessage("Unable to set the default Java installation obtained from the build path of project "
                    + activeProject.getName() + " in configuration " + configuration.getName(), e);
        }

        super.setDefaults(configuration);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_UI, javaInstallation);
        }
    }

    /**
     * Resolves the java installation to use based on the configuration.
     */
    public static String resolveJavaHome(ILaunchConfiguration configuration) {
        IVMInstall install = null;
        String keyValue = null;

        // The JRE_CONTAINER_KEY is set when using the configuration's execution environment
        // or an alternate JRE. If this is not set, the workspace default JRE is used.
        try {
            ILaunchConfigurationWorkingCopy configWorkingCopy = configuration.getWorkingCopy();
            keyValue = configWorkingCopy.getAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH, (String) null);
        } catch (Exception e) {
            String msg = "Unable to resolve the Java installation path using configuration." + configuration.getName()
                    + ". Using the workspace Java installation";
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_UI, msg, e);
            }
            ErrorHandler.processWarningMessage(msg, e);
        }

        if (keyValue != null) {
            IPath javaPath = org.eclipse.core.runtime.Path.fromOSString(keyValue);
            install = JavaRuntime.getVMInstall(javaPath);
        } else {
            install = JavaRuntime.getDefaultVMInstall();
        }

        return install.getInstallLocation().getAbsolutePath();
    }

    /**
     * Returns the Java execution environment configured in the Java build path of the input project (.classpath).
     * 
     * @param iProject The project currently being processed.
     * 
     * @return the Java execution environment configured in the Java build path of the input project (.classpath). Null if the
     *         required data is not found.
     * 
     * @throws Exception
     */
    public static String getDefaulJavaFromBuildPath(IProject iProject) throws Exception {
        if (iProject.hasNature(JavaCore.NATURE_ID)) {
            IJavaProject ijp = JavaCore.create(iProject);

            IClasspathEntry[] rawCPEs = ijp.getRawClasspath();
            for (IClasspathEntry rawCPE : rawCPEs) {
                if (rawCPE.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
                    IPath path = rawCPE.getPath();
                    if (path.segmentCount() == 3) {
                        if (path.segment(0).equals(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH)) {
                            return path.toOSString();
                        }
                    }
                }
            }
        }

        return null;
    }
}
