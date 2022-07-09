/**
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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientUtilsTest {

    private List<CertificateInfo> createCertificateInfoList() {
        List<CertificateInfo> certificateInfos = new ArrayList<>();

        CertificateTestUtils.CertificateInfoBuilder certificateInfoBuilder =
                new CertificateTestUtils.CertificateInfoBuilder();

        // Create cert with good ocsp response status
        ClientId.Conf clientId1 = ClientId.Conf.create("FI", "GOV", "M1");
        certificateInfoBuilder.clientId(clientId1);
        CertificateInfo cert1 = certificateInfoBuilder.build();

        // Create cert with revoked ocsp response status
        certificateInfoBuilder.ocspStatus(new RevokedStatus(new Date(), CRLReason.certificateHold));
        CertificateInfo cert2 = certificateInfoBuilder.build();

        // Create cert with unknown ocsp response status
        certificateInfoBuilder.ocspStatus(new UnknownStatus());
        CertificateInfo cert3 = certificateInfoBuilder.build();

        certificateInfos.addAll(Arrays.asList(cert2, cert3, cert1));
        return certificateInfos;
    }

    @Test
    public void hasValidLocalSignCertTest() throws Exception {
        // Valid sign cert found
        ClientId.Conf clientId = ClientId.Conf.create("FI", "GOV", "M1");
        assertTrue(ClientUtils.hasValidLocalSignCert(clientId,
                createCertificateInfoList()));

        // No valid sign cert found
        CertificateTestUtils.CertificateInfoBuilder certBuilder =
                new CertificateTestUtils.CertificateInfoBuilder();
        certBuilder.ocspStatus(new UnknownStatus());
        CertificateInfo cert = certBuilder.build();
        assertFalse(ClientUtils.hasValidLocalSignCert(clientId, Collections.singletonList(cert)));

        // Null ocsp response status – should return false
        CertificateInfo nullCert = certBuilder.clientId(clientId).build();
        ReflectionTestUtils.setField(nullCert, "ocspBytes", null);
        assertFalse(ClientUtils.hasValidLocalSignCert(clientId, Collections.singletonList(nullCert)));

        // No valid sign cert for the client
        clientId = ClientId.Conf.create("FI", "GOV", "M2");
        assertFalse(ClientUtils.hasValidLocalSignCert(clientId,
                createCertificateInfoList()));
    }
}
