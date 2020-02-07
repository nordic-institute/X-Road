/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.repository.InternalTlsCertificateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;

/**
 * test system api
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class SystemApiControllerTest {

    @MockBean
    private InternalTlsCertificateRepository mockRepository;

    @Autowired
    private SystemApiController systemApiController;

    @Test
    @WithMockUser(authorities = { "VIEW_PROXY_INTERNAL_CERT" })
    public void getSystemCertificateWithViewProxyInternalCertPermission() throws Exception {
        getSystemCertificate();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_INTERNAL_SSL_CERT" })
    public void getSystemCertificateWithViewInternalSslCertPermission() throws Exception {
        getSystemCertificate();
    }

    private void getSystemCertificate() throws IOException {
        X509Certificate x509Certificate = null;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("internal.crt")) {
            x509Certificate = CryptoUtils.readCertificate(stream);
        }
        given(mockRepository.getInternalTlsCertificate()).willReturn(x509Certificate);

        CertificateDetails certificate =
                systemApiController.getSystemCertificate().getBody();
        assertEquals("xroad2-lxd-ss1", certificate.getIssuerCommonName());
    }
}
