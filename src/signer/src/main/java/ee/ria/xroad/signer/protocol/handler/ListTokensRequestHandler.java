/**
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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.tokenmanager.TokenManager;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.niis.xroad.signer.proto.ListTokensResponse;
import org.niis.xroad.signer.proto.SignerApiGrpc;
import org.niis.xroad.signer.proto.TokenInfo;

/**
 * Handles requests for token list.
 */
public class ListTokensRequestHandler extends SignerApiGrpc.SignerApiImplBase {

    @Override
    public void listTokens(Empty request, StreamObserver<ListTokensResponse> responseObserver) {
        final ListTokensResponse.Builder builder = ListTokensResponse.newBuilder();
        TokenManager.listTokens().forEach(tokenInfo -> builder.addTokens(toProtoDto(tokenInfo)));

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private TokenInfo toProtoDto(ee.ria.xroad.signer.protocol.dto.TokenInfo tokenInfo) {
        TokenInfo.Builder builder = TokenInfo.newBuilder()
                .setType(tokenInfo.getType())
                .setFriendlyName(tokenInfo.getFriendlyName())
                .setId(tokenInfo.getId())
                .setReadOnly(tokenInfo.isReadOnly())
                .setAvailable(tokenInfo.isAvailable())
                .setActive(tokenInfo.isActive())
                .setSlotIndex(tokenInfo.getSlotIndex());

        if (tokenInfo.getSerialNumber() != null) {
            builder.setSerialNumber(tokenInfo.getSerialNumber());
        }
        if (tokenInfo.getLabel() != null) {
            builder.setLabel(tokenInfo.getLabel());
        }
        return builder.build();
    }
}
