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
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.signer.core.protocol.AbstractRpcHandler;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorker;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorkerProvider;
import org.niis.xroad.signer.proto.UpdateSoftwareTokenPinReq;

import java.io.IOException;

import static ee.ria.xroad.common.util.SignerProtoUtils.byteToChar;

/**
 * Handles token pin update
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class UpdateSoftwareTokenPinReqHandler extends AbstractRpcHandler<UpdateSoftwareTokenPinReq, Empty> {
    private final TokenWorkerProvider tokenWorkerProvider;

    @Override
    protected Empty handle(UpdateSoftwareTokenPinReq request) {
        final TokenWorker tokenWorker = tokenWorkerProvider.getTokenWorker(request.getTokenId());
        if (tokenWorker.isSoftwareToken()) {
            try {
                tokenWorker.handleUpdateTokenPin(byteToChar(request.getOldPin().toByteArray()),
                        byteToChar(request.getNewPin().toByteArray()));
                return Empty.getDefaultInstance();
            } catch (IOException e) {
                throw XrdRuntimeException.systemException(e);
            }
        } else {
            throw XrdRuntimeException.systemInternalError("Software token not found");
        }
    }
}
