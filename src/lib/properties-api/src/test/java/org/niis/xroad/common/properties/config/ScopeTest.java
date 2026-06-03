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

package org.niis.xroad.common.properties.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScopeTest {

    @Test
    void ofRequiresNonNullRootPath() {
        assertThatThrownBy(() -> Scope.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rootScopeExposesRootPathAndEmptyName() {
        var scope = Scope.of("xroad.proxy");

        assertThat(scope.rootPath()).isEqualTo("xroad.proxy");
        assertThat(scope.name()).isEmpty();
    }

    @Test
    void namedRootScopeExposesNameAndRejectsNullName() {
        var scope = Scope.of("xroad.proxy", "proxy");

        assertThat(scope.rootPath()).isEqualTo("xroad.proxy");
        assertThat(scope.name()).contains("proxy");
        assertThatThrownBy(() -> Scope.of("xroad.proxy", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void childChainsRootPathAndInheritsName() {
        var buffer = Scope.of("xroad.proxy", "proxy")
                .child("addon")
                .child("op-monitor")
                .child("buffer");

        assertThat(buffer.rootPath()).isEqualTo("xroad.proxy.addon.op-monitor.buffer");
        assertThat(buffer.name()).contains("proxy");
    }

    @Test
    void builderFactoriesFormEffectiveKeysWithFullPath() {
        var proxy = Scope.of("xroad.proxy");
        var client = proxy.child("client-proxy");

        assertThat(proxy.integer("admin-port").build().key()).isEqualTo("xroad.proxy.admin-port");
        assertThat(client.bool("reuse").build().key()).isEqualTo("xroad.proxy.client-proxy.reuse");
        assertThat(client.string("host").build().key()).isEqualTo("xroad.proxy.client-proxy.host");
    }

    @Test
    void builderFactoriesCarryDeclaredType() {
        var scope = Scope.of("xroad.svc");

        assertThat(scope.integer("i").build().type()).isEqualTo(Integer.class);
        assertThat(scope.longValue("l").build().type()).isEqualTo(Long.class);
        assertThat(scope.bool("b").build().type()).isEqualTo(Boolean.class);
        assertThat(scope.string("s").build().type()).isEqualTo(String.class);
        assertThat(scope.stringArray("a").build().type()).isEqualTo(String[].class);
        assertThat(scope.keyDuration("d").build().type()).isEqualTo(Duration.class);
        assertThat(scope.keyEnum("e", Mode.class).build().type()).isEqualTo(Mode.class);
    }

    @Test
    void keysTrackedOnRootInDeclarationOrderAcrossNestedScopes() {
        var root = Scope.of("xroad.proxy");
        var rootKey = root.integer("admin-port").build();
        var childKey = root.child("client-proxy").bool("reuse").build();
        var grandChildKey = root.child("addon").child("op-monitor").bool("enabled").build();

        // every key — root, child, grandchild — is tracked on the root scope, in declaration order
        assertThat(root.keys()).containsExactly(rootKey, childKey, grandChildKey);
    }

    @Test
    void childScopeDoesNotTrackKeysItself() {
        var root = Scope.of("xroad.proxy");
        var child = root.child("client-proxy");
        child.bool("reuse").build();

        // tracking lives on the root scope only
        assertThat(child.keys()).isEmpty();
        assertThat(root.keys()).hasSize(1);
    }

    private enum Mode { ON, OFF }
}
