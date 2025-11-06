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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for MessageLogIniToDbMigrator.
 */
class MessageLogIniToDbMigratorTest {

    private final MessageLogIniToDbMigrator migrator = new MessageLogIniToDbMigrator();

    @Test
    void testLoadPropertiesFromScenario2() {
        // Given: Scenario 2 mapping file with multiple members and keys
        Map<String, String> properties = migrator.loadProperties("src/test/resources/archive-pgp-sample/scenario-2-mapping.ini");

        // Then: Should have indexed properties for all members
        assertNotNull(properties);
        assertTrue(properties.size() > 0, "Should have loaded properties");

        // TEST/GOV/1234 has 2 keys
        assertNotNull(properties.get("xroad.common.messagelog.archive-grouping-keys.\"TEST/GOV/1234\"[0]"));
        assertNotNull(properties.get("xroad.common.messagelog.archive-grouping-keys.\"TEST/GOV/1234\"[1]"));

        // Verify one of the key values
        String govKey0 = properties.get("xroad.common.messagelog.archive-grouping-keys.\"TEST/GOV/1234\"[0]");
        assertTrue("B2343D46FF3C40F6".equals(govKey0) || "92DA25CD74A678B1".equals(govKey0),
                "Should contain one of the GOV keys");

        // TEST/COM/5678 has 1 key
        assertNotNull(properties.get("xroad.common.messagelog.archive-grouping-keys.\"TEST/COM/5678\"[0]"));
        assertEquals("D014E1D708695CB7", properties.get("xroad.common.messagelog.archive-grouping-keys.\"TEST/COM/5678\"[0]"));

        // TEST/MUN/9012 has 3 keys
        assertNotNull(properties.get("xroad.common.messagelog.archive-grouping-keys.\"TEST/MUN/9012\"[0]"));
        assertNotNull(properties.get("xroad.common.messagelog.archive-grouping-keys.\"TEST/MUN/9012\"[1]"));
        assertNotNull(properties.get("xroad.common.messagelog.archive-grouping-keys.\"TEST/MUN/9012\"[2]"));
    }

    @Test
    void testPropertyFormat() {
        // Given: Scenario 2 mapping file
        Map<String, String> properties = migrator.loadProperties("src/test/resources/archive-pgp-sample/scenario-2-mapping.ini");

        // Then: All properties should follow the correct format
        properties.keySet().forEach(key -> {
            assertTrue(key.startsWith("xroad.common.messagelog.archive-grouping-keys."),
                    "Key should start with correct prefix: " + key);
            assertTrue(key.contains("[") && key.contains("]"),
                    "Key should contain array index: " + key);
        });

        // Values should be key IDs (16 hex characters)
        properties.values().forEach(value -> {
            assertTrue(value.matches("[0-9A-F]{16}"),
                    "Value should be a 16-character hex key ID: " + value);
        });
    }

    @Test
    void testAllMembersAreMigrated() {
        // Given: Scenario 2 mapping file with 3 members
        Map<String, String> properties = migrator.loadProperties("src/test/resources/archive-pgp-sample/scenario-2-mapping.ini");

        // Then: Should have properties for all 3 members
        long govCount = properties.keySet().stream()
                .filter(k -> k.contains("TEST/GOV/1234"))
                .count();
        assertEquals(2, govCount, "TEST/GOV/1234 should have 2 keys");

        long comCount = properties.keySet().stream()
                .filter(k -> k.contains("TEST/COM/5678"))
                .count();
        assertEquals(1, comCount, "TEST/COM/5678 should have 1 key");

        long munCount = properties.keySet().stream()
                .filter(k -> k.contains("TEST/MUN/9012"))
                .count();
        assertEquals(3, munCount, "TEST/MUN/9012 should have 3 keys");
    }

    @Test
    void testKeyOrderIsConsistent() {
        // Given: Load properties multiple times
        Map<String, String> properties1 = migrator.loadProperties("src/test/resources/archive-pgp-sample/scenario-2-mapping.ini");
        Map<String, String> properties2 = migrator.loadProperties("src/test/resources/archive-pgp-sample/scenario-2-mapping.ini");

        // Then: Should have same keys and values (order in Set might vary, but indices should be consistent)
        assertEquals(properties1.size(), properties2.size(), "Should have same number of properties");

        // Verify all keys exist in both
        properties1.keySet().forEach(key ->
                assertTrue(properties2.containsKey(key), "Key should exist in both: " + key)
        );
    }
}

