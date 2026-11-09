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
package org.niis.xroad.common.properties.dbsource;

import org.niis.xroad.common.properties.NodeProperties;

import java.util.regex.Pattern;

/**
 * The one rule for the name of a cluster node. It decides which {@code configuration_properties} rows a process
 * loads and which secret paths it uses. An instance always holds a valid name.
 * <p>
 * The name comes from the environment variable {@value NodeProperties#NODE_NAME_ENV_VARIABLE}, see
 * {@link #fromEnvironment()} and {@link #resolve(String)}.
 *
 * @param name       the validated node name
 * @param configured whether the operator set the name; {@code false} means {@code name} is the default
 */
public record NodeName(String name, boolean configured) {

    /** Node name used when the environment variable is blank or missing. */
    public static final String DEFAULT_NODE_NAME = "node_0";

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");

    /** The rule as a clause that completes the sentence "the name ...", for messages that name their own source. */
    public static final String RULE = "must match " + VALID_NAME.pattern();

    public NodeName {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new InvalidNodeNameException("Invalid node name '%s' in %s: it %s"
                    .formatted(name, NodeProperties.NODE_NAME_ENV_VARIABLE, RULE));
        }
    }

    /**
     * The node name for a configured value.
     *
     * @param configuredValue the raw configured value; {@code null} or blank when not configured
     * @return the node name, {@link #DEFAULT_NODE_NAME} with {@code configured = false} when the value is blank
     * @throws InvalidNodeNameException if the value breaks the node name rule; the value is never rewritten
     */
    public static NodeName resolve(String configuredValue) {
        if (configuredValue == null || configuredValue.isBlank()) {
            return new NodeName(DEFAULT_NODE_NAME, false);
        }
        return new NodeName(configuredValue, true);
    }

    /**
     * The node name of this process, read from {@value NodeProperties#NODE_NAME_ENV_VARIABLE}.
     *
     * @return the node name, {@link #DEFAULT_NODE_NAME} with {@code configured = false} when the variable is blank
     * or unset
     * @throws InvalidNodeNameException if the value breaks the node name rule
     */
    public static NodeName fromEnvironment() {
        return resolve(NodeProperties.getConfiguredNodeName());
    }
}
