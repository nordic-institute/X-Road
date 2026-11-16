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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.Prefix;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.Validator;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable.Row;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
class XRoadConfigBuilderTest {

    private static final Path SETTINGS_PATH = Path.of("/test/settings.yaml");

    private static Map<String, String> databaseEnvironment;

    private final Prefix scope = Prefix.of("xroad.test");
    private final ConfigKey<String> withContainerDefault = scope.string("with-container")
            .withDefaultValue("native").withContainerDefaultValue("container").build();
    private final ConfigKey<String> nativeOnly = scope.string("native-only")
            .withDefaultValue("native-only").build();
    private final ConfigKey<String> unsetEverywhere = scope.string("unset")
            .withDefaultValue("native").withContainerDefaultValue("container").build();
    private final ConfigKey<Integer> ranged = scope.integer("ranged")
            .withDefaultValue("5").withValidator(Validator.range(1, 10)).build();
    private final ConfigKeyProvider provider = ConfigKeyProvider.forPrefix(scope);

    private final Logger builderLogger = (Logger) LoggerFactory.getLogger(XRoadConfigBuilder.class);
    private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    private Level originalLevel;

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @BeforeAll
    static void createConfigurationTable() throws SQLException {
        databaseEnvironment = NodeConfigTestTable.create("builderfilelayer", List.of(
                new Row("xroad.test.with-container", "from-db", null),
                new Row("xroad.test.ranged", "3", null)));
    }

    @BeforeEach
    void captureLog() {
        originalLevel = builderLogger.getLevel();
        builderLogger.setLevel(Level.DEBUG);
        logEvents.start();
        builderLogger.addAppender(logEvents);
    }

    @AfterEach
    void releaseLog() {
        builderLogger.detachAppender(logEvents);
        builderLogger.setLevel(originalLevel);
    }

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

    @Test
    void keyWithNoDbRowResolvesToFileValueWhenTheDbIsConfigured() {
        var file = Map.of("xroad.test.native-only", "from-file");

        var config = configuredDbBuilder().fileOverrides(SETTINGS_PATH, file).dbOverrides("test-app").build();

        var value = config.get(nativeOnly);
        assertThat(value.value()).isEqualTo("from-file");
        assertThat(value.source()).isEqualTo(Source.FILE);
        assertThat(logMessages())
                .anyMatch(message -> message.startsWith("Loaded 2 config override(s) from DB for 'test-app'"))
                .contains("Config key xroad.test.native-only resolved from source FILE (/test/settings.yaml)");
    }

    @Test
    void keySetInDbAndFileResolvesToDbValue() {
        var file = Map.of("xroad.test.with-container", "from-file");

        var config = configuredDbBuilder().fileOverrides(SETTINGS_PATH, file).dbOverrides("test-app").build();

        var value = config.get(withContainerDefault);
        assertThat(value.value()).isEqualTo("from-db");
        assertThat(value.source()).isEqualTo(Source.DB);
        assertThat(logMessages()).contains("Config key xroad.test.with-container resolved from source DB");
    }

    @Test
    void nativeKeySetInNeitherDbNorFileResolvesToPackagedDefault() {
        var file = Map.of("xroad.test.ranged", "7");

        var config = configuredDbBuilder().deploymentMode(DeploymentMode.NATIVE)
                .fileOverrides(SETTINGS_PATH, file).dbOverrides("test-app").build();

        var value = config.get(unsetEverywhere);
        assertThat(value.value()).isEqualTo("native");
        assertThat(value.source()).isEqualTo(Source.DEFAULT);
        assertThat(logMessages()).contains("Config key xroad.test.unset resolved from source DEFAULT");
    }

    @Test
    void fileValueWinsOverContainerDefault() {
        var file = Map.of("xroad.test.with-container", "from-file");

        var config = XRoadConfigBuilder.create().register(provider)
                .deploymentMode(DeploymentMode.CONTAINERIZED)
                .fileOverrides(SETTINGS_PATH, file)
                .build();

        assertThat(config.get(withContainerDefault).value()).isEqualTo("from-file");
    }

    @Test
    void fileValueFailsTheKeyValidatorLikeADbValue() {
        var file = Map.of("xroad.test.ranged", "11");
        var builder = XRoadConfigBuilder.create().register(provider).fileOverrides(SETTINGS_PATH, file);

        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xroad.test.ranged");
    }

    @Test
    void disabledDbSourceLogDoesNotClaimPackagedDefaultsOnlyWhenFileLayerIsEnabled() {
        var file = Map.of("xroad.test.native-only", "from-file");

        XRoadConfigBuilder.create().register(provider).fileOverrides(SETTINGS_PATH, file).dbOverrides("test-app");

        assertThat(logMessages())
                .anyMatch(message -> message.startsWith("DB config source disabled for 'test-app'"))
                .noneMatch(message -> message.contains("packaged defaults only"));
    }

    private XRoadConfigBuilder configuredDbBuilder() {
        databaseEnvironment.forEach(variables::set);
        return XRoadConfigBuilder.create().register(provider);
    }

    private List<String> logMessages() {
        return logEvents.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
