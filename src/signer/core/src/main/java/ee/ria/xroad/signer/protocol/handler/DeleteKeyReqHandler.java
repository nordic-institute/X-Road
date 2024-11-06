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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRpcHandler;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.TokenAndKey;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.DeleteKeyReq;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Component;

/**
 * Handles key deletions.
 */
@Slf4j
@Component
public class DeleteKeyReqHandler extends AbstractRpcHandler<DeleteKeyReq, Empty> {

    @Override
    protected Empty handle(DeleteKeyReq request) throws Exception {
        TokenAndKey tokenAndKey =
                TokenManager.findTokenAndKey(request.getKeyId());

        if (request.getDeleteFromDevice()) {
            log.trace("Deleting key '{}' from device", request.getKeyId());

            deleteKeyFile(tokenAndKey.tokenId(), request);
        } else {
            log.trace("Deleting key '{}' from configuration", request.getKeyId());

            removeCertsFromKey(tokenAndKey.key());
        }
        return Empty.getDefaultInstance();
    }


    private void deleteKeyFile(String tokenId, DeleteKeyReq request) {
        getTokenWorker(tokenId)
                .handleDeleteKey(request.getKeyId());
    }

    private static void removeCertsFromKey(KeyInfo keyInfo) {
        keyInfo.getCerts().stream().filter(CertificateInfo::isSavedToConfiguration)
                .map(CertificateInfo::getId).forEach(TokenManager::removeCert);

        keyInfo.getCertRequests().stream()
                .map(CertRequestInfo::getId).forEach(TokenManager::removeCertRequest);
    }
}
