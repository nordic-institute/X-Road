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
package org.niis.xroad.common.acme.spring.provider;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.niis.xroad.common.acme.AcmeProfileIdContext;
import org.niis.xroad.common.acme.provider.AcmeXroadHttpConnector;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.connector.NetworkSettings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * {@link AcmeProfileIdConnection} holds no CSR- or CA-list-derived state: it just reads whatever profile id
 * {@link AcmeProfileIdContext} carries for the call in flight. These tests exercise that directly, bypassing
 * acme4j's session/order machinery entirely.
 */
class AcmeProfileIdConnectionTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private AcmeProfileIdConnection connection;
    private Session session;
    private URL requestUrl;

    @BeforeEach
    void setUp() throws Exception {
        connection = new AcmeProfileIdConnection(new AcmeXroadHttpConnector(new NetworkSettings()));
        session = new Session(wm.getRuntimeInfo().getHttpBaseUrl() + "/directory");
        requestUrl = new URL(wm.getRuntimeInfo().getHttpBaseUrl() + "/some-resource");

        wm.stubFor(get(urlEqualTo("/some-resource")).willReturn(noContent()));
    }

    @Test
    void sendsProfileIdHeaderWhenAProfileIdIsActiveOnTheCallingThread() throws Exception {
        AcmeProfileIdContext.runWithProfileId("sign-profile-id", () -> {
            try {
                connection.sendRequest(session, requestUrl, body -> { });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        });

        wm.verify(getRequestedFor(urlEqualTo("/some-resource"))
                .withHeader("User-Agent", equalTo("profile_id=sign-profile-id " + AcmeXroadHttpConnector.XROAD_ACME_USER_AGENT)));
    }

    @Test
    void sendsPlainUserAgentWhenNoProfileIdIsActive() throws IOException {
        connection.sendRequest(session, requestUrl, body -> { });

        wm.verify(getRequestedFor(urlEqualTo("/some-resource"))
                .withHeader("User-Agent", equalTo(AcmeXroadHttpConnector.XROAD_ACME_USER_AGENT)));
    }
}
