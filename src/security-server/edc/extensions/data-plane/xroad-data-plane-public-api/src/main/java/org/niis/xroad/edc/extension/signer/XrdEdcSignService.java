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
package org.niis.xroad.edc.extension.signer;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.edc.sig.SignatureResponse;
import org.niis.xroad.edc.sig.XrdSignatureCreationException;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.edc.sig.XrdSignatureVerificationException;

@RequiredArgsConstructor
public class XrdEdcSignService {
    private final XrdSignatureService signService;

    private final Monitor monitor;

    public SignatureResponse sign(ServiceId.Conf serviceId, byte[] message, String attachmentDigest) {
        monitor.debug("Signing request payload using digest..");
        try {
            return signService.sign(serviceId.getClientId(), message, attachmentDigest);
        } catch (XrdSignatureCreationException e) {
            throw new RuntimeException("Failed to sign response payload", e);
        }
    }

    public SignatureResponse sign(ServiceId.Conf serviceId, byte[] message) {
        monitor.debug("Signing request payload..");
        try {
            return signService.sign(serviceId.getClientId(), message);
        } catch (XrdSignatureCreationException e) {
            throw new RuntimeException("Failed to sign response payload", e);
        }
    }

    public void verifyRequest(String signature, byte[] message, byte[] attachment,
                              ClientId clientId) throws XrdSignatureVerificationException {
        signService.verify(signature, message, attachment, clientId);
    }

}