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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class HttpUrlConnectionChecker {
    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final Integer PORT_80 = 80;
    private static final Integer PORT_443 = 443;
    private final HttpUrlConnectionConfigurer httpUrlConnectionConfigurer;

    public List<ConnectionStatus> getConnectionStatuses(String localInstance, String instance, String address, String directory,
            String allowedFederations) {

        if (shouldDownload(localInstance, instance, allowedFederations)) {
            return getDownloadUrls(address, directory).stream()
                    .map(this::getConnectionStatus)
                    .toList();
        }

        return List.of();
    }

    private boolean shouldDownload(String localInstance, String instance, String allowedFederations) {
        return localInstance.equals(instance)
                || new FederationConfigurationSourceFilter(localInstance, allowedFederations).shouldDownloadConfigurationFor(instance);
    }

    private ConnectionStatus getConnectionStatus(URL url) {
        HttpURLConnection connection = null;
        try {
            assert url != null;
            connection = (HttpURLConnection) url.openConnection();
            httpUrlConnectionConfigurer.apply(connection);
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return new ConnectionStatus(getDownloadUrl(url), null, null);
            } else {
                var responseMessage = connection.getResponseMessage() != null ? connection.getResponseMessage() : "";
                throw XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_GET_VERSION_FAILED)
                        .details(String.format("%s — HTTP %d %s", getDownloadUrl(url), responseCode, responseMessage))
                        .build();
            }
        } catch (Exception e) {
            XrdRuntimeException result = XrdRuntimeException.systemException(e);
            return new ConnectionStatus(getDownloadUrl(url), result.getErrorCode(), result.getDetails());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<URL> getDownloadUrls(String address, String configurationDirectory) {
        return Stream.of(
                        getUrl(HTTP, address, PORT_80, configurationDirectory),
                        getUrl(HTTPS, address, PORT_443, configurationDirectory)
                )
                .toList();
    }

    private URL getUrl(String protocol, String address, int port, String directory) {
        try {
            return URI.create(getDownloadUrl(protocol, address, port, directory)).toURL();
        } catch (MalformedURLException e) {
            log.error("Could not create URL from address {}", address, e);
        }
        return null;
    }

    private String getDownloadUrl(URL url) {
        return getDownloadUrl(url.getProtocol(), url.getHost(), url.getPort(), url.getPath().replaceFirst("^/", ""));
    }

    private String getDownloadUrl(String protocol, String address, int port, String directory) {
        return String.format("%s://%s:%d/%s", protocol, address, port, directory);
    }

    public record ConnectionStatus(String downloadUrl, String errorCode, String errorDetails) {
    }
}
