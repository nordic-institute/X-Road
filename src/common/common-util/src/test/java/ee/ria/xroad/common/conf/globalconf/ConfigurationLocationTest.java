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
package ee.ria.xroad.common.conf.globalconf;

import org.junit.Test;

import javax.net.ssl.HttpsURLConnection;

import java.io.IOException;
import java.net.URLConnection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ConfigurationLocation}
 */
public class ConfigurationLocationTest {
    private static final String TLS_CERTIFICATION_VERIFICATION_ENABLED = "xroad.configuration-client.global_conf_tls_cert_verification";
    private static final String HOSTNAME_VERIFICATION_ENABLED = "xroad.configuration-client.global_conf_hostname_verification";

    /**
     * Checks that {@link ConfigurationLocation} uses connections that timeout after a period of time.
     * @throws IOException
     */
    @Test
    public void connectionsTimeout() throws IOException {
        URLConnection connection = ConfigurationLocation.getDownloadURLConnection("https://configurationLocationTestu.com");
        assertEquals(ConfigurationLocation.READ_TIMEOUT, connection.getReadTimeout());
        assertTrue(connection.getReadTimeout() > 0);
    }

    @Test
    public void connectionShouldWorkAfterDisablingTlsCertificationAndHostnameVerification() throws IOException {
        System.setProperty(TLS_CERTIFICATION_VERIFICATION_ENABLED, "false");
        System.setProperty(HOSTNAME_VERIFICATION_ENABLED, "false");
        HttpsURLConnection connection =
                (HttpsURLConnection) ConfigurationLocation.getDownloadURLConnection("https://ConfigurationLocationTest.com");
        assertEquals("NO_OP", connection.getHostnameVerifier().toString());
    }
}
