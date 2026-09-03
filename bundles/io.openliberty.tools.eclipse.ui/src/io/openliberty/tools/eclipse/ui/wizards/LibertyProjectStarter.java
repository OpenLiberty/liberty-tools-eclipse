/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
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
package io.openliberty.tools.eclipse.ui.wizards;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.GradleBuild;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.GradleWorkspace;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IProjectConfigurationManager;
import org.eclipse.m2e.core.project.MavenProjectInfo;
import org.eclipse.m2e.core.project.ProjectImportConfiguration;
import org.json.JSONArray;
import org.json.JSONObject;

import io.openliberty.tools.eclipse.LibertyDevPlugin;
import io.openliberty.tools.eclipse.logging.Logger;
import io.openliberty.tools.eclipse.logging.Trace;
import io.openliberty.tools.eclipse.messages.Messages;

/**
 * Manages interaction with the Open Liberty Starter service to generate new
 * Liberty projects. This class provides a singleton instance that communicates
 * with the Liberty Starter API to retrieve configuration options and generate
 * starter projects with specified parameters.
 */
public class LibertyProjectStarter {

    /** Singleton instance of LibertyProjectStarter. */
    private static final LibertyProjectStarter INSTANCE = new LibertyProjectStarter();

    /** Liberty starter API URL for retrieving configuration information. */
    private final String starterURL = "https://start.openliberty.io/api/start/info";

    /** Base URL for generating Liberty starter projects. */
    private final String STARTER_GEN_BASE_URL = "https://start.openliberty.io/api/start";

    /**
     * Build wrapper script file names that require execute permission.
     */
    private static final Set<String> WRAPPER_SCRIPT_NAMES = new HashSet<>(Arrays.asList("gradlew", "mvnw", "gradlew.bat", "mvnw.cmd"));

    /** Starter directory name. */
    private static final String STARTER_LOG_DIR_NAME = "starter";

    /** Default project name retrieved from the starter API. */
    private String defaultProjectName;

    /** Default group name retrieved from the starter API. */
    private String defaultGroupName;

    /** Default build type (Maven or Gradle) retrieved from the starter API. */
    private String defaultBuildType;

    /** Default Java SE level retrieved from the starter API. */
    private String defaultSELevel;

    /** Default Jakarta EE level retrieved from the starter API. */
    private String defaultEELevel;

    /** Default MicroProfile level retrieved from the starter API. */
    private String defaultMPLevel;

    /** Available build type options (Maven, Gradle). */
    private List<Object> buildTypeOptions;

    /** Available Java SE version options. */
    private List<Object> jseOptions;

    /** Available Jakarta EE version options. */
    private List<Object> jeeOptions;

    /** Available MicroProfile version options. */
    private List<Object> mpOptions;

    /** Mapping of Jakarta EE versions to compatible MicroProfile versions. */
    HashMap<String, JSONArray> dependenciesEE2MP = new HashMap<String, JSONArray>();

    /** Mapping of MicroProfile versions to compatible Jakarta EE versions. */
    HashMap<String, JSONArray> dependenciesMP2EE = new HashMap<String, JSONArray>();

    /** Mapping of Jakarta EE versions to minimum required Java SE version. */
    HashMap<String, String> javaSeRequirementsEE = new HashMap<String, String>();

    /** Mapping of MicroProfile versions to minimum required Java SE version. */
    HashMap<String, String> javaSeRequirementsMP = new HashMap<String, String>();

    /**
     * Returns the LibertyProjectStarter instance.
     *
     * @return The LibertyProjectStarter instance.
     */
    public static LibertyProjectStarter getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the mapping of Jakarta EE versions to compatible MicroProfile versions.
     *
     * @return The dependencies map (EE to MP).
     */
    public HashMap<String, JSONArray> getDependenciesEE2MP() {
        return dependenciesEE2MP;
    }

    /**
     * Returns the mapping of MicroProfile versions to compatible Jakarta EE versions.
     *
     * @return The dependencies map (MP to EE).
     */
    public HashMap<String, JSONArray> getDependenciesMP2EE() {
        return dependenciesMP2EE;
    }

    /**
     * Returns the mapping of Jakarta EE versions to minimum required Java SE version.
     *
     * @return The Java SE requirements map for EE versions.
     */
    public HashMap<String, String> getJavaSeRequirementsEE() {
        return javaSeRequirementsEE;
    }

    /**
     * Returns the mapping of MicroProfile versions to minimum required Java SE version.
     *
     * @return The Java SE requirements map for MP versions.
     */
    public HashMap<String, String> getJavaSeRequirementsMP() {
        return javaSeRequirementsMP;
    }

    /**
     * Returns the default project name from the starter API.
     *
     * @return The default project name.
     */
    public String getDefaultProjectName() {
        return defaultProjectName;
    }

    /**
     * Returns the default group name from the starter API.
     *
     * @return The default group name.
     */
    public String getDefaultGroupName() {
        return defaultGroupName;
    }

    /**
     * Returns the default build type from the starter API.
     *
     * @return The default build type (Maven or Gradle).
     */
    public String getDefaultBuildType() {
        return defaultBuildType;
    }

    /**
     * Returns the default Java SE level from the starter API.
     *
     * @return The default Java SE version.
     */
    public String getDefaultSELevel() {
        return defaultSELevel;
    }

    /**
     * Returns the default Jakarta EE level from the starter API.
     *
     * @return The default Jakarta EE version.
     */
    public String getDefaultEELevel() {
        return defaultEELevel;
    }

    /**
     * Returns the default MicroProfile level from the starter API.
     *
     * @return The default MicroProfile version.
     */
    public String getDefaultMPLevel() {
        return defaultMPLevel;
    }

    /**
     * Refreshes the configuration data by re-fetching from the Liberty Starter API.
     *
     * @throws Exception If an error occurs while fetching data from the API.
     */
    public void refresh() throws Exception {
        loadData();
    }

    /**
     * Returns the available build type options.
     *
     * @return An array of build type options (Maven, Gradle).
     */
    public String[] getBuildTypeOptions() {
        return buildTypeOptions.toArray(new String[0]);
    }

    /**
     * Returns the available Java SE version options.
     *
     * @return An array of Java SE version options.
     */
    public String[] getJseOptions() {
        return jseOptions.toArray(new String[0]);
    }

    /**
     * Returns the available Jakarta EE version options.
     *
     * @return An array of Jakarta EE version options.
     */
    public String[] getJeeOptions() {
        return jeeOptions.toArray(new String[0]);
    }

    /**
     * Returns the available MicroProfile version options.
     *
     * @return An array of MicroProfile version options.
     */
    public String[] getMpOptions() {
        return mpOptions.toArray(new String[0]);
    }

    /**
     * Returns the default directory path for storing starter project files.
     *
     * @return The default starter directory path.
     *
     * @throws IOException If an error occurs while creating the directory.
     */
    public String getDefaultStarterDirPath() throws IOException {
        return LibertyDevPlugin.getWorkareaDir(STARTER_LOG_DIR_NAME);
    }

    /**
     * Loads configuration data from the Liberty Starter API including default
     * values, available options, and version compatibility constraints.
     *
     * @throws Exception If an error occurs while communicating with the API or
     *                       parsing the response.
     */
    public void loadData() throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS);
        }

        HttpClient client = HttpClient.newHttpClient();

        // Call the Liberty starter project API
        HttpRequest request = HttpRequest.newBuilder().uri(new URI(starterURL)).GET().build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception(Messages.getMessage("starter_fetch_config_error", response.statusCode()));
        }

        // Retrieve the default values.
        JSONObject jsonObject = new JSONObject(response.body());
        defaultProjectName = jsonObject.getJSONObject("a").getString("default");
        defaultGroupName = jsonObject.getJSONObject("g").getString("default");
        defaultBuildType = jsonObject.getJSONObject("b").getString("default");
        defaultSELevel = jsonObject.getJSONObject("j").getString("default");
        defaultEELevel = jsonObject.getJSONObject("e").getString("default");
        defaultMPLevel = jsonObject.getJSONObject("m").getString("default");

        // Retrieve the available options.
        buildTypeOptions = jsonObject.getJSONObject("b").getJSONArray("options").toList();
        mpOptions = jsonObject.getJSONObject("m").getJSONArray("options").toList();
        jseOptions = jsonObject.getJSONObject("j").getJSONArray("options").toList();
        JSONArray jeeOptionsJson = jsonObject.getJSONObject("e").getJSONArray("options");
        jeeOptions = jeeOptionsJson.toList();

        // Retrieve the EE/MP dependency constraints.
        JSONObject constraints = jsonObject.getJSONObject("e").getJSONObject("constraints");

        for (int i = 0; i < jeeOptionsJson.length(); i++) {
            String jeeVersion = jeeOptionsJson.getString(i);
            JSONObject jeeConstraints = constraints.getJSONObject(jeeVersion);
            JSONArray validMPVersions = jeeConstraints.getJSONArray("m");

            dependenciesEE2MP.put(jeeVersion, validMPVersions);

            // Build reverse mapping (MP to EE)
            for (int j = 0; j < validMPVersions.length(); j++) {
                String mpVersion = validMPVersions.getString(j);
                dependenciesMP2EE.computeIfAbsent(mpVersion, k -> new JSONArray()).put(jeeVersion);
            }
        }

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS,
                                        new Object[] { "defaultProjectName=" + defaultProjectName, "defaultGroupName=" + defaultGroupName,
                                                       "defaultBuildType=" + defaultBuildType, "defaultSELevel=" + defaultSELevel,
                                                       "defaultEELevel=" + defaultEELevel, "defaultMPLevel=" + defaultMPLevel,
                                                       "buildTypeOptions=" + buildTypeOptions, "jseOptions=" + jseOptions,
                                                       "jeeOptions=" + jeeOptions, "mpOptions=" + mpOptions });
        }
    }

    /**
     * Generates a Liberty starter project with the specified configuration
     * parameters and downloads it as a ZIP file to the specified directory.
     *
     * @param appName     The application/artifact name for the project.
     * @param groupName   The group ID for the project.
     * @param buildType   The build tool type (Maven or Gradle).
     * @param jeeLevel    The Jakarta EE version to use.
     * @param jseLevel    The Java SE version to use.
     * @param mpLevel     The MicroProfile version to use.
     * @param destDirPath The directory path where the generated application file
     *                        will be saved.
     * @throws Exception If an error occurs while generating or downloading the
     *                       starter project.
     */
    public void generateStarter(String appName, String groupName, String buildType, String jeeLevel, String jseLevel,
                                String mpLevel, String destDirPath) throws Exception {

        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS,
                                         new Object[] { appName, groupName, buildType, jeeLevel, jseLevel, mpLevel, destDirPath });
        }

        HttpClient client = HttpClient.newHttpClient();

        StringBuilder url = new StringBuilder(STARTER_GEN_BASE_URL);
        url.append("?a=").append(encodeParam(appName));
        url.append("&g=").append(encodeParam(groupName));
        url.append("&b=").append(encodeParam(buildType));
        url.append("&e=").append(encodeParam(jeeLevel));
        url.append("&j=").append(encodeParam(jseLevel));
        url.append("&m=").append(encodeParam(mpLevel));

        HttpRequest request = HttpRequest.newBuilder().uri(new URI(url.toString())).GET().build();

        // Send request and get response
        HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());

        // Validate response status
        if (response.statusCode() != 200) {
            // Read error message from response body
            String errorMessage = "";
            try (BufferedInputStream bis = new BufferedInputStream(response.body())) {
                byte[] buffer = new byte[1024];
                int bytesRead = bis.read(buffer);
                if (bytesRead > 0) {
                    errorMessage = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                if (Trace.isEnabled()) {
                    Trace.getTracer().trace(Trace.TRACE_TOOLS, "Failed to read error response body: " + e.getMessage(), e);
                }
                Logger.logWarning("Failed to read error response body: " + e.getMessage());
            }
            
            // Include both status code and error message from API
            String fullErrorMessage = Messages.getMessage("starter_generate_error", response.statusCode());
            if (!errorMessage.isEmpty()) {
                fullErrorMessage += ": " + errorMessage;
            }
            throw new IOException(fullErrorMessage);
        }

        // Download and save the ZIP file
        File parentDir = new File(destDirPath);
        if (!parentDir.exists()) {
            Files.createDirectories(Paths.get(destDirPath));
        }

        // Read the application archive from the response, expand it, and install it.
        Path destDir = Paths.get(destDirPath, appName);
        installStarterApplication(destDir, response.body(), buildType);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, new Object[] { url, destDir });
        }
    }

    /**
     * Installs the application associated with the input path.
     *
     * @param destinationDirPath The path to the directory where the application will be installed.
     * @param archiveIStream     The input stream of the application archive.
     * @param buildType          The build type.
     */
    private void installStarterApplication(Path destinationDirPath, InputStream archiveIStream, String buildType) throws Exception {

        if (!Files.exists(destinationDirPath)) {
            Files.createDirectories(destinationDirPath);
        }

        // Unzip the application. After extraction, set the execute bit on build
        // wrapper scripts whose execute permission is lost by ZipInputStream.
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(archiveIStream))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                Path newFilePath = destinationDirPath.resolve(fileName).normalize();

                if (entry.isDirectory()) {
                    Files.createDirectories(newFilePath);
                } else {
                    Files.createDirectories(newFilePath.getParent());
                    Files.copy(zis, newFilePath, StandardCopyOption.REPLACE_EXISTING);
                    setExecuteBitsIfWrapperScript(newFilePath);
                }
                zis.closeEntry();
            }
        }

        // Import the application
        if ("maven".equals(buildType)) {
            importMavenProjects(destinationDirPath);
        } else if ("gradle".equals(buildType)) {
            importGradleApplications(destinationDirPath);
        }
    }

    /**
     * URL-encodes a parameter value.
     *
     * @param param The parameter value to encode.
     *
     * @return The URL-encoded parameter value.
     */
    private String encodeParam(String param) {
        return URLEncoder.encode(param, StandardCharsets.UTF_8);
    }

    /**
     * Sets the execute permission on a file if it is a known build wrapper script.
     *
     * @param filePath The path to the extracted file.
     */
    private void setExecuteBitsIfWrapperScript(Path filePath) {
        if (!WRAPPER_SCRIPT_NAMES.contains(filePath.getFileName().toString())) {
            return;
        }

        File file = filePath.toFile();
        if (!file.setExecutable(true, false)) {
            if (Trace.isEnabled()) {
                Trace.getTracer().trace(Trace.TRACE_TOOLS, "Unable to set execute permission on wrapper script: " + filePath);
            }
            Logger.logError("Unable to set execute permission on wrapper script: " + filePath, null);
        }
    }

    /**
     * Imports the Gradle application associated with the input file location.
     *
     * @param appPath The path to the application to install.
     */
    public static void importGradleApplications(Path appPath) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { appPath });
        }

        Job job = new Job(Messages.getMessage("starter_install_gradle_job", appPath)) {

            /**
             * {@inheritDoc}
             */
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                IPath projectLocation = org.eclipse.core.runtime.Path.fromOSString(appPath.toString());
                BuildConfiguration configuration = BuildConfiguration.forRootProjectDirectory(projectLocation.toFile()).gradleDistribution(GradleDistribution.fromBuild()).overrideWorkspaceConfiguration(true).autoSync(true).build();
                GradleWorkspace workspace = GradleCore.getWorkspace();
                monitor.worked(40);
                GradleBuild newBuild = workspace.createBuild(configuration);
                newBuild.synchronize(new NullProgressMonitor());
                return Status.OK_STATUS;
            }
        };

        job.schedule();

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, appPath);
        }
    }

    /**
     * Imports the Maven application associated with the input file location.
     *
     * @param appPath The path to the application to install.
     */
    public static void importMavenProjects(Path appPath) {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, new Object[] { appPath });
        }

        Job job = new Job(Messages.getMessage("starter_install_maven_job", appPath)) {

            /**
             * {@inheritDoc}
             */
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                IProjectConfigurationManager configManager = MavenPlugin.getProjectConfigurationManager();

                File pomFile = new File(appPath.toString().concat(File.separator).concat("pom.xml"));
                String appName = appPath.getFileName().toString();
                MavenProjectInfo projectInfo = new MavenProjectInfo(appName, pomFile, null, null);
                ProjectImportConfiguration configuration = new ProjectImportConfiguration();
                try {
                    configManager.importProjects(Collections.singletonList(projectInfo), configuration,
                                                 new NullProgressMonitor());
                } catch (Exception e) {
                    if (Trace.isEnabled()) {
                        Trace.getTracer().trace(Trace.TRACE_TOOLS, Messages.getMessage("starter_maven_import_error"), e);
                    }
                    Logger.logError(Messages.getMessage("starter_maven_import_error"), e);
                    return new Status(IStatus.ERROR, LibertyDevPlugin.PLUGIN_ID, Messages.getMessage("starter_maven_import_failed", e.getMessage()));
                }
                return Status.OK_STATUS;
            }
        };

        job.schedule();

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, appPath);
        }
    }
}