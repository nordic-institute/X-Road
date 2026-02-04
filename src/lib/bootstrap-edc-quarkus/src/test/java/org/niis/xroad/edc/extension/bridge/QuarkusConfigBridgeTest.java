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
package org.niis.xroad.edc.extension.bridge;

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.system.configuration.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuarkusConfigBridgeTest {

    private QuarkusConfigBridge configBridge;

    @BeforeEach
    void setUp() {
        var testConfig = createMockMicroProfileConfig(Map.of(
                "edc.connector.name", "test-connector",
                "edc.api.port", "8080",
                "edc.enabled", "true",
                "edc.timeout.seconds", "30",
                "edc.nested.property.one", "value1",
                "edc.nested.property.two", "value2",
                "other.config.key", "other-value"
        ));
        configBridge = new QuarkusConfigBridge(testConfig);
    }

    @Nested
    class GetStringTests {

        @Test
        void shouldReturnStringValue() {
            assertEquals("test-connector", configBridge.getString("edc.connector.name"));
        }

        @Test
        void shouldReturnDefaultValueWhenKeyNotFound() {
            assertEquals("default", configBridge.getString("non.existent.key", "default"));
        }

        @Test
        void shouldReturnNullAsDefaultWhenKeyNotFound() {
            assertNull(configBridge.getString("non.existent.key", null));
        }

        @Test
        void shouldThrowExceptionWhenRequiredKeyNotFound() {
            var exception = assertThrows(EdcException.class,
                    () -> configBridge.getString("non.existent.key"));
            assertTrue(exception.getMessage().contains("No setting found"));
        }
    }

    @Nested
    class GetIntegerTests {

        @Test
        void shouldParseIntegerValue() {
            assertEquals(8080, configBridge.getInteger("edc.api.port"));
        }

        @Test
        void shouldReturnDefaultIntegerWhenKeyNotFound() {
            assertEquals(9090, configBridge.getInteger("non.existent", 9090));
        }

        @Test
        void shouldReturnNullWhenKeyNotFoundAndDefaultIsNull() {
            assertNull(configBridge.getInteger("non.existent", null));
        }

        @Test
        void shouldThrowExceptionOnInvalidIntegerFormat() {
            var badConfig = createMockMicroProfileConfig(Map.of("bad.int", "not-a-number"));
            var bridge = new QuarkusConfigBridge(badConfig);

            var exception = assertThrows(EdcException.class,
                    () -> bridge.getInteger("bad.int"));
            assertTrue(exception.getMessage().contains("cannot be parsed to integer"));
        }
    }

    @Nested
    class GetLongTests {

        @Test
        void shouldParseLongValue() {
            assertEquals(30L, configBridge.getLong("edc.timeout.seconds"));
        }

        @Test
        void shouldReturnDefaultLongWhenKeyNotFound() {
            assertEquals(60L, configBridge.getLong("non.existent", 60L));
        }

        @Test
        void shouldThrowExceptionOnInvalidLongFormat() {
            var badConfig = createMockMicroProfileConfig(Map.of("bad.long", "not-a-number"));
            var bridge = new QuarkusConfigBridge(badConfig);

            var exception = assertThrows(EdcException.class,
                    () -> bridge.getLong("bad.long"));
            assertTrue(exception.getMessage().contains("cannot be parsed to long"));
        }
    }

    @Nested
    class GetBooleanTests {

        @Test
        void shouldParseTrueValue() {
            assertTrue(configBridge.getBoolean("edc.enabled"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"true", "TRUE", "True"})
        void shouldParseTrueCaseInsensitive(String value) {
            var config = createMockMicroProfileConfig(Map.of("bool.key", value));
            var bridge = new QuarkusConfigBridge(config);

            assertTrue(bridge.getBoolean("bool.key"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"false", "FALSE", "False"})
        void shouldParseFalseCaseInsensitive(String value) {
            var config = createMockMicroProfileConfig(Map.of("bool.key", value));
            var bridge = new QuarkusConfigBridge(config);

            assertFalse(bridge.getBoolean("bool.key"));
        }

        @Test
        void shouldReturnDefaultBooleanWhenKeyNotFound() {
            assertFalse(configBridge.getBoolean("non.existent", false));
        }

        @ParameterizedTest
        @ValueSource(strings = {"yes", "no", "1", "0", "invalid"})
        void shouldThrowExceptionOnInvalidBooleanFormat(String value) {
            var badConfig = createMockMicroProfileConfig(Map.of("bad.bool", value));
            var bridge = new QuarkusConfigBridge(badConfig);

            // getBoolean(key) should throw EdcException for invalid boolean values
            var exception = assertThrows(EdcException.class,
                    () -> bridge.getBoolean("bad.bool"));
            assertTrue(exception.getMessage().contains("cannot be parsed to boolean"),
                    "Expected message to contain 'cannot be parsed to boolean' but was: " + exception.getMessage());
        }
    }

    @Nested
    class GetConfigTests {

        @Test
        void shouldReturnNestedConfig() {
            Config nestedConfig = configBridge.getConfig("edc.nested");

            assertNotNull(nestedConfig);
            assertEquals("value1", nestedConfig.getString("property.one"));
            assertEquals("value2", nestedConfig.getString("property.two"));
        }

        @Test
        void shouldReturnCurrentNodeName() {
            Config nestedConfig = configBridge.getConfig("edc.nested");

            assertEquals("nested", nestedConfig.currentNode());
        }

        @Test
        void shouldFilterEntriesForNestedConfig() {
            Config nestedConfig = configBridge.getConfig("edc.nested");
            Map<String, String> entries = nestedConfig.getEntries();

            assertEquals(2, entries.size());
            assertTrue(entries.containsKey("edc.nested.property.one"));
            assertTrue(entries.containsKey("edc.nested.property.two"));
        }
    }

    @Nested
    class MergeTests {

        @Test
        void shouldMergeConfigs() {
            var additionalConfig = createMockMicroProfileConfig(Map.of(
                    "new.key", "new-value",
                    "edc.connector.name", "overridden-name"
            ));
            var otherBridge = new QuarkusConfigBridge(additionalConfig);

            Config merged = configBridge.merge(otherBridge);

            assertEquals("new-value", merged.getString("new.key"));
            assertEquals("overridden-name", merged.getString("edc.connector.name"));
            assertEquals("8080", merged.getString("edc.api.port"));
        }
    }

    @Nested
    class PartitionTests {

        @Test
        void shouldPartitionByFirstKeySegment() {
            var partitions = configBridge.partition().toList();

            assertTrue(partitions.size() >= 2);
        }
    }

    @Nested
    class RelativeEntriesTests {

        @Test
        void shouldReturnRelativeEntries() {
            Config nestedConfig = configBridge.getConfig("edc.nested");
            Map<String, String> relativeEntries = nestedConfig.getRelativeEntries();

            assertTrue(relativeEntries.containsKey("property.one"));
            assertTrue(relativeEntries.containsKey("property.two"));
            assertFalse(relativeEntries.containsKey("edc.nested.property.one"));
        }

        @Test
        void shouldReturnRelativeEntriesWithBasePath() {
            Map<String, String> filteredEntries = configBridge.getRelativeEntries("edc.nested");

            assertTrue(filteredEntries.containsKey("edc.nested.property.one"));
            assertTrue(filteredEntries.containsKey("edc.nested.property.two"));
            assertFalse(filteredEntries.containsKey("edc.connector.name"));
        }
    }

    @Nested
    class StateTests {

        @Test
        void shouldDetectLeafConfig() {
            var leafConfig = createMockMicroProfileConfig(Map.of("single.key", "value"));
            var bridge = new QuarkusConfigBridge(leafConfig);
            Config nested = bridge.getConfig("single.key");

            assertTrue(nested.isLeaf());
        }

        @Test
        void shouldDetectNonLeafConfig() {
            assertFalse(configBridge.isLeaf());
        }

        @Test
        void shouldCheckKeyExists() {
            assertTrue(configBridge.hasKey("edc.connector.name"));
            assertFalse(configBridge.hasKey("non.existent"));
        }

        @Test
        void shouldCheckPathExists() {
            assertTrue(configBridge.hasPath("edc.nested"));
            assertTrue(configBridge.hasPath("edc.connector"));
            assertFalse(configBridge.hasPath("non.existent.path"));
        }
    }

    @Nested
    class EmptyConfigTests {

        @Test
        void shouldHandleEmptyConfig() {
            var emptyConfig = createMockMicroProfileConfig(Map.of());
            var bridge = new QuarkusConfigBridge(emptyConfig);

            assertTrue(bridge.getEntries().isEmpty());
            assertFalse(bridge.hasKey("any.key"));
            assertFalse(bridge.hasPath("any.path"));
        }
    }

    private static org.eclipse.microprofile.config.Config createMockMicroProfileConfig(Map<String, String> properties) {
        var mockConfig = mock(org.eclipse.microprofile.config.Config.class);

        when(mockConfig.getPropertyNames()).thenReturn(properties.keySet());

        when(mockConfig.getOptionalValue(any(String.class), eq(String.class)))
                .thenAnswer((Answer<Optional<String>>) invocation -> {
                    String key = invocation.getArgument(0);
                    return Optional.ofNullable(properties.get(key));
                });

        return mockConfig;
    }
}
