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
package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.signer.protocol.message.ActivateToken;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.ActivateTokenRequest;
import org.niis.xroad.signer.proto.Empty;
import org.niis.xroad.signer.proto.ListTokensResponse;
import org.niis.xroad.signer.proto.TokensApiGrpc;
import scala.concurrent.Await;

import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.signer.protocol.ComponentNames.REQUEST_PROCESSOR;

/**
 * Handles requests for token list.
 */
@Slf4j
@RequiredArgsConstructor
public class ListTokensRequestHandler extends TokensApiGrpc.TokensApiImplBase {
    private final ActorSystem actorSystem;

    @Override
    public void listTokens(Empty request, StreamObserver<ListTokensResponse> responseObserver) {
        final ListTokensResponse.Builder builder = ListTokensResponse.newBuilder();

        TokenManager.listTokens().forEach(tokenInfo -> builder.addTokens(tokenInfo.asMessage()));

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @SneakyThrows
    @Override
    public void activateToken(ActivateTokenRequest request, StreamObserver<Empty> responseObserver) {
        ActivateToken actorMsg = new ActivateToken(request.getTokenId(), request.getActivate());
        //TODO:grpc this is for debugging purposes.
        log.info("Resending back to actor system..");

        Timeout timeout = new Timeout(10, TimeUnit.SECONDS);
        Await.result(Patterns.ask(actorSystem.actorSelection("/user/" + REQUEST_PROCESSOR), actorMsg, timeout),
                timeout.duration());

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }
}