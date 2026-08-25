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
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerConfigurablePropertyDto;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;

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
 * monitoring, message log archiver and message log encryption keys reach the Security Server
 * catalogue from their own modules, with the scope names the {@code configuration_properties}
 * table and the UI expect.
 */
@ExtendWith(MockitoExtension.class)
class ConfigurablePropertiesCatalogueTest {

    /**
     * key | scope | default — the keys that were reachable only through {@code configurable-properties.yaml}
     * before the registries moved into shared modules.
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
            """;

    /** {@code application.name} of every Security Server process whose keys the UI edits. */
    private static final Set<String> TARGET_SCOPES = Set.of(
            "proxy", "proxy-ui-api", "signer", "monitor", "op-monitor-daemon",
            "configuration-client", "auxiliary-service", "message-log-archiver");

    @Mock
    private ConfigurationPropertyRepository repository;

    @Mock
    private AuditDataHelper auditDataHelper;

    private ConfigurablePropertiesService service;

    @BeforeEach
    void setup() {
        service = new ConfigurablePropertiesService(repository, auditDataHelper);
        when(repository.findAll()).thenReturn(List.of());
    }

    @Test
    void catalogueIncludesCrossModuleKeysWithExpectedScopeAndDefault() {
        var actual = service.getConfigurationProperties().stream()
                .collect(Collectors.toMap(SecurityServerConfigurablePropertyDto::getPropertyName, dto -> dto,
                        (first, second) -> first));

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
                .map(SecurityServerConfigurablePropertyDto::getScope)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(scopes).isEqualTo(TARGET_SCOPES);
    }

    @Test
    void catalogueOffersAcmeWaitKeysUnderTheNamesTheCodeReads() {
        var keys = service.getConfigurationProperties().stream()
                .map(SecurityServerConfigurablePropertyDto::getPropertyName)
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
    void catalogueOmitsDocumentValuedKeys() {
        var keys = service.getConfigurationProperties().stream()
                .map(SecurityServerConfigurablePropertyDto::getPropertyName)
                .collect(Collectors.toSet());

        assertThat(keys)
                .isNotEmpty()
                .doesNotContain(
                        "xroad.signer.modules",
                        "xroad.message-log-encryption.archive.grouping-keys");
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
}
