package org.wildfly.swarm.plugin.repository;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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


    public static Stream<Node> toStream(final NodeList nodeList) {
        return new AbstractList<Node>() {
            @Override
            public int size() {
                return nodeList.getLength();
            }

            @Override
            public Node get(int index) {
                return nodeList.item(index);
            }
        }.stream();
    }

    public static class XmlToString {
        private final NodeList nodeList;

        private final Set<String> expressionsToSkip = new HashSet<>();

        XmlToString(NodeList nodeList) {
            this.nodeList = nodeList;
        }

        public XmlToString skipping(String... expressions) {
            if (expressions != null) {
                expressionsToSkip.addAll(Arrays.asList(expressions));
            }
            return this;
        }

        public NodeList raw() {
            return nodeList;
        }

        public <T> List<T> translate(BiFunction<Node, String, T> translator) {
            try {
                List<T> result = new ArrayList<>();

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

                for (int i = 0; i < nodeList.getLength(); ++i) {
                    Node node = nodeList.item(i);
                    String element = nodeAsString(transformer, node);
                    if (expressionsToSkip.stream().noneMatch(element::contains)) {
                        result.add(translator.apply(node, element));
                    }
                }
                return result;
            } catch (TransformerException e) {
                throw new RuntimeException("Failure to transform Xml element", e);
            }
        }

        public String asString() {
           return translate((node, value) -> value).stream().collect(Collectors.joining("\n"));
        }

        private static String nodeAsString(Transformer transformer, Node node) throws TransformerException {
            StringWriter writer = new StringWriter();
            StreamResult streamResult = new StreamResult(writer);
            DOMSource source = new DOMSource();
            source.setNode(node);
            transformer.transform(source, streamResult);
            return writer.toString();
        }
    }
}
