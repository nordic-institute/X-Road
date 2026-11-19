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
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class MessageLogBodyLoggingAndNewKeysTest {

    private static final String DB_PROPERTIES_PATH = Path.of("src/test/resources/db-test.properties")
            .toAbsolutePath()
            .toString();

    private static final String MESSAGE_BODY_LOGGING_KEY = "xroad.proxy.message-log.message-body-logging";
    private static final String SIGNER_DEADLINE_KEY = "xroad.common-rpc.channel.signer.deadline-after";
    private static final String CLEAN_TRANSACTION_BATCH_KEY = "xroad.message-log-archiver.clean-transaction-batch-size";

    private static final String THREE_NEW_KEYS_INI = """
            [message-log]
            message-body-logging=false
            clean-transaction-batch=5000

            [signer]
            client-timeout=50000
            """;

    @TempDir
    Path dir;

    private final IniUtil iniUtil = new IniUtil();

    @Test
    void flatLoaderProducesTheThreeNewKeys() throws IOException {
        var file = write("local.ini", THREE_NEW_KEYS_INI);

        assertThat(iniUtil.loadToFlatMap(file.toString(), "xroad"))
                .containsEntry(MESSAGE_BODY_LOGGING_KEY, "false")
                .containsEntry(CLEAN_TRANSACTION_BATCH_KEY, "5000")
                .containsEntry(SIGNER_DEADLINE_KEY, "50000");
    }

    @Test
    void nestedLoaderProducesTheThreeNewKeys() throws IOException {
        var file = write("local.ini", THREE_NEW_KEYS_INI);

        var flat = new TreeMap<String, Object>();
        flatten("", iniUtil.loadToNestedMap(file.toString(), "xroad"), flat);

        assertThat(flat)
                .containsEntry(MESSAGE_BODY_LOGGING_KEY, false)
                .containsEntry(CLEAN_TRANSACTION_BATCH_KEY, 5000)
                .containsEntry(SIGNER_DEADLINE_KEY, 50000);
    }

    @Test
    void legacyPropertiesMigratorProducesTheThreeNewKeys() throws IOException {
        var file = write("legacy.properties", """
                message-log.message-body-logging=false
                message-log.clean-transaction-batch=5000
                signer.client-timeout=50000
                """);

        new LegacyPropertiesMigrator().migrateProperties(file.toString());

        var migrated = loadProperties(file);
        assertThat(migrated.getProperty(MESSAGE_BODY_LOGGING_KEY)).isEqualTo("false");
        assertThat(migrated.getProperty(CLEAN_TRANSACTION_BATCH_KEY)).isEqualTo("5000");
        assertThat(migrated.getProperty(SIGNER_DEADLINE_KEY)).isEqualTo("50000");
    }

    @Test
    void soapBodyLoggingWinsOverMessageBodyLoggingWhenSoapLineComesFirst() throws IOException {
        var file = write("soap-first.ini", """
                [message-log]
                soap-body-logging=true
                message-body-logging=false
                """);

        assertThat(migrateToDb(file)).containsEntry(MESSAGE_BODY_LOGGING_KEY, "true");
    }

    @Test
    void soapBodyLoggingWinsOverMessageBodyLoggingWhenMessageLineComesFirst() throws IOException {
        var file = write("message-first.ini", """
                [message-log]
                message-body-logging=false
                soap-body-logging=true
                """);

        assertThat(migrateToDb(file)).containsEntry(MESSAGE_BODY_LOGGING_KEY, "true");
    }

    @Test
    void onlySoapBodyLoggingSetStillMapsToMessageBodyLoggingKey() throws IOException {
        var file = write("local.ini", """
                [message-log]
                soap-body-logging=true
                """);

        assertThat(migrateToDb(file)).containsEntry(MESSAGE_BODY_LOGGING_KEY, "true");
    }

    @Test
    void soapBodyLoggingInEarlierOverrideWinsOverMessageBodyLoggingInLocalIni() throws IOException {
        var override = write("override-90-operator.ini", """
                [message-log]
                soap-body-logging=false
                """);
        var local = write("local.ini", """
                [message-log]
                message-body-logging=true
                """);

        Map<String, String> db = new TreeMap<>();
        db.putAll(migrateToDb(override));
        db.putAll(migrateToDb(local));

        assertThat(db).containsEntry(MESSAGE_BODY_LOGGING_KEY, "false");
    }

    @Test
    void nestedLoaderSkipsMessageBodyLoggingWhenSoapBodyLoggingIsSetElsewhereInTheChain() throws IOException {
        write("override-90-operator.ini", """
                [message-log]
                soap-body-logging=false
                """);
        var local = write("local.ini", """
                [message-log]
                message-body-logging=true
                """);

        var flat = new TreeMap<String, Object>();
        flatten("", iniUtil.loadToNestedMap(local.toString(), "xroad"), flat);

        assertThat(flat).doesNotContainKey(MESSAGE_BODY_LOGGING_KEY);
    }

    @Test
    void messageBodyLoggingFromOverrideIsMigratedWhenNoFileSetsSoapBodyLogging() throws IOException {
        var override = write("override-90-operator.ini", """
                [message-log]
                message-body-logging=false
                """);
        write("local.ini", """
                [message-log]
                clean-transaction-batch=5000
                """);

        assertThat(migrateToDb(override)).containsEntry(MESSAGE_BODY_LOGGING_KEY, "false");
    }

    private Map<String, String> migrateToDb(Path file) {
        try (MockedConstruction<DbRepository> mockedDb = Mockito.mockConstruction(DbRepository.class)) {
            var migrator = new IniToDbMigrator() {
                @Override
                boolean confirmProceed(Map<String, String> properties) {
                    return true;
                }
            };
            migrator.migrate(file.toString(), DB_PROPERTIES_PATH);

            var dbRepo = mockedDb.constructed().getFirst();
            Map<String, String> saved = new TreeMap<>();
            Mockito.mockingDetails(dbRepo).getInvocations().stream()
                    .filter(invocation -> invocation.getMethod().getName().equals("saveProperty"))
                    .forEach(invocation -> saved.put(
                            invocation.getArgument(0, String.class),
                            invocation.getArgument(1, String.class)));
            return saved;
        }
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }

    private Properties loadProperties(Path file) throws IOException {
        var props = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            props.load(reader);
        }
        return props;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map, Map<String, Object> out) {
        map.forEach((key, value) -> {
            var path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) {
                flatten(path, (Map<String, Object>) nested, out);
            } else {
                out.put(path, value);
            }
        });
    }
}
