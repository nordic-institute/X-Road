/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.signer.core.protocol.handler;

import ee.ria.xroad.common.CodedException;

import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.core.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.proto.GetKeyIdForCertHashReq;
import org.niis.xroad.signer.proto.GetKeyIdForCertHashResp;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;

/**
 * Handles requests for key id based on certificate hashes.
 */
@Component
public class GetKeyIdForCertHashReqHandler extends AbstractRpcHandler<GetKeyIdForCertHashReq, GetKeyIdForCertHashResp> {

    @Override
    protected GetKeyIdForCertHashResp handle(GetKeyIdForCertHashReq request) throws Exception {
        KeyInfo keyInfo = TokenManager.getKeyInfoForCertHash(request.getCertHash());

        if (keyInfo == null) {
            throw CodedException.tr(X_CERT_NOT_FOUND, "certificate_with_hash_not_found",
                    "Certificate with hash '%s' not found", request.getCertHash());
        }

        return GetKeyIdForCertHashResp.newBuilder()
                .setKeyId(keyInfo.getId())
                .setSignMechanismName(keyInfo.getSignMechanismName())
                .build();
    }
}
