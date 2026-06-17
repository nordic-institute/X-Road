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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SinglePropertySetterTest {

    private static final String DB_PROPERTIES_PATH = Paths.get("src/test/resources/db-test.properties")
            .toAbsolutePath()
            .toString();

    @Test
    void loadPropertiesReturnsSingleEntry() {
        var setter = new SinglePropertySetter("xroad.sample.property", "sample");

        Map<String, String> properties = setter.loadProperties("ignored");

        assertEquals(Map.of("xroad.sample.property", "sample"), properties);
    }

    @Nested
    class CliValidation {

        @Test
        void throwsWhenArgumentCountInvalid() {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> SinglePropertySetter.main(new String[]{"/etc/xroad/db.properties", "only-two"}));

            assertEquals("Invalid number of arguments provided.", exception.getMessage());
        }

        @Test
        void throwsWhenPropertyKeyBlank() {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> SinglePropertySetter.main(new String[]{DB_PROPERTIES_PATH, " ", "value"}));

            assertEquals("Property key or value cannot be empty", exception.getMessage());
        }

        @Test
        void throwsWhenPropertyValueBlank() {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> SinglePropertySetter.main(new String[]{DB_PROPERTIES_PATH, "prop.key", ""}));

            assertEquals("Property key or value cannot be empty", exception.getMessage());
        }
    }

    @Nested
    class PersistenceFlow {

        @Test
        void savesPropertyWithoutScope() {
            try (MockedConstruction<DbRepository> mockedDb = Mockito.mockConstruction(DbRepository.class)) {
                new TestSinglePropertySetter("prop.key", "propValue")
                        .migrate("not/used", DB_PROPERTIES_PATH);

                var dbRepo = mockedDb.constructed().getFirst();
                Mockito.verify(dbRepo).saveProperty("prop.key", "propValue", null);
                Mockito.verify(dbRepo).close();
                Mockito.verifyNoMoreInteractions(dbRepo);
            }
        }

        @Test
        void savesPropertyWithScope() {
            try (MockedConstruction<DbRepository> mockedDb = Mockito.mockConstruction(DbRepository.class)) {
                new TestSinglePropertySetter("prop.key", "propValue")
                        .migrate("not/used", DB_PROPERTIES_PATH, "signer");

                assertEquals(1, mockedDb.constructed().size());
                var dbRepo = mockedDb.constructed().getFirst();
                Mockito.verify(dbRepo).saveProperty("prop.key", "propValue", "signer");
                Mockito.verify(dbRepo).close();
                Mockito.verifyNoMoreInteractions(dbRepo);
            }
        }

        static class TestSinglePropertySetter extends SinglePropertySetter {
            TestSinglePropertySetter(String propertyKey, String propertyValue) {
                super(propertyKey, propertyValue);
            }

            @Override
            boolean confirmProceed(Map<String, String> properties) {
                return true; // auto-confirm for testing
            }
        }
    }

}
