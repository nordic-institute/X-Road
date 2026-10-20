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

package org.niis.xroad.proxy.core.configuration;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyAgreementTokenPropertiesTest {

    @Test
    void defaultsMatchTheDocumentedValues() {
        var config = XRoadConfigBuilder.create().register(ProxyConfigKeys.instance()).build();
        var properties = new ProxyAgreementTokenProperties(config);

        assertThat(properties.issuer()).isEqualTo("x-road-provider-data-plane");
        assertThat(properties.audience()).isEqualTo("x-road-server-proxy");
        assertThat(properties.tokenTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.keyRefreshInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxKeyAge()).isEqualTo(Duration.ofDays(30));
        assertThat(properties.keyRetention()).isEqualTo(Duration.ofDays(1));
        assertThat(properties.grantLookupDeadline()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void valuesAreReadFromConfiguration() {
        var config = XRoadConfigBuilder.create().register(ProxyConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.proxy.agreement-token.issuer", "custom-issuer",
                        "xroad.proxy.agreement-token.audience", "custom-audience",
                        "xroad.proxy.agreement-token.token-ttl", "PT30S",
                        "xroad.proxy.agreement-token.key-refresh-interval", "PT15S",
                        "xroad.proxy.agreement-token.max-key-age", "P7D",
                        "xroad.proxy.agreement-token.key-retention", "PT12H",
                        "xroad.proxy.agreement-token.grant-lookup-deadline", "PT2S"))
                .build();
        var properties = new ProxyAgreementTokenProperties(config);

        assertThat(properties.issuer()).isEqualTo("custom-issuer");
        assertThat(properties.audience()).isEqualTo("custom-audience");
        assertThat(properties.tokenTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.keyRefreshInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.maxKeyAge()).isEqualTo(Duration.ofDays(7));
        assertThat(properties.keyRetention()).isEqualTo(Duration.ofHours(12));
        assertThat(properties.grantLookupDeadline()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void zeroMaxKeyAgeDisablesAutomaticRotation() {
        var config = XRoadConfigBuilder.create().register(ProxyConfigKeys.instance())
                .overrides(Map.of("xroad.proxy.agreement-token.max-key-age", "PT0S"))
                .build();

        assertThat(new ProxyAgreementTokenProperties(config).maxKeyAge()).isZero();
    }

    @Test
    void negativeMaxKeyAgeIsRefused() {
        var builder = XRoadConfigBuilder.create().register(ProxyConfigKeys.instance())
                .overrides(Map.of("xroad.proxy.agreement-token.max-key-age", "-PT1S"));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-key-age");
    }

    @Test
    void zeroKeyRetentionKeepsEveryKey() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.key-retention", "PT0S"));

        assertThat(properties.keyRetention()).isZero();
    }

    @Test
    void negativeKeyRetentionIsRefused() {
        var builder = XRoadConfigBuilder.create().register(ProxyConfigKeys.instance())
                .overrides(Map.of("xroad.proxy.agreement-token.key-retention", "-PT1S"));

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key-retention");
    }

    @Test
    void defaultsPassTheKeyLifecycleCheck() {
        assertThatCode(propertiesWith(Map.of())::validateKeyLifecycle).doesNotThrowAnyException();
    }

    @Test
    void keyRetentionBelowTenTokenLifetimesIsRefused() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.key-retention", "PT599S"));

        assertThatThrownBy(properties::validateKeyLifecycle)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-retention")
                .hasMessageContaining("token-ttl");
    }

    @Test
    void keyRetentionOfExactlyTenTokenLifetimesIsAccepted() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.key-retention", "PT600S"));

        assertThatCode(properties::validateKeyLifecycle).doesNotThrowAnyException();
    }

    @Test
    void keyRetentionBelowThreeRefreshIntervalsPlusTokenLifetimeAndLeewayIsRefused() {
        var properties = propertiesWith(Map.of(
                "xroad.proxy.agreement-token.key-refresh-interval", "PT10M",
                "xroad.proxy.agreement-token.key-retention", "PT31M4S"));

        assertThatThrownBy(properties::validateKeyLifecycle)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-retention")
                .hasMessageContaining("key-refresh-interval");
    }

    @Test
    void keyRetentionOfThreeRefreshIntervalsPlusTokenLifetimeAndLeewayIsAccepted() {
        var properties = propertiesWith(Map.of(
                "xroad.proxy.agreement-token.key-refresh-interval", "PT10M",
                "xroad.proxy.agreement-token.key-retention", "PT31M5S"));

        assertThatCode(properties::validateKeyLifecycle).doesNotThrowAnyException();
    }

    @Test
    void aRetentionThatWouldRefuseMintingDuringTheLastPartOfEveryRefreshCycleIsRefused() {
        var properties = propertiesWith(Map.of(
                "xroad.proxy.agreement-token.key-refresh-interval", "PT5M",
                "xroad.proxy.agreement-token.key-retention", "PT10M"));

        assertThatThrownBy(properties::validateKeyLifecycle)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("key-retention");
    }

    @Test
    void theEndToEndPruningSettingsPassTheKeyLifecycleCheck() {
        var properties = propertiesWith(Map.of(
                "xroad.proxy.agreement-token.token-ttl", "PT30S",
                "xroad.proxy.agreement-token.key-refresh-interval", "PT5S",
                "xroad.proxy.agreement-token.max-key-age", "PT1M",
                "xroad.proxy.agreement-token.key-retention", "PT5M"));

        assertThatCode(properties::validateKeyLifecycle).doesNotThrowAnyException();
    }

    @Test
    void staleSigningKeyLimitIsTheRetentionMinusRefreshIntervalTokenLifetimeAndLeeway() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.key-retention", "PT1H"));

        assertThat(properties.staleSigningKeyLimit()).contains(Duration.ofSeconds(3600 - 30 - 60 - 5));
    }

    @Test
    void staleSigningKeyLimitIsAbsentWhenTheRetentionIsZero() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.key-retention", "PT0S"));

        assertThat(properties.staleSigningKeyLimit()).isEmpty();
    }

    @Test
    void zeroKeyRetentionPassesTheKeyLifecycleCheck() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.key-retention", "PT0S"));

        assertThatCode(properties::validateKeyLifecycle).doesNotThrowAnyException();
    }

    @Test
    void maxKeyAgeEqualToTheKeyRefreshIntervalIsRefused() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.max-key-age", "PT30S"));

        assertThatThrownBy(properties::validateKeyLifecycle)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-key-age")
                .hasMessageContaining("key-refresh-interval");
    }

    @Test
    void maxKeyAgeLongerThanTheKeyRefreshIntervalIsAccepted() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.max-key-age", "PT31S"));

        assertThatCode(properties::validateKeyLifecycle).doesNotThrowAnyException();
    }

    @Test
    void zeroMaxKeyAgePassesTheKeyLifecycleCheck() {
        var properties = propertiesWith(Map.of("xroad.proxy.agreement-token.max-key-age", "PT0S"));

        assertThatCode(properties::validateKeyLifecycle).doesNotThrowAnyException();
    }

    private static ProxyAgreementTokenProperties propertiesWith(Map<String, String> overrides) {
        var config = XRoadConfigBuilder.create().register(ProxyConfigKeys.instance())
                .overrides(overrides)
                .build();
        return new ProxyAgreementTokenProperties(config);
    }
}
