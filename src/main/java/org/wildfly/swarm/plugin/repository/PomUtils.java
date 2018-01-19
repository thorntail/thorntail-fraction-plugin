package org.wildfly.swarm.plugin.repository;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Author: Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * Date: 3/17/17
 * Time: 11:04 PM
 */
class PomUtils {
    private PomUtils() {
    }

    static XmlToString extract(File xmlFile, String xpathLocator) {
        try {
            NodeList nodeList = extractNodes(xmlFile, xpathLocator);
            return new XmlToString(nodeList);
        } catch (IOException | SAXException | ParserConfigurationException | XPathExpressionException e) {
            System.err.println("Error extracting dependencies from xmlFile " + xmlFile);
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    private static NodeList extractNodes(File xmlFile, String xpathLocator) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlFile);
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile(xpathLocator);
        return (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    }

    static class XmlToString {
        private final NodeList nodeList;

        private final Set<String> expressionsToSkip = new HashSet<>();

        XmlToString(NodeList nodeList) {
            this.nodeList = nodeList;
        }

        XmlToString skipping(String... expressions) {
            if (expressions != null) {
                expressionsToSkip.addAll(Arrays.asList(expressions));
            }
            return this;
        }

        String asString() throws TransformerException {
            StringBuilder result = new StringBuilder();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            for (int i = 0; i < nodeList.getLength(); ++i) {
                StringWriter writer = new StringWriter();
                StreamResult streamResult = new StreamResult(writer);
                DOMSource source = new DOMSource();
                source.setNode(nodeList.item(i));
                transformer.transform(source, streamResult);
                String element = writer.toString();
                if (expressionsToSkip.stream().noneMatch(element::contains)) {
                    result.append(element).append("\n");
                }
            }

            return result.toString();
        }
    }

}
