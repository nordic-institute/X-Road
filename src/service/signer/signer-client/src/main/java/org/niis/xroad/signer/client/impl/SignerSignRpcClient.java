package org.niis.xroad.signer.client.impl;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import java.io.Closeable;
import java.security.PublicKey;

import static org.niis.xroad.signer.client.util.SignerRpcUtils.tryToRun;

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
