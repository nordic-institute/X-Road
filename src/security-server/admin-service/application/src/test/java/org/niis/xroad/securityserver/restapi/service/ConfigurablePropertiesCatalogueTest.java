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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.config.ActiveCountryProfile;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.impl.CountryProfileLoader;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.entity.ConfigurationPropertyEntity;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyRestartPolicy;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyValueSource;
import org.niis.xroad.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.restapi.service.ConfigurablePropertiesService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Guards the cross-module part of the system-parameters catalogue: the signer, environmental
 * monitoring, message log archiver, message log encryption and dataspace issuer data keys reach the
 * Security Server catalogue from their own modules, with the scope names the {@code configuration_properties}
 * table and the UI expect. Also covers the effective value and source reported for the active country
 * profile and the deployment mode.
 */
@ExtendWith(MockitoExtension.class)
class ConfigurablePropertiesCatalogueTest {

    /**
     * key | scope | default — the keys that reach the catalogue from registries in shared modules: the signer,
     * monitoring, archiver and message log encryption keys, which were once reachable only through
     * {@code configurable-properties.yaml}, and the dataspace issuer data keys.
     * Duration defaults render in ISO-8601 form ({@code PT1M}), not the yaml's {@code 60s}.
     */
    private static final String CROSS_MODULE_KEYS = """
            xroad.signer.selfsigned-cert-digest-algorithm|signer|SHA-512
            xroad.signer.csr-signature-digest-algorithm|signer|SHA-256
            xroad.signer.enforce-token-pin-policy|signer|false
            xroad.signer.ocsp-response-retrieval-active|signer|false
            xroad.signer.ocsp-retry-delay|signer|60
            xroad.signer.ocsp-cache-path|signer|/var/cache/xroad/
            xroad.signer.ocsp-prioritization-strategy|signer|NONE
            xroad.signer.module-manager-update-interval|signer|60
            xroad.signer.soft-token-rsa-sign-mechanism|signer|CKM_RSA_PKCS
            xroad.signer.soft-token-ec-sign-mechanism|signer|CKM_ECDSA
            xroad.signer.soft-token-pin-keystore-algorithm|signer|RSA
            xroad.signer.key-length|signer|2048
            xroad.signer.key-named-curve|signer|secp256r1
            xroad.signer.autologin.enabled|signer|false
            xroad.signer.autologin.retry.retry-delay|signer|PT3S
            xroad.signer.autologin.retry.retry-exponential-backoff-multiplier|signer|1.0
            xroad.signer.autologin.retry.retry-max-attempts|signer|20
            xroad.signer.autologin.retry.retry-timeout|signer|PT1M
            xroad.signer.addon.hwtoken.enabled|signer|false
            xroad.signer.addon.hwtoken.session-pool-enabled|signer|false
            xroad.signer.addon.hwtoken.session-pool-max-total|signer|10
            xroad.signer.addon.hwtoken.session-pool-min-idle|signer|2
            xroad.signer.addon.hwtoken.session-pool-max-idle|signer|5
            xroad.signer.addon.hwtoken.session-acquire-timeout|signer|PT15S
            xroad.signer.rpc.enabled|signer|true
            xroad.signer.rpc.listen-address|signer|127.0.0.1
            xroad.signer.rpc.port|signer|5560
            xroad.signer.pin-hasher.iterations|signer|4
            xroad.signer.pin-hasher.memory-kb|signer|19456
            xroad.signer.pin-hasher.parallelism|signer|4
            xroad.signer.pin-hasher.hash-length|signer|32
            xroad.signer.pin-hasher.salt-length|signer|16
            xroad.env-monitor.certificate-info-sensor-interval|monitor|PT24H
            xroad.env-monitor.disk-space-sensor-interval|monitor|PT1M
            xroad.env-monitor.exec-listing-sensor-interval|monitor|PT1M
            xroad.env-monitor.system-metrics-sensor-interval|monitor|PT5S
            xroad.env-monitor.limit-remote-data-set|monitor|false
            xroad.env-monitor.rpc.enabled|monitor|true
            xroad.env-monitor.rpc.listen-address|monitor|127.0.0.1
            xroad.env-monitor.rpc.port|monitor|2552
            xroad.message-log-archiver.clean-transaction-batch-size|message-log-archiver|10000
            xroad.message-log-archiver.clean-keep-records-for|message-log-archiver|30
            xroad.message-log-archiver.max-filesize|message-log-archiver|33554432
            xroad.message-log-archiver.transaction-batch-size|message-log-archiver|10000
            xroad.message-log-archiver.archive-path|message-log-archiver|/var/lib/xroad
            xroad.message-log-archiver.archive-transfer-command|message-log-archiver|
            xroad.message-log-archiver.hash-algo-id|message-log-archiver|SHA-512
            xroad.message-log-encryption.archive.encryption-enabled||false
            xroad.message-log-encryption.archive.default-key-id||
            xroad.message-log-encryption.archive.grouping-strategy||NONE
            xroad.message-log-encryption.db.encryption-enabled||false
            xroad.message-log-encryption.db.key-id||default
            xroad.dsp.issuer-data.download-wait||PT2S
            xroad.dsp.issuer-data.download-retry-seconds||5,10,15,20,30,60
            xroad.dsp.issuer-data.download-expire-after||PT10M
            xroad.dsp.issuer-data.download-timeout||PT30S
            xroad.dsp.issuer-data.cache.statuslist.size||1000
            xroad.dsp.issuer-data.cache.statuslist-download.size||10000
            xroad.dsp.issuer-data.cache.did-download.size||10000
            xroad.dsp.issuer-data.cleanup.interval||PT10M
            xroad.dsp.issuer-data.cleanup.enabled||true
            """;

    /** {@code application.name} of every Security Server process whose keys the UI edits. */
    private static final Set<String> TARGET_SCOPES = Set.of(
            "proxy", "proxy-ui-api", "signer", "monitor", "op-monitor-daemon",
            "configuration-client", "auxiliary-service", "message-log-archiver");

    private static final String COMMON_SCOPE = "common";

    private static final String CLIENT_HTTPS_PORT = "xroad.proxy.client-proxy.client-https-port";

    @Mock
    private ConfigurationPropertyRepository repository;

    @Mock
    private AuditDataHelper auditDataHelper;

    private ConfigurablePropertiesService service;

    @BeforeEach
    void setup() {
        service = new ConfigurablePropertiesService(auditDataHelper, repository,
                new SsConfigurablePropertySource(DeploymentMode.NATIVE, ActiveCountryProfile.NONE));
        when(repository.findAll()).thenReturn(List.of());
    }

    @Test
    void catalogueIncludesCrossModuleKeysWithExpectedScopeAndDefault() {
        var actual = service.getConfigurationProperties().stream()
                .collect(Collectors.toMap(ConfigurablePropertyDto::getPropertyName, dto -> dto,
                        (first, _) -> first));

        assertThat(expectedCrossModuleProperties())
                .allSatisfy((key, expected) -> {
                    var dto = actual.get(key);
                    assertThat(dto).as("%s missing from the catalogue", key).isNotNull();
                    assertThat(dto.getScope()).as("scope of %s", key).isEqualTo(expected.scope());
                    assertThat(dto.getDefaultValue()).as("default of %s", key).isEqualTo(expected.defaultValue());
                });
    }

    @Test
    void catalogueScopesAreLimitedToSecurityServerProcessNames() {
        var scopes = service.getConfigurationProperties().stream()
                .map(ConfigurablePropertyDto::getScope)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(scopes).isEqualTo(TARGET_SCOPES);
    }

    @Test
    void everyExposedPropertyResolvesToItsNativeRestartTarget() {
        assertRestartTargets(service, Map.of(
                "proxy", services("xroad-proxy"),
                "signer", services("xroad-signer"),
                "proxy-ui-api", services("xroad-proxy-ui-api"),
                "op-monitor-daemon", services("xroad-opmonitor"),
                "monitor", services("xroad-monitor"),
                "configuration-client", services("xroad-confclient"),
                "auxiliary-service", services("xroad-auxiliary-service"),
                "message-log-archiver", new Restart(ConfigurablePropertyRestartPolicy.NEXT_RUN, List.of()),
                COMMON_SCOPE, new Restart(ConfigurablePropertyRestartPolicy.ALL_SERVICES, List.of())),
                services("xroad-proxy", "xroad-proxy-ui-api"));
    }

    @Test
    void everyExposedPropertyResolvesToItsHelmDeploymentNameInContainerizedMode() {
        var containerizedService = new ConfigurablePropertiesService(auditDataHelper, repository,
                new SsConfigurablePropertySource(DeploymentMode.CONTAINERIZED, ActiveCountryProfile.NONE));

        assertRestartTargets(containerizedService, Map.of(
                "proxy", services("proxy"),
                "signer", services("signer"),
                "proxy-ui-api", services("proxy-ui-api"),
                "op-monitor-daemon", services("op-monitor"),
                "monitor", services("monitor"),
                "configuration-client", services("configuration-client"),
                "auxiliary-service", services("auxiliary-service"),
                "message-log-archiver", new Restart(ConfigurablePropertyRestartPolicy.NEXT_RUN, List.of()),
                COMMON_SCOPE, new Restart(ConfigurablePropertyRestartPolicy.ALL_SERVICES, List.of())),
                services("proxy", "proxy-ui-api"));
    }

    @Test
    void everyExposedPropertyHasRestartPolicyConsistentWithItsServiceNames() {
        var properties = service.getConfigurationProperties();

        assertThat(properties).isNotEmpty().allSatisfy(dto -> {
            assertThat(dto.getRestartPolicy()).as("policy of %s", dto.getPropertyName()).isNotNull();
            if (dto.getRestartPolicy() == ConfigurablePropertyRestartPolicy.SERVICES) {
                assertThat(dto.getRestartServices()).as("services of %s", dto.getPropertyName()).isNotEmpty();
            } else {
                assertThat(dto.getRestartServices()).as("services of %s", dto.getPropertyName()).isEmpty();
            }
        });
    }

    @Test
    void catalogueOffersAcmeWaitKeysUnderTheNamesTheCodeReads() {
        var keys = service.getConfigurationProperties().stream()
                .map(ConfigurablePropertyDto::getPropertyName)
                .collect(Collectors.toSet());

        assertThat(keys)
                .contains(
                        "xroad.proxy-ui-api.acme-certificate-wait-attempts",
                        "xroad.proxy-ui-api.acme-certificate-wait-interval")
                .doesNotContain(
                        "xroad.proxy-ui-api.acme-certification-wait-attempts",
                        "xroad.proxy-ui-api.acme-certification-wait-interval");
    }

    @Test
    void catalogueOffersLegacyProtocolDisabledInProxyScopeDefaultingToFalse() {
        var properties = service.getConfigurationProperties();

        assertThat(properties)
                .filteredOn(dto -> "xroad.proxy.legacy-protocol-disabled".equals(dto.getPropertyName()))
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getScope()).isEqualTo("proxy");
                    assertThat(dto.getDefaultValue()).isEqualTo("false");
                });
    }

    @Test
    void catalogueOmitsDocumentValuedKeys() {
        var keys = service.getConfigurationProperties().stream()
                .map(ConfigurablePropertyDto::getPropertyName)
                .collect(Collectors.toSet());

        assertThat(keys)
                .isNotEmpty()
                .doesNotContain(
                        "xroad.signer.modules",
                        "xroad.message-log-encryption.archive.grouping-keys");
    }

    @Test
    void storedOpMonitorRpcPortIsListedInCatalogue() {
        when(repository.findAll()).thenReturn(List.of(row()));

        var properties = service.getConfigurationProperties();

        assertThat(properties).filteredOn(dto -> "xroad.op-monitor.rpc.port".equals(dto.getPropertyName()))
                .singleElement()
                .satisfies(dto -> assertThat(dto.getCurrentValue()).isEqualTo("2091"));
    }

    @Test
    void allowedClientNamesIsListedUnderOpMonitorScopeWithEmptyDefault() {
        var properties = service.getConfigurationProperties();

        assertThat(properties)
                .filteredOn(dto -> "xroad.op-monitor.rpc.allowed-client-names".equals(dto.getPropertyName()))
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getScope()).isEqualTo("op-monitor-daemon");
                    assertThat(dto.getDefaultValue()).isEmpty();
                });
    }

    @Test
    void keyWithoutProfileValueReportsDefaultSourceWhileProfileIsActive() {
        var dto = propertiesWithProfile("fi").get("xroad.signer.rpc.port");

        assertThat(dto.getEffectiveValue()).isEqualTo("5560");
        assertThat(dto.getValueSource()).isEqualTo(ConfigurablePropertyValueSource.DEFAULT);
        assertThat(dto.getCountryProfile()).isNull();
        assertThat(dto.getCurrentValue()).isNull();
    }

    @Test
    void keyFromActiveProfileReportsCountryProfileSourceWithCode() {
        var dto = propertiesWithProfile("fi").get("xroad.signer.key-length");

        assertThat(dto.getEffectiveValue()).isEqualTo("3072");
        assertThat(dto.getValueSource()).isEqualTo(ConfigurablePropertyValueSource.COUNTRY_PROFILE);
        assertThat(dto.getCountryProfile()).isEqualTo("FI");
        assertThat(dto.getDefaultValue()).isEqualTo("2048");
        assertThat(dto.getCurrentValue()).isNull();
    }

    @Test
    void databaseRowOverProfileKeyReportsDbSourceAndDatabaseValue() {
        when(repository.findAll()).thenReturn(List.of(row("xroad.signer.key-length", "4096")));

        var dto = propertiesWithProfile("FI").get("xroad.signer.key-length");

        assertThat(dto.getEffectiveValue()).isEqualTo("4096");
        assertThat(dto.getValueSource()).isEqualTo(ConfigurablePropertyValueSource.DB);
        assertThat(dto.getCountryProfile()).isNull();
        assertThat(dto.getCurrentValue()).isEqualTo("4096");
    }

    @Test
    void withoutProfileEveryKeyReportsDefaultSource() {
        var dto = service.getConfigurationProperties().stream()
                .filter(property -> "xroad.signer.key-length".equals(property.getPropertyName()))
                .findFirst().orElseThrow();

        assertThat(dto.getEffectiveValue()).isEqualTo("2048");
        assertThat(dto.getValueSource()).isEqualTo(ConfigurablePropertyValueSource.DEFAULT);
        assertThat(dto.getCountryProfile()).isNull();
    }

    @Test
    void sidecarVariantReportsSidecarProfileValues() {
        var properties = propertiesWith(DeploymentMode.NATIVE, Map.of(
                CountryProfileLoader.PROFILE_ENV_VARIABLE, "EE",
                CountryProfileLoader.VARIANT_ENV_VARIABLE, CountryProfileLoader.SIDECAR_VARIANT));

        var connectorHost = properties.get("xroad.proxy.client-proxy.connector-host");
        assertThat(connectorHost.getEffectiveValue()).isEqualTo("0.0.0.0");
        assertThat(connectorHost.getValueSource()).isEqualTo(ConfigurablePropertyValueSource.DEFAULT);
        assertThat(connectorHost.getCountryProfile()).isNull();
        var keyLength = properties.get("xroad.signer.key-length");
        assertThat(keyLength.getEffectiveValue()).isEqualTo("3072");
        assertThat(keyLength.getValueSource()).isEqualTo(ConfigurablePropertyValueSource.COUNTRY_PROFILE);
        assertThat(keyLength.getCountryProfile()).isEqualTo("EE");
    }

    @Test
    void containerizedModeReportsContainerDefaultForKeyThatDeclaresOne() {
        var dto = propertiesWith(DeploymentMode.CONTAINERIZED, Map.of())
                .get("xroad.proxy-ui-api.authentication-provider");

        assertThat(dto.getEffectiveValue()).isEqualTo("DATABASE");
        assertThat(dto.getValueSource()).isEqualTo(ConfigurablePropertyValueSource.DEFAULT);
        assertThat(dto.getDefaultValue()).isEqualTo("DATABASE");
    }

    @Test
    void nativeModeReportsNativeDefaultForKeyThatDeclaresContainerDefault() {
        var dto = propertiesWith(DeploymentMode.NATIVE, Map.of())
                .get("xroad.proxy-ui-api.authentication-provider");

        assertThat(dto.getEffectiveValue()).isEqualTo("PAM");
        assertThat(dto.getValueSource()).isEqualTo(ConfigurablePropertyValueSource.DEFAULT);
    }

    private static void assertRestartTargets(ConfigurablePropertiesService propertiesService,
                                             Map<String, Restart> restartByScope, Restart clientHttpsPortRestart) {
        assertThat(propertiesService.getConfigurationProperties()).isNotEmpty().allSatisfy(dto -> {
            var expected = CLIENT_HTTPS_PORT.equals(dto.getPropertyName())
                    ? clientHttpsPortRestart
                    : restartByScope.get(dto.getScope() == null ? COMMON_SCOPE : dto.getScope());
            assertThat(new Restart(dto.getRestartPolicy(), List.copyOf(dto.getRestartServices())))
                    .as("restart target of %s", dto.getPropertyName())
                    .isEqualTo(expected);
        });
    }

    private static Restart services(String... names) {
        return new Restart(ConfigurablePropertyRestartPolicy.SERVICES, List.of(names));
    }

    private Map<String, ConfigurablePropertyDto> propertiesWithProfile(String profileName) {
        return propertiesWith(DeploymentMode.NATIVE,
                Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, profileName));
    }

    private Map<String, ConfigurablePropertyDto> propertiesWith(DeploymentMode mode, Map<String, String> env) {
        var countryProfile = CountryProfileLoader.resolve(env)
                .orElse(ActiveCountryProfile.NONE);
        var profileService = new ConfigurablePropertiesService(auditDataHelper, repository,
                new SsConfigurablePropertySource(mode, countryProfile));
        return profileService.getConfigurationProperties().stream()
                .collect(Collectors.toMap(ConfigurablePropertyDto::getPropertyName, dto -> dto,
                        (first, _) -> first));
    }

    private static ConfigurationPropertyEntity row() {
        return row("xroad.op-monitor.rpc.port", "2091");
    }

    private static ConfigurationPropertyEntity row(String key, String value) {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(key);
        entity.setPropertyValue(value);
        return entity;
    }

    private static Map<String, ExpectedProperty> expectedCrossModuleProperties() {
        return Arrays.stream(CROSS_MODULE_KEYS.split("\n"))
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\\|", -1))
                .collect(Collectors.toMap(
                        parts -> parts[0],
                        parts -> new ExpectedProperty(
                                parts[1].isEmpty() ? null : parts[1],
                                parts[2].isEmpty() ? null : parts[2])));
    }

    private record ExpectedProperty(String scope, String defaultValue) {
    }

    private record Restart(ConfigurablePropertyRestartPolicy policy, List<String> services) {
    }
}
