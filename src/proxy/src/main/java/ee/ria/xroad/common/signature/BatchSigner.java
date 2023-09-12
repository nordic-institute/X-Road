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
package ee.ria.xroad.common.signature;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.signer.SignerProxy;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;

/**
 * This class handles batch signing. Batch signatures are created always, if
 * there are more than one message parts (e.g. messages with attachments).
 * Signing requests are grouped by the signing certificate.
 * <p>
 * Moreover, multiple signing requests for the same signing certificate
 * (and thus the same key id) are signed in batch and the resulting hash
 * chain is produced for each request.
 * <p>
 * The batch signer is an Akka actor, it creates child actors per
 * signing certificate, which means there is essentially one batch signer
 * per signing certificate.
 */
@Slf4j
public class BatchSigner {

    private static final int TIMEOUT_MILLIS = SystemProperties.getSignerClientTimeout();

    private static BatchSigner instance;

    private final Map<String, WorkerImpl> workers = new ConcurrentHashMap<>();

    public static void init() {
        instance = new BatchSigner();
    }

    /**
     * Submits the given signing request for batch signing.
     *
     * @param keyId                the signing key
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param request              the signing request
     * @return the signature data
     * @throws Exception in case of any errors
     */
    public static SignatureData sign(String keyId, String signatureAlgorithmId, SigningRequest request)
            throws Exception {
        if (instance == null) {
            throw new IllegalStateException("BatchSigner is not initialized");
        }

        CompletableFuture<SignatureData> completableFuture = new CompletableFuture<>();
        final SigningRequestWrapper signRequestWrapper = new SigningRequestWrapper(
                completableFuture,
                keyId, signatureAlgorithmId, request);
        instance.handle(signRequestWrapper);
        return completableFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void handle(SigningRequestWrapper signRequest) {
        // New incoming sign request. Find the corresponding batch signer
        // (if not found, create one) and relay the sign request to the worker.
        getWorker(signRequest).handleSignRequest(signRequest);
    }

    private WorkerImpl getWorker(SigningRequestWrapper signRequest) {
        // Signing worker based on cert hash.
        try {
            String name = calculateCertHexHash(signRequest.getSigningCert());

            return workers.computeIfAbsent(name, key -> {
                log.trace("Creating new worker for cert '{}'", name);
                return new WorkerImpl();
            });
        } catch (Exception e) {
            throw new RuntimeException("Unable to get worker", e);
        }
    }

    /**
     * This is the worker that does the heavy lifting.
     */
    private static class WorkerImpl {

        private long signStartTime;
        private boolean workerBusy;

        private volatile Boolean batchSigningEnabled;

        private final BlockingQueue<SigningRequestWrapper> requestsQueue = new LinkedBlockingQueue<>();
        private boolean stopping;
        private final Thread workerThread;

        protected WorkerImpl() {
            workerThread = new Thread(this::process);
            workerThread.setDaemon(true); // todo check, if really needed?
            workerThread.start();
        }

        public synchronized void handleSignRequest(SigningRequestWrapper signRequest) {
            log.trace("handleSignRequest()");

            // If we do not know whether batch signing is enabled for the token,
            // we ask from Signer. This call will block until response is
            // received or error occurs.
            if (batchSigningEnabled == null) {
                queryBatchSigningEnabled(signRequest.getKeyId());
            }
            requestsQueue.add(signRequest);
        }

        private void queryBatchSigningEnabled(String keyId) {
            try {
                batchSigningEnabled = SignerProxy.isTokenBatchSigningEnabled(keyId);
            } catch (Exception e) {
                log.error("Failed to query if batch signing is enabled for token with key {}", keyId, e);
            }
        }

        private boolean isWorkerBusy() {
            if (isSignatureCreationTimedOut()) {
                workerBusy = false;

                throw new CodedException(X_INTERNAL_ERROR, "Signature creation timed out");
            }

            return workerBusy;
        }

        private boolean isSignatureCreationTimedOut() {
            return workerBusy && System.currentTimeMillis() - signStartTime >= TIMEOUT_MILLIS;
        }

        private void sendSignatureResponse(BatchSignatureCtx ctx, byte[] signatureValue) throws Exception {
            String signature = ctx.createSignatureXml(signatureValue);

            // Each client gets corresponding hash chain -- client index in the
            // clients list determines the hash chain.
            for (int i = 0; i < ctx.getClients().size(); i++) {
                CompletableFuture<SignatureData> client = ctx.getClients().get(i);
                final boolean completed = client.complete(ctx.createSignatureData(signature, i));
                if (!completed) {
                    log.trace("future was completed already");
                }
            }
        }

        private void sendResponse(BatchSignatureCtx ctx, Object message) {
            for (CompletableFuture<SignatureData> client : ctx.getClients()) {
                sendResponse(client, message);
            }
        }

        private void sendResponse(CompletableFuture<SignatureData> client, Object message) {
//            if (message instanceof CodedException) {
//                client.completeExceptionally((CodedException) message);
            if (message instanceof Exception) {
                client.completeExceptionally((Exception) message);
            } else {
                client.complete((SignatureData) message);
            }
        }

        private boolean isExpired(SigningRequestWrapper requestWrapper) {
            // do not sign requests if timeout is already passed.
            return System.currentTimeMillis() - requestWrapper.getCreatedOn() > TIMEOUT_MILLIS;
        }

        private synchronized void process() {
            while (!stopping) {
                log.trace("polling queue");
                List<SigningRequestWrapper> requests = new LinkedList<>();
                try {
                    SigningRequestWrapper first;
                    do {
                        first = requestsQueue.take();
                    } while (isExpired(first));

                    requests.add(first);
                    if (batchSigningEnabled) {
                        // poll all. todo should add max batchSize param?
                        requestsQueue.drainTo(requests);
                    }

                    log.trace("processing {} sign requests", requests.size());
                    BatchSignatureCtx ctx = new BatchSignatureCtx(first.getKeyId(), first.getSignatureAlgorithmId());
                    requests.stream()
                            .filter(req -> !isExpired(req))
                            .forEach(req -> ctx.add(req.getClientFuture(), req.getRequest()));

                    try {
                        byte[] digest = calculateDigest(getDigestAlgorithmId(ctx.getSignatureAlgorithmId()), ctx.getDataToBeSigned());
                        final byte[] response = SignerProxy.sign(ctx.getKeyId(), ctx.getSignatureAlgorithmId(), digest);

                        sendSignatureResponse(ctx, response);
                    } catch (Exception exception) {
                        sendResponse(ctx, exception);
                    }
                } catch (InterruptedException interruptedException) {
                    log.trace("queue polling interrupted");
                }
            }
        }

        protected void stop() {
            log.trace("stop()");
            this.stopping = true;
            this.workerThread.interrupt();
        }

    }


    /**
     * Convenience class that wraps the request along with the keyId
     * and algorithm id.
     */
    @Data
    private static class SigningRequestWrapper {
        private final long createdOn;
        private final CompletableFuture<SignatureData> clientFuture;
        private final String keyId;
        private final String signatureAlgorithmId;
        private final SigningRequest request;

        public SigningRequestWrapper(CompletableFuture<SignatureData> clientFuture, String keyId, String signatureAlgorithmId, SigningRequest request) {
            this.createdOn = System.currentTimeMillis();
            this.clientFuture = clientFuture;
            this.keyId = keyId;
            this.signatureAlgorithmId = signatureAlgorithmId;
            this.request = request;
        }

        X509Certificate getSigningCert() {
            return request.getSigningCert();
        }
    }

    /**
     * This signature context is used for batch signing where there might
     * be more than one signature receiver (client).
     */
    private static class BatchSignatureCtx extends SignatureCtx {

        @Getter
        private final List<CompletableFuture<SignatureData>> clients = new ArrayList<>();

        @Getter
        private final String keyId;

        BatchSignatureCtx(String keyId, String signatureAlgorithmId) {
            super(signatureAlgorithmId);

            this.keyId = keyId;
        }

        void add(CompletableFuture<SignatureData> client, SigningRequest request) {
            clients.add(client);
            add(request);
        }
    }

}
