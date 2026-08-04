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
import org.niis.xroad.common.properties.config.keys.ConfigKeyProviders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkPublishedConfigTest {

    /**
     * Every key a packaged {@code application.yaml} interpolates into a framework setting, and nothing
     * else. Flagging a key means its stored value is copied into the Spring {@code Environment} /
     * SmallRye config, so the list is deliberately hard to grow by accident.
     */
    private static final List<String> FRAMEWORK_VISIBLE_KEYS = List.of(
            "xroad.proxy.health-check-interface",
            "xroad.proxy.health-check-enabled",
            "xroad.proxy.health-check-port",
            "xroad.proxy-ui-api.request-size-limit-binary-upload",
            "xroad.admin-service.request-size-limit-binary-upload",
            "xroad.edc.iam.trusted-issuer.issuer.id");

    @Test
    void onlyTheDeclaredFrameworkSettingsAreFlagged() {
        var flagged = ConfigKeyProviders.allProviders().stream()
                .flatMap(provider -> provider.keys().stream())
                .filter(ConfigKey::publishedToFramework)
                .map(ConfigKey::key)
                .toList();

        assertThat(flagged).containsExactlyInAnyOrderElementsOf(FRAMEWORK_VISIBLE_KEYS);
    }

    @Test
    void keysAreNotFrameworkVisibleByDefault() {
        var prefix = Prefix.of(Category.PROXY, "xroad.framework-flag-test");
        var key = prefix.integer("plain").withDefaultValue(1).build();

        assertThat(key.publishedToFramework()).isFalse();
    }

    @Test
    void flaggedKeyReportsItself() {
        var prefix = Prefix.of(Category.PROXY, "xroad.framework-flag-test-published");
        var key = prefix.integer("published").withDefaultValue(1).publishedToFramework().build();

        assertThat(key.publishedToFramework()).isTrue();
    }

    @Test
    void nothingIsPublishedWhenNoDatabaseIsConfigured() {
        assertThat(FrameworkPublishedConfig.storedOverrides("proxy", ConfigKeyProviders.allProviders())).isEmpty();
    }
}
