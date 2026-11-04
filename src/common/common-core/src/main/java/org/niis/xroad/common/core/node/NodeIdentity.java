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
package org.niis.xroad.common.core.node;

import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Identity of one cluster node. An instance always holds a valid node name, so code that receives one needs
 * no further validation.
 * <p>
 * The name comes from the configured value {@value #PROPERTY_KEY}, see {@link #resolve(String)}. The check
 * that rejects values YAML reads as a non-string sees the raw value only when it comes from the environment
 * variable. A value in {@code local.yaml} is converted by the YAML loader before it reaches Java, so for that
 * source the installer and the backup scripts check the file instead.
 *
 * @param name       the validated node name
 * @param configured whether the operator set the name; {@code false} means {@code name} is the default
 */
public record NodeIdentity(String name, boolean configured) {

    public static final String PROPERTY_KEY = "xroad.admin-service.ha-node-name";
    public static final String DEFAULT_NODE_NAME = "node_0";

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");

    private static final Set<String> YAML_NON_STRING_WORDS = Set.of(
            "true", "True", "TRUE", "false", "False", "FALSE",
            "yes", "Yes", "YES", "no", "No", "NO",
            "on", "On", "ON", "off", "Off", "OFF",
            "null", "Null", "NULL");

    private static final Pattern YAML_NON_STRING_NUMBER = Pattern.compile(
            "^(?:0[0-9_]+|0[xX][0-9A-Fa-f_]+|0[bB][01_]+|0[oO][0-7_]+|[0-9]+_[0-9_]*|[0-9][0-9_]*[eE]-?[0-9]+"
                    + "|[0-9]{4}-[0-9]{2}-[0-9]{2})$");

    private static final String RULE = "must match " + VALID_NAME.pattern()
            + " and must not be a value that YAML reads as a non-string, such as true, on, yes, null, 0123, 1_0 or 1e3";

    public NodeIdentity {
        if (name == null || !VALID_NAME.matcher(name).matches()
                || YAML_NON_STRING_WORDS.contains(name)
                || YAML_NON_STRING_NUMBER.matcher(name).matches()) {
            throw XrdRuntimeException.systemException(ErrorCode.INVALID_PROPERTY_VALUE,
                    "Invalid node name '%s' in %s: it %s", name, PROPERTY_KEY, RULE);
        }
    }

    /**
     * The node identity for a configured value.
     *
     * @param configuredValue the raw configured value; {@code null} or blank when not configured
     * @return the node identity, {@link #DEFAULT_NODE_NAME} with {@code configured = false} when the value is blank
     * @throws XrdRuntimeException if the value breaks the node name rule; the value is never rewritten
     */
    public static NodeIdentity resolve(String configuredValue) {
        if (configuredValue == null || configuredValue.isBlank()) {
            return new NodeIdentity(DEFAULT_NODE_NAME, false);
        }
        return new NodeIdentity(configuredValue, true);
    }
}
