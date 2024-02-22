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

import ee.ria.xroad.common.message.RestMessage;

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.connector.dataplane.api.controller.ContainerRequestContextApiImpl;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.niis.xroad.edc.sig.XrdSignatureCreationException;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.edc.sig.XrdSignatureVerificationException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class XrdEdcSignService {
    private final XrdSignatureService signService;

    private final Monitor monitor;

    public Map<String, String> signPayload(DataAddress dataAddress, String responseStr) {
        monitor.debug("Signing response payload..");
        var assetId = dataAddress.getStringProperty("assetId");
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            var headerWithSig = signService.sign(assetId, responseStr, headers);
            monitor.debug("Response payload signed. Signature: " + headerWithSig);
            return headerWithSig;
        } catch (XrdSignatureCreationException e) {
            throw new RuntimeException("Failed to sign response payload", e);
        }
    }

    public void verifyRequest(ContainerRequestContextApiImpl contextApi) throws XrdSignatureVerificationException {
        var clientId = RestMessage.decodeClientId(contextApi.headers().get("X-Road-Client"));
        signService.verify(contextApi.headers(), contextApi.body().getBytes(StandardCharsets.UTF_8), clientId);

    }

}