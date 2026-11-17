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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

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

    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(CountryProfileLoader.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logAppender);
    }

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
    void excludeListRemovesNamedKeysAndTrimsEntries() {
        var all = resolve("FI", null);

        var values = resolve("FI", " xroad.signer.key-length , xroad.proxy-ui-api.acme-challenge-port-enabled,, ");

        assertThat(values).hasSize(all.size() - 2)
                .doesNotContainKeys("xroad.signer.key-length", "xroad.proxy-ui-api.acme-challenge-port-enabled");
        assertThat(warnings()).isEmpty();
    }

    @Test
    void unmatchedExcludeEntryLogsOneWarningNamingEntryAndProfile() {
        var all = resolve("FI", null);

        var values = resolve("FI", "xroad.signer.key-length, not.a.key");

        assertThat(values).hasSize(all.size() - 1);
        assertThat(warnings()).singleElement().satisfies(message ->
                assertThat(message).contains("'not.a.key'", "FI"));
    }

    @Test
    void excludeListWithoutProfileLogsOneWarningAndSelectsNothing() {
        var profile = CountryProfileLoader.resolve(Map.of(CountryProfileLoader.EXCLUDE_ENV_VARIABLE, "xroad.signer.key-length"));

        assertThat(profile).isEmpty();
        assertThat(warnings()).singleElement().satisfies(message ->
                assertThat(message).contains(CountryProfileLoader.EXCLUDE_ENV_VARIABLE, "ignored"));
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
                assertBundledCodesAreSupported(jar.getPath(PROFILE_DIRECTORY));
            }
        } else {
            assertBundledCodesAreSupported(Path.of(uri));
        }
    }

    private static void assertBundledCodesAreSupported(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            var codes = files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".yaml"))
                    .map(name -> name.substring(0, name.length() - ".yaml".length()))
                    .toList();

            assertThat(codes).containsExactlyInAnyOrderElementsOf(CountryProfileLoader.SUPPORTED_COUNTRIES);
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

    private static Map<String, String> resolve(String code, String excludeList) {
        var env = excludeList == null
                ? Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, code)
                : Map.of(CountryProfileLoader.PROFILE_ENV_VARIABLE, code,
                CountryProfileLoader.EXCLUDE_ENV_VARIABLE, excludeList);
        return CountryProfileLoader.resolve(env).orElseThrow().values();
    }

    private List<String> warnings() {
        return logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
