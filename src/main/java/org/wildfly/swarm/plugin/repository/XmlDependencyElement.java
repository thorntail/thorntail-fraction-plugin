/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.plugin.repository;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.wildfly.swarm.plugin.repository.PomUtils.toStream;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * <br>
 * Date: 1/22/18
 */
public class XmlDependencyElement {

    private static final Transformer transformer;

    private String groupId;
    private String artifactId;
    private String scope;
    private String elementAsString;

    static {
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new IllegalStateException("Cannot create XML transformer", e);
        }
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    }

    public String getElementAsString() {
        return elementAsString;
    }

    public boolean isNormalDependency() {
        return !"import".equals(scope); // `scope` can be `null`
    }

    public static XmlDependencyElement fromNode(Node node, String elementAsString) {
        Map<String, String> map = toMap(node.getChildNodes());

        XmlDependencyElement result = new XmlDependencyElement();
        result.groupId = map.get("groupId");
        result.artifactId = map.get("artifactId");
        result.scope = map.get("scope");

        result.elementAsString = elementAsString;

        return result;
    }

    private static Map<String, String> toMap(NodeList nodeList) {
        Map<String, String> resultMap = new HashMap<>();
        toStream(nodeList)
                .forEach(node ->
                        resultMap.put(node.getNodeName(), node.getTextContent())
                );
        return resultMap;
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        XmlDependencyElement dependencyElement = (XmlDependencyElement) o;
        return Objects.equals(groupId, dependencyElement.groupId) &&
                Objects.equals(artifactId, dependencyElement.artifactId);
    }
}
