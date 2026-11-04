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
package org.niis.xroad.common.vault;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeScopedSecretPathsTest {

    @Test
    void shouldBuildAllPathsUnderTheNodeName() {
        var paths = new NodeScopedSecretPaths("node_a");

        assertThat(paths.tokenPin("tok1")).isEqualTo("signer/token-pins/node_a/tok1");
        assertThat(paths.adminServiceTls()).isEqualTo("tls/admin-service/node_a");
        assertThat(paths.managementServiceTls()).isEqualTo("tls/management-service/node_a");
    }

    @Test
    void shouldKeepTokenZeroAndNodeZeroOnDifferentPaths() {
        var nodeZero = new NodeScopedSecretPaths("0");
        var nodeOne = new NodeScopedSecretPaths("1");

        assertThat(nodeZero.tokenPin("0")).isEqualTo("signer/token-pins/0/0");
        assertThat(nodeZero.tokenPin("1")).isEqualTo("signer/token-pins/0/1");
        assertThat(nodeOne.tokenPin("0")).isEqualTo("signer/token-pins/1/0");
    }

    @Test
    void shouldFallBackToTheDefaultNodeNameWhenBlank() {
        assertThat(new NodeScopedSecretPaths(null).tokenPin("tok1")).isEqualTo("signer/token-pins/node_0/tok1");
        assertThat(new NodeScopedSecretPaths(" ").adminServiceTls()).isEqualTo("tls/admin-service/node_0");
    }

    @Test
    void shouldRejectAnInvalidNodeName() {
        assertThatThrownBy(() -> new NodeScopedSecretPaths("../x"))
                .isInstanceOf(XrdRuntimeException.class);
    }
}
