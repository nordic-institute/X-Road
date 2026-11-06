/*
 * The MIT License
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
package org.niis.xroad.configuration.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.node.NodeIdentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeOptionTest {

    @Test
    void shouldDefaultToNodeZeroWhenOptionIsAbsent() {
        NodeOption option = NodeOption.extract(new String[]{"/some/script.sh"});

        assertThat(option.nodeIdentity()).isEqualTo(new NodeIdentity("node_0", false));
        assertThat(option.remainingArgs()).containsExactly("/some/script.sh");
    }

    @Test
    void shouldTakeTheNodeNameFromSeparateArgument() {
        NodeOption option = NodeOption.extract(new String[]{"--node", "node_1", "/some/script.sh"});

        assertThat(option.nodeIdentity()).isEqualTo(new NodeIdentity("node_1", true));
        assertThat(option.remainingArgs()).containsExactly("/some/script.sh");
    }

    @Test
    void shouldTakeTheNodeNameFromEqualsForm() {
        NodeOption option = NodeOption.extract(new String[]{"/some/script.sh", "--node=cs-2"});

        assertThat(option.nodeIdentity()).isEqualTo(new NodeIdentity("cs-2", true));
        assertThat(option.remainingArgs()).containsExactly("/some/script.sh");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_node", "-node", "node.1", "true", "0123", "1e3",
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void shouldRejectAnInvalidNodeNameWithTheRule(String name) {
        assertThatThrownBy(() -> NodeOption.extract(new String[]{"--node", name}))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");
    }

    @Test
    void shouldRejectOptionWithoutValue() {
        assertThatThrownBy(() -> NodeOption.extract(new String[]{"--node"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a node name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void shouldRejectABlankValueInSeparateForm(String value) {
        assertThatThrownBy(() -> NodeOption.extract(new String[]{"--node", value, "/some/script.sh"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a node name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"--node=", "--node= "})
    void shouldRejectABlankValueInEqualsForm(String arg) {
        assertThatThrownBy(() -> NodeOption.extract(new String[]{arg, "/some/script.sh"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a node name");
    }

    @Test
    void shouldRejectOptionGivenTwice() {
        assertThatThrownBy(() -> NodeOption.extract(new String[]{"--node", "node_1", "--node=node_2"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only once");
    }
}
