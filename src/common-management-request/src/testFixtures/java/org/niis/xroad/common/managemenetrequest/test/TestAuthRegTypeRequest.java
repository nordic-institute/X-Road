/**
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
package org.niis.xroad.common.managemenetrequest.test;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;

import org.eclipse.jetty.util.MultiPartOutputStream;

import java.security.PrivateKey;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_SIG_ALGO_ID;

public class TestAuthRegTypeRequest extends TestBaseManagementRequest {
    private final byte[] authCert;
    private final PrivateKey ownerKey;
    private final PrivateKey authKey;
    private final byte[] ownerCert;
    private final byte[] ownerCertOcsp;

    public TestAuthRegTypeRequest(byte[] authCert, byte[] ownerCert, byte[] ownerCertOcsp, SoapMessageImpl request,
                                  PrivateKey authKey, PrivateKey ownerKey) {
        super(request.getBytes());
        this.authCert = authCert;

        this.ownerCert = ownerCert;
        this.ownerCertOcsp = ownerCertOcsp;
        this.ownerKey = ownerKey;
        this.authKey = authKey;
    }

    @Override
    protected void writeMultipart(MultiPartOutputStream multipart) throws Exception {
        writeSignatures(multipart);
        writeCerts(multipart);
    }

    private void writeCerts(MultiPartOutputStream multipart) throws Exception {
        // Write authentication certificate
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(authCert);

        // Write security server owner certificate and corresponding OCSP response
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(ownerCert);
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(ownerCertOcsp);
    }

    private void writeSignatures(MultiPartOutputStream multipart) throws Exception {
        String signAlgoId = CryptoUtils.SHA512WITHRSA_ID;
        String[] authSignaturePartHeaders = {HEADER_SIG_ALGO_ID + ": " + signAlgoId};
        String[] ownerSignaturePartHeaders = {HEADER_SIG_ALGO_ID + ": " + signAlgoId};

        multipart.startPart(MimeTypes.BINARY, authSignaturePartHeaders);
        multipart.write(createSignature(authKey, signAlgoId));

        multipart.startPart(MimeTypes.BINARY, ownerSignaturePartHeaders);
        multipart.write(createSignature(ownerKey, signAlgoId));
    }
}
