/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;
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
 * Guards the cross-module part of the system-parameters catalogue: the common global-conf,
 * OCSP-verifier, common-RPC and co-located signer keys reach the Central Server catalogue from
 * their own modules, with the scope names the {@code configuration_properties} table and the UI
 * expect.
 */
@ExtendWith(MockitoExtension.class)
class CsConfigurablePropertiesCatalogueTest {

    /**
     * key | scope | default — a representative exposed key from each provider registered in
     * {@link CsConfigurablePropertySource#getConfigKeyProviders()}.
     */
    private static final String CROSS_MODULE_KEYS = """
            xroad.common-global-conf.source||FILESYSTEM
            xroad.common-global-conf.refresh-rate||PT1M
            xroad.common-global-conf.configuration-path||/etc/xroad/globalconf
            xroad.common-ocsp-verifier.cache-period||60
            xroad.common-rpc.use-tls||true
            xroad.common-rpc.certificate-provisioning.refresh-interval||PT5H
            xroad.signer.selfsigned-cert-digest-algorithm|signer|SHA-512
            xroad.signer.key-length|signer|2048
            xroad.signer.key-named-curve|signer|secp256r1
            """;

    /** Scope names the {@code configuration_properties} table and the UI restart-warning expect. */
    private static final Set<String> TARGET_SCOPES = Set.of(
            "admin-service", "management-service", "registration-service", "signer");

    @Mock
    private ConfigurationPropertyRepository repository;

    @Mock
    private AuditDataHelper auditDataHelper;

    private ConfigurablePropertiesService service;

    @BeforeEach
    void setup() {
        service = new ConfigurablePropertiesService(auditDataHelper, repository, new CsConfigurablePropertySource());
        when(repository.findAll()).thenReturn(List.of());
    }

    @Test
    void catalogueIncludesCrossModuleKeysWithExpectedScopeAndDefault() {
        var actual = service.getConfigurationProperties().stream()
                .collect(Collectors.toMap(ConfigurablePropertyDto::getPropertyName, dto -> dto,
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
    void catalogueScopesAreLimitedToCentralServerProcessNames() {
        var scopes = service.getConfigurationProperties().stream()
                .map(ConfigurablePropertyDto::getScope)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(scopes).isEqualTo(TARGET_SCOPES);
    }

    @Test
    void catalogueOmitsDataspaceKeysNotYetExposedInUi() {
        var keys = service.getConfigurationProperties().stream()
                .map(ConfigurablePropertyDto::getPropertyName)
                .collect(Collectors.toSet());

        assertThat(keys).noneMatch(key -> key.startsWith("xroad.dataspace."));
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
