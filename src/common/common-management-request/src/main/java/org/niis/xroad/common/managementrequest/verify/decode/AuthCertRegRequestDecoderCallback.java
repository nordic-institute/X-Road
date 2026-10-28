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
package org.niis.xroad.common.managementrequest.verify.decode;

import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.request.AuthCertRegRequestType;

import jakarta.xml.soap.SOAPException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestParser;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestVerifier;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;

import java.util.Arrays;

import static org.niis.xroad.common.core.exception.ErrorCode.CERT_VALIDATION;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.assertAddress;

public class AuthCertRegRequestDecoderCallback extends DirectRequestDecoderCallback<AuthCertRegRequestType> {

    public AuthCertRegRequestDecoderCallback(GlobalConfProvider globalConfProvider,
                                             OcspVerifierFactory ocspVerifierFactory,
                                             ManagementRequestVerifier.DecoderCallback rootCallback) {
        super(globalConfProvider, ocspVerifierFactory, rootCallback);
    }

    @Override
    protected AuthCertRegRequestType parse(SoapMessageImpl message) throws SOAPException {
        return ManagementRequestParser.parseAuthCertRegRequest(message);
    }

    @Override
    protected SecurityServerId getServer(AuthCertRegRequestType request) {
        return request.getServer();
    }

    @Override
    protected void verifyAttachedAuthCert(AuthCertRegRequestType request, byte[] attachedAuthCert) {
        if (!Arrays.equals(attachedAuthCert, request.getAuthCert())) {
            throw XrdRuntimeException.systemException(CERT_VALIDATION, "Authentication certificates do not match.");
        }
    }

    @Override
    protected void verifyRequestFields(AuthCertRegRequestType request) {
        assertAddress(request.getAddress());
    }
}
