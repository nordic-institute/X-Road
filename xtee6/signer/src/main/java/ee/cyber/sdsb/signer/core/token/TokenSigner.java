package ee.cyber.sdsb.signer.core.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.ComponentNames;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.protocol.message.SignResponse;
import ee.cyber.sdsb.signer.util.CalculateSignature;
import ee.cyber.sdsb.signer.util.CalculatedSignature;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.ErrorCodes.SIGNER_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateException;

// TODO: resource management -- make sure every key gets to sign on this token.
public class TokenSigner extends UntypedActor {

    private static final Logger LOG =
            LoggerFactory.getLogger(TokenSigner.class);

    @Override
    public void onReceive(Object message) throws Exception {
        LOG.trace("onReceive({})", message);

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
        LOG.trace("handleSignRequest()");

        sign(signRequest);
    }

    protected void handleCalculatedSignature(CalculatedSignature message) {
        LOG.trace("handleCalculatedSignature()");

        Object response = null;
        if (message.getException() != null) {
            response = message.getException();
            LOG.error("Error in token batch signer", message.getException());
        } else {
            response = new SignResponse(message.getSignature());
        }

        sendResponse(message.getRequest().getReceiver(), response);
    }

    protected void sign(Sign signRequest) throws Exception {
        calculateSignature(signRequest.getKeyId(), signRequest.getDigest());
    }

    protected void calculateSignature(String keyId, byte[] digest) {
        byte[] tbsData = SignerUtil.createDataToSign(digest);

        ActorSelection tokenWorker =
                getContext().actorSelection(
                        "../" + ComponentNames.TOKEN_WORKER);

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
