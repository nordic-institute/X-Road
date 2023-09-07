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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.signer.proto.CertificateRequestFormat;

import static org.junit.Assert.assertEquals;

/**
 * test CsrFilenameCreatorTest
 */
public class CsrFilenameCreatorTest {

    private CsrFilenameCreator csrFilenameCreator;
    private static final String DATE = "20190228";

    @Before
    public void setup() throws Exception {
        csrFilenameCreator = new CsrFilenameCreator() {
            @Override
            String createDateString() {
                return DATE;
            }
        };
    }

    @Test
    public void createCsrFilename() {
        SecurityServerId.Conf securityServerId = SecurityServerId.Conf.create("I", "MEMCLASS", "MEMCODE", "SERVERCODE");
        ClientId.Conf memberId = ClientId.Conf.create("I", "MEMCLASS", "MEMCODE", null);
        String authFilename = csrFilenameCreator.createCsrFilename(KeyUsageInfo.AUTHENTICATION,
                CertificateRequestFormat.PEM,
                memberId, securityServerId);
        assertEquals("auth_csr_" + DATE + "_securityserver_I_MEMCLASS_MEMCODE_SERVERCODE.pem", authFilename);
        String signFilename = csrFilenameCreator.createCsrFilename(KeyUsageInfo.SIGNING,
                CertificateRequestFormat.DER,
                memberId, securityServerId);
        assertEquals("sign_csr_" + DATE + "_member_I_MEMCLASS_MEMCODE.der", signFilename);
    }

    @Test
    public void createInternalCertCsrFilename() {
        String filename = csrFilenameCreator.createInternalCsrFilename();
        assertEquals(CsrFilenameCreator.INTERNAL_CSR_FILE_PREFIX + DATE
                + CsrFilenameCreator.INTERNAL_CSR_FILE_EXTENSION, filename);
    }
}
