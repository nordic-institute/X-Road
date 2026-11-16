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
package org.niis.xroad.opmonitor.core.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.HealthCheckConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(SystemStubsExtension.class)
class OpMonitorConfigTest {

    private static final String APP_NAME = "op-monitor-daemon";
    private static final Optional<String> TOKEN = Optional.of("a-token");
    private static final String IDENTITY_SOURCE = "xroad.op-monitor.rpc.identity-source";

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @TempDir
    private Path tempDir;

    private final Logger configLogger = (Logger) LoggerFactory.getLogger(OpMonitorConfig.class);
    private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    private Level originalLevel;

    @BeforeEach
    void captureLog() {
        variables.remove("DB_CONFIG_SOURCE_URL");
        originalLevel = configLogger.getLevel();
        configLogger.setLevel(Level.DEBUG);
        logEvents.start();
        configLogger.addAppender(logEvents);
    }

    @AfterEach
    void releaseLog() {
        configLogger.detachAppender(logEvents);
        configLogger.setLevel(originalLevel);
    }

    @Test
    void settingsFileIsReadFromTheOpMonitorSettingsPath() {
        assertThat(OpMonitorConfig.SETTINGS_FILE).isEqualTo(Path.of("/etc/xroad/conf.d/op-monitor-settings.yaml"));
    }

    @Test
    void productionConfigurationResolvesAnOpMonitorKeyFromTheSettingsFile() throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.listen-address: 10.1.2.3\n");

        var config = new OpMonitorConfig(file).xRoadConfig("op-monitor-daemon", TOKEN);

        var value = config.get(OpMonitorConfigKeys.RPC_LISTEN_ADDRESS);
        assertThat(value.value()).isEqualTo("10.1.2.3");
        assertThat(value.source()).isEqualTo(Source.FILE);
    }

    @Test
    void containerizedModeIgnoresTheSettingsFile() throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.listen-address: 10.1.2.3\n");

        var config = new OpMonitorConfig(file).buildConfig("op-monitor-daemon", DeploymentMode.CONTAINERIZED);

        assertThat(config.get(OpMonitorConfigKeys.RPC_LISTEN_ADDRESS).source()).isEqualTo(Source.DEFAULT);
    }

    @Test
    void settingsFileOnAContainerizedRunIsIgnoredWithAWarningAfterANativeRunUsedIt() throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.listen-address: 10.1.2.3\n");
        var opMonitorConfig = new OpMonitorConfig(file);

        var nativeValue = opMonitorConfig.buildConfig(APP_NAME, DeploymentMode.NATIVE)
                .get(OpMonitorConfigKeys.RPC_LISTEN_ADDRESS);
        assertThat(warnings()).isEmpty();
        var containerValue = opMonitorConfig.buildConfig(APP_NAME, DeploymentMode.CONTAINERIZED)
                .get(OpMonitorConfigKeys.RPC_LISTEN_ADDRESS);

        assertThat(nativeValue.source()).isEqualTo(Source.FILE);
        assertThat(containerValue.value()).isEqualTo("0.0.0.0");
        assertThat(containerValue.source()).isEqualTo(Source.DEFAULT);
        assertThat(warnings()).singleElement().asString().contains(file.toString());
    }

    @Test
    void containerizedModeWithoutASettingsFileDoesNotWarn() {
        new OpMonitorConfig(tempDir.resolve("absent.yaml")).buildConfig(APP_NAME, DeploymentMode.CONTAINERIZED);

        assertThat(warnings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "?currentSchema=serverconf"})
    void blankConfigurationDatabaseUrlFailsStartupInsteadOfUsingTheSettingsFileOrDefaults(String url)
            throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.listen-address: 10.1.2.3\n");
        variables.set("DB_CONFIG_SOURCE_URL", url);
        var opMonitorConfig = new OpMonitorConfig(file);

        assertThatThrownBy(() -> opMonitorConfig.buildConfig(APP_NAME, DeploymentMode.NATIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuration database source")
                .hasMessageContaining("DB_CONFIG_SOURCE_URL")
                .hasMessageContaining("is set but has no base URL")
                .hasMessageNotContaining("connection pool");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# only a comment\n", "---\n", "{}\n"})
    void fileWithoutSettingsResolvesEveryKeyAsWithNoFile(String content) throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"), content);

        var withFile = new OpMonitorConfig(file).xRoadConfig(APP_NAME, TOKEN);
        var withoutFile = new OpMonitorConfig(tempDir.resolve("absent.yaml")).xRoadConfig(APP_NAME, TOKEN);

        var keys = Stream.of(CommonRpcConfigKeys.instance(), CommonConfigKeys.instance(),
                        OpMonitorConfigKeys.instance(), HealthCheckConfigKeys.instance())
                .map(ConfigKeyProvider::keys).flatMap(Collection::stream).toList();
        assertThat(keys).isNotEmpty().allSatisfy(key -> {
            assertThat(withFile.get(key).source()).isEqualTo(Source.DEFAULT);
            assertThat(withFile.get(key).value()).usingRecursiveComparison().isEqualTo(withoutFile.get(key).value());
        });
    }

    @Test
    void keyWithoutValueFailsStartupNamingTheFileAndTheKey() throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.listen-address:\n");
        var opMonitorConfig = new OpMonitorConfig(file);

        assertThatThrownBy(() -> opMonitorConfig.xRoadConfig(APP_NAME, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("xroad.op-monitor.rpc.listen-address");
    }

    @ParameterizedTest
    @ValueSource(strings = {"~", "null", "Null", "NULL"})
    void yamlNullSpellingsReachAKeyWithoutRestrictingValidatorAsWrittenText(String written) throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.listen-address: " + written + "\n");

        var value = new OpMonitorConfig(file).xRoadConfig(APP_NAME, TOKEN).get(OpMonitorConfigKeys.RPC_LISTEN_ADDRESS);

        assertThat(value.value()).isEqualTo(written);
        assertThat(value.source()).isEqualTo(Source.FILE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"~", "null", "Null", "NULL"})
    void yamlNullSpellingsAreRejectedByTheValidatorOfAValidatedKey(String written) throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.identity-source: " + written + "\n");
        var opMonitorConfig = new OpMonitorConfig(file);

        assertThatThrownBy(() -> opMonitorConfig.xRoadConfig(APP_NAME, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xroad.op-monitor.rpc.identity-source")
                .hasMessageNotContaining("no value");
    }

    @Test
    void unquotedAndQuotedValuesResolveToTheSameTypedValue() throws IOException {
        var unquoted = Files.writeString(tempDir.resolve("unquoted.yaml"),
                "xroad.op-monitor.rpc.port: 2085\nxroad.common-rpc.use-tls: false\n");
        var quoted = Files.writeString(tempDir.resolve("quoted.yaml"),
                "xroad.op-monitor.rpc.port: \"2085\"\nxroad.common-rpc.use-tls: \"false\"\n");

        for (var file : new Path[] {unquoted, quoted}) {
            var config = new OpMonitorConfig(file).xRoadConfig(APP_NAME, TOKEN);

            assertThat(config.get(OpMonitorConfigKeys.RPC_PORT).value()).isEqualTo(2085);
            assertThat(config.get(CommonRpcConfigKeys.USE_TLS).value()).isFalse();
            assertThat(config.get(OpMonitorConfigKeys.RPC_PORT).source()).isEqualTo(Source.FILE);
        }
    }

    @Test
    void invalidFileValueFailsThroughTheKeyValidator() throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"),
                "xroad.op-monitor.rpc.identity-source: unsupported\n");
        var opMonitorConfig = new OpMonitorConfig(file);

        assertThatThrownBy(() -> opMonitorConfig.xRoadConfig(APP_NAME, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xroad.op-monitor.rpc.identity-source");
    }

    @Test
    void rpcPropertiesPickUpTheFileIdentitySourceFromTheConfiguration() {
        var rpcProperties = new OpMonitorConfig().rpcProperties(config(Map.of(
                "xroad.op-monitor.rpc.identity-source", "file")));

        assertThat(rpcProperties).isInstanceOf(OpMonitorRpcProperties.class);
        assertThat(rpcProperties.identitySource()).isEqualTo(RpcProperties.IdentitySource.FILE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void nativeWithoutDatabaseAndDefaultIdentityFailsNamingTheSettingFileAndTheMissingToken(String token) {
        var opMonitorConfig = new OpMonitorConfig(tempDir.resolve("absent.yaml"));
        var openBaoToken = Optional.ofNullable(token);

        var thrown = catchThrowable(() -> opMonitorConfig.xRoadConfig(APP_NAME, openBaoToken));

        assertThat(thrown).isInstanceOf(XrdRuntimeException.class);
        assertDefaultIdentityMessage(thrown);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# only a comment\n", "---\n", "{}\n", "xroad.op-monitor.rpc.listen-address: 10.1.2.3\n"})
    void fileWithoutIdentitySettingGivesTheSameFailureAsNoFile(String content) throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"), content);
        var opMonitorConfig = new OpMonitorConfig(file);
        Optional<String> noToken = Optional.empty();

        var thrown = catchThrowable(() -> opMonitorConfig.xRoadConfig(APP_NAME, noToken));

        assertThat(thrown).isInstanceOf(XrdRuntimeException.class);
        assertDefaultIdentityMessage(thrown);
    }

    @Test
    void openBaoIdentityFromTheSettingsFileIsNamedAsComingFromTheSettingsFile() throws IOException {
        var file = Files.writeString(tempDir.resolve("op-monitor-settings.yaml"), IDENTITY_SOURCE + ": openbao\n");
        var opMonitorConfig = new OpMonitorConfig(file);
        Optional<String> noToken = Optional.empty();

        var thrown = catchThrowable(() -> opMonitorConfig.xRoadConfig(APP_NAME, noToken));

        assertThat(thrown).isInstanceOf(XrdRuntimeException.class).hasMessageContaining("(from the settings file)");
    }

    @Test
    void openBaoIdentityFromTheDatabaseIsNamedAsComingFromTheDatabase() {
        var config = config(Map.of(IDENTITY_SOURCE, "openbao"));
        Optional<String> noToken = Optional.empty();

        assertThatThrownBy(() -> OpMonitorConfig.requireIdentityConfigured(config, DeploymentMode.NATIVE, false, noToken))
                .hasMessageContaining("(from the database)");
    }

    @Test
    void fileIdentityWithoutTokenPasses() {
        var config = config(Map.of(IDENTITY_SOURCE, "file"));

        assertThatCode(() -> OpMonitorConfig.requireIdentityConfigured(config, DeploymentMode.NATIVE, false,
                Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void openBaoIdentityWithTokenPasses() {
        var config = config(Map.of());

        assertThatCode(() -> OpMonitorConfig.requireIdentityConfigured(config, DeploymentMode.NATIVE, false, TOKEN))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void tlsOffWithoutTokenPasses(boolean databaseConfigured) {
        var config = config(Map.of("xroad.common-rpc.use-tls", "false"));

        assertThatCode(() -> OpMonitorConfig.requireIdentityConfigured(config, DeploymentMode.NATIVE,
                databaseConfigured, Optional.empty())).doesNotThrowAnyException();
    }

    @Test
    void configurationDatabaseWithDefaultIdentityAndNoTokenPasses() {
        var config = config(Map.of());

        assertThatCode(() -> OpMonitorConfig.requireIdentityConfigured(config, DeploymentMode.NATIVE, true,
                Optional.empty())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void containerizedModeWithoutTokenPasses(boolean databaseConfigured) {
        var config = config(Map.of());

        assertThatCode(() -> OpMonitorConfig.requireIdentityConfigured(config, DeploymentMode.CONTAINERIZED,
                databaseConfigured, Optional.empty())).doesNotThrowAnyException();
    }

    private static void assertDefaultIdentityMessage(Throwable thrown) {
        assertThat(thrown.getMessage()).contains(
                "op-monitor is not configured",
                OpMonitorConfig.SETTINGS_FILE.toString(),
                "xroad.op-monitor.rpc.identity-source is openbao (from the default)",
                "XROAD_SECRET_STORE_TOKEN is missing or blank",
                "xroad.op-monitor.rpc.file-identity.private-key-path",
                "xroad.op-monitor.rpc.file-identity.certificate-path",
                "xroad.op-monitor.rpc.file-identity.trusted-ca-path",
                "restart xroad-opmonitor",
                "provide an OpenBao token");
    }

    private List<String> warnings() {
        return logEvents.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static XRoadConfig config(Map<String, String> overrides) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(OpMonitorConfigKeys.instance())
                .overrides(overrides)
                .build();
    }
}
