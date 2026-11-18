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

package org.niis.xroad.common.properties.config.impl;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.Prefix;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.Validator;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XRoadConfigBuilderTest {

    private static final Map<String, String> FI_ENV = Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, "FI");

    private final Prefix scope = Prefix.of("xroad.test");
    private final ConfigKey<String> withContainerDefault = scope.string("with-container")
            .withDefaultValue("native").withContainerDefaultValue("container").build();
    private final ConfigKey<String> nativeOnly = scope.string("native-only")
            .withDefaultValue("native-only").build();
    private final ConfigKeyProvider provider = ConfigKeyProvider.forPrefix(scope);

    @Test
    void nativeModeResolvesRegularDefault() {
        var config = XRoadConfigBuilder.create().register(provider).build();

        assertThat(config.get(withContainerDefault).value()).isEqualTo("native");
        assertThat(config.get(nativeOnly).value()).isEqualTo("native-only");
    }

    @Test
    void containerModeResolvesContainerDefaultWhenDeclared() {
        var config = XRoadConfigBuilder.create().register(provider)
                .deploymentMode(DeploymentMode.CONTAINERIZED)
                .build();

        assertThat(config.get(withContainerDefault).value()).isEqualTo("container");
    }

    @Test
    void containerModeFallsBackToRegularDefaultWhenNoContainerDefault() {
        var config = XRoadConfigBuilder.create().register(provider)
                .deploymentMode(DeploymentMode.CONTAINERIZED)
                .build();

        assertThat(config.get(nativeOnly).value()).isEqualTo("native-only");
    }

    @Test
    void dbOverrideWinsOverContainerDefault() {
        var config = XRoadConfigBuilder.create().register(provider)
                .deploymentMode(DeploymentMode.CONTAINERIZED)
                .overrides(Map.of("xroad.test.with-container", "overridden"))
                .build();

        assertThat(config.get(withContainerDefault).value()).isEqualTo("overridden");
    }

    @Test
    void ignoredEnvVarsReportsEnvEntriesMatchingRegisteredKeys() {
        var builder = XRoadConfigBuilder.create().register(provider);

        var ignored = builder.ignoredEnvVars(Map.of(
                "XROAD_TEST_WITH_CONTAINER", "from-env",
                "XROAD_TEST_UNKNOWN_KEY", "not-registered",
                "XROAD_HOST", "not-a-dsl-key",
                "PATH", "/usr/bin"));

        assertThat(ignored).containsExactly("XROAD_TEST_WITH_CONTAINER");
    }

    @Test
    void createRegistersCommonConfigKeysWithoutExplicitRegistration() {
        var config = XRoadConfigBuilder.create().build();

        assertThat(config.value(CommonConfigKeys.VAULT_RETRY_BUDGET)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void ignoredEnvVarsEmptyWhenNoEnvEntryMatches() {
        var builder = XRoadConfigBuilder.create().register(provider);

        assertThat(builder.ignoredEnvVars(Map.of("XROAD_HOST", "cs", "HOME", "/root"))).isEmpty();
    }

    @Test
    void nestedGrandchildKeyBuildsFullPathRegistersOnRootAndResolves() {
        var root = Prefix.of("xroad.test");
        // a scope with a child, whose child has its own child
        var signerChannel = root.subPrefix("channel").subPrefix("signer");
        var hostKey = signerChannel.string("host").withDefaultValue("127.0.0.1").build();

        // the key path is the full root -> child -> grandchild path
        assertThat(hostKey.key()).isEqualTo("xroad.test.channel.signer.host");
        // and the key is tracked on the root scope's keys() list, not the intermediate scopes'
        assertThat(root.keys()).contains(hostKey);
        assertThat(signerChannel.keys()).isEmpty();

        // so a provider exposing the root scope registers the nested key and the builder resolves it
        ConfigKeyProvider nestedProvider = ConfigKeyProvider.forPrefix(root);
        var config = XRoadConfigBuilder.create().register(nestedProvider).build();
        assertThat(config.get(hostKey).value()).isEqualTo("127.0.0.1");

        // and a DB override keyed by the full nested path wins, proving the path is used for lookup
        var overridden = XRoadConfigBuilder.create().register(nestedProvider)
                .overrides(Map.of("xroad.test.channel.signer.host", "signer"))
                .build();
        assertThat(overridden.get(hostKey).value()).isEqualTo("signer");
    }

    @Test
    void countryProfileValueResolvesWithCountrySource() {
        var config = proxyBuilder().build(FI_ENV);

        var value = config.get(ProxyConfigKeys.SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS);
        assertThat(value.value()).isTrue();
        assertThat(value.source()).isEqualTo(Source.COUNTRY);
    }

    @Test
    void keyAbsentFromCountryProfileResolvesDefault() {
        var config = proxyBuilder().build(FI_ENV);

        var value = config.get(ProxyConfigKeys.CLIENT_PROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD);
        assertThat(value.value()).isEqualTo(3600);
        assertThat(value.source()).isEqualTo(Source.DEFAULT);
    }

    @Test
    void dbRowWinsOverCountryProfile() {
        var config = proxyBuilder()
                .overrides(Map.of("xroad.proxy.server.support-clients-pooled-connections", "false"))
                .build(FI_ENV);

        var value = config.get(ProxyConfigKeys.SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS);
        assertThat(value.value()).isFalse();
        assertThat(value.source()).isEqualTo(Source.DB);
    }

    @Test
    void unsetSelectorLeavesCountryLayerOut() {
        var config = proxyBuilder().build(Map.of());

        var value = config.get(ProxyConfigKeys.SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS);
        assertThat(value.value()).isFalse();
        assertThat(value.source()).isEqualTo(Source.DEFAULT);
    }

    @Test
    void unknownProfileNameFailsBuild() {
        var builder = proxyBuilder();
        assertThatThrownBy(() -> builder.build(Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, "XX")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'XX'")
                .hasMessageContaining("supported profiles");
    }

    @Test
    void profileValueRejectedByKeyValidatorFailsBuild() {
        var signer = Prefix.of("xroad.signer");
        signer.integer("key-length").withValidator(Validator.range(1, 2048)).withDefaultValue(2048).build();
        var builder = XRoadConfigBuilder.create().register(ConfigKeyProvider.forPrefix(signer));

        assertThatThrownBy(() -> builder.build(FI_ENV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xroad.signer.key-length")
                .hasMessageContaining("3072");
    }

    @Test
    void blankProfileNameLeavesCountryLayerOut() {
        var config = proxyBuilder().build(Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, "  "));
        var value = config.get(ProxyConfigKeys.SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS);
        assertThat(value.value()).isFalse();
        assertThat(value.source()).isEqualTo(Source.DEFAULT);
    }

    @Test
    void lowerCaseProfileNameSelectsProfile() {
        var config = proxyBuilder().build(Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, "fi"));
        assertThat(config.get(ProxyConfigKeys.SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS).source())
                .isEqualTo(Source.COUNTRY);
    }

    @Test
    void sidecarVariantDropsCountryConnectorHostAndKeepsOtherProfileKeys() {
        var config = proxyBuilder().build(eeEnv(CountryProfileLoader.SIDECAR_VARIANT));
        var connectorHost = config.get(ProxyConfigKeys.CLIENT_PROXY_CONNECTOR_HOST);
        assertThat(connectorHost.value()).isEqualTo("0.0.0.0");
        assertThat(connectorHost.source()).isEqualTo(Source.DEFAULT);
        assertThat(config.get(ProxyConfigKeys.SERVER_MIN_SUPPORTED_CLIENT_VERSION).source())
                .isEqualTo(Source.COUNTRY);
    }

    @Test
    void withoutVariantCountryConnectorHostApplies() {
        var config = proxyBuilder().build(eeEnv(null));
        var connectorHost = config.get(ProxyConfigKeys.CLIENT_PROXY_CONNECTOR_HOST);
        assertThat(connectorHost.value()).isEqualTo("127.0.0.1");
        assertThat(connectorHost.source()).isEqualTo(Source.COUNTRY);
    }

    @Test
    void dbRowWinsForConnectorHostInSidecarVariant() {
        var config = proxyBuilder()
                .overrides(Map.of("xroad.proxy.client-proxy.connector-host", "10.0.0.1"))
                .build(eeEnv(CountryProfileLoader.SIDECAR_VARIANT));
        var value = config.get(ProxyConfigKeys.CLIENT_PROXY_CONNECTOR_HOST);
        assertThat(value.value()).isEqualTo("10.0.0.1");
        assertThat(value.source()).isEqualTo(Source.DB);
    }

    @Test
    void overridesOfUnregisteredKeysAreIgnoredAndRegisteredKeysKeepTheirValues() {
        var config = XRoadConfigBuilder.create().register(provider)
                .overrides(Map.of(
                        "xroad.test.removed-key", "abc",
                        "xroad.test.removed-scope.nested", "",
                        "xroad.test.native-only", "set"))
                .build();

        assertThat(config.get(nativeOnly).value()).isEqualTo("set");
        assertThat(config.get(withContainerDefault).value()).isEqualTo("native");
    }

    private static Map<String, String> eeEnv(String variant) {
        return variant == null
                ? Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, "EE")
                : Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, "EE",
                CountryProfileLoader.VARIANT_ENV_VARIABLE, variant);
    }

    private static XRoadConfigBuilder proxyBuilder() {
        return XRoadConfigBuilder.create().register(ProxyConfigKeys.instance());
    }
}
