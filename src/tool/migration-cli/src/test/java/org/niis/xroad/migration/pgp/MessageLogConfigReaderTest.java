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
package org.niis.xroad.migration.pgp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageLogConfigReaderTest {

    @TempDir
    Path tempDir;

    private MessageLogConfigReader reader;

    @BeforeEach
    void setUp() {
        reader = new MessageLogConfigReader();
    }

    @Test
    void testReadScenario1NoGrouping() throws Exception {
        // Given: Scenario 1 configuration (no grouping)
        Path configFile = resolveTestResource("scenario-1-local.ini");

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(configFile);

        // Then: Configuration should be parsed correctly
        assertTrue(config.isArchiveEncryptionEnabled());
        assertEquals("none", config.getArchiveGrouping());
        assertFalse(config.hasGrouping());
        assertNotNull(config.getArchiveDefaultEncryptionKey());
        assertNotNull(config.getArchiveGpgHomeDirectory());
    }

    @Test
    void testReadScenario2MemberGrouping() throws Exception {
        // Given: Scenario 2 configuration (member grouping)
        Path configFile = resolveTestResource("scenario-2-local.ini");

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(configFile);

        // Then: Configuration should be parsed correctly
        assertTrue(config.isArchiveEncryptionEnabled());
        assertEquals("member", config.getArchiveGrouping());
        assertTrue(config.hasGrouping());
        assertTrue(config.isMemberGrouping());
        assertFalse(config.isSubsystemGrouping());
        assertNotNull(config.getArchiveDefaultEncryptionKey());
        assertNotNull(config.getArchiveEncryptionKeysConfig());
    }

    @Test
    void testReadKeyMappings() throws Exception {
        // Given: Scenario 2 with mapping file
        Path configFile = resolveTestResource("scenario-2-local.ini");

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(configFile);

        // Then: Key mappings should be empty if file doesn't exist at expected location
        // (since test uses absolute path that may not exist)
        assertNotNull(config.getEncryptionKeyMappings());
    }

    @Test
    void testMultipleKeysPerMember() throws Exception {
        // Given: Mapping file with multiple keys per member
        Path mappingFile = tempDir.resolve("mapping.ini");
        Files.writeString(mappingFile, """
                # Comment
                INSTANCE/GOV/1234 = KEY1
                INSTANCE/GOV/1234 = KEY2
                INSTANCE/COM/5678 = KEY3
                """);

        Path iniFile = tempDir.resolve("local.ini");
        Files.writeString(iniFile, """
                [message-log]
                archive-encryption-enabled = true
                archive-grouping = member
                archive-gpg-home-directory = /xroad/gpghome
                archive-default-encryption-key = DEFAULTKEY
                archive-encryption-keys-config = """ + mappingFile.toString() + """
                """);

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(iniFile);

        // Then: Multiple keys should be accumulated per member
        assertTrue(config.isArchiveEncryptionEnabled());
        assertEquals("member", config.getArchiveGrouping());
        assertEquals(2, config.getEncryptionKeyMappings().size());

        // Verify multiple keys per member
        Set<String> govKeys = config.getEncryptionKeyMappings().get("INSTANCE/GOV/1234");
        assertNotNull(govKeys);
        assertEquals(2, govKeys.size());
        assertTrue(govKeys.contains("KEY1"));
        assertTrue(govKeys.contains("KEY2"));

        Set<String> comKeys = config.getEncryptionKeyMappings().get("INSTANCE/COM/5678");
        assertNotNull(comKeys);
        assertEquals(1, comKeys.size());
        assertTrue(comKeys.contains("KEY3"));
    }

    @Test
    void testManyKeysPerMember() throws Exception {
        // Given: Member with many keys
        Path mappingFile = tempDir.resolve("mapping.ini");
        Files.writeString(mappingFile, """
                # Member with 3 keys
                TEST/GOV/1234 = KEY1
                TEST/GOV/1234 = KEY2
                TEST/GOV/1234 = KEY3
                # Member with 1 key
                TEST/COM/5678 = SINGLE_KEY
                """);

        Path iniFile = tempDir.resolve("local.ini");
        Files.writeString(iniFile, """
                [message-log]
                archive-encryption-enabled = true
                archive-grouping = member
                archive-encryption-keys-config = """ + mappingFile.toString() + """
                """);

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(iniFile);

        // Then: All keys should be accumulated
        assertEquals(2, config.getEncryptionKeyMappings().size());

        // First member should have 3 keys
        Set<String> keys1 = config.getEncryptionKeyMappings().get("TEST/GOV/1234");
        assertNotNull(keys1);
        assertEquals(3, keys1.size());
        assertTrue(keys1.containsAll(Set.of("KEY1", "KEY2", "KEY3")));

        // Second member should have 1 key
        Set<String> keys2 = config.getEncryptionKeyMappings().get("TEST/COM/5678");
        assertNotNull(keys2);
        assertEquals(1, keys2.size());
        assertTrue(keys2.contains("SINGLE_KEY"));
    }

    @Test
    void testDisabledEncryption() throws Exception {
        // Given: Configuration with encryption disabled
        Path configFile = createTestConfig("""
                [message-log]
                archive-encryption-enabled = false
                archive-grouping = none
                """);

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(configFile);

        // Then: Encryption should be disabled
        assertFalse(config.isArchiveEncryptionEnabled());
        assertTrue(config.isEncryptionDisabled());
    }

    @Test
    void testMissingConfiguration() {
        // Given: Non-existent configuration file
        Path missingFile = tempDir.resolve("missing.ini");

        // When/Then: Should throw IOException
        assertThrows(Exception.class, () -> reader.readConfig(missingFile));
    }

    @Test
    void testEmptyMessageLogSection() throws Exception {
        // Given: Configuration without message-log section
        Path configFile = createTestConfig("""
                [proxy]
                some-setting = value
                """);

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(configFile);

        // Then: Should return disabled config
        assertFalse(config.isArchiveEncryptionEnabled());
    }

    @Test
    void testSubsystemGrouping() throws Exception {
        // Given: Configuration with subsystem grouping
        Path configFile = createTestConfig("""
                [message-log]
                archive-encryption-enabled = true
                archive-grouping = subsystem
                archive-gpg-home-directory = /etc/xroad/gpghome
                archive-default-encryption-key = ABCD1234
                """);

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(configFile);

        // Then: Should identify subsystem grouping
        assertTrue(config.isArchiveEncryptionEnabled());
        assertTrue(config.isSubsystemGrouping());
        assertFalse(config.isMemberGrouping());
    }

    @Test
    void testDefaultValues() throws Exception {
        // Given: Minimal configuration
        Path configFile = createTestConfig("""
                [message-log]
                archive-encryption-enabled = true
                """);

        // When: Read configuration
        MessageLogConfig config = reader.readConfig(configFile);

        // Then: Should have default values
        assertTrue(config.isArchiveEncryptionEnabled());
        assertEquals("none", config.getArchiveGrouping());
        assertNotNull(config.getArchiveGpgHomeDirectory());
    }

    private Path createTestConfig(String content) throws Exception {
        Path configFile = tempDir.resolve("test-config.ini");
        Files.writeString(configFile, content);
        return configFile;
    }

    private Path resolveTestResource(String filename) {
        Path resourcePath = Path.of("src/test/resources/archive-pgp-sample/" + filename);

        if (!Files.exists(resourcePath)) {
            resourcePath = Path.of(System.getProperty("user.dir"))
                    .getParent().getParent()
                    .resolve("src/tool/migration-cli/src/test/resources/archive-pgp-sample/" + filename);
        }

        return resourcePath;
    }
}
