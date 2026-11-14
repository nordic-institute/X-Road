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
package org.niis.xroad.configuration.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.niis.xroad.configuration.migration.LegacyConfigMigrationCLI.Command;
import org.niis.xroad.migration.utils.InstallationType;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
class LegacyConfigMigrationCLIInstallationTest {

    @SystemStub
    private final EnvironmentVariables envVars = new EnvironmentVariables();

    @TempDir
    Path tempDir;

    @BeforeEach
    void stubEnvironment() {
        envVars.set(BasePropertiesToDbMigrator.AUTO_CONFIRM_ENV, "true");
        envVars.remove("XROAD_MIGRATION_ACME_KEYSTORE_PASSWORD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"acme-account-keys", "configuration-anchor", "config", "set-property"})
    void shouldRejectCommandOutsideTheCentralServerAllowListBeforeAnyMigrationWork(String command) throws IOException {
        String[] args = argsThatWouldRunIfAllowed(command);

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("'" + command + "'")
                .hasMessageContaining("Central Server")
                .hasMessageContaining("--installation=cs")
                .hasMessageNotContaining("Migration failed");
    }

    @ParameterizedTest
    @EnumSource(value = Command.class, names = {
        "VALIDATE", "NODE_NAME", "SIGNER_DEVICES", "INI_TO_DB", "KEYCONF", "SIGNER_TOKEN_PINS"})
    void shouldAcceptCommandInCentralServerMode(Command command) {
        assertThat(LegacyConfigMigrationCLI.isSupported(command, InstallationType.CENTRAL_SERVER)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = Command.class, mode = EnumSource.Mode.EXCLUDE, names = {
        "VALIDATE", "NODE_NAME", "SIGNER_DEVICES", "INI_TO_DB", "KEYCONF", "SIGNER_TOKEN_PINS"})
    void shouldRejectOtherCommandsInCentralServerMode(Command command) {
        assertThat(LegacyConfigMigrationCLI.isSupported(command, InstallationType.CENTRAL_SERVER)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Command.class)
    void shouldAcceptEveryCommandInSecurityServerMode(Command command) {
        assertThat(LegacyConfigMigrationCLI.isSupported(command, InstallationType.SECURITY_SERVER)).isTrue();
    }

    @Test
    void shouldRejectAnUnknownInstallationTypeListingTheAcceptedValues() {
        String[] args = {"node-name", "--installation=xx", missing("local.ini"), missing("node.ini")};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("accepted values: ss, cs")
                .hasMessageNotContaining("Migration failed");
    }

    @Test
    void shouldRejectInstallationOptionWithoutValue() {
        String[] args = {"node-name", missing("local.ini"), missing("node.ini"), "--installation"};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("requires a value")
                .hasMessageNotContaining("Migration failed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss", "cs"})
    void shouldFailKeyconfOnAMalformedKeyConfigurationDirectory(String installation) throws URISyntaxException {
        String[] args = {"keyconf", "--installation=" + installation, resource("signer-malformed"), missing("db.properties")};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("Migration failed")
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Unable to parse keyconf.xml file");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss", "cs"})
    void shouldFailKeyconfWhenTheDirectoryHasNoKeyconfXml(String installation) {
        String[] args = {"keyconf", "--installation=" + installation, tempDir.toString(), missing("db.properties")};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("Migration failed")
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Key configuration file not found")
                .hasMessageContaining("keyconf.xml");
    }

    @Test
    void shouldUseSecurityServerKeysWhenTheOptionIsAbsent() throws Exception {
        Path dbProperties = writeProperties("cs-db.properties", "spring.datasource.username=centerui\n");

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(
                new String[]{"keyconf", resource("signer"), dbProperties.toString()}))
                .isInstanceOf(MigrationException.class)
                .cause()
                .hasMessageContaining("'xroad.db.serverconf.hibernate.connection.url'");
    }

    @Test
    void shouldUseCentralServerKeysWithTheOption() throws Exception {
        Path dbProperties = writeProperties("ss-db.properties", "xroad.db.serverconf.hibernate.connection.username=serverconf\n");

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(
                new String[]{"keyconf", "--installation=cs", resource("signer"), dbProperties.toString()}))
                .isInstanceOf(MigrationException.class)
                .cause()
                .hasMessageContaining("'spring.datasource.url'");
    }

    @ParameterizedTest
    @MethodSource("databaseCommands")
    void shouldConstructDbRepositoryForTheSelectedInstallation(
            String command, String resource, String option, InstallationType expected) throws URISyntaxException {
        String[] args = Stream.of(command, option, resource(resource), tempDir.resolve("db.properties").toString())
                .filter(Objects::nonNull)
                .toArray(String[]::new);
        List<Object> installations = new ArrayList<>();

        try (MockedConstruction<DbRepository> constructed = Mockito.mockConstruction(DbRepository.class,
                (_, context) -> installations.add(context.arguments().get(1)))) {
            LegacyConfigMigrationCLI.main(args);

            assertThat(constructed.constructed()).hasSize(1);
        }

        assertThat(installations).containsExactly(expected);
    }

    static Stream<Arguments> databaseCommands() {
        return Stream.of("ini-to-db", "signer-devices").flatMap(command -> {
            String resource = "ini-to-db".equals(command) ? "local-test.ini" : "devices.ini";
            return Stream.of(
                    Arguments.of(command, resource, "--installation=cs", InstallationType.CENTRAL_SERVER),
                    Arguments.of(command, resource, null, InstallationType.SECURITY_SERVER));
        });
    }

    private String[] argsThatWouldRunIfAllowed(String command) throws IOException {
        String option = "--installation=cs";
        String input = writeProperties("input.properties", "some.key=value\n").toString();
        String db = writeProperties("db.properties", "").toString();
        return switch (command) {
            case "config" -> new String[]{command, option, input};
            case "set-property" -> new String[]{command, option, db, "some.key", "value"};
            default -> new String[]{command, option, input, db};
        };
    }

    private Path writeProperties(String fileName, String contents) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, contents);
        return file;
    }

    private String resource(String name) throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource(name).toURI()).toString();
    }

    private String missing(String name) {
        return tempDir.resolve("absent").resolve(name).toString();
    }
}
