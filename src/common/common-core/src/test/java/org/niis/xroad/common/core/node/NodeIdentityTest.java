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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeIdentityTest {

    @ParameterizedTest
    @ValueSource(strings = {"node_1", "0", "a", "Z", "9", "cs-node-2", "A_b-c", "node_0", "0abc", "1a",
            "0bad", "0bed", "0oface", "0b2", "0o8",
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void shouldAcceptValidNames(String name) {
        assertThat(NodeIdentity.resolve(name)).isEqualTo(new NodeIdentity(name, true));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t"})
    void shouldResolveDefaultNameForBlankOrUnset(String value) {
        assertThat(NodeIdentity.resolve(value)).isEqualTo(new NodeIdentity("node_0", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"_node", "-node", "_", "-",
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "node name", "node.1", "node/1", "node:1", "nodé", "node ", " node", "node\n", "a$", "a=b"})
    void shouldRejectNamesBreakingTheCharacterRules(String name) {
        assertInvalid(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "True", "TRUE", "false", "False", "FALSE", "yes", "Yes", "YES",
            "no", "No", "NO", "on", "On", "ON", "off", "Off", "OFF", "null", "Null", "NULL",
            "0123", "007", "0x1F", "0b101", "0o17", "1_0", "1_000", "1e3", "2E10", "2024-01-31"})
    void shouldRejectValuesYamlReadsAsNonString(String name) {
        assertInvalid(name);
    }

    @Test
    void shouldNameTheRuleInTheFailureMessage() {
        assertThatThrownBy(() -> NodeIdentity.resolve("-bad"))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("xroad.admin-service.ha-node-name")
                .hasMessageContaining("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$")
                .hasMessageContaining("YAML");
    }

    private static void assertInvalid(String name) {
        assertThatThrownBy(() -> NodeIdentity.resolve(name))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PROPERTY_VALUE.code()));
    }
}
