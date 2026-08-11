/*******************************************************************************
 * Copyright (c) 2017, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial implementation
 *******************************************************************************/
package it.io.openliberty.guides.multimodules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class ConverterAppIT {
    String port = System.getProperty("default.http.port");
    String war = "converter";
    String urlBase = "http://localhost:" + port + "/" + war + "/";

    public final String LOOSE_APP = "build/wlp/usr/servers/defaultServer/apps/guide-gradle-multimodules-custmm-ear-skinny-modules-1.0-SNAPSHOT.ear.xml";

    @Test
    public void testLooseApplicationFileExist() throws Exception {
        File f = new File(LOOSE_APP);
        assertTrue(f.exists(), f.getCanonicalFile() + " doesn't exist");
    }

    @Test
    public void testLooseApplicationFileContent() throws Exception {
        File f = new File(LOOSE_APP);
        try (FileInputStream input = new FileInputStream(f)) {

            DocumentBuilderFactory inputBuilderFactory = DocumentBuilderFactory.newInstance();
            inputBuilderFactory.setIgnoringComments(true);
            inputBuilderFactory.setCoalescing(true);
            inputBuilderFactory.setIgnoringElementContentWhitespace(true);
            inputBuilderFactory.setValidating(false);
            inputBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            inputBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder inputBuilder = inputBuilderFactory.newDocumentBuilder();
            Document inputDoc = inputBuilder.parse(input);

            XPath xPath = XPathFactory.newInstance().newXPath();

            // Two top-level <file> elements: application.xml and MANIFEST.MF
            String expression = "/archive/file";
            NodeList nodes = (NodeList) xPath.compile(expression).evaluate(inputDoc, XPathConstants.NODESET);
            assertEquals(2, nodes.getLength(), "Expected 2 top-level <file/> elements: application.xml and MANIFEST.MF");
            assertEquals("/META-INF/application.xml",
                    nodes.item(0).getAttributes().getNamedItem("targetInArchive").getNodeValue(),
                    "First top-level <file/> targetInArchive should be application.xml");
            assertEquals("/META-INF/MANIFEST.MF",
                    nodes.item(1).getAttributes().getNamedItem("targetInArchive").getNodeValue(),
                    "Second top-level <file/> targetInArchive should be MANIFEST.MF");

            // Five top-level <archive> elements: war, war2, rar, jar, ejb
            // NOTE: Unlike Maven with skinnyModules, Gradle does not strip dependencies from
            // WARs into the EAR lib/ directory, so jar/ejb appear both inside WAR WEB-INF/lib
            // and as standalone EAR-level archives (deployed via the 'deploy' configuration).
            expression = "/archive/archive";
            nodes = (NodeList) xPath.compile(expression).evaluate(inputDoc, XPathConstants.NODESET);
            assertEquals(5, nodes.getLength(), "Expected 5 top-level <archive/> elements: war, war2, rar, jar, ejb");
            assertEquals("/guide-gradle-multimodules-custmm-war-1.0-SNAPSHOT.war",
                    nodes.item(0).getAttributes().getNamedItem("targetInArchive").getNodeValue(),
                    "First <archive/> should be the war module");
            assertEquals("/guide-gradle-multimodules-custmm-war2-1.0-SNAPSHOT.war",
                    nodes.item(1).getAttributes().getNamedItem("targetInArchive").getNodeValue(),
                    "Second <archive/> should be the war2 module");
            // NOTE: RAR archive name is rar-1.0-SNAPSHOT.jar because Gradle's EAR deploy
            // configuration resolves the custom Zip artifact by project name, not archiveBaseName.
            assertEquals("/rar-1.0-SNAPSHOT.jar",
                    nodes.item(2).getAttributes().getNamedItem("targetInArchive").getNodeValue(),
                    "Third <archive/> should be the rar module");
            assertEquals("/guide-gradle-multimodules-custmm-jar-1.0-SNAPSHOT.jar",
                    nodes.item(3).getAttributes().getNamedItem("targetInArchive").getNodeValue(),
                    "Fourth <archive/> should be the jar module");
            assertEquals("/guide-gradle-multimodules-custmm-ejb-1.0-SNAPSHOT.jar",
                    nodes.item(4).getAttributes().getNamedItem("targetInArchive").getNodeValue(),
                    "Fifth <archive/> should be the ejb module");

            // Six <dir> elements inside top-level archives:
            // war(webapp + WEB-INF/classes) + war2(WEB-INF/classes) + rar(/) + jar(/) + ejb(/)
            expression = "/archive/archive/dir";
            nodes = (NodeList) xPath.compile(expression).evaluate(inputDoc, XPathConstants.NODESET);
            assertEquals(6, nodes.getLength(), "Expected 6 <dir/> elements inside top-level archives");

            // Six <file> elements inside top-level archives:
            // war(MANIFEST.MF) + war2(MANIFEST.MF + commons-io) + rar(MANIFEST.MF) +
            // jar(MANIFEST.MF) + ejb(MANIFEST.MF)
            expression = "/archive/archive/file";
            nodes = (NodeList) xPath.compile(expression).evaluate(inputDoc, XPathConstants.NODESET);
            assertEquals(6, nodes.getLength(), "Expected 6 <file/> elements inside top-level archives");

            // Three <archive> elements nested inside top-level archives:
            // jar-in-war/WEB-INF/lib + ejb-in-war/WEB-INF/lib + jar-in-war2/WEB-INF/lib
            // NOTE: Unlike Maven skinnyModules, dependencies are not stripped from WARs,
            // so lib JARs appear inside each WAR.
            expression = "/archive/archive/archive";
            nodes = (NodeList) xPath.compile(expression).evaluate(inputDoc, XPathConstants.NODESET);
            assertEquals(3, nodes.getLength(), "Expected 3 nested <archive/> elements (lib jars inside WARs)");
        }
    }

    @Test
    public void testIndexPage() throws Exception {
        String url = this.urlBase;
        HttpURLConnection con = testRequestHelper(url, "GET");
        assertEquals(200, con.getResponseCode(), "Incorrect response code from " + url);
        assertTrue(testBufferHelper(con).contains("Enter the height in centimeters"),
                        "Incorrect response from " + url);
    }

    @Test
    public void testHeightsPage() throws Exception {
        String url = this.urlBase + "heights.jsp?heightCm=10";
        HttpURLConnection con = testRequestHelper(url, "POST");
        assertTrue(testBufferHelper(con).contains("3        inches"),
                        "Incorrect response from " + url);
    }

    private HttpURLConnection testRequestHelper(String url, String method)
                    throws Exception {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        // optional default is GET
        con.setRequestMethod(method);
        return con;
    }

    private String testBufferHelper(HttpURLConnection con) throws Exception {
        BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }

}
