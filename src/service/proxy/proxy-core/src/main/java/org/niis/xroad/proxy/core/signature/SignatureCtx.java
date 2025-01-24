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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.crypto.Signatures;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.hashchain.HashChainBuilder;
import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.signature.SigningRequest;
import ee.ria.xroad.common.util.MessageFileNames;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.signature.XMLSignatureByteInput;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.util.MessageFileNames.MESSAGE;
import static ee.ria.xroad.common.util.MessageFileNames.SIG_HASH_CHAIN;
import static ee.ria.xroad.common.util.MessageFileNames.SIG_HASH_CHAIN_RESULT;

/**
 * This class handles the (batch) signature creation. After requests
 * have been added to the context, the signature is created.
 * <p>
 * Depending on the amount of input hashes (one hash for a single message,
 * multiple hashes for a single message with attachments etc.) the result
 * is a XML signature with one referenced message or a referenced hash chain
 * result with corresponding hash chains.
 */
@Slf4j
class SignatureCtx {

    private final List<SigningRequest> requests = new ArrayList<>();

    @Getter(AccessLevel.PACKAGE)
    private final SignAlgorithm signatureAlgorithmId;

    private String hashChainResult;
    private String[] hashChains;

    private SignatureXmlBuilder builder;

    @SneakyThrows
    SignatureCtx(SignAlgorithm signatureAlgorithmId) {
        this.signatureAlgorithmId = signatureAlgorithmId;
    }

    /**
     * Adds a new signing request to this context.
     */
    synchronized void add(SigningRequest request) {
        requests.add(request);
    }

    /**
     * Produces the XML signature from the given signed data.
     */
    synchronized String createSignatureXml(byte[] signatureValue) throws Exception {
        return builder.createSignatureXml(Signatures.useRawFormat(signatureAlgorithmId, signatureValue));
    }

    /**
     * Returns the signature data for a given signer -- either normal signature
     * or batch signature with corresponding hash chain and hash chain result.
     */
    synchronized SignatureData createSignatureData(String signature, int signerIndex) {
        return new SignatureData(signature, hashChainResult, hashChains != null ? hashChains[signerIndex] : null);
    }

    /**
     * Returns the data to be signed -- if there is only one signing request
     * and the request is simple message (no attachments), then no hash chain is used.
     */
    synchronized byte[] getDataToBeSigned() throws Exception {
        log.trace("getDataToBeSigned(requests = {})", requests.size());

        if (requests.isEmpty()) {
            throw new CodedException(X_INTERNAL_ERROR, "No requests in signing context");
        }

        SigningRequest firstRequest = requests.getFirst();

        builder = new SignatureXmlBuilder(firstRequest, signatureAlgorithmId.digest());

        // If only one single hash (message), then no hash chain
        if (requests.size() == 1 && firstRequest.isSingleMessage()) {
            return builder.createDataToBeSigned(MESSAGE, createResourceResolver(
                    firstRequest.getParts().getFirst().getMessage()), signatureAlgorithmId);
        }

        buildHashChain();

        byte[] hashChainResultBytes = hashChainResult.getBytes(StandardCharsets.UTF_8);

        return builder.createDataToBeSigned(SIG_HASH_CHAIN_RESULT, createResourceResolver(hashChainResultBytes),
                signatureAlgorithmId);
    }

    private void buildHashChain() throws Exception {
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

    /**
     * This resource resolver will provide the message or hash chain data to be digested.
     */
    private ResourceResolverSpi createResourceResolver(final byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data must not be null");
        }

        return new ResourceResolverSpi() {
            @Override
            public boolean engineCanResolveURI(ResourceResolverContext context) {
                return switch (context.attr.getValue()) {
                    case MessageFileNames.MESSAGE, MessageFileNames.SIG_HASH_CHAIN_RESULT -> true;
                    default -> false;
                };
            }

            @Override
            public XMLSignatureInput engineResolveURI(ResourceResolverContext context)
                    throws ResourceResolverException {
                return new XMLSignatureByteInput(data);
            }
        };
    }

}
