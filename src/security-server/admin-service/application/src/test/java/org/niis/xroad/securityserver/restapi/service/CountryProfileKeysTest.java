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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.properties.config.ActiveCountryProfile;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.impl.CountryProfileLoader;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A key in a bundled country profile that is not an exposed catalogue key would be ignored at runtime or could not
 * be edited, and a key published to a framework would reach the process outside the resolver, so every profile
 * key must be a registered, exposed key that no framework publishes.
 */
class CountryProfileKeysTest {

    private static final Map<String, ConfigKey<?>> REGISTERED_KEYS =
            new SsConfigurablePropertySource(DeploymentMode.NATIVE, ActiveCountryProfile.NONE)
                    .getConfigKeyProviders().stream()
                    .flatMap(provider -> provider.keys().stream())
                    .collect(Collectors.toMap(ConfigKey::key, key -> key, (first, _) -> first));

    @ParameterizedTest
    @MethodSource("profileEnvironments")
    void everyProfileKeyIsARegisteredExposedKey(Map<String, String> env) {
        var keys = profileValues(env).keySet();

        assertThat(keys).isNotEmpty().isSubsetOf(REGISTERED_KEYS.keySet());
        assertThat(keys).allSatisfy(name ->
                assertThat(REGISTERED_KEYS.get(name).exposedInUi()).as("%s is exposed in the UI", name).isTrue());
    }

    @ParameterizedTest
    @MethodSource("profileEnvironments")
    void noProfileKeyIsPublishedToFramework(Map<String, String> env) {
        assertThat(profileValues(env).keySet()).allSatisfy(name ->
                assertThat(REGISTERED_KEYS.get(name).publishedToFramework())
                        .as("%s is published to a framework", name).isFalse());
    }

    @ParameterizedTest
    @MethodSource("profileEnvironments")
    void everyProfileValueIsAcceptedByItsKeyConverterAndValidator(Map<String, String> env) {
        profileValues(env).forEach((name, raw) -> {
            var key = REGISTERED_KEYS.get(name);
            assertThat(key).as("%s is registered", name).isNotNull();
            assertThat(validationMessage(key, raw)).as("%s=%s in %s", name, raw, env).isNull();
        });
    }

    private static Map<String, String> profileValues(Map<String, String> env) {
        return CountryProfileLoader.resolve(env).orElseThrow().values();
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
