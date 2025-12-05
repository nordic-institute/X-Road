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
package org.niis.xroad.signer.client.impl;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.signer.client.SoftwareTokenSignerRpcChannelProperties;
import org.niis.xroad.signer.proto.SignCertificateReq;
import org.niis.xroad.signer.proto.SignReq;
import org.niis.xroad.signer.proto.SignServiceGrpc;

import java.io.Closeable;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@ApplicationScoped
public class SignerSignRpcClient extends AbstractRpcClient implements SignerSignClient, Closeable {

    private final ManagedChannel signerChannel;
    private final SignServiceGrpc.SignServiceBlockingStub signerSignServiceBlockingStub;

    private final ManagedChannel softTokenSignerChannel;
    private final SignServiceGrpc.SignServiceBlockingStub softTokenSignerSignServiceBlockingStub;
    private final SignerRpcClient signerRpcClient;

    private final Map<String, Boolean> cachedKeyIds = new HashMap<>();

    public SignerSignRpcClient(RpcChannelFactory rpcChannelFactory, SignerRpcChannelProperties signerRpcChannelProperties) {
        log.info("Initializing Signer RPC client to {}:{}", signerRpcChannelProperties.host(), signerRpcChannelProperties.port());
        signerChannel = rpcChannelFactory.createChannel(signerRpcChannelProperties);
        signerSignServiceBlockingStub = SignServiceGrpc.newBlockingStub(signerChannel).withWaitForReady();

        softTokenSignerChannel = null;
        softTokenSignerSignServiceBlockingStub = null;
        signerRpcClient = null;
    }

    public SignerSignRpcClient(RpcChannelFactory rpcChannelFactory,
                               SignerRpcChannelProperties signerRpcChannelProperties,
                               SoftwareTokenSignerRpcChannelProperties softTokenSignerRpcChannelProperties,
                               SignerRpcClient signerRpcClient) {
        log.info("Initializing Signer RPC client to {}:{}", signerRpcChannelProperties.host(), signerRpcChannelProperties.port());
        signerChannel = rpcChannelFactory.createChannel(signerRpcChannelProperties);
        signerSignServiceBlockingStub = SignServiceGrpc.newBlockingStub(signerChannel).withWaitForReady();

        log.info("Initializing SoftToken Signer RPC client to {}:{}",
                softTokenSignerRpcChannelProperties.host(), softTokenSignerRpcChannelProperties.port());
        softTokenSignerChannel = rpcChannelFactory.createChannel(signerRpcChannelProperties);
        softTokenSignerSignServiceBlockingStub = SignServiceGrpc.newBlockingStub(softTokenSignerChannel).withWaitForReady();
        this.signerRpcClient = signerRpcClient;
    }

    @Override
    @PreDestroy
    public void close() {
        if (signerChannel != null) {
            signerChannel.shutdown();
        }
        if (softTokenSignerChannel != null) {
            softTokenSignerChannel.shutdown();
        }
    }


    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.SIGNER;
    }

    @Override
    @WithSpan("SignerSignRpcClient#sign")
    public byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] digest) {
        var serviceStub = shouldUseSoftTokenSigner(keyId) ? softTokenSignerSignServiceBlockingStub : signerSignServiceBlockingStub;
        return exec(
                () -> serviceStub.sign(SignReq.newBuilder()
                                .setKeyId(keyId)
                                .setSignatureAlgorithmId(signatureAlgorithmId.name())
                                .setDigest(ByteString.copyFrom(digest))
                                .build())
                        .getSignature().toByteArray()
        );
    }

    private boolean shouldUseSoftTokenSigner(String keyId) {
        if (softTokenSignerSignServiceBlockingStub != null) {
            // cache the result to avoid redundant network calls for the same key
            if (cachedKeyIds.get(keyId) == null) {
                var isSoftTokenBased = signerRpcClient.isSoftTokenBased(keyId);
                cachedKeyIds.put(keyId, isSoftTokenBased);
            }
            return true == cachedKeyIds.get(keyId);
        }
        return false;
    }

    @Override
    public byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey) {
        return exec(
                () -> signerSignServiceBlockingStub.signCertificate(SignCertificateReq.newBuilder()
                                .setKeyId(keyId)
                                .setSignatureAlgorithmId(signatureAlgorithmId.name())
                                .setSubjectName(subjectName)
                                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                                .build())
                        .getCertificateChain().toByteArray()
        );
    }
}
