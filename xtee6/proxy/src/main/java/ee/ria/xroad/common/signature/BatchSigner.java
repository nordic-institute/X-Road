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
package ee.ria.xroad.common.signature;

import akka.actor.*;
import akka.pattern.Patterns;
import akka.util.Timeout;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.message.GetTokenBatchSigningEnabled;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import scala.concurrent.Await;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.*;

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
@Slf4j
public class BatchSigner extends UntypedActor {

    private static final Timeout DEFAULT_TIMEOUT = new Timeout(30000, TimeUnit.MILLISECONDS);

    // Holds the actor instance, which sends and receives messages.
    private static ActorRef instance;

    /**
     * Initializes the batch signer with the given actor system.
     * @param actorSystem actor system the batch signer should use
     */
    public static void init(ActorSystem actorSystem) {
        if (instance == null) {
            instance = actorSystem.actorOf(Props.create(BatchSigner.class));
        }
    }

    /**
     * Submits the given signing request for batch signing.
     * @param keyId the signing key
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param request the signing request
     * @return the signature data
     * @throws Exception in case of any errors
     */
    public static SignatureData sign(String keyId, String signatureAlgorithmId,
            SigningRequest request) throws Exception {
        if (instance == null) {
            throw new IllegalStateException("BatchSigner is not initialized");
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
                log.trace("unhandled({})", message);
                unhandled(message);
            }
        } catch (Exception e) {
            log.error("Error in signing worker", e);

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
            log.trace("Creating new worker for cert '{}'", name);

            worker = getContext().actorOf(Props.create(WorkerImpl.class), name);
        }

        return worker;
    }

    /**
     * This is the worker that does the heavy lifting.
     */
    private static class WorkerImpl extends UntypedActorWithStash {

        // The currently active signing ctx.
        private BatchSignatureCtx workingSigningCtx;

        // The next signing ctx, if batch signing.
        private BatchSignatureCtx nextSigningCtx;

        private long signStartTime;
        private boolean workerBusy;

        private Boolean batchSigningEnabled;

        @Override
        public void onReceive(Object message) throws Exception {
            log.trace("onReceive({})", message);

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
            log.trace("handleSignRequest()");

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
                    log.trace("Batch signing not enabled, stashing request");
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
                log.error("Failed to query if batch signing is enabled for "
                        + "token with key {}", keyId, e);
            }
        }

        private void doBatchSign(SigningRequestWrapper wrapper) {
            log.trace("doBatchSign()");

            if (nextSigningCtx == null) {
                nextSigningCtx = new BatchSignatureCtx(wrapper.getKeyId(),
                        wrapper.getSignatureAlgorithmId());
            }

            nextSigningCtx.add(getSender(), wrapper.getRequest());
        }

        private void doSign(SigningRequestWrapper wrapper) throws Exception {
            log.trace("doSign()");

            BatchSignatureCtx ctx = new BatchSignatureCtx(wrapper.getKeyId(),
                    wrapper.getSignatureAlgorithmId());
            ctx.add(getSender(), wrapper.getRequest());

            workingSigningCtx = ctx;

            doCalculateSignature(ctx.getKeyId(), ctx.getSignatureAlgorithmId(),
                    ctx.getDataToBeSigned());
        }

        private void handleSignResponse(SignResponse signResponse) {
            log.trace("handleSignResponse()");

            workerBusy = false;

            // Handle the (successful) signature calculation result that came
            // from Signer -- send the signature to the clients.
            sendResponse(signResponse);

            // If batch signing is not enabled, then start signing the next
            // stashed messages.
            if (!batchSigningEnabled) {
                unstashAll();
            } else if (nextSigningCtx != null) {
                // Start the next batch signing (if any).
                startNextBatchSigning();
            }
        }

        private void handleException(Exception exception) {
            log.trace("handleException()");

            workerBusy = false;

            sendResponse(exception);
        }

        private void startNextBatchSigning() {
            log.trace("startNextBatchSigning()");

            workingSigningCtx = nextSigningCtx;
            nextSigningCtx = null;
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
                if (System.currentTimeMillis() - signStartTime
                        >= DEFAULT_TIMEOUT.duration().length()) {
                    workerBusy = false;
                    throw new CodedException(X_INTERNAL_ERROR,
                            "Signature creation timed out");
                }
            }

            return workerBusy;
        }

        private void doCalculateSignature(String keyId,
                String signatureAlgorithmId, byte[] data) throws
                NoSuchAlgorithmException, IOException, OperatorCreationException {
            workerBusy = true;
            signStartTime = System.currentTimeMillis();

            byte[] digest = calculateDigest(
                    getDigestAlgorithmId(signatureAlgorithmId), data);

            // Proxy this request to the Signer.
            SignerClient.execute(new Sign(keyId, signatureAlgorithmId, digest), getSelf());
        }

        private void sendResponse(Object message) {
            log.trace("sendResponse({})", message);

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
