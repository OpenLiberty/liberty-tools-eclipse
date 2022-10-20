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

package io.openliberty.tools.eclipse.jakarta.languageserver;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;

import io.openliberty.tools.eclipse.ls.plugin.LibertyToolsLSPlugin;

public class JakartaLSConnection extends ProcessStreamConnectionProvider {

    public JakartaLSConnection() {

//      Locale.setDefault(new Locale("my", "TEST")); // testing code

      List<String> commands = new ArrayList<>();
      commands.add(computeJavaPath());
      String debugPortString = System.getProperty(getClass().getName() + ".debugPort");
      if (debugPortString != null) {
          commands.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPortString);
      }
      commands.add("-classpath");
      try {
          commands.add(computeClasspath());

          // set current locale to LS JVM
          // probably don't need this when locale is set to system
          Locale currentLocale = Locale.getDefault();
          commands.add("-Duser.language=" + currentLocale.getLanguage());
          commands.add("-Duser.country=" + currentLocale.getCountry());

          commands.add("org.eclipse.lsp4jakarta.JakartaLanguageServerLauncher");
          setCommands(commands);
          setWorkingDirectory(System.getProperty("user.dir"));
      } catch (IOException e) {
          LibertyToolsLSPlugin.getDefault().getLog().log(
                  new Status(IStatus.ERROR, LibertyToolsLSPlugin.getDefault().getBundle().getSymbolicName(), e.getMessage(), e));
      }

  }
    private String computeClasspath() throws IOException {
        StringBuilder builder = new StringBuilder();
        URL url = FileLocator.toFileURL(getClass().getResource("/server/jakarta-langserver/org.eclipse.lsp4jakarta.ls-0.0.1-SNAPSHOT-jar-with-dependencies.jar"));
        builder.append(new java.io.File(url.getPath()).getAbsolutePath());
        return builder.toString();
    }

    private String computeJavaPath() {
        String javaPath = "java";
        boolean existsInPath = Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator))).map(Paths::get)
                .anyMatch(path -> Files.exists(path.resolve("java")));
        if (!existsInPath) {
            File f = new File(System.getProperty("java.home"),
                    "bin/java" + (Platform.getOS().equals(Platform.OS_WIN32) ? ".exe" : ""));
            javaPath = f.getAbsolutePath();
        }
        return javaPath;
    }

    @Override
    public Object getInitializationOptions(URI rootUri) {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> settings = new HashMap<>();
        Map<String, Object> jakarta = new HashMap<>();
        Map<String, Object> tools = new HashMap<>();
        Map<String, Object> trace = new HashMap<>();
        trace.put("server", "verbose");
        tools.put("trace", trace);
        jakarta.put("tools", tools);
        settings.put("jakararta", jakarta);
        root.put("settings", settings);

        return root;
    }
}
