/*
 * The MIT License
 *
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

package org.niis.xroad.proxy.core.tls;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.rpc.server.CommonRpcHandler;
import org.niis.xroad.proxy.core.GenerateCertScriptProperties;
import org.niis.xroad.proxy.proto.GenerateInternalCsrRequest;
import org.niis.xroad.proxy.proto.GenerateInternalCsrResponse;
import org.niis.xroad.proxy.proto.InternalTlsCertificateChainMessage;
import org.niis.xroad.proxy.proto.InternalTlsCertificateMessage;
import org.niis.xroad.proxy.proto.InternalTlsServiceGrpc;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.CERTIFICATE_ALREADY_EXISTS;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.IMPORT_INTERNAL_CERT_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INTERNAL_ERROR;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INTERNAL_KEY_CERT_INTERRUPTED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_CERTIFICATE;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_DISTINGUISHED_NAME;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.KEY_CERT_GENERATION_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.KEY_NOT_FOUND;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class InternalTlsService extends InternalTlsServiceGrpc.InternalTlsServiceImplBase {

    private final CommonRpcHandler commonRpcHandler = new CommonRpcHandler();

    private final ExternalProcessRunner externalProcessRunner;
    private final ServerConfProvider serverConfProvider;
    private final GenerateCertScriptProperties generateCertScriptProperties;
    private final InternalTlsCertificateRepository internalTlsCertificateRepository;

    private String internalCertPath = SystemProperties.getConfPath() + InternalSSLKey.CRT_FILE_NAME;
    private String internalKeyPath = SystemProperties.getConfPath() + InternalSSLKey.PK_FILE_NAME;
    private String internalKeystorePath = SystemProperties.getConfPath() + InternalSSLKey.KEY_FILE_NAME;

    @Override
    public void getInternalTlsCertificate(Empty request, StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        commonRpcHandler.handleRequest(responseObserver, () -> {
            var cert = internalTlsCertificateRepository.getInternalTlsCertificate();
            return toCertificateMessage(cert);
        });
    }

    @Override
    public void getInternalTlsCertificateChain(Empty request, StreamObserver<InternalTlsCertificateChainMessage> responseObserver) {
        commonRpcHandler.handleRequest(responseObserver, () -> {
            var certChain = internalTlsCertificateRepository.getInternalTlsCertificateChain();
            return toCertificateChainMessage(certChain);
        });
    }

    @Override
    public void generateInternalTlsKeyAndCertificate(Empty request, StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        commonRpcHandler.handleRequest(responseObserver, () -> {
            var cert = generateInternalTlsKeyAndCertificate();
            return toCertificateMessage(cert);
        });
    }

    @Override
    public void generateInternalTlsCsr(GenerateInternalCsrRequest request, StreamObserver<GenerateInternalCsrResponse> responseObserver) {
        commonRpcHandler.handleRequest(responseObserver, () -> {
            var csr = generateInternalCsr(request.getDistinguishedName());
            return toGenerateInternalCsrResponse(csr);
       });
    }

    @Override
    public void importInternalTlsCertificate(InternalTlsCertificateMessage request,
                                             StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        commonRpcHandler.handleRequest(responseObserver, () -> {
            var importedCert = importInternalTlsCertificate(request.getInternalTlsCertificate().toByteArray());
            return toCertificateMessage(importedCert);
        });
    }

    private InternalTlsCertificateMessage toCertificateMessage(X509Certificate certificate) {
        try {
            return InternalTlsCertificateMessage.newBuilder()
                    .setInternalTlsCertificate(ByteString.copyFrom(certificate.getEncoded()))
                    .build();
        } catch (CertificateEncodingException e) {
            throw new CodedException(INVALID_CERTIFICATE.code(), e);
        }
    }

    private InternalTlsCertificateChainMessage toCertificateChainMessage(Collection<X509Certificate> certificates) {
        try {
            var messageBuilder = InternalTlsCertificateChainMessage.newBuilder();
            for (X509Certificate cert : certificates) {
                messageBuilder.addInternalTlsCertificate(ByteString.copyFrom(cert.getEncoded()));
            }
            return messageBuilder.build();
        } catch (CertificateEncodingException e) {
            throw new CodedException(INVALID_CERTIFICATE.code(), e);
        }
    }

    private GenerateInternalCsrResponse toGenerateInternalCsrResponse(byte[] csr) {
        return GenerateInternalCsrResponse.newBuilder()
                .setTlsCsr(ByteString.copyFrom(csr))
                .build();
    }

    private X509Certificate generateInternalTlsKeyAndCertificate() {
        try {
            externalProcessRunner.executeAndThrowOnFailure(generateCertScriptProperties.path(),
                    generateCertScriptProperties.args().split("\\s+"));
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            throw new CodedException(KEY_CERT_GENERATION_FAILED.code(), e);
        } catch (InterruptedException e) {
            throw new CodedException(INTERNAL_KEY_CERT_INTERRUPTED.code(), e);
        }

        serverConfProvider.clearCache();

        return internalTlsCertificateRepository.getInternalTlsCertificate();
    }

    /**
     * Generate internal auth cert CSR
     * @param distinguishedName
     * @return
     */
    private byte[] generateInternalCsr(String distinguishedName) {
        try {
            KeyPair keyPair = CertUtils.readKeyPairFromPemFile(internalKeyPath);
            return CertUtils.generateCertRequest(keyPair.getPrivate(), keyPair.getPublic(), distinguishedName);
        } catch (IllegalArgumentException e) {
            throw new CodedException(INVALID_DISTINGUISHED_NAME.code(), e);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | OperatorCreationException e) {
            throw new CodedException(INTERNAL_ERROR.code(), e);
    }
    }

    /**
     * Imports a new internal TLS certificate.
     * @param certificateBytes
     * @return X509Certificate
     */
    private X509Certificate importInternalTlsCertificate(byte[] certificateBytes) {
        Collection<X509Certificate> x509Certificates;
        try {
            // the imported file can be a single certificate or a chain
            x509Certificates = CryptoUtils.readCertificates(certificateBytes);
            if (x509Certificates == null || x509Certificates.isEmpty()) {
                throw new CodedException(INVALID_CERTIFICATE.code());
            }
        } catch (Exception e) {
            throw new CodedException(INVALID_CERTIFICATE.code(), e);
        }
        verifyInternalCertImportability(x509Certificates);
        try {
            // create pkcs12 checks the certificate chain validity
            CertUtils.createPkcs12(internalKeyPath, certificateBytes, internalKeystorePath);
            CertUtils.writePemToFile(certificateBytes, internalCertPath);
        } catch (Exception e) {
            throw new CodedException(IMPORT_INTERNAL_CERT_FAILED.code(), e);
        }

        serverConfProvider.clearCache();

        return Iterables.get(x509Certificates, 0);
    }

    /**
     * Verifies that the chain matches the internal TLS key
     * @param newCertChain the cert chain to be imported
     */
    private void verifyInternalCertImportability(Collection<X509Certificate> newCertChain) {
        Collection<X509Certificate> internalCertChain = internalTlsCertificateRepository.getInternalTlsCertificateChain();
        PublicKey internalPublicKey = Iterables.get(internalCertChain, 0).getPublicKey();

        boolean found = newCertChain.stream().anyMatch(c -> c.getPublicKey().equals(internalPublicKey));
        if (!found) {
            throw new CodedException(KEY_NOT_FOUND.code());
        } else if (Iterables.elementsEqual(internalCertChain, newCertChain)) {
            throw new CodedException(CERTIFICATE_ALREADY_EXISTS.code());
        }
    }

}
