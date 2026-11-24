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

package org.niis.xroad.configuration.migration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.migration.tokenpin.PinSourceLocations;
import org.niis.xroad.migration.tokenpin.PinSourceNotFoundException;
import org.niis.xroad.migration.utils.InstallationType;
import org.niis.xroad.signer.common.config.SignerConfigKeys;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(SystemStubsExtension.class)
class SignerTokenPinsNodeTest {

    private static final String AUTOLOGIN_KEY = "xroad.signer.autologin.enabled";
    private static final String PIN_FIXTURE = "fixturepin4711";
    private static final NodeIdentity NODE_B = new NodeIdentity("node_b", true);

    @SystemStub
    private final EnvironmentVariables envVars = new EnvironmentVariables();

    @SystemStub
    private final SystemOut systemOut = new SystemOut();

    @TempDir
    Path tempDir;

    private FakeOpenBao openBao;
    private Map<String, String> store;
    private Path ssDbProperties;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void startFakeOpenBao() throws IOException {
        openBao = new FakeOpenBao();
        openBao.pointEnvironmentAt(envVars);
        store = openBao.store();

        ssDbProperties = writeDbProperties("ss-db.properties", """
                xroad.db.serverconf.hibernate.connection.url=jdbc:postgresql://localhost/ss
                xroad.db.serverconf.hibernate.connection.username=user
                xroad.db.serverconf.hibernate.connection.password=secret
                """);

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logAppender);
    }

    @AfterEach
    void stopFakeOpenBao() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logAppender);
        openBao.close();
    }

    @Test
    void shouldStorePinsUnderTheNodeGivenAndSucceedWhenEveryPinIsAlreadyStoredOnRerun() throws IOException {
        String[] args = {"signer-token-pins", "--node", "node_b", "--db-properties", ssDbProperties.toString(),
                fetchPinScript().toString()};

        try (MockedConstruction<DbRepository> dbRepositories = Mockito.mockConstruction(DbRepository.class)) {
            LegacyConfigMigrationCLI.main(args);
            Map<String, String> afterFirstRun = Map.copyOf(store);
            LegacyConfigMigrationCLI.main(args);

            assertThat(afterFirstRun).containsOnlyKeys("signer/token-pins/node_b/0");
            assertThat(store).isEqualTo(afterFirstRun);
            assertThat(dbRepositories.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldAcceptCentralServerInstallationOptionNextToTheNodeOption() throws IOException {
        Path csDbProperties = writeCentralServerDbProperties();
        String[] args = {"signer-token-pins", "--node", "node_b", "--installation=cs",
                "--db-properties=" + csDbProperties, fetchPinScript().toString()};

        try (MockedConstruction<DbRepository> ignored = Mockito.mockConstruction(DbRepository.class)) {
            LegacyConfigMigrationCLI.main(args);
        }

        assertThat(store).containsOnlyKeys("signer/token-pins/node_b/0");
    }

    @Test
    void shouldStoreNothingWhenTheNodeNameIsInvalid() throws IOException {
        String[] args = {"signer-token-pins", "--node", "_bad", "--db-properties", ssDbProperties.toString(),
                fetchPinScript().toString()};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .cause()
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");
        assertThat(store).isEmpty();
    }

    @Test
    void shouldStoreThePinOfTokenZeroFromAPinFileWhenNoScriptExists() throws IOException {
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), PIN_FIXTURE + "\n");

        try (MockedConstruction<DbRepository> ignored = Mockito.mockConstruction(DbRepository.class)) {
            migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations(null, pinFile));
        }

        assertThat(store).containsOnlyKeys("signer/token-pins/node_b/0");
    }

    @Test
    void shouldStoreOnePinPerTokenFromAPinFileOfTokenIdLines() throws IOException {
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), "0:" + PIN_FIXTURE + "\n1:" + PIN_FIXTURE + "x\n");

        try (MockedConstruction<DbRepository> ignored = Mockito.mockConstruction(DbRepository.class)) {
            migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations(null, pinFile));
        }

        assertThat(store).containsOnlyKeys("signer/token-pins/node_b/0", "signer/token-pins/node_b/1");
    }

    @Test
    void shouldThrowWhenOneTokenCannotBeStoredEvenThoughAnotherWas() throws IOException {
        openBao.rejectWritesTo("signer/token-pins/node_b/1");
        Path script = tempDir.resolve("two-token-fetch-pin.sh");
        Files.writeString(script, "#!/bin/sh\necho 0:1111\necho 1:2222\n");
        assertThat(script.toFile().setExecutable(true)).isTrue();
        String[] args = {"signer-token-pins", "--node", "node_b", "--db-properties", ssDbProperties.toString(),
                script.toString()};

        try (MockedConstruction<DbRepository> ignored = Mockito.mockConstruction(DbRepository.class)) {
            assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                    .isInstanceOf(MigrationException.class)
                    .cause()
                    .hasMessageContaining("did not succeed");
        }
        assertThat(store).containsOnlyKeys("signer/token-pins/node_b/0");
    }

    @Test
    void shouldFailNamingAllFourLocationsWhenNoPinSourceExists() {
        PinSourceLocations locations = locations(null, tempDir.resolve("missing-autologin"));
        List<String> expectedNames = List.of("script path argument (not given)", locations.customScript().toString(),
                locations.defaultScript().toString(), locations.pinFile().toString());

        try (MockedConstruction<DbRepository> dbRepositories = Mockito.mockConstruction(DbRepository.class)) {
            assertThatThrownBy(() -> migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations))
                    .isInstanceOf(PinSourceNotFoundException.class)
                    .satisfies(e -> expectedNames.forEach(name -> assertThat(e).hasMessageContaining(name)));
            assertThat(dbRepositories.constructed()).isEmpty();
        }
        assertThat(store).isEmpty();
    }

    @Test
    void shouldNotFallBackToThePinFileWhenTheExplicitScriptIsMissing() throws IOException {
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), PIN_FIXTURE + "\n");
        PinSourceLocations locations = locations(tempDir.resolve("missing-script.sh"), pinFile);

        assertThatThrownBy(() -> migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations))
                .isInstanceOf(PinSourceNotFoundException.class)
                .hasMessageContaining("missing-script.sh");
        assertThat(store).isEmpty();
    }

    @Test
    void shouldSucceedAndChangeNothingWhenThePinFileIsEmpty() throws IOException {
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), "\n");

        try (MockedConstruction<DbRepository> dbRepositories = Mockito.mockConstruction(DbRepository.class)) {
            migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations(null, pinFile));

            assertThat(dbRepositories.constructed()).isEmpty();
        }
        assertThat(store).isEmpty();
    }

    @Test
    void shouldFailWithoutAnUncaughtReadErrorWhenThePinFileCannotBeRead() throws IOException {
        Path unreadablePinFile = Files.createDirectory(tempDir.resolve("autologin"));

        try (MockedConstruction<DbRepository> dbRepositories = Mockito.mockConstruction(DbRepository.class)) {
            assertThatThrownBy(() -> migrate(InstallationType.SECURITY_SERVER, ssDbProperties,
                    locations(null, unreadablePinFile)))
                    .isInstanceOf(XrdRuntimeException.class)
                    .hasMessageContaining("did not succeed");
            assertThat(dbRepositories.constructed()).isEmpty();
        }
        assertThat(store).isEmpty();
    }

    @Test
    void shouldStoreNothingWhenTheDbPropertiesFileIsMissing() throws IOException {
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), PIN_FIXTURE + "\n");

        assertThatThrownBy(() -> migrate(InstallationType.SECURITY_SERVER, tempDir.resolve("absent.properties"),
                locations(null, pinFile)))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("absent.properties")
                .hasMessageContaining("--db-properties");
        assertThat(store).isEmpty();
    }

    @Test
    void shouldStoreNothingWhenTheDbPropertiesFileLacksTheKeysOfTheInstallation() throws IOException {
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), PIN_FIXTURE + "\n");

        assertThatThrownBy(() -> migrate(InstallationType.CENTRAL_SERVER, ssDbProperties, locations(null, pinFile)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store).isEmpty();
    }

    @Test
    void shouldEnableAutologinInTheConfigurationDatabaseOfEachInstallationWhenAPinIsStored() throws IOException {
        Path csDbProperties = writeCentralServerDbProperties();
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), PIN_FIXTURE + "\n");
        List<List<Object>> constructorArguments = new ArrayList<>();

        try (MockedConstruction<DbRepository> dbRepositories = Mockito.mockConstruction(DbRepository.class,
                (_, context) -> constructorArguments.add(List.copyOf(context.arguments())))) {
            migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations(null, pinFile));
            migrate(InstallationType.CENTRAL_SERVER, csDbProperties, locations(null, pinFile),
                    new NodeIdentity("node_c", true));

            assertThat(dbRepositories.constructed()).hasSize(2);
            dbRepositories.constructed().forEach(repository -> verify(repository).saveProperty(AUTOLOGIN_KEY, "true"));
        }
        assertThat(constructorArguments).containsExactly(
                List.of(ssDbProperties.toString(), InstallationType.SECURITY_SERVER),
                List.of(csDbProperties.toString(), InstallationType.CENTRAL_SERVER));
    }

    @Test
    void shouldLeaveAutologinUnchangedWhenNoPinIsStoredOnRerun() throws IOException {
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), PIN_FIXTURE + "\n");

        try (MockedConstruction<DbRepository> dbRepositories = Mockito.mockConstruction(DbRepository.class)) {
            migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations(null, pinFile));
            migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations(null, pinFile));

            assertThat(dbRepositories.constructed()).hasSize(1);
        }
    }

    @Test
    void shouldNameTheSourceInTheLogAndNeverLogThePin() throws IOException {
        Path pinFile = Files.writeString(tempDir.resolve("autologin"), PIN_FIXTURE + "\n");

        try (MockedConstruction<DbRepository> ignored = Mockito.mockConstruction(DbRepository.class)) {
            migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations(null, pinFile));
            migrate(InstallationType.SECURITY_SERVER, ssDbProperties, locations(null, pinFile));
        }

        List<String> messages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(message -> message.contains("PIN file " + pinFile));
        assertThat(messages).noneMatch(message -> message.contains(PIN_FIXTURE));
        assertThat(systemOut.getText()).doesNotContain(PIN_FIXTURE);
    }

    @Test
    void shouldListThePinSourceOrderInTheHelpText() {
        LegacyConfigMigrationCLI.main(new String[]{"help"});

        assertThat(systemOut.getText())
                .contains("--db-properties <path>")
                .containsSubsequence(
                        "1. <script-path>",
                        "2. " + PinSourceLocations.CUSTOM_SCRIPT,
                        "3. " + PinSourceLocations.DEFAULT_SCRIPT,
                        "4. " + PinSourceLocations.PIN_FILE);
    }

    @Test
    void shouldKeepTheAutologinKeyInStepWithTheSignerConfigKey() {
        assertThat(LegacyConfigMigrationCLI.SIGNER_AUTOLOGIN_ENABLED_KEY)
                .isEqualTo(SignerConfigKeys.AUTOLOGIN_ENABLED.key());
    }

    private void migrate(InstallationType installation, Path dbProperties, PinSourceLocations locations)
            throws IOException {
        migrate(installation, dbProperties, locations, NODE_B);
    }

    private void migrate(InstallationType installation, Path dbProperties, PinSourceLocations locations,
                         NodeIdentity node) throws IOException {
        LegacyConfigMigrationCLI.migrateSignerTokenPins(node, installation, dbProperties.toString(), locations);
    }

    private PinSourceLocations locations(Path explicitScript, Path pinFile) {
        return new PinSourceLocations(explicitScript, tempDir.resolve("no-custom.sh"), tempDir.resolve("no-default.sh"),
                pinFile);
    }

    private Path fetchPinScript() throws IOException {
        Path script = tempDir.resolve("fetch-pin.sh");
        Files.writeString(script, "#!/bin/sh\necho 0:1111\n");
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private Path writeCentralServerDbProperties() throws IOException {
        return writeDbProperties("cs-db.properties", """
                spring.datasource.url=jdbc:postgresql://localhost/cs
                spring.datasource.username=user
                spring.datasource.password=secret
                """);
    }

    private Path writeDbProperties(String name, String content) throws IOException {
        return Files.writeString(tempDir.resolve(name), content);
    }
}
