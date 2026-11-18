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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CountryProfileLoaderTest {

    private static final String PROFILE_DIRECTORY = "xroad-config/country-profile/";
    private static final String INVALID_DIRECTORY = "country-profile-invalid/";
    private static final String SIDECAR_SUFFIX = "-sidecar.yaml";
    private static final String CONNECTOR_HOST_KEY = "xroad.proxy.client-proxy.connector-host";
    private static final String MIN_CLIENT_VERSION_KEY = "xroad.proxy.server.min-supported-client-version";

    @Test
    void listsRenderAsCommaSeparatedStrings() {
        var values = resolve("FI", null);

        assertThat(values).containsEntry("xroad.proxy.client-proxy.client-tls-protocols", "TLSv1.2");
        assertThat(values.get("xroad.proxy.client-proxy.client-tls-ciphers"))
                .startsWith("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,")
                .endsWith(",TLS_DHE_RSA_WITH_AES_256_CBC_SHA256")
                .doesNotContain("[", " ");
    }

    @Test
    void nestedKeysFlattenToDottedDslKeys() {
        var values = resolve("FI", null);

        assertThat(values)
                .containsEntry("xroad.signer.key-length", "3072")
                .containsKey("xroad.proxy.server.min-supported-client-version")
                .containsEntry("xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period", "172800")
                .containsEntry("xroad.proxy-ui-api.acme-challenge-port-enabled", "true")
                .doesNotContainKey("xroad")
                .allSatisfy((key, _) -> assertThat(key).startsWith("xroad."));
    }

    @Test
    void metadataBlockIsNotLoaded() {
        assertThat(resolve("FI", null).keySet()).noneMatch(key -> key.startsWith("metadata"));
    }

    @Test
    void resolveReturnsNormalisedCodeForLowerCaseSelectorWithSurroundingBlanks() {
        var profile = CountryProfileLoader.resolve(Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, " fi ")).orElseThrow();

        assertThat(profile.code()).isEqualTo("FI");
        assertThat(profile.values()).containsEntry("xroad.signer.key-length", "3072");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankSelectorSelectsNoProfile(String selector) {
        assertThat(CountryProfileLoader.resolve(Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, selector))).isEmpty();
        assertThat(CountryProfileLoader.resolve(Map.of())).isEmpty();
    }

    @Test
    void sidecarVariantLoadsSidecarFileWithoutConnectorHostAndKeepsCode() {
        var profile = CountryProfileLoader.resolve(env("EE", CountryProfileLoader.SIDECAR_VARIANT)).orElseThrow();

        assertThat(profile.code()).isEqualTo("EE");
        assertThat(profile.values())
                .doesNotContainKey(CONNECTOR_HOST_KEY)
                .containsEntry("xroad.proxy.client-proxy.client-http-port", "80")
                .containsEntry("xroad.proxy.client-proxy.client-https-port", "443");
    }

    @Test
    void sidecarVariantWithoutSidecarFileLoadsBaseFile() {
        assertThat(resolve("FI", CountryProfileLoader.SIDECAR_VARIANT)).isEqualTo(resolve("FI", null));
    }

    @Test
    void withoutVariantNativeFileIsLoaded() {
        assertThat(resolve("EE", null)).containsEntry(CONNECTOR_HOST_KEY, "127.0.0.1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankVariantBehavesAsUnset(String variant) {
        assertThat(resolve("EE", variant)).isEqualTo(resolve("EE", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"native", "../x", "sidecar/../x", "sidecar.yaml"})
    void unknownVariantIsRejected(String variant) {
        var env = env("EE", variant);

        assertThatThrownBy(() -> CountryProfileLoader.resolve(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown deployment variant")
                .hasMessageContaining(CountryProfileLoader.VARIANT_ENV_VARIABLE);
    }

    @Test
    void variantWithoutSelectorSelectsNothing() {
        var env = Map.of(CountryProfileLoader.VARIANT_ENV_VARIABLE, CountryProfileLoader.SIDECAR_VARIANT);

        assertThat(CountryProfileLoader.resolve(env)).isEmpty();
    }

    @Test
    void unknownProfileListsSupportedCodes() {
        var env = Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, "XX");

        assertThatThrownBy(() -> CountryProfileLoader.resolve(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'XX'")
                .hasMessageContaining("supported profiles: " + String.join(", ", CountryProfileLoader.SUPPORTED_COUNTRIES));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../x", "FI/../EE", "FI.yaml", "xroad-config/country-profile/FI"})
    void selectorWithPathCharactersIsRejectedAsUnknown(String selector) {
        var env = Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, selector);

        assertThatThrownBy(() -> CountryProfileLoader.resolve(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown country profile");
    }

    @ParameterizedTest
    @MethodSource("supportedCountries")
    void everySupportedCountryHasBundledYaml(String code) throws IOException {
        try (var in = resourceStream(PROFILE_DIRECTORY + code + ".yaml")) {
            assertThat(in).isNotNull();
        }
        assertThat(resolve(code, null)).isNotEmpty();
    }

    @Test
    void everyBundledYamlHasSupportedCountry() throws IOException, URISyntaxException {
        var directory = CountryProfileLoaderTest.class.getClassLoader().getResource(PROFILE_DIRECTORY);
        assertThat(directory).isNotNull();

        var uri = directory.toURI();
        if ("jar".equals(uri.getScheme())) {
            try (var jar = FileSystems.newFileSystem(uri, Map.of())) {
                assertBundledFilesAreConsistent(jar.getPath(PROFILE_DIRECTORY));
            }
        } else {
            assertBundledFilesAreConsistent(Path.of(uri));
        }
    }

    private static void assertBundledFilesAreConsistent(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            var names = files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".yaml"))
                    .toList();
            var baseCodes = names.stream()
                    .filter(name -> !name.endsWith(SIDECAR_SUFFIX))
                    .map(name -> name.substring(0, name.length() - ".yaml".length()))
                    .toList();
            var sidecarCodes = names.stream()
                    .filter(name -> name.endsWith(SIDECAR_SUFFIX))
                    .map(name -> name.substring(0, name.length() - SIDECAR_SUFFIX.length()))
                    .toList();

            assertThat(baseCodes).containsExactlyInAnyOrderElementsOf(CountryProfileLoader.SUPPORTED_COUNTRIES);
            assertThat(sidecarCodes).isNotEmpty().isSubsetOf(baseCodes);
            sidecarCodes.forEach(code -> assertThat(resolve(code, CountryProfileLoader.SIDECAR_VARIANT).get(MIN_CLIENT_VERSION_KEY))
                    .isEqualTo(resolve(code, null).get(MIN_CLIENT_VERSION_KEY)));
        }
    }

    @Test
    void malformedYamlFailsNamingTheProfile() throws IOException {
        try (var in = resourceStream(INVALID_DIRECTORY + "malformed.yaml")) {
            assertThatThrownBy(() -> CountryProfileLoader.parse("ZZ", in))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ZZ");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"list-of-objects", "nested-list", "null-value", "null-list-item"})
    void containerInListOrNullValueFailsNamingProfileAndKey(String file) throws IOException {
        try (var in = resourceStream(INVALID_DIRECTORY + file + ".yaml")) {
            assertThatThrownBy(() -> CountryProfileLoader.parse("ZZ", in))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ZZ")
                    .hasMessageContaining("xroad.test.");
        }
    }

    static List<String> supportedCountries() {
        return CountryProfileLoader.SUPPORTED_COUNTRIES;
    }

    private static InputStream resourceStream(String name) {
        return CountryProfileLoaderTest.class.getClassLoader().getResourceAsStream(name);
    }

    private static Map<String, String> resolve(String code, String variant) {
        return CountryProfileLoader.resolve(env(code, variant)).orElseThrow().values();
    }

    private static Map<String, String> env(String code, String variant) {
        return variant == null
                ? Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, code)
                : Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, code,
                CountryProfileLoader.VARIANT_ENV_VARIABLE, variant);
    }
}
