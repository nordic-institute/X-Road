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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.signer.proto.SignCertificateReq;
import org.niis.xroad.signer.proto.SignReq;
import org.niis.xroad.signer.proto.SignServiceGrpc;

import java.io.Closeable;
import java.security.PublicKey;

import static org.niis.xroad.signer.client.util.SignerRpcUtils.tryToRun;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class SignerSignRpcClient implements SignerSignClient, Closeable {
    private final RpcChannelFactory proxyRpcChannelFactory;
    private final SignerRpcChannelProperties rpcChannelProperties;

    private ManagedChannel channel;
    private SignServiceGrpc.SignServiceBlockingStub signServiceBlockingStub;

    @PostConstruct
    public void init() throws Exception {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(), rpcChannelProperties.host(),
                rpcChannelProperties.port());
        channel = proxyRpcChannelFactory.createChannel(rpcChannelProperties);

        signServiceBlockingStub = SignServiceGrpc.newBlockingStub(channel).withWaitForReady();
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }


    @Override
    @WithSpan("SignerSignRpcClient#sign")
    public byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] digest) throws SignerException {
        return tryToRun(
                () -> signServiceBlockingStub.sign(SignReq.newBuilder()
                                .setKeyId(keyId)
                                .setSignatureAlgorithmId(signatureAlgorithmId.name())
                                .setDigest(ByteString.copyFrom(digest))
                                .build())
                        .getSignature().toByteArray()
        );
    }

    @Override
    public byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey)
            throws SignerException {
        return tryToRun(
                () -> signServiceBlockingStub.signCertificate(SignCertificateReq.newBuilder()
                                .setKeyId(keyId)
                                .setSignatureAlgorithmId(signatureAlgorithmId.name())
                                .setSubjectName(subjectName)
                                .setPublicKey(ByteString.copyFrom(publicKey.getEncoded()))
                                .build())
                        .getCertificateChain().toByteArray()
        );
    }
}
