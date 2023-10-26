/******************************************************************************* 
 * Copyright (c) 2019 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
/**
 * This class is a copy/paste of jbosstools-quarkus language server plugin
 * https://github.com/jbosstools/jbosstools-quarkus/blob/main/plugins/org.jboss.tools.quarkus.lsp4e/src/org/jboss/tools/quarkus/lsp4e/QuarkusLanguageServer.java
 * with modifications made for the Liberty Tools Microprofile LS plugin
 *
 */

package io.openliberty.tools.eclipse.liberty.languageserver;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;

import io.openliberty.tools.eclipse.ls.plugin.LibertyToolsLSPlugin;
import io.openliberty.tools.eclipse.lsclient.DebugUtil;

public class LibertyLSConnection extends ProcessStreamConnectionProvider {

    public LibertyLSConnection() {
        List<String> commands = new ArrayList<>();
        commands.add(computeJavaPath());
        try {
            String debugArg = DebugUtil.getDebugJVMArg(getClass().getName());
            if (debugArg.length() > 0) {
                commands.add(debugArg);
            }
            commands.add("-classpath");
            commands.add(computeClasspath());
            commands.add("io.openliberty.tools.langserver.LibertyLanguageServerLauncher");
            setCommands(commands);
            setWorkingDirectory(System.getProperty("user.dir"));
        } catch (IOException e) {
            LibertyToolsLSPlugin.getDefault().getLog()
                    .log(new Status(IStatus.ERROR, LibertyToolsLSPlugin.getDefault().getBundle().getSymbolicName(), e.getMessage(), e));
        }
    }

    private String computeClasspath() throws IOException {
        StringBuilder builder = new StringBuilder();
        URL url = FileLocator.toFileURL(getClass().getResource("/server/liberty-langserver/liberty-langserver.jar"));
        builder.append(new java.io.File(url.getPath()).getAbsolutePath());
        return builder.toString();
    }

    private String computeJavaPath() {
        File f = new File(System.getProperty("java.home"), "bin/java" + (Platform.getOS().equals(Platform.OS_WIN32) ? ".exe" : ""));
        return f.getAbsolutePath();
    }

    @Override
    public String toString() {
        return "Liberty Config Language Server: " + super.toString();
    }

}
