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
package org.niis.xroad.proxy.core.signature;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.hashchain.HashChainBuilder;
import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.signature.SignatureResourceResolver;
import ee.ria.xroad.common.signature.SigningRequest;

import jakarta.xml.bind.JAXBException;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.signature.MessagePart.hashChainMessagePart;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.MessageFileNames.MESSAGE;
import static ee.ria.xroad.common.util.MessageFileNames.SIG_HASH_CHAIN;

/**
 * This class handles batch signing. Batch signatures are created always, if
 * there are more than one message parts (e.g. messages with attachments).
 * Signing requests are grouped by the signing certificate.
 * <p>
 * Moreover, multiple signing requests for the same signing certificate
 * (and thus the same key id) are signed in batch and the resulting hash
 * chain is produced for each request.
 */
@Slf4j
@RequiredArgsConstructor
public class BatchSigner implements MessageSigner {

    private final SignerRpcClient signerClient;
    private final SignerSignClient signerSignClient;
    private final SignerRpcChannelProperties signerRpcChannelProperties;

    private final Map<String, WorkerImpl> workers = new ConcurrentHashMap<>();

    public void destroy() {
        workers.values().forEach(WorkerImpl::stop);
    }

    /**
     * Submits the given signing request for batch signing.
     *
     * @param keyId                the signing key
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param request              the signing request
     * @return the signature data
     */
    @Override
    public SignatureData sign(String keyId, SignAlgorithm signatureAlgorithmId, SigningRequest request)
            throws ExecutionException, InterruptedException {

        CompletableFuture<SignatureData> completableFuture = new CompletableFuture<>();
        final SigningRequestWrapper signRequestWrapper = new SigningRequestWrapper(
                completableFuture,
                keyId, signatureAlgorithmId, request);
        handle(signRequestWrapper);

        try {
            return completableFuture.get(signerRpcChannelProperties.deadlineAfter(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            throw XrdRuntimeException.systemInternalError("Signature creation timed out");
        }
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
                return new WorkerImpl(signRequest.getKeyId());
            });
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError("Unable to get worker", e);
        }
    }

    /**
     * This is the worker that does the heavy lifting.
     */
    private class WorkerImpl {
        private final boolean batchSigningEnabled;
        private final BlockingQueue<SigningRequestWrapper> requestsQueue = new LinkedBlockingQueue<>();
        private boolean stopping;
        private final Thread workerThread;

        protected WorkerImpl(String keyId) {
            try {
                batchSigningEnabled = signerClient.isTokenBatchSigningEnabled(keyId);
            } catch (Exception e) {
                log.error("Failed to query if batch signing is enabled for token with key {}", keyId, e);
                throw XrdRuntimeException.systemException(e);
            }
            workerThread = new Thread(this::process);
            workerThread.setDaemon(true);
            workerThread.start();
        }

        public void handleSignRequest(SigningRequestWrapper signRequest) {
            log.trace("handleSignRequest()");
            requestsQueue.add(signRequest);
        }

        private void sendSignatureResponse(BatchSignatureCtx ctx, byte[] signatureValue) throws IOException, TransformerException {
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

        private void sendException(BatchSignatureCtx ctx, Exception message) {
            for (CompletableFuture<SignatureData> client : ctx.getClients()) {
                client.completeExceptionally(message);
            }
        }

        private boolean isExpired(SigningRequestWrapper requestWrapper) {
            // do not sign requests if timeout is already passed.
            return System.currentTimeMillis() - requestWrapper.getCreatedOn() > signerRpcChannelProperties.deadlineAfter();
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
                        // poll all remaining
                        requestsQueue.drainTo(requests);
                    }

                    log.trace("processing {} sign requests", requests.size());
                    BatchSignatureCtx ctx = new BatchSignatureCtx(first.getKeyId(), first.getSignatureAlgorithmId());
                    requests.stream()
                            .filter(req -> !isExpired(req))
                            .forEach(req -> ctx.add(req.getClientFuture(), req.getRequest()));

                    sign(ctx);
                } catch (InterruptedException interruptedException) {
                    log.trace("queue polling interrupted");
                    Thread.currentThread().interrupt();
                }
            }
            log.trace("Worker thread stopped");
        }

        private void sign(BatchSignatureCtx ctx) {
            try {
                byte[] digest = calculateDigest(ctx.getSignatureAlgorithmId().digest(),
                        ctx.getDataToBeSigned());
                final byte[] response = signerSignClient.sign(ctx.getKeyId(), ctx.getSignatureAlgorithmId(), digest);
                sendSignatureResponse(ctx, response);
            } catch (Exception exception) {
                sendException(ctx, exception);
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
    private static final class SigningRequestWrapper {
        private final long createdOn = System.currentTimeMillis();
        private final CompletableFuture<SignatureData> clientFuture;
        private final String keyId;
        private final SignAlgorithm signatureAlgorithmId;
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

        @Getter
        private final List<CompletableFuture<SignatureData>> clients = new ArrayList<>();

        @Getter
        private final String keyId;

        private String hashChainResult;
        private String[] hashChains;

        BatchSignatureCtx(String keyId, SignAlgorithm signatureAlgorithmId) {
            super(signatureAlgorithmId);

            this.keyId = keyId;
        }

        void add(CompletableFuture<SignatureData> client, SigningRequest request) {
            clients.add(client);
            add(request);
        }

        @Override
        public synchronized byte[] getDataToBeSigned()
                throws CertificateEncodingException, ParserConfigurationException, IOException, XMLSecurityException, JAXBException {
            log.trace("getDataToBeSigned(requests = {})", requests.size());

            if (requests.isEmpty()) {
                throw XrdRuntimeException.systemInternalError("No requests in signing context");
            }

            SigningRequest firstRequest = requests.getFirst();

            builder = new SignatureXmlBuilder(firstRequest, signatureAlgorithmId);

            // If only one single hash (message), then no hash chain
            if (requests.size() == 1 && firstRequest.isSingleMessage()) {
                return builder.addAndCalculateDataToBeSigned(new SignatureResourceResolver(firstRequest.getParts(), null));
            }

            buildHashChain();

            return builder.addAndCalculateDataToBeSigned(
                    new SignatureResourceResolver(List.of(hashChainMessagePart()), hashChainResult));
        }

        /**
         * Returns the signature data for a given signer -- either normal signature
         * or batch signature with corresponding hash chain and hash chain result.
         */
        public synchronized SignatureData createSignatureData(String signature, int signerIndex) {
            return new SignatureData(signature, hashChainResult, hashChains != null ? hashChains[signerIndex] : null);
        }

        private void buildHashChain() throws IOException, JAXBException {
            log.trace("buildHashChain()");

            HashChainBuilder hashChainBuilder = new HashChainBuilder(signatureAlgorithmId.digest());

            for (SigningRequest request : requests) {
                hashChainBuilder.addInputHash(getHashChainInputs(request));
            }

            hashChainBuilder.finishBuilding();

            hashChainResult = hashChainBuilder.getHashChainResult(SIG_HASH_CHAIN);
            hashChains = hashChainBuilder.getHashChains(MESSAGE);
        }

        private static byte[][] getHashChainInputs(SigningRequest request) {
            return request.getParts().stream()
                    .map(MessagePart::getData)
                    .toArray(byte[][]::new);
        }
    }

}
