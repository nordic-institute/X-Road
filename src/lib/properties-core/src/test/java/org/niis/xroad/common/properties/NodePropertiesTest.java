/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.common.properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.common.properties.NodeProperties.NODE_TYPE_ENV_VARIABLE;

@ExtendWith(SystemStubsExtension.class)
class NodePropertiesTest {

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @Test
    void testGetServerNodeTypePrimary() {
        variables.set(NODE_TYPE_ENV_VARIABLE, "primary");
        assertEquals(NodeProperties.NodeType.PRIMARY, NodeProperties.getServerNodeType());
        variables.set(NODE_TYPE_ENV_VARIABLE, "PRIMARY");
        assertEquals(NodeProperties.NodeType.PRIMARY, NodeProperties.getServerNodeType());

        variables.set(NODE_TYPE_ENV_VARIABLE, "master");
        assertEquals(NodeProperties.NodeType.PRIMARY, NodeProperties.getServerNodeType());
        variables.set(NODE_TYPE_ENV_VARIABLE, "MASTER");
        assertEquals(NodeProperties.NodeType.PRIMARY, NodeProperties.getServerNodeType());
    }

    @Test
    void testGetServerNodeTypeSecondary() {
        variables.set(NODE_TYPE_ENV_VARIABLE, "slave");
        assertEquals(NodeProperties.NodeType.SECONDARY, NodeProperties.getServerNodeType());

        variables.set(NODE_TYPE_ENV_VARIABLE, "secondary");
        assertEquals(NodeProperties.NodeType.SECONDARY, NodeProperties.getServerNodeType());
    }

    @Test
    void testGetServerNodeTypeStandalone() {
        variables.set(NODE_TYPE_ENV_VARIABLE, "standalone");
        assertEquals(NodeProperties.NodeType.STANDALONE, NodeProperties.getServerNodeType());
    }

    @Test
    void testGetServerNodeTypeDefault() {
        assertEquals(NodeProperties.NodeType.STANDALONE, NodeProperties.getServerNodeType());

        variables.set(NODE_TYPE_ENV_VARIABLE, "something-else");
        assertEquals(NodeProperties.NodeType.STANDALONE, NodeProperties.getServerNodeType());
    }

    @Test
    void testIsSecondaryNode() {
        variables.set(NODE_TYPE_ENV_VARIABLE, "primary");
        assertFalse(NodeProperties.isSecondaryNode());

        variables.set(NODE_TYPE_ENV_VARIABLE, "master");
        assertFalse(NodeProperties.isSecondaryNode());

        variables.set(NODE_TYPE_ENV_VARIABLE, "slave");
        assertTrue(NodeProperties.isSecondaryNode());

        variables.set(NODE_TYPE_ENV_VARIABLE, "secondary");
        assertTrue(NodeProperties.isSecondaryNode());

        variables.set(NODE_TYPE_ENV_VARIABLE, "standalone");
        assertFalse(NodeProperties.isSecondaryNode());
    }

}
