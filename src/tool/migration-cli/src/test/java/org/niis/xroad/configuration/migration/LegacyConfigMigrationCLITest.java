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

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyConfigMigrationCLITest {
    private static final String INPUT_INI = "src/test/resources/local-test.ini";
    private static final String INPUT_DB_PROPERTIES_SRC = "src/test/resources/db-test.properties";
    private static final String INPUT_DB_PROPERTIES = "build/db-test.properties";

    private static final String OUTPUT_YAML = "build/local.yml";
    private static final String OUTPUT_PROPERTIES = "build/db-test.properties";

    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    @Test
    void testPropertyCopy() {
        LegacyConfigMigrationCLI.main(new String[]{
                INPUT_INI,
                OUTPUT_YAML});

        var result = loadYaml("build/local.yml");

        assertEquals("test-scheme", result.getProperty("xroad.proxy.addon.op-monitor.connection.scheme"));
        assertEquals("test-scheme", result.getProperty("xroad.op-monitor.scheme"));
        assertEquals(1313, result.getProperty("xroad.proxy.addon.op-monitor.connection.port"));
        assertEquals(1313, result.getProperty("xroad.op-monitor.port"));

        assertEquals("test1,test2,test3", result.getProperty("xroad.proxy.xroad-tls-ciphers"));
        assertEquals("test1,test2,test3", result.getProperty("xroad.op-monitor.xroad-tls-ciphers"));
        assertEquals("test1,test2,test3", result.getProperty("xroad.proxy.addon.op-monitor.connection.xroad-tls-ciphers"));
    }

    @Test
    void shouldMigrateToYaml() {
        LegacyConfigMigrationCLI.main(new String[]{
                INPUT_INI,
                OUTPUT_YAML});

        var result = loadYaml("build/local.yml");

        assertEquals("0.0.0.0", result.getProperty("xroad.proxy.client-proxy.connector-host"));
        assertEquals(false, result.getProperty("xroad.proxy.client-proxy.pool-enable-connection-reuse"));
        assertEquals(5665, result.getProperty("xroad.configuration-client.port"));
        assertEquals("/var/cache/xroad", result.getProperty("xroad.signer.ocsp-cache-path"));
        assertEquals("", result.getProperty("xroad.proxy.empty-prop"));
        assertNull(result.getProperty("xroad.configuration-client.configuration-anchor-file"));
    }

    @Test
    void shouldThrowExceptionWhenMissingArgs() {
        assertThrows(IllegalArgumentException.class, () -> LegacyConfigMigrationCLI.main(new String[]{}));
    }

    @Test
    void shouldThrowExceptionWhenInputFileDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () -> LegacyConfigMigrationCLI.main(new String[]{
                "nonexistent.ini",
                OUTPUT_YAML}));
    }

    @Test
    void shouldThrowExceptionWhenOutputPathIsDir() {
        assertThrows(IllegalArgumentException.class, () -> LegacyConfigMigrationCLI.main(new String[]{
                INPUT_INI,
                "."}));
    }

    @Test
    void shouldMigratePropertiesFile() throws IOException {
        Files.copy(Paths.get(INPUT_DB_PROPERTIES_SRC), Paths.get(INPUT_DB_PROPERTIES), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        LegacyConfigMigrationCLI.main(new String[]{
                INPUT_DB_PROPERTIES
        });

        var result = new Properties();
        result.load(Files.newInputStream(Paths.get(OUTPUT_PROPERTIES)));

        // Verify mapped properties
        assertEquals("jdbc:postgresql://xrd-db:5432/xrd_ss",
                result.getProperty("xroad.db.serverconf.hibernate.connection.url"));
        assertEquals("xrd",
                result.getProperty("xroad.db.serverconf.hibernate.connection.username"));
        assertEquals("secret",
                result.getProperty("xroad.db.serverconf.hibernate.connection.password"));

        // Verify original file was backed up
        assertTrue(Files.exists(Paths.get(INPUT_DB_PROPERTIES + ".original")));
    }

    @Test
    void shouldPreserveOriginalPropertiesAsComments() throws IOException {
        Files.copy(Paths.get(INPUT_DB_PROPERTIES_SRC), Paths.get(INPUT_DB_PROPERTIES), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        LegacyConfigMigrationCLI.main(new String[]{
                INPUT_DB_PROPERTIES
        });

        var lines = Files.readAllLines(Paths.get(OUTPUT_PROPERTIES));

        // Check original content is preserved in comments
        assertTrue(lines.stream()
                .anyMatch(l -> l.contains("# serverconf.hibernate.connection.url")));
        assertTrue(lines.stream()
                .anyMatch(l -> l.contains("# serverconf.hibernate.connection.username")));
        assertTrue(lines.stream()
                .anyMatch(l -> l.contains("# serverconf.hibernate.connection.password")));
    }


    @SneakyThrows
    private PropertySource<?> loadYaml(String path) {
        Resource resourcePath = new PathResource(path);
        return yamlLoader.load("test", resourcePath).getFirst();
    }

}
