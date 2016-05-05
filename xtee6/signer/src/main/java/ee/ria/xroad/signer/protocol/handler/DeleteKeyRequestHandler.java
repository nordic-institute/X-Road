/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.message.DeleteKey;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.TokenAndKey;

/**
 * Handles key deletions.
 */
@Slf4j
public class DeleteKeyRequestHandler
        extends AbstractDeleteFromKeyInfo<DeleteKey> {

    @Override
    protected Object handle(DeleteKey message) throws Exception {
        TokenAndKey tokenAndKey =
                TokenManager.findTokenAndKey(message.getKeyId());

        if (message.isDeleteFromDevice()) {
            log.trace("Deleting key '{}' from device", message.getKeyId());

            deleteKeyFile(tokenAndKey.getTokenId(), message);
            return nothing();
        } else {
            log.trace("Deleting key '{}' from configuration",
                    message.getKeyId());

            removeCertsFromKey(tokenAndKey.getKey());
            return success();
        }
    }

    private static void removeCertsFromKey(KeyInfo keyInfo) throws Exception {
        keyInfo.getCerts().stream().filter(c -> c.isSavedToConfiguration())
            .map(c -> c.getId()).forEach(TokenManager::removeCert);

        keyInfo.getCertRequests().stream()
            .map(c -> c.getId()).forEach(TokenManager::removeCertRequest);
    }
}
