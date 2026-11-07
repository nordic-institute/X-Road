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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.properties.dbsource.NodeNameCases.Category.BLANK;
import static org.niis.xroad.common.properties.dbsource.NodeNameCases.Category.INVALID_CHARACTERS;
import static org.niis.xroad.common.properties.dbsource.NodeNameCases.Category.INVALID_YAML_NON_STRING;
import static org.niis.xroad.common.properties.dbsource.NodeNameCases.Category.VALID;

class NodeNameTest {

    @ParameterizedTest
    @MethodSource("validNames")
    void validNameIsUsedUnchanged(String name) {
        assertThat(NodeName.resolve(name)).isEqualTo(new NodeName(name, true));
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("blankValues")
    void blankOrMissingNameResolvesToTheDefaultNode(String value) {
        assertThat(NodeName.resolve(value)).isEqualTo(new NodeName("node_0", false));
    }

    @ParameterizedTest
    @MethodSource("invalidCharacterNames")
    void nameBreakingTheCharacterRulesIsRejected(String name) {
        assertRejected(name);
    }

    @ParameterizedTest
    @MethodSource("yamlNonStringNames")
    void valueYamlReadsAsNonStringIsRejected(String name) {
        assertRejected(name);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void constructorRejectsABlankName(String name) {
        assertThatThrownBy(() -> new NodeName(name, false)).isInstanceOf(InvalidNodeNameException.class);
    }

    @Test
    void rejectionNamesTheKeyAndTheRule() {
        assertThatThrownBy(() -> NodeName.resolve("-bad"))
                .isInstanceOf(InvalidNodeNameException.class)
                .hasMessageContaining("xroad.admin-service.ha-node-name")
                .hasMessageContaining("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$")
                .hasMessageContaining("YAML");
    }

    static Stream<String> validNames() {
        return NodeNameCases.values(VALID);
    }

    static Stream<String> blankValues() {
        return NodeNameCases.values(BLANK);
    }

    static Stream<String> invalidCharacterNames() {
        return NodeNameCases.values(INVALID_CHARACTERS);
    }

    static Stream<String> yamlNonStringNames() {
        return NodeNameCases.values(INVALID_YAML_NON_STRING);
    }

    private static void assertRejected(String name) {
        assertThatThrownBy(() -> NodeName.resolve(name)).isInstanceOf(InvalidNodeNameException.class);
        assertThatThrownBy(() -> new NodeName(name, true)).isInstanceOf(InvalidNodeNameException.class);
    }
}
