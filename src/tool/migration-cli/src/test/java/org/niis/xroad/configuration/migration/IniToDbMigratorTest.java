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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IniToDbMigratorTest {

    private static final String DB_PROPERTIES_PATH = Paths.get("src/test/resources/db-test.properties")
            .toAbsolutePath()
            .toString();

    private static final String SCENARIO_2_INI = "src/test/resources/archive-pgp-sample/scenario-2-local.ini";
    private static final String MAPPING_FILE_PATH = "src/test/resources/archive-pgp-sample/scenario-2-mapping.ini";

    static class TestIniToDbMigrator extends IniToDbMigrator {
        @Override
        boolean confirmProceed(Map<String, String> properties) {
            return true;
        }
    }

    @Test
    void archiveEncryptionKeysConfigIsSkippedWhileOtherArchiveSettingsMigrate() {
        Map<String, String> saved = migrateAndCollectSavedProperties();

        assertThat(saved).containsEntry("xroad.message-log-encryption.archive.grouping-strategy", "MEMBER");
        assertThat(saved).containsEntry("xroad.message-log-encryption.archive.encryption-enabled", "true");
        assertThat(saved).containsEntry("xroad.message-log-encryption.archive.default-key-id", "FBA2E67AD8988E02");

        assertThat(saved.keySet()).noneMatch(key -> key.contains("grouping-keys"));
        assertThat(saved.values()).noneMatch(value -> value.contains(MAPPING_FILE_PATH));
    }

    @Test
    void keyAlgorithmValuesSavedAsExactConstants() {
        Map<String, String> saved = migrateAndCollectSavedProperties();

        assertThat(saved).containsEntry("xroad.proxy-ui-api.authentication-key-algorithm", "EC");
        assertThat(saved).containsEntry("xroad.proxy-ui-api.signing-key-algorithm", "RSA");
    }

    @Test
    void rerunGivesSameRows() {
        Map<String, String> firstRun = migrateAndCollectSavedProperties();
        Map<String, String> secondRun = migrateAndCollectSavedProperties();

        assertThat(secondRun).isEqualTo(firstRun);
    }

    private Map<String, String> migrateAndCollectSavedProperties() {
        try (MockedConstruction<DbRepository> mockedDb = Mockito.mockConstruction(DbRepository.class)) {
            new TestIniToDbMigrator().migrate(SCENARIO_2_INI, DB_PROPERTIES_PATH);

            var dbRepo = mockedDb.constructed().getFirst();
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(dbRepo, Mockito.atLeastOnce()).saveProperty(keyCaptor.capture(), valueCaptor.capture());

            Map<String, String> saved = new LinkedHashMap<>();
            var keys = keyCaptor.getAllValues();
            var values = valueCaptor.getAllValues();
            for (int i = 0; i < keys.size(); i++) {
                saved.put(keys.get(i), values.get(i));
            }
            return saved;
        }
    }

}
