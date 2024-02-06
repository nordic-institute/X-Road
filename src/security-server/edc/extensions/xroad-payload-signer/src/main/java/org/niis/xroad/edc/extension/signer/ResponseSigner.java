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

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.signer.SignerProxy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;

@RequiredArgsConstructor
public class ResponseSigner {
    private final JWSSigner signer = new XrdJwsSigner();

    private final Monitor monitor;

    public String signPayload(DataAddress dataAddress, String responseStr) throws Exception {
        monitor.debug("Signing response payload..");
        var assetId = dataAddress.getStringProperty("assetId");

        var bodySig = sign(assetId, responseStr);

        monitor.debug("Response payload signed. Signature: " + bodySig);
        return bodySig;
    }

    public String sign(String assetId, String dataToSign) throws Exception {
        var serviceId = ServiceId.Conf.fromEncodedId(assetId);

        SignerProxy.MemberSigningInfoDto signingInfo = SignerProxy.getMemberSigningInfo(serviceId.getClientId());

        JWSObject jwsObject = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingInfo.getKeyId()).build(),
                new Payload(dataToSign));

        jwsObject.sign(signer);

        return jwsObject.serialize(true);
    }

}
