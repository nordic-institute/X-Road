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
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.niis.xroad.proxy.proto.GenerateInternalCsrRequest;
import org.niis.xroad.proxy.proto.GenerateInternalCsrResponse;
import org.niis.xroad.proxy.proto.InternalTlsCertificateChainMessage;
import org.niis.xroad.proxy.proto.InternalTlsCertificateMessage;
import org.niis.xroad.proxy.proto.InternalTlsServiceGrpc;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import static java.util.Arrays.stream;
import static org.niis.xroad.common.core.exception.ErrorCode.CERTIFICATE_ALREADY_EXISTS;
import static org.niis.xroad.common.core.exception.ErrorCode.IMPORT_INTERNAL_CERT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CERTIFICATE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_DISTINGUISHED_NAME;
import static org.niis.xroad.common.core.exception.ErrorCode.KEY_NOT_FOUND;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class InternalTlsService extends InternalTlsServiceGrpc.InternalTlsServiceImplBase {

    private final RpcResponseHandler rpcResponseHandler = new RpcResponseHandler();

    private final ServerConfProvider serverConfProvider;
    private final VaultClient vaultClient;
    private final VaultKeyClient vaultKeyClient;

    @Override
    public void getInternalTlsCertificate(Empty request, StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        rpcResponseHandler.handleRequest(responseObserver, () -> {
            try {
                var internalSslKey = vaultClient.getInternalTlsCredentials();
                return toCertificateMessage(internalSslKey.getCertChain()[0]);
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(e);
            }
        });
    }

    @Override
    public void getInternalTlsCertificateChain(Empty request, StreamObserver<InternalTlsCertificateChainMessage> responseObserver) {
        rpcResponseHandler.handleRequest(responseObserver, () -> {
            try {
                var internalSslKey = vaultClient.getInternalTlsCredentials();
                return toCertificateChainMessage(internalSslKey.getCertChain());
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(e);
            }
        });
    }

    @Override
    public void generateInternalTlsKeyAndCertificate(Empty request, StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        rpcResponseHandler.handleRequest(responseObserver, () -> {
            try {
                var cert = generateInternalTlsKeyAndCertificate();
                return toCertificateMessage(cert);
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(e);
            }
        });
    }

    @Override
    public void generateInternalTlsCsr(GenerateInternalCsrRequest request, StreamObserver<GenerateInternalCsrResponse> responseObserver) {
        rpcResponseHandler.handleRequest(responseObserver, () -> {
            var csr = generateInternalCsr(request.getDistinguishedName());
            return toGenerateInternalCsrResponse(csr);
        });
    }

    @Override
    public void importInternalTlsCertificate(InternalTlsCertificateMessage request,
                                             StreamObserver<InternalTlsCertificateMessage> responseObserver) {
        rpcResponseHandler.handleRequest(responseObserver, () -> {
            try {
                var importedCert = importInternalTlsCertificate(request.getInternalTlsCertificate().toByteArray());
                return toCertificateMessage(importedCert);
            } catch (Exception e) {
                throw XrdRuntimeException.systemException(e);
            }
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

    private InternalTlsCertificateChainMessage toCertificateChainMessage(X509Certificate[] certificates) {
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

    private X509Certificate generateInternalTlsKeyAndCertificate()
            throws CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var vaultKeyData = vaultKeyClient.provisionNewCerts();
        var certChain = Stream.concat(stream(vaultKeyData.identityCertChain()), stream(vaultKeyData.trustCerts()))
                .toArray(X509Certificate[]::new);
        var internalTlsKey = new InternalSSLKey(vaultKeyData.identityPrivateKey(), certChain);
        vaultClient.createInternalTlsCredentials(internalTlsKey);
        log.info("Successfully created internal TLS credentials");
        var internalSslKey = vaultClient.getInternalTlsCredentials();
        return internalSslKey.getCertChain()[0];
    }

    /**
     * Generate internal auth cert CSR
     *
     * @param distinguishedName the DN to be used in the CSR
     * @return the CSR bytes
     */
    private byte[] generateInternalCsr(String distinguishedName) {
        try {
            var internalSslKey = vaultClient.getInternalTlsCredentials();
            return CertUtils.generateCertRequest(
                    internalSslKey.getKey(), internalSslKey.getCertChain()[0].getPublicKey(), distinguishedName
            );
        } catch (IllegalArgumentException e) {
            throw new CodedException(INVALID_DISTINGUISHED_NAME.code(), e);
        } catch (Exception e) {
            throw new CodedException(INTERNAL_ERROR.code(), e);
        }
    }

    /**
     * Imports a new internal TLS certificate.
     *
     * @param certificateBytes the certificate bytes
     * @return X509Certificate
     */
    private X509Certificate importInternalTlsCertificate(byte[] certificateBytes)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        Collection<X509Certificate> x509Certificates;
        try {
            // the imported file can be a single certificate or a chain
            x509Certificates = CryptoUtils.readCertificates(certificateBytes);
            if (x509Certificates.isEmpty()) {
                throw new CodedException(INVALID_CERTIFICATE.code());
            }
        } catch (Exception e) {
            throw new CodedException(INVALID_CERTIFICATE.code(), e);
        }
        verifyInternalCertImportability(x509Certificates);
        try {
            var internalSslKey = vaultClient.getInternalTlsCredentials();
            var internalSslKeyWithNewCert = new InternalSSLKey(internalSslKey.getKey(), x509Certificates.toArray(X509Certificate[]::new));
            vaultClient.createInternalTlsCredentials(internalSslKeyWithNewCert);
        } catch (Exception e) {
            throw new CodedException(IMPORT_INTERNAL_CERT_FAILED.code(), e);
        }

        serverConfProvider.clearCache();

        return Iterables.get(x509Certificates, 0);
    }

    /**
     * Verifies that the chain matches the internal TLS key
     *
     * @param newCertChain the cert chain to be imported
     */
    private void verifyInternalCertImportability(Collection<X509Certificate> newCertChain)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var internalCertChain = Arrays.asList(vaultClient.getInternalTlsCredentials().getCertChain());
        PublicKey internalPublicKey = internalCertChain.getFirst().getPublicKey();

        boolean found = newCertChain.stream().anyMatch(c -> c.getPublicKey().equals(internalPublicKey));
        if (!found) {
            throw new CodedException(KEY_NOT_FOUND.code());
        } else if (Iterables.elementsEqual(internalCertChain, newCertChain)) {
            throw new CodedException(CERTIFICATE_ALREADY_EXISTS.code());
        }
    }

}
