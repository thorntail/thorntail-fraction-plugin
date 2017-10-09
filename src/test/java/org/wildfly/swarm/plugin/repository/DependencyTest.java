/*
 * Copyright 2017 Red Hat, Inc, and individual contributors.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DependencyTest {

    @Test
    public void testDependencyPriority() {
        Dependency d1 = new Dependency();
        Dependency d2 = new Dependency();

        d1.version = "3.3.1.redhat-12";
        d2.version = "3.4";
        // Productized artifacts takes precedence
        assertTrue(d1.hasHigherPriority(d2));
        assertFalse(d2.hasHigherPriority(d1));

        // Latest version takes precedence
        // 3.3.1 < 3.4
        d1.version = "3.3.1";
        assertFalse(d1.hasHigherPriority(d2));
        d1.version = "3.0.0.1";
        d2.version = "3.0.0.0";

        // Only major/minor/bugfix is considered
        assertFalse(d1.hasHigherPriority(d2));
        assertFalse(d2.hasHigherPriority(d1));

        // Classifiers
        d1.version = "3.0.0.Final";
        d2.version = "3.0.1.Final";
        // 3.0.0.Final < 3.0.1.Final
        assertFalse(d1.hasHigherPriority(d2));
        assertTrue(d2.hasHigherPriority(d1));

        // Unparsable versions
        d1.version = "3.x";
        d2.version = "3.0.y";
        assertFalse(d1.hasHigherPriority(d2));
        assertTrue(d2.hasHigherPriority(d1));

        // Incomplete versions
        d1.version = "1";
        d2.version = "1.Final";
        assertFalse(d1.hasHigherPriority(d2));
        assertFalse(d2.hasHigherPriority(d1));

        // Meaningless versions
        d1.version = "x";
        d2.version = "@abc";
        assertFalse(d1.hasHigherPriority(d2));
        assertFalse(d2.hasHigherPriority(d1));
    }

}
