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
import org.mockito.ArgumentMatchers;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileToDbPropertyMigrator copies a whole file's contents verbatim into a single DB property,
 * with no key filtering. The DS TLS ACME migration (issue XRDDEV-3296) relies on exactly this:
 * a "dataspace-tls" EAB credentials entry in acme.yml rides the existing acme.yml -> xroad.acme
 * migration automatically. These tests prove that content survival directly, rather than merely
 * asserting the migration runs without an exception.
 */
class FileToDbPropertyMigratorTest {

    private static final String ACME_YML = "src/test/resources/acme-with-ds-tls-eab.yml";
    private static final String PROPERTY_KEY = "xroad.acme";
    private static final String DS_TLS_ALIAS = "\"dataspace-tls\"";
    private static final String DS_TLS_KID = "dstls_keyid_1";
    private static final String DS_TLS_MAC_KEY = "7f1c9e5b2a4d6f80c3e17b95a2d4f608b1e3c7a95d2f4608e1b3c7a9d2f4608e";

    @Test
    void loadPropertiesCopiesWholeFileVerbatimWithNoKeyFiltering() throws IOException {
        var migrator = new FileToDbPropertyMigrator(PROPERTY_KEY);

        Map<String, String> properties = migrator.loadProperties(ACME_YML);

        assertThat(properties).containsOnlyKeys(PROPERTY_KEY);
        assertThat(properties.get(PROPERTY_KEY)).isEqualTo(Files.readString(Path.of(ACME_YML)));
    }

    @Test
    void dsTlsEabCredentialSurvivesWholesaleAcmeMigration() {
        var migrator = new FileToDbPropertyMigrator(PROPERTY_KEY);

        Map<String, String> properties = migrator.loadProperties(ACME_YML);

        assertThat(properties.get(PROPERTY_KEY))
                .as("the dataspace-tls EAB entry must ride the wholesale file copy untouched")
                .contains(DS_TLS_ALIAS)
                .contains(DS_TLS_KID)
                .contains(DS_TLS_MAC_KEY);
    }

    @Test
    void migrateEndToEndPersistsDsTlsEabEntryToDatabase() {
        var migrator = new AutoConfirmingFileToDbPropertyMigrator(PROPERTY_KEY);

        try (MockedConstruction<DbRepository> mocked = Mockito.mockConstruction(DbRepository.class)) {
            migrator.migrate(ACME_YML, "db.props", "proxy-ui-api");

            assertThat(mocked.constructed()).hasSize(1);
            Mockito.verify(mocked.constructed().getFirst()).saveProperty(
                    ArgumentMatchers.eq(PROPERTY_KEY),
                    ArgumentMatchers.argThat(value -> value != null
                            && value.contains(DS_TLS_ALIAS)
                            && value.contains(DS_TLS_KID)
                            && value.contains(DS_TLS_MAC_KEY)),
                    ArgumentMatchers.eq("proxy-ui-api"));
        }
    }

    private static final class AutoConfirmingFileToDbPropertyMigrator extends FileToDbPropertyMigrator {
        AutoConfirmingFileToDbPropertyMigrator(String propertyKey) {
            super(propertyKey);
        }

        @Override
        String readEnv(String name) {
            return AUTO_CONFIRM_ENV.equals(name) ? "true" : null;
        }
    }
}
