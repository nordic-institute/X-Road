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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.AbstractRpcHandler;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.token.TokenWorker;

import org.niis.xroad.signer.proto.InitSoftwareTokenReq;
import org.niis.xroad.signer.protocol.dto.Empty;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Handles requests for software token initialization.
 */
@Component
public class InitSoftwareTokenReqHandler
        extends AbstractRpcHandler<InitSoftwareTokenReq, Empty> {

    @Override
    protected Empty handle(InitSoftwareTokenReq request) throws Exception {
        String softwareTokenId = TokenManager.getSoftwareTokenId();

        if (softwareTokenId != null) {
            final TokenWorker tokenWorker = getTokenWorker(softwareTokenId);
            if (tokenWorker.isSoftwareToken()) {
                try {
                    tokenWorker.initializeToken(request.getPin().toCharArray());
                    return Empty.getDefaultInstance();
                } catch (Exception e) {
                    throw new CodedException(X_INTERNAL_ERROR, e); //todo move to worker
                }
            }
        }
        throw new CodedException(X_INTERNAL_ERROR, "Software token not found");
    }
}
