package ee.ria.xroad.signer.tokenmanager.token;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.ComponentNames;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;
import ee.ria.xroad.signer.util.CalculateSignature;
import ee.ria.xroad.signer.util.CalculatedSignature;
import ee.ria.xroad.signer.util.SignerUtil;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * Token signer actor. Handles signing requests and calculated signatures.
 */
@Slf4j
public class TokenSigner extends UntypedActor {

    private final ActorSelection tokenWorker =
            getContext().actorSelection("../" + ComponentNames.TOKEN_WORKER);

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

    protected void handleSignRequest(Sign signRequest) throws Exception {
        log.trace("handleSignRequest()");

        calculateSignature(signRequest.getKeyId(), signRequest.getDigest());
    }

    protected void handleCalculatedSignature(CalculatedSignature message) {
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

    protected void calculateSignature(String keyId, byte[] digest) {
        byte[] tbsData = SignerUtil.createDataToSign(digest);

        tokenWorker.tell(new CalculateSignature(getSender(), keyId, tbsData),
                getSelf());
    }

    protected void sendResponse(ActorRef client, Object message) {
        if (client != ActorRef.noSender()) {
            if (message instanceof CodedException) {
                client.tell(((CodedException) message).withPrefix(SIGNER_X),
                        getSelf());
            } else {
                client.tell(message, getSelf());
            }
        }
    }
}
