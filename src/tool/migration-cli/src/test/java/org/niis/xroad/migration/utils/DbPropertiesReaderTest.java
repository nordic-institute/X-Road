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

package org.niis.xroad.migration.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbPropertiesReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadCredentialsWithCustomDbKey() throws IOException {
        Path propertiesPath = writeProperties("messagelog", """
            xroad.db.messagelog.hibernate.connection.url=jdbc:postgresql://127.0.0.1:5432/messagelog
            xroad.db.messagelog.hibernate.hikari.dataSource.currentSchema=messagelog
            xroad.db.messagelog.hibernate.connection.username=messagelog
            xroad.db.messagelog.hibernate.connection.password=topsecret
            """);

        DbCredentials credentials = DbPropertiesReader.readDbCredentials(propertiesPath, "messagelog");

        assertEquals("jdbc:postgresql://127.0.0.1:5432/messagelog", credentials.jdbcUrl());
        assertEquals("messagelog", credentials.username());
        assertArrayEquals("topsecret".toCharArray(), credentials.password());
        assertEquals("messagelog", credentials.schema());
    }

    @Test
    void shouldFailWhenRequiredPropertyMissing() throws IOException {
        Path propertiesPath = writeProperties("serverconf", """
            xroad.db.serverconf.hibernate.connection.url=jdbc:postgresql://127.0.0.1:5432/serverconf
            xroad.db.serverconf.hibernate.connection.username=serverconf
            """);

        assertThrows(RuntimeException.class, () -> DbPropertiesReader.readDbCredentials(propertiesPath, null));
    }

    @Test
    void shouldReadCentralServerKeysKeepingSchemaListUnchanged() throws IOException {
        Path propertiesPath = writeProperties("cs", centralServerProperties("""
            spring.datasource.hikari.data-source-properties.currentSchema=centerui,public
            """));

        DbCredentials credentials =
                DbPropertiesReader.readForInstallation(propertiesPath, InstallationType.CENTRAL_SERVER);

        assertEquals("jdbc:postgresql://127.0.0.1:5432/centerui_production", credentials.jdbcUrl());
        assertEquals("centerui", credentials.username());
        assertArrayEquals("cs-secret".toCharArray(), credentials.password());
        assertEquals("centerui,public", credentials.schema());
    }

    @Test
    void shouldReadSecurityServerKeysIncludingSchema() throws IOException {
        Path propertiesPath = writeProperties("ss", securityServerProperties("""
            xroad.db.serverconf.hibernate.hikari.dataSource.currentSchema=serverconf
            """));

        DbCredentials credentials =
                DbPropertiesReader.readForInstallation(propertiesPath, InstallationType.SECURITY_SERVER);

        assertEquals("jdbc:postgresql://127.0.0.1:5432/serverconf", credentials.jdbcUrl());
        assertEquals("serverconf", credentials.username());
        assertArrayEquals("ss-secret".toCharArray(), credentials.password());
        assertEquals("serverconf", credentials.schema());
    }

    @Test
    void shouldReturnNoSchemaWhenCentralServerSchemaKeyIsAbsent() throws IOException {
        Path propertiesPath = writeProperties("cs", centralServerProperties(""));

        DbCredentials credentials =
                DbPropertiesReader.readForInstallation(propertiesPath, InstallationType.CENTRAL_SERVER);

        assertNull(credentials.schema());
    }

    @Test
    void shouldReturnNoSchemaWhenSecurityServerSchemaKeyIsAbsent() throws IOException {
        Path propertiesPath = writeProperties("ss", securityServerProperties(""));

        DbCredentials credentials =
                DbPropertiesReader.readForInstallation(propertiesPath, InstallationType.SECURITY_SERVER);

        assertNull(credentials.schema());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "spring.datasource.url",
        "spring.datasource.username",
        "spring.datasource.password"})
    void shouldNameFileAndKeyWhenCentralServerKeyIsMissing(String missingKey) throws IOException {
        Path propertiesPath = writeProperties("cs", centralServerProperties("")
                .lines()
                .filter(line -> !line.startsWith(missingKey + "="))
                .collect(Collectors.joining("\n")));

        var exception = assertThrows(IllegalStateException.class,
                () -> DbPropertiesReader.readForInstallation(propertiesPath, InstallationType.CENTRAL_SERVER));

        assertTrue(exception.getMessage().contains(propertiesPath.toString()));
        assertTrue(exception.getMessage().contains("'" + missingKey + "'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "xroad.db.serverconf.hibernate.connection.url",
        "xroad.db.serverconf.hibernate.connection.username",
        "xroad.db.serverconf.hibernate.connection.password"})
    void shouldNameFileAndKeyWhenSecurityServerKeyIsMissing(String missingKey) throws IOException {
        Path propertiesPath = writeProperties("ss", securityServerProperties("")
                .lines()
                .filter(line -> !line.startsWith(missingKey + "="))
                .collect(Collectors.joining("\n")));

        var exception = assertThrows(IllegalStateException.class,
                () -> DbPropertiesReader.readForInstallation(propertiesPath, InstallationType.SECURITY_SERVER));

        assertTrue(exception.getMessage().contains(propertiesPath.toString()));
        assertTrue(exception.getMessage().contains("'" + missingKey + "'"));
    }

    @Test
    void shouldFailOnSecurityServerFileInCentralServerMode() throws IOException {
        Path propertiesPath = writeProperties("ss", securityServerProperties(""));

        var exception = assertThrows(IllegalStateException.class,
                () -> DbPropertiesReader.readForInstallation(propertiesPath, InstallationType.CENTRAL_SERVER));

        assertTrue(exception.getMessage().contains(propertiesPath.toString()));
        assertTrue(exception.getMessage().contains("'spring.datasource.url'"));
    }

    @Test
    void shouldFailOnCentralServerFileInSecurityServerMode() throws IOException {
        Path propertiesPath = writeProperties("cs", centralServerProperties(""));

        var exception = assertThrows(IllegalStateException.class,
                () -> DbPropertiesReader.readForInstallation(propertiesPath, InstallationType.SECURITY_SERVER));

        assertTrue(exception.getMessage().contains(propertiesPath.toString()));
        assertTrue(exception.getMessage().contains("'xroad.db.serverconf.hibernate.connection.url'"));
    }

    private static String centralServerProperties(String extra) {
        return """
            spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/centerui_production
            spring.datasource.username=centerui
            spring.datasource.password=cs-secret
            """ + extra;
    }

    private static String securityServerProperties(String extra) {
        return """
            xroad.db.serverconf.hibernate.connection.url=jdbc:postgresql://127.0.0.1:5432/serverconf
            xroad.db.serverconf.hibernate.connection.username=serverconf
            xroad.db.serverconf.hibernate.connection.password=ss-secret
            """ + extra;
    }

    private Path writeProperties(String filePrefix, String contents) throws IOException {
        Path propertiesFile = Files.createTempFile(tempDir, filePrefix, ".properties");
        Files.writeString(propertiesFile, contents);
        return propertiesFile;
    }
}


