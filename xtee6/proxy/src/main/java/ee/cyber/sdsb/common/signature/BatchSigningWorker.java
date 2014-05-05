package ee.cyber.sdsb.common.signature;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.Getter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.Await;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorWithStash;
import akka.pattern.Patterns;
import akka.util.Timeout;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.message.GetTokenBatchSigningEnabled;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.protocol.message.SignResponse;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;

/**
 * This class handles batch signing. Batch signatures are created always, if
 * there are more then one message parts (e.g. messages with attachments).
 * Signing requests are grouped by the signing certificate.
 *
 * Moreover, multiple signing requests for the same signing certificate
 * (and thus the same key id) are signed in batch and the resulting hash
 * chain is produced for each request.
 *
 * The batch signer is an Akka actor, it creates child actors per
 * signing certificate, which means there is essentially one batch signer
 * per signing certificate.
 */
public class BatchSigningWorker extends UntypedActor {

    private static final Logger LOG =
            LoggerFactory.getLogger(BatchSigningWorker.class);

    // TODO: Timeout
    private static final Timeout DEFAULT_TIMEOUT = new Timeout(10000);

    // Holds the actor instance, which sends and receives messages.
    private static ActorRef instance;

    public static void init(ActorSystem actorSystem) {
        if (instance == null) {
            instance = actorSystem.actorOf(Props.create(
                    BatchSigningWorker.class));
        }
    }

    public static SignatureData sign(String keyId, String signatureAlgorithmId,
            SigningRequest request) throws Exception {
        if (instance == null) {
            throw new IllegalStateException(
                    "BatchSigningWorker is not initialized");
        }

        // Send the signing request to the actor instance (itself)
        return SignerClient.result(Await.result(Patterns.ask(instance,
                new SigningRequestWrapper(keyId, signatureAlgorithmId, request),
                DEFAULT_TIMEOUT.duration().length()),
                DEFAULT_TIMEOUT.duration()));
    }

    @Override
    public void onReceive(Object message) throws Exception {
        try {
            if (message instanceof SigningRequestWrapper) {
                handle((SigningRequestWrapper) message);
            } else {
                LOG.trace("unhandled({})", message);
                unhandled(message);
            }
        } catch (Exception e) {
            LOG.error("Error in signing worker", e);

            getSender().tell(e, getSelf());
        }
    }

    private void handle(SigningRequestWrapper signRequest) throws Exception {
        // New incoming sign request. Find the corresponding batch signer actor
        // (if not found, create one) and relay the sign request to the worker.
        try {
            getWorker(signRequest).tell(signRequest, getSender());
        } catch (Exception e) {
            throw new RuntimeException("Unable to get worker", e);
        }
    }

    private ActorRef getWorker(SigningRequestWrapper signRequest)
            throws Exception {
        // Signing worker based on cert hash.
        String name = calculateCertHexHash(signRequest.getSigningCert());

        ActorRef worker = getContext().getChild(name);
        if (worker == null) {
            LOG.trace("Creating new worker for cert '{}'", name);

            worker = getContext().actorOf(Props.create(WorkerImpl.class), name);
        }

        return worker;
    }

    /**
     * This is the worker that does the heavy lifting.
     */
    private static class WorkerImpl extends UntypedActorWithStash {

        // Maps a signing context to a certificate
        private final Map<X509Certificate, BatchSignatureCtx> signingQueue =
                new LinkedHashMap<>();

        // The currently active signing ctx.
        private BatchSignatureCtx workingSigningCtx;

        private long signStartTime;
        private boolean workerBusy;

        private Boolean batchSigningEnabled;

        @Override
        public void onReceive(Object message) throws Exception {
            LOG.trace("onReceive({})", message);

            if (message instanceof SigningRequestWrapper) {
                handleSignRequest((SigningRequestWrapper) message);
            } else if (message instanceof SignResponse) {
                handleSignResponse((SignResponse) message);
            } else if (message instanceof Exception) {
                handleException((Exception) message);
            } else {
                unhandled(message);
            }
        }

        private void handleSignRequest(SigningRequestWrapper signRequest)
                throws Exception {
            LOG.trace("handleSignRequest()");

            // If we do not know whether batch signing is enabled for the token,
            // we ask from Signer. This call will block until response is
            // received or error occurs.
            if (batchSigningEnabled == null) {
                queryBatchSigningEnabled(signRequest.getKeyId());
            }

            // Handle incoming sign request. If the token worker is currently
            // busy (signing, generating key, etc...) and batch signing is
            // enabled then create signing context and collect all following
            // sign requests to be signed in batch. Otherwise just sign the
            // data straight away.
            if (isWorkerBusy()) {
                if (batchSigningEnabled) {
                    doBatchSign(signRequest);
                } else {
                    LOG.trace("Batch signing not enabled, stashing request");
                    // Batch signing not enabled, but currently busy,
                    // so stash this message for future.
                    stash();
                }
            } else {
                doSign(signRequest);
            }
        }

        private void queryBatchSigningEnabled(String keyId) {
            try {
                batchSigningEnabled =
                        SignerClient.execute(
                                new GetTokenBatchSigningEnabled(keyId));
            } catch (Exception e) {
                LOG.error("Failed to query if batch signing is enabled for " +
                        "token with key {}: {}", keyId, e.getMessage());
                batchSigningEnabled = false;
            }
        }

        private void doBatchSign(SigningRequestWrapper wrapper) {
            LOG.trace("doBatchSign()");

            X509Certificate signingCert = wrapper.getRequest().getSigningCert();

            BatchSignatureCtx ctx = signingQueue.get(signingCert);
            if (ctx == null) {
                ctx = new BatchSignatureCtx(wrapper.getKeyId(),
                        wrapper.getSignatureAlgorithmId());
                signingQueue.put(signingCert, ctx);
            }

            ctx.add(getSender(), wrapper.getRequest());
        }

        private void doSign(SigningRequestWrapper wrapper) throws Exception {
            LOG.trace("doSign()");

            BatchSignatureCtx ctx = new BatchSignatureCtx(wrapper.getKeyId(),
                    wrapper.getSignatureAlgorithmId());
            ctx.add(getSender(), wrapper.getRequest());

            workingSigningCtx = ctx;

            doCalculateSignature(ctx.getKeyId(), ctx.getSignatureAlgorithmId(),
                    ctx.getDataToBeSigned());
        }

        private void handleSignResponse(SignResponse signResponse) {
            LOG.trace("handleSignResponse()");

            workerBusy = false;

            // Handle the (successful) signature calculation result that came
            // from Signer -- send the signature to the clients.
            sendResponse(signResponse);

            // If batch signing is not enabled, then start signing the next
            // stashed messages.
            if (!batchSigningEnabled) {
                unstashAll();
            } else if (!signingQueue.isEmpty()) {
                // Start the next batch signing (if any).
                startNextBatchSigning();
            }
        }

        private void handleException(Exception exception) {
            LOG.trace("handleException()");

            workerBusy = false;

            sendResponse(exception);
        }

        private void startNextBatchSigning() {
            LOG.trace("startNextBatchSigning()");

            X509Certificate signingCert =
                    signingQueue.keySet().iterator().next();

            workingSigningCtx = signingQueue.remove(signingCert);
            try {
                doCalculateSignature(
                        workingSigningCtx.getKeyId(),
                        workingSigningCtx.getSignatureAlgorithmId(),
                        workingSigningCtx.getDataToBeSigned());
            } catch (Exception e) {
                sendResponse(workingSigningCtx, translateException(e));

                workerBusy = true;
                workingSigningCtx = null;
            }
        }

        private boolean isWorkerBusy() {
            if (workerBusy) {
                if (System.currentTimeMillis() - signStartTime >=
                        DEFAULT_TIMEOUT.duration().length()) {
                    workerBusy = false;
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Signature creation timed out");
                }
            }

            return workerBusy;
        }

        private void doCalculateSignature(String keyId,
                String signatureAlgorithmId, byte[] data) throws Exception {
            workerBusy = true;
            signStartTime = System.currentTimeMillis();

            byte[] digest = calculateDigest(
                    getDigestAlgorithmId(signatureAlgorithmId), data);

            // Proxy this request to the Signer.
            SignerClient.execute(new Sign(keyId, signatureAlgorithmId, digest),
                    getSelf());
        }

        private void sendResponse(Object message) {
            LOG.trace("sendResponse({})", message);

            if (workingSigningCtx != null) {
                try {
                    if (message instanceof SignResponse) {
                        sendSignatureResponse(workingSigningCtx,
                                ((SignResponse) message).getSignature());
                    } else {
                        sendResponse(workingSigningCtx, message);
                    }
                } catch (Exception e) {
                    sendResponse(workingSigningCtx, e);
                }

                workingSigningCtx = null;
            } else {
                throw new RuntimeException("No signing context");
            }
        }

        private void sendSignatureResponse(BatchSignatureCtx ctx,
                byte[] signatureValue) throws Exception {
            String signature = ctx.createSignatureXml(signatureValue);

            // Each client gets corresponding hash chain -- client index in the
            // clients list determines the hash chain.
            for (int i = 0; i < ctx.getClients().size(); i++) {
                ActorRef client = ctx.getClients().get(i);
                client.tell(ctx.createSignatureData(signature, i), getSelf());
            }
        }

        private void sendResponse(BatchSignatureCtx ctx, Object message) {
            for (ActorRef client : ctx.getClients()) {
                sendResponse(client, message);
            }
        }

        private void sendResponse(ActorRef client, Object message) {
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

    /**
     * Convenience class that wraps the request along with the keyId
     * and algorithm id.
     */
    @Data
    private static class SigningRequestWrapper {
        private final String keyId;
        private final String signatureAlgorithmId;
        private final SigningRequest request;

        X509Certificate getSigningCert() {
            return request.getSigningCert();
        }
    }

    /**
     * This signature context is used for batch signing where there might
     * be more than one signature receiver (client).
     */
    private static class BatchSignatureCtx extends SignatureCtx {

        @Getter private final List<ActorRef> clients = new ArrayList<>();
        @Getter private final String keyId;

        BatchSignatureCtx(String keyId, String signatureAlgorithmId) {
            super(signatureAlgorithmId);

            this.keyId = keyId;
        }

        void add(ActorRef client, SigningRequest request) {
            clients.add(client);
            add(request);
        }
    }

}
