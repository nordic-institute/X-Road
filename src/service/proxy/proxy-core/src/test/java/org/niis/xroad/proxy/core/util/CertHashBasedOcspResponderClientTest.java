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
package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.util.CertUtils;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;

import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CertHashBasedOcspResponderClientTest {

    private static final ProxyProperties.OcspResponderProperties PROPS =
            ConfigUtils.defaultConfiguration(ProxyProperties.OcspResponderProperties.class);

    /**
     * Regression guard for X-Road 7.x interoperability.
     *
     * <p>A 7.x security server's cert-hash OCSP responder only recognises the legacy
     * {@code cert=<sha1>} URL parameter. An 8.x security server's responder uses
     * {@code cert_hash=<sha256>}. During the mixed-version upgrade window an 8.x client
     * must send <b>both</b> parameters so it can fetch OCSP responses from either peer.
     */
    @Test
    void buildsUrlWithBothSha1AndSha256ParamsForXroad7Interoperability() throws Exception {
        var client = new CertHashBasedOcspResponderClient(PROPS);
        X509Certificate cert = TestCertUtil.getCaCert();

        URL url = client.createUrl("ocsp.example.test", List.of(cert));

        String expectedSha1 = Digests.hexDigest(DigestAlgorithm.SHA1, cert.getEncoded());
        String expectedSha256 = CertUtils.getHashes(List.of(cert))[0];
        String query = url.getQuery();

        assertThat(query)
                .as("8.x→7.x interop: legacy SHA-1 'cert' parameter must be present alongside 'cert_hash'")
                .contains(CertHashBasedOcspResponderClient.SHA_1_CERT_PARAM + "=" + expectedSha1)
                .contains(CertHashBasedOcspResponderClient.SHA_256_CERT_PARAM + "=" + expectedSha256);
    }
}
