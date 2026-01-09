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
package org.niis.xroad.confclient.core;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.globalconf.model.ConfigurationAnchor;
import org.niis.xroad.globalconf.model.ConfigurationLocation;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyGlobalConfTest {
    private static final String SYSTEM_TEST_RESOURCES = "src/intTest/resources";
    private static final String CONF_ROOT = SYSTEM_TEST_RESOURCES + "/nginx-container-files/var/lib/xroad/public";
    private static final String ANCHOR_PATH = SYSTEM_TEST_RESOURCES + "/files/trusted-anchor/configuration_anchor_CS_internal.xml";

    @Test
    void verifySystemTestGlobalConfiguration(@TempDir Path confDownloadDir) {
        var anchor = new ConfigurationAnchor(ANCHOR_PATH);
        assertThat(anchor.getLocations()).isNotEmpty();

        var downloader = new MockConfigurationDownloader(confDownloadDir.toString(), 6);
        var downloadResult = downloader.download(anchor);
        assertThat(downloadResult.isSuccess())
                .withFailMessage("Configuration validation failed, run GlobalConfSignTest to re-sign global configuration.")
                .isTrue();
    }

    private static class MockConfigurationDownloader extends ConfigurationDownloader {

        MockConfigurationDownloader(String globalConfigurationDir, int configurationVersion) {
            super(globalConfigurationDir, configurationVersion);
        }

        @Override
        byte[] downloadContent(ConfigurationLocation location, ConfigurationFile file) {
            try {
                return Files.readAllBytes(Path.of(CONF_ROOT, file.getContentLocation()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        ConfigurationParser getParser() {
            return new ConfigurationParser(this) {
                @Override
                @SneakyThrows
                protected InputStream getInputStream() {
                    return new FileInputStream(Path.of(CONF_ROOT, "V6/internalconf").toFile());
                }
            };
        }

        @Override
        public URLConnection getDownloadURLConnection(URL url) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

}
