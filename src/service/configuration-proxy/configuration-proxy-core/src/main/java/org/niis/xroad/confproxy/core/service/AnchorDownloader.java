/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.confproxy.core.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.confclient.common.service.HttpUrlConnectionConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

import static org.niis.xroad.common.properties.config.keys.CommonConfigKeys.TEMP_FILES_PATH;
import static org.niis.xroad.confproxy.common.domain.ConfProxyInstance.ANCHOR_XML;
import static org.niis.xroad.confproxy.common.exceptions.ConfClientErrorCode.DOWNLOAD_ERROR;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class AnchorDownloader {
    private static final String ANCHOR_DIR_NAME = "anchors";

    private final XRoadConfig xRoadConfig;
    private final HttpUrlConnectionConfigurer httpUrlConnectionConfigurer;

    public Path downloadAnchor(String uri) {
        try {
            var sourceUri = URI.create(uri);
            var targetDir = Paths.get(xRoadConfig.value(TEMP_FILES_PATH), ANCHOR_DIR_NAME, Instant.now().getEpochSecond() + "");
            Files.createDirectories(targetDir);
            var targetPath = targetDir.resolve(ANCHOR_XML);

            if (isHttpUri(sourceUri)) {
                downloadFromHttp(sourceUri, targetPath);
            } else {
                copyFromFile(sourceUri, targetPath);
            }

            return targetPath;
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(DOWNLOAD_ERROR)
                    .details("Failed to download configuration anchor from: " + uri)
                    .cause(e)
                    .build();
        }
    }

    private boolean isHttpUri(URI uri) {
        var scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private void downloadFromHttp(URI sourceUri, Path targetPath) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) sourceUri.toURL().openConnection();
            httpUrlConnectionConfigurer.apply(connection);
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void copyFromFile(URI sourceUri, Path targetPath) throws IOException {
        Path sourcePath;
        if (sourceUri.getScheme() != null && sourceUri.getScheme().equals("file")) {
            sourcePath = Paths.get(sourceUri);
        } else {
            // Treat as a regular file path
            sourcePath = Paths.get(sourceUri.toString());
        }

        if (!Files.exists(sourcePath)) {
            throw new IOException("Source file does not exist: " + sourcePath);
        }

        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
}
