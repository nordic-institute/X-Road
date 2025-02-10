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
package org.niis.xroad.signer.core.protocol.handler;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.niis.xroad.signer.api.message.GetOcspResponses;
import org.niis.xroad.signer.api.message.GetOcspResponsesResponse;
import org.niis.xroad.signer.core.certmanager.OcspResponseManager;
import org.niis.xroad.signer.core.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.proto.GetOcspResponsesReq;
import org.niis.xroad.signer.proto.GetOcspResponsesResp;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles OCSP requests.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class GetOcspResponsesReqHandler
        extends AbstractRpcHandler<GetOcspResponsesReq, GetOcspResponsesResp> {

    private final OcspResponseManager ocspResponseManager;

    @Override
    protected GetOcspResponsesResp handle(GetOcspResponsesReq request) throws Exception {
        var message = new GetOcspResponses(
                request.getCertHashList().toArray(new String[0]));

        GetOcspResponsesResponse response = ocspResponseManager.handleGetOcspResponses(message);

        // todo return map from ocsp responses manager
        Map<String, String> ocspResponses = new HashMap<>();
        for (int i = 0; i < message.getCertHash().length; i++) {
            if (ArrayUtils.get(response.getBase64EncodedResponses(), i) != null) {
                ocspResponses.put(request.getCertHash(i), response.getBase64EncodedResponses()[i]);
            }
        }

        return GetOcspResponsesResp.newBuilder()
                .putAllBase64EncodedResponses(ocspResponses)
                .build();
    }

}
