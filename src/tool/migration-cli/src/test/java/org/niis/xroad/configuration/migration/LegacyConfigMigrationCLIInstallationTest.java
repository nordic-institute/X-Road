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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegacyConfigMigrationCLIInstallationTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"acme-account-keys", "configuration-anchor", "config", "set-property"})
    void shouldRejectCommandOutsideTheCentralServerAllowListBeforeAnyFileAccess(String command) {
        String[] args = {command, "--installation=cs", missing("input"), missing("db.properties")};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("'" + command + "'")
                .hasMessageContaining("Central Server")
                .hasMessageContaining("--installation=cs")
                .hasMessageNotContaining("Migration failed");
    }

    @Test
    void shouldRejectAnUnknownInstallationTypeListingTheAcceptedValues() {
        String[] args = {"node-name", "--installation=xx", missing("node.ini"), missing("db.properties")};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("accepted values: ss, cs");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ss", "cs"})
    void shouldFailKeyconfOnAMalformedKeyConfigurationDirectory(String installation) throws URISyntaxException {
        Path malformed = Paths.get(getClass().getClassLoader().getResource("signer-malformed").toURI());

        String[] args = {"keyconf", "--installation=" + installation, malformed.toString(), missing("db.properties")};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("Migration failed")
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Unable to parse keyconf.xml file");
    }

    @Test
    void shouldReadSecurityServerKeysWhenTheOptionIsAbsent() throws Exception {
        Path dbProperties = writeCentralServerProperties();

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(
                new String[]{"keyconf", validKeyConf(), dbProperties.toString()}))
                .isInstanceOf(MigrationException.class)
                .cause()
                .hasMessageContaining("'xroad.db.serverconf.hibernate.connection.url'");
    }

    @Test
    void shouldReadCentralServerKeysWithTheOption() throws Exception {
        Path dbProperties = writeSecurityServerProperties();

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(
                new String[]{"keyconf", "--installation=cs", validKeyConf(), dbProperties.toString()}))
                .isInstanceOf(MigrationException.class)
                .cause()
                .hasMessageContaining("'spring.datasource.url'");
    }

    private String validKeyConf() throws URISyntaxException {
        return Paths.get(getClass().getClassLoader().getResource("signer").toURI()).toString();
    }

    private Path writeCentralServerProperties() throws IOException {
        Path file = tempDir.resolve("cs-db.properties");
        Files.writeString(file, "spring.datasource.username=centerui\n");
        return file;
    }

    private Path writeSecurityServerProperties() throws IOException {
        Path file = tempDir.resolve("ss-db.properties");
        Files.writeString(file, "xroad.db.serverconf.hibernate.connection.username=serverconf\n");
        return file;
    }

    private String missing(String name) {
        return tempDir.resolve("absent").resolve(name).toString();
    }
}
