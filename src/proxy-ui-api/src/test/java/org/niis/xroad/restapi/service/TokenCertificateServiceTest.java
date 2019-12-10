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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.CodedException;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.niis.xroad.restapi.service.TokenCertificateService.CERT_NOT_FOUND_FAULT_CODE;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class TokenCertificateServiceTest {

    private static final String MISSING_CERTIFICATE_HASH = "MISSING_HASH";

    @Autowired
    private TokenCertificateService tokenCertificateService;

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @Before
    public void setup() throws Exception {

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String hash = (String) args[0];
            if (MISSING_CERTIFICATE_HASH.equals(hash)) {
                throw new CodedException(CERT_NOT_FOUND_FAULT_CODE);
            }

            return null;
        }).when(signerProxyFacade).deactivateCert(any());
    }

    @Test(expected = CertificateNotFoundException.class)
    public void activateCertificate() throws CertificateNotFoundException {
        try {
            tokenCertificateService.activateCertificate(MISSING_CERTIFICATE_HASH);
        } catch (CodedException expected) {
            assertEquals(expected.getFaultCode(), CERT_NOT_FOUND_FAULT_CODE);
        }
    }

    @Test(expected = CertificateNotFoundException.class)
    public void deactivateCertificate() throws CertificateNotFoundException {
        try {
            tokenCertificateService.deactivateCertificate(MISSING_CERTIFICATE_HASH);
        } catch (CodedException e) {
            assertEquals(e.getFaultCode(), CERT_NOT_FOUND_FAULT_CODE);
        }
    }

}
