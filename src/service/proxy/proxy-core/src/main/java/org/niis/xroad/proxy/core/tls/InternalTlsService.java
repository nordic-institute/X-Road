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
import org.niis.xroad.common.exception.CertificateAlreadyExistsException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.InvalidCertificateException;
import org.niis.xroad.common.exception.InvalidDistinguishedNameException;
import org.niis.xroad.common.exception.KeyNotFoundException;
import org.niis.xroad.proxy.core.GenerateCertScriptProperties;
import org.niis.xroad.proxy.proto.GenerateInternalCsrRequest;
import org.niis.xroad.proxy.proto.GenerateInternalCsrResponse;
import org.niis.xroad.proxy.proto.InternalTlsCertificateChainMessage;
import org.niis.xroad.proxy.proto.InternalTlsCertificateMessage;
import org.niis.xroad.proxy.proto.InternalTlsServiceGrpc;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
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

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.KEY_CERT_GENERATION_FAILED;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class InternalTlsService extends InternalTlsServiceGrpc.InternalTlsServiceImplBase {
    public static final String IMPORT_INTERNAL_CERT_FAILED = "import_internal_cert_failed";

    private final ExternalProcessRunner externalProcessRunner;
    private final ServerConfProvider serverConfProvider;
    private final GenerateCertScriptProperties generateCertScriptProperties;
    private final InternalTlsCertificateRepository internalTlsCertificateRepository;

    private String internalCertPath = SystemProperties.getConfPath() + InternalSSLKey.CRT_FILE_NAME;
    private String internalKeyPath = SystemProperties.getConfPath() + InternalSSLKey.PK_FILE_NAME;
    private String internalKeystorePath = SystemProperties.getConfPath() + InternalSSLKey.KEY_FILE_NAME;

    @Override
    public void getInternalTlsCertificate(Empty request, StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        try {
            responseObserver.onNext(toCertificateMessage(internalTlsCertificateRepository.getInternalTlsCertificate()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void getInternalTlsCertificateChain(Empty request, StreamObserver<InternalTlsCertificateChainMessage> responseObserver) {
        try {
            responseObserver.onNext(toCertificateChainMessage(internalTlsCertificateRepository.getInternalTlsCertificateChain()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void generateInternalTlsKeyAndCertificate(Empty request, StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        try {
            X509Certificate certificate = generateInternalTlsKeyAndCertificate();
            responseObserver.onNext(toCertificateMessage(certificate));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void generateInternalCsr(GenerateInternalCsrRequest request, StreamObserver<GenerateInternalCsrResponse> responseObserver) {
        try {
            byte[] csr = generateInternalCsr(request.getDistinguishedName());
            responseObserver.onNext(toGenerateInternalCsrResponse(csr));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void importInternalTlsCertificate(InternalTlsCertificateMessage request,
                                             StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        try {
            responseObserver.onNext(toCertificateMessage(importInternalTlsCertificate(request.getInternalTlsCertificate().toByteArray())));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private InternalTlsCertificateMessage toCertificateMessage(X509Certificate certificate) throws CertificateEncodingException {
        return InternalTlsCertificateMessage.newBuilder()
                .setInternalTlsCertificate(ByteString.copyFrom(certificate.getEncoded()))
                .build();
    }

    private InternalTlsCertificateChainMessage toCertificateChainMessage(Collection<X509Certificate> certificates)
            throws CertificateEncodingException {
        var messageBuilder = InternalTlsCertificateChainMessage.newBuilder();
        for (X509Certificate cert : certificates) {
            messageBuilder.addInternalTlsCertificate(ByteString.copyFrom(cert.getEncoded()));
        }
        return messageBuilder.build();
    }

    private GenerateInternalCsrResponse toGenerateInternalCsrResponse(byte[] csr) {
        return GenerateInternalCsrResponse.newBuilder()
                .setTlsCsr(ByteString.copyFrom(csr))
                .build();
    }

    private X509Certificate generateInternalTlsKeyAndCertificate() throws InterruptedException {
        try {
            externalProcessRunner.executeAndThrowOnFailure(generateCertScriptProperties.path(),
                    generateCertScriptProperties.args().split("\\s+"));
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            log.error("Failed to generate internal TLS key and cert", e);
            throw new InternalServerErrorException(e, KEY_CERT_GENERATION_FAILED.build());
        }

        serverConfProvider.clearCache();

        return internalTlsCertificateRepository.getInternalTlsCertificate();
    }

    /**
     * Generate internal auth cert CSR
     * @param distinguishedName
     * @return
     * @throws InvalidDistinguishedNameException if {@code distinguishedName} does not conform to
     *                                           <a href="http://www.ietf.org/rfc/rfc1779.txt">RFC 1779</a> or
     *                                           <a href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>
     */
    private byte[] generateInternalCsr(String distinguishedName) throws InvalidDistinguishedNameException {
        try {
            KeyPair keyPair = CertUtils.readKeyPairFromPemFile(internalKeyPath);
            return CertUtils.generateCertRequest(keyPair.getPrivate(), keyPair.getPublic(), distinguishedName);
        } catch (IllegalArgumentException e) {
            throw new InvalidDistinguishedNameException(e);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | OperatorCreationException e) {
            throw new InternalServerErrorException(e);
        }
    }

    /**
     * Imports a new internal TLS certificate.
     * @param certificateBytes
     * @return X509Certificate
     * @throws InvalidCertificateException
     */
    private X509Certificate importInternalTlsCertificate(byte[] certificateBytes)
            throws InvalidCertificateException, KeyNotFoundException, CertificateAlreadyExistsException {
        Collection<X509Certificate> x509Certificates;
        try {
            // the imported file can be a single certificate or a chain
            x509Certificates = CryptoUtils.readCertificates(certificateBytes);
            if (x509Certificates == null || x509Certificates.isEmpty()) {
                throw new InvalidCertificateException("cannot convert bytes to certificate");
            }
        } catch (Exception e) {
            throw new InvalidCertificateException("cannot convert bytes to certificate", e);
        }
        verifyInternalCertImportability(x509Certificates);
        try {
            // create pkcs12 checks the certificate chain validity
            CertUtils.createPkcs12(internalKeyPath, certificateBytes, internalKeystorePath);
            CertUtils.writePemToFile(certificateBytes, internalCertPath);
        } catch (Exception e) {
            log.error("Failed to import internal TLS cert", e);
            throw new DeviationAwareRuntimeException("cannot import internal TLS cert", e,
                    new ErrorDeviation(IMPORT_INTERNAL_CERT_FAILED));
        }

        serverConfProvider.clearCache();

        return Iterables.get(x509Certificates, 0);
    }

    /**
     * Verifies that the chain matches the internal TLS key
     * @param newCertChain the cert chain to be imported
     * @throws KeyNotFoundException              if the public key of the cert does not match
     * @throws CertificateAlreadyExistsException if the certificate has already been imported
     */
    private void verifyInternalCertImportability(Collection<X509Certificate> newCertChain)
            throws KeyNotFoundException, CertificateAlreadyExistsException {
        Collection<X509Certificate> internalCertChain = internalTlsCertificateRepository.getInternalTlsCertificateChain();
        PublicKey internalPublicKey = Iterables.get(internalCertChain, 0).getPublicKey();

        boolean found = newCertChain.stream().anyMatch(c -> c.getPublicKey().equals(internalPublicKey));
        if (!found) {
            throw new KeyNotFoundException("The imported cert does not match the internal TLS key");
        } else if (Iterables.elementsEqual(internalCertChain, newCertChain)) {
            throw new CertificateAlreadyExistsException("The imported cert already exists");
        }
    }

}
