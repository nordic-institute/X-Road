/**
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
package ee.ria.xroad.signer.tokenmanager.token;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.ComponentNames;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;
import ee.ria.xroad.signer.util.CalculateSignature;
import ee.ria.xroad.signer.util.CalculatedSignature;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedAbstractActor;
import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * Token signer actor. Handles signing requests and calculated signatures.
 */
@Slf4j
public class TokenSigner extends UntypedAbstractActor {

    private final ActorSelection tokenWorker = getContext().actorSelection("../" + ComponentNames.TOKEN_WORKER);

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message);

        try {
            if (message instanceof Sign) {
                handleSignRequest((Sign) message);
            } else if (message instanceof CalculatedSignature) {
                handleCalculatedSignature((CalculatedSignature) message);
            } else {
                unhandled(message);
            }
        } catch (Exception e) {
            sendResponse(getSender(), translateException(e));
        }
    }

    private void handleSignRequest(Sign signRequest) throws Exception {
        log.trace("handleSignRequest()");

        calculateSignature(signRequest.getKeyId(), signRequest.getSignatureAlgorithmId(), signRequest.getDigest());
    }

    private void handleCalculatedSignature(CalculatedSignature message) {
        log.trace("handleCalculatedSignature()");

        Object response = null;

        if (message.getException() != null) {
            response = message.getException();

            log.error("Error in token batch signer", message.getException());
        } else {
            response = new SignResponse(message.getSignature());
        }

        sendResponse(message.getRequest().getReceiver(), response);
    }

    private void calculateSignature(String keyId, String signatureAlgorithmId, byte[] digest) {
        tokenWorker.tell(new CalculateSignature(getSender(), keyId, signatureAlgorithmId, digest), getSelf());
    }

    private void sendResponse(ActorRef client, Object message) {
        if (client != ActorRef.noSender()) {
            if (message instanceof CodedException) {
                client.tell(((CodedException) message).withPrefix(SIGNER_X), getSelf());
            } else {
                client.tell(message, getSelf());
            }
        }
    }
}
