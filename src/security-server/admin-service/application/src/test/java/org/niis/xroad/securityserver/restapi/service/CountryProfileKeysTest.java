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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.impl.CountryProfileLoader;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyDto;
import org.niis.xroad.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.restapi.service.ConfigurablePropertiesService;
import org.niis.xroad.restapi.service.ConfigurablePropertySource.CountryProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A key in a bundled country profile that is not a catalogue key would be ignored at runtime, so every
 * profile key must be declared in the Security Server configurable-properties catalogue.
 */
@ExtendWith(MockitoExtension.class)
class CountryProfileKeysTest {

    @Mock
    private ConfigurationPropertyRepository repository;

    @Mock
    private AuditDataHelper auditDataHelper;

    private Set<String> catalogueKeys;

    @BeforeEach
    void setup() {
        when(repository.findAll()).thenReturn(List.of());
        var service = new ConfigurablePropertiesService(auditDataHelper, repository,
                new SsConfigurablePropertySource(DeploymentMode.NATIVE, CountryProfile.NONE));
        catalogueKeys = service.getConfigurationProperties().stream()
                .map(ConfigurablePropertyDto::getPropertyName)
                .collect(Collectors.toSet());
    }

    @ParameterizedTest
    @MethodSource("profileEnvironments")
    void everyProfileKeyIsACatalogueKey(Map<String, String> env) {
        var keys = CountryProfileLoader.resolve(env)
                .orElseThrow().values().keySet();

        assertThat(keys).isNotEmpty().isSubsetOf(catalogueKeys);
    }

    @ParameterizedTest
    @MethodSource("profileEnvironments")
    void everyProfileValueIsAcceptedByItsKeyConverterAndValidator(Map<String, String> env) {
        var values = CountryProfileLoader.resolve(env)
                .orElseThrow().values();
        var keys = new SsConfigurablePropertySource(DeploymentMode.NATIVE, CountryProfile.NONE)
                .getConfigKeyProviders().stream()
                .flatMap(provider -> provider.keys().stream())
                .collect(Collectors.toMap(ConfigKey::key, key -> key, (first, _) -> first));

        values.forEach((name, raw) -> {
            var key = keys.get(name);
            if (key != null) {
                assertThat(validationMessage(key, raw)).as("%s=%s in %s", name, raw, env).isNull();
            }
        });
    }

    private static <T> String validationMessage(ConfigKey<T> key, String raw) {
        var result = key.validate(key.convert(raw));
        return result.valid() ? null : result.message();
    }

    private static List<Map<String, String>> profileEnvironments() {
        return CountryProfileLoader.SUPPORTED_COUNTRIES.stream()
                .flatMap(code -> Stream.of(
                        Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, code),
                        Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, code,
                                CountryProfileLoader.VARIANT_ENV_VARIABLE, CountryProfileLoader.SIDECAR_VARIANT)))
                .toList();
    }
}
