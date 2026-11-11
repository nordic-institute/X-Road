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

import lombok.experimental.UtilityClass;

import java.util.Set;

@UtilityClass
public class NodeProperties {

    public static final String NODE_TYPE_ENV_VARIABLE = "XROAD_NODE_TYPE";

    public static final String NODE_NAME_ENV_VARIABLE = "XROAD_NODE_NAME";

    public enum NodeType {
        STANDALONE("standalone"),
        PRIMARY("primary", "master"),
        SECONDARY("secondary", "slave");

        private final Set<String> aliases;

        NodeType(String... aliases) {
            this.aliases = Set.of(aliases);
        }

        public static NodeType fromStringIgnoreCaseOrReturnDefault(String name) {
            if (name != null) {

                for (NodeType type : values()) {
                    if (type.aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(name))) {
                        return type;
                    }
                }
            }
            return STANDALONE;
        }
    }

    public static NodeType getServerNodeType() {
        return NodeType.fromStringIgnoreCaseOrReturnDefault(System.getenv(NODE_TYPE_ENV_VARIABLE));
    }

    /**
     * @return the raw value of the node name environment variable, {@code null} when it is not set
     */
    public static String getConfiguredNodeName() {
        return System.getenv(NODE_NAME_ENV_VARIABLE);
    }

    public boolean isSecondaryNode() {
        return getServerNodeType() == NodeType.SECONDARY;
    }

}
