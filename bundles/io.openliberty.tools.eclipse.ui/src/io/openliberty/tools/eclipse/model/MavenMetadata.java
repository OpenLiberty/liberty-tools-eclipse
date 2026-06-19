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
package io.openliberty.tools.eclipse.model;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import io.openliberty.tools.eclipse.logging.Trace;

/**
 * Represents metadata extracted from a Maven POM file.
 */
public class MavenMetadata implements Metadata {

    private String projectName;
    private String parentProjectName;
    private List<String> subprojects;
    private List<String> projectDependencies;
    private boolean hasLibertyPlugin;
    private boolean isLibertyModuleDisabled;
    private boolean isAggregator;
    private String buildFilePath;

    /**
     * Constructor.
     * 
     * @param buildGradlePath
     */
    public MavenMetadata(String buildFilePath) throws Exception {
        extract(buildFilePath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getProjectName() {
        return projectName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getParentProjectName() {
        return parentProjectName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSubprojects() {
        return subprojects;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLibertyPluginConfigured() {
        return hasLibertyPlugin;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAggregator() {
        return isAggregator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFilePath() {
        return buildFilePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isModuleDisabled() {
        return isLibertyModuleDisabled;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getProjectDependencies() {
        if (projectDependencies == null) {
            return new ArrayList<>();
        }
        return projectDependencies;
    }

    /**
     * Extracts metadata from a Maven POM file.
     * 
     * @param pomXmlPath The path to the pom.xml file.
     * 
     * @return The MavenProjectMetadata object containing extracted information.
     * 
     * @throws Exception if parsing fails.
     */
    public void extract(String pomXmlPath) throws Exception {
        if (Trace.isEnabled()) {
            Trace.getTracer().traceEntry(Trace.TRACE_TOOLS, pomXmlPath);
        }

        buildFilePath = pomXmlPath;
        String xmlContent = new String(Files.readAllBytes(Paths.get(pomXmlPath)));
        parsePomXml(xmlContent);

        if (Trace.isEnabled()) {
            Trace.getTracer().traceExit(Trace.TRACE_TOOLS, this);
        }
    }

    /**
     * Parses POM XML content to extract metadata.
     *
     * @param xmlContent The XML content of pom.xml.
     * 
     * @return The MavenProjectMetadata object.
     * 
     * @throws Exception if parsing fails
     */
    private void parsePomXml(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();

        // Extract artifactId
        NodeList artifactIdNodes = root.getElementsByTagName("artifactId");
        if (artifactIdNodes.getLength() > 0) {
            // Get the first artifactId (project's own, not parent's)
            for (int i = 0; i < artifactIdNodes.getLength(); i++) {
                Node node = artifactIdNodes.item(i);
                if (node.getParentNode().getNodeName().equals("project")) {
                    projectName = node.getTextContent().trim();
                    break;
                }
            }
        }

        // Extract parent artifactId
        NodeList parentNodes = root.getElementsByTagName("parent");
        if (parentNodes.getLength() > 0) {
            Element parentElement = (Element) parentNodes.item(0);
            NodeList parentArtifactIdNodes = parentElement.getElementsByTagName("artifactId");
            if (parentArtifactIdNodes.getLength() > 0) {
                parentProjectName = parentArtifactIdNodes.item(0).getTextContent().trim();
            }
        }

        // Check for packaging type "pom"
        boolean pomPackageType = false;
        NodeList packagingNodes = root.getElementsByTagName("packaging");
        if (packagingNodes.getLength() > 0) {
            String packaging = packagingNodes.item(0).getTextContent().trim();
            if ("pom".equals(packaging)) {
                pomPackageType = true;
            }
        }

        // An aggregator module must always declare its child modules, and
        // its pom.xml's packaging type must be pom. We cannot
        // assert that a module is an aggregator solely based on the pom packaging type
        // because that only indicates that the module is strictly just a parent or BOM.
        NodeList modulesNodes = root.getElementsByTagName("modules");
        if (modulesNodes.getLength() > 0) {
            Element modulesElement = (Element) modulesNodes.item(0);
            subprojects = getSubModules(modulesElement);

            if (!subprojects.isEmpty() && pomPackageType) {
                isAggregator = true;
            }
        }

        // Extract project dependencies
        projectDependencies = extractProjectDependencies(root);

        // Check for Liberty Maven plugin
        hasLibertyPlugin = isLibertyPluginInConfig(doc);
    }

    /**
     * Extracts module names from modules element.
     * 
     * @param modulesElement The modules XML element.
     * 
     * @return The list of module names.
     */
    private List<String> getSubModules(Element modulesElement) {
        List<String> modules = new ArrayList<>();
        NodeList moduleNodes = modulesElement.getElementsByTagName("module");

        for (int i = 0; i < moduleNodes.getLength(); i++) {
            String moduleName = moduleNodes.item(i).getTextContent().trim();
            if (!moduleName.isEmpty()) {
                modules.add(moduleName);
            }
        }

        return modules;
    }

    /**
     * Checks if POM contains Liberty Maven plugin and detect skip configuration.
     * Sets both hasLibertyPlugin and libertyPluginSkipped fields.
     *
     * @param doc The parsed POM document.
     * 
     * @return true if Liberty plugin is found (regardless of skip setting).
     */
    private boolean isLibertyPluginInConfig(Document doc) {
        // Check in build section
        if (checkLibertyPluginInElement(doc.getDocumentElement(), "build")) {
            return true;
        }

        // Check in profiles
        NodeList profileNodes = doc.getElementsByTagName("profile");
        for (int i = 0; i < profileNodes.getLength(); i++) {
            Element profileElement = (Element) profileNodes.item(i);
            if (checkLibertyPluginInElement(profileElement, "build")) {
                return true;
            }
        }

        // Check in pluginManagement
        NodeList pluginMgmtNodes = doc.getElementsByTagName("pluginManagement");
        for (int i = 0; i < pluginMgmtNodes.getLength(); i++) {
            Element pluginMgmtElement = (Element) pluginMgmtNodes.item(i);
            if (checkLibertyPluginInElement(pluginMgmtElement, "plugins")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks for Liberty plugin in a specific element and detect skip configuration.
     * Sets libertyPluginSkipped field if <skip>true</skip> is found in any plugin configuration.
     *
     * @param element The element to search in.
     * @param tagName The tag name to look for (build or plugins).
     * 
     * @return true if Liberty plugin is found (regardless of skip setting).
     */
    private boolean checkLibertyPluginInElement(Element element, String tagName) {
        NodeList buildNodes = element.getElementsByTagName(tagName);
        for (int i = 0; i < buildNodes.getLength(); i++) {
            Element buildElement = (Element) buildNodes.item(i);
            NodeList pluginsNodes = buildElement.getElementsByTagName("plugins");

            for (int j = 0; j < pluginsNodes.getLength(); j++) {
                Element pluginsElement = (Element) pluginsNodes.item(j);
                NodeList pluginNodes = pluginsElement.getElementsByTagName("plugin");

                for (int k = 0; k < pluginNodes.getLength(); k++) {
                    Element pluginElement = (Element) pluginNodes.item(k);

                    String groupId = getElementText(pluginElement, "groupId");
                    String artifactId = getElementText(pluginElement, "artifactId");

                    if ("io.openliberty.tools".equals(groupId) && "liberty-maven-plugin".equals(artifactId)) {
                        // Check for <skip>true</skip> in all configuration elements
                        // (plugin-level config, execution-level configs, etc.)
                        NodeList configNodes = pluginElement.getElementsByTagName("configuration");
                        for (int m = 0; m < configNodes.getLength(); m++) {
                            Element configElement = (Element) configNodes.item(m);
                            String skipValue = getElementText(configElement, "skip");
                            if ("true".equalsIgnoreCase(skipValue)) {
                                isLibertyModuleDisabled = true;
                                break; // Found skip=true, no need to check further
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Extracts project dependencies from the POM.
     * Looks for dependencies in:
     * - Regular dependencies section.
     * - dependencyManagement section.
     * - Profile dependencies.
     *
     * @param root The root element of the POM.
     * 
     * @return The list of artifact IDs from dependencies.
     */
    private List<String> extractProjectDependencies(Element root) {
        List<String> dependencies = new ArrayList<>();

        // 1. Get dependencies from regular <dependencies> sections
        NodeList dependenciesNodes = root.getElementsByTagName("dependencies");
        for (int i = 0; i < dependenciesNodes.getLength(); i++) {
            Element dependenciesElement = (Element) dependenciesNodes.item(i);
            extractDependenciesFromElement(dependenciesElement, dependencies);
        }

        // 2. Get dependencies from <dependencyManagement> section
        NodeList depMgmtNodes = root.getElementsByTagName("dependencyManagement");
        for (int i = 0; i < depMgmtNodes.getLength(); i++) {
            Element depMgmtElement = (Element) depMgmtNodes.item(i);
            NodeList depMgmtDepsNodes = depMgmtElement.getElementsByTagName("dependencies");
            for (int j = 0; j < depMgmtDepsNodes.getLength(); j++) {
                Element dependenciesElement = (Element) depMgmtDepsNodes.item(j);
                extractDependenciesFromElement(dependenciesElement, dependencies);
            }
        }

        // 3. Get dependencies from profiles
        NodeList profilesNodes = root.getElementsByTagName("profiles");
        for (int i = 0; i < profilesNodes.getLength(); i++) {
            Element profilesElement = (Element) profilesNodes.item(i);
            NodeList profileNodes = profilesElement.getElementsByTagName("profile");
            for (int j = 0; j < profileNodes.getLength(); j++) {
                Element profileElement = (Element) profileNodes.item(j);
                NodeList profileDepsNodes = profileElement.getElementsByTagName("dependencies");
                for (int k = 0; k < profileDepsNodes.getLength(); k++) {
                    Element dependenciesElement = (Element) profileDepsNodes.item(k);
                    extractDependenciesFromElement(dependenciesElement, dependencies);
                }
            }
        }

        return dependencies;
    }

    /**
     * Extracts dependency artifactIds from a dependencies element.
     *
     * @param dependenciesElement The dependencies XML element.
     * @param dependencies        The list to add found artifactIds to.
     */
    private void extractDependenciesFromElement(Element dependenciesElement, List<String> dependencies) {
        NodeList dependencyNodes = dependenciesElement.getElementsByTagName("dependency");

        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependencyElement = (Element) dependencyNodes.item(i);

            // Get artifactId - this is what we'll match against workspace projects
            String artifactId = getElementText(dependencyElement, "artifactId");
            if (!artifactId.isEmpty() && !dependencies.contains(artifactId)) {
                dependencies.add(artifactId);
            }
        }
    }

    /**
     * Gets text content of a child element.
     *
     * @param parent  The arent element.
     * @param tagName The tag name to search.
     * 
     * @return The text content or empty string.
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return "";
    }

    /**
     * Checks if a file is a valid Maven POM file.
     *
     * @param filePath The path to the file.
     * @return true if the pom.xml is valid.
     */
    public boolean isValidPomFile(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.getName().equals("pom.xml");
    }
}