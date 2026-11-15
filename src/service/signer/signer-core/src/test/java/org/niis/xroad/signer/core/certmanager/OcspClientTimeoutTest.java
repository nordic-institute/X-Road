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
package org.niis.xroad.signer.core.certmanager;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.signer.common.config.SignerConfigKeys;
import org.niis.xroad.signer.core.config.SignerProperties;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Tests that the OCSP client applies the configured connection timeouts.
 */
class OcspClientTimeoutTest {

    @Test
    void usesDefaultTimeouts() throws Exception {
        HttpURLConnection connection = openConnection(Map.of());

        assertEquals(20000, connection.getConnectTimeout());
        assertEquals(60000, connection.getReadTimeout());
    }

    @Test
    void usesConfiguredTimeouts() throws Exception {
        HttpURLConnection connection = openConnection(Map.of(
                SignerConfigKeys.OCSP_CLIENT_CONNECT_TIMEOUT.key(), "1500",
                SignerConfigKeys.OCSP_CLIENT_READ_TIMEOUT.key(), "2500"));

        assertEquals(1500, connection.getConnectTimeout());
        assertEquals(2500, connection.getReadTimeout());
    }

    private static HttpURLConnection openConnection(Map<String, String> overrides) throws Exception {
        SignerProperties signerProperties = new SignerProperties(XRoadConfigBuilder.create()
                .register(SignerConfigKeys.instance())
                .overrides(overrides)
                .build());
        OcspClient ocspClient = new OcspClient(mock(GlobalConfProvider.class), signerProperties);

        // A bound server socket accepts the TCP handshake through its backlog, so connect() succeeds
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            URL url = new URL("http://127.0.0.1:" + server.getLocalPort() + "/");
            HttpURLConnection connection = ocspClient.createConnection(url);
            connection.disconnect();
            return connection;
        }
    }
}
