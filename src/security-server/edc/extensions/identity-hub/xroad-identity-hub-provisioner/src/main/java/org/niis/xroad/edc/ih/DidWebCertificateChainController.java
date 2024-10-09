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
package org.niis.xroad.edc.ih;


import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.eclipse.edc.identithub.spi.did.DidWebParser;
import org.eclipse.edc.identityhub.spi.keypair.KeyPairService;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@Path(DidWebCertificateChainController.CERTIFICATE_CHAIN_PATH)
@RequiredArgsConstructor
public class DidWebCertificateChainController {

    static final String CERTIFICATE_CHAIN_PATH = "/.well-known/certificate-chain.pem";

    private final DidWebParser didWebParser;
    private final KeyPairService keyPairService;
    private final GlobalConfProvider globalConfProvider;

    @GET
    public String getCertificateChain(@Context ContainerRequestContext context) throws Exception {
        var absolutePath = context.getUriInfo().getAbsolutePath().toString();
        var didUrl = URI.create(absolutePath.replace(CERTIFICATE_CHAIN_PATH, "/.well-known/did.json"));
        String did = didWebParser.parse(didUrl, Charset.defaultCharset());

        var query = QuerySpec.Builder.newInstance()
                .filter(new Criterion("participantId", "=", did))
                .build();
        var keyPairResult = keyPairService.query(query)
                .orElseThrow(f -> new EdcException("Error obtaining key pair for participant '%s': %s".formatted(did, f.getFailureDetail())));

        var keyPair = keyPairResult.stream().findFirst().orElseThrow();
        String keyId = keyPair.getPrivateKeyAlias();
        var activeCertificate = getActiveCertificate(keyId);

        var certChainStringBuilder = new StringBuilder();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(activeCertificate.getCertificateBytes())) {
            var certFactory = CertificateFactory.getInstance("X.509");
            var x509Cert = (X509Certificate) certFactory.generateCertificate(inputStream);
            var certChain = globalConfProvider.getCertChain(globalConfProvider.getInstanceIdentifier(), x509Cert);
            for (var cert : certChain.getAllCerts()) {
                certChainStringBuilder.append(convertCertificateToPem(cert.getEncoded()));
            }
        }
        return certChainStringBuilder.toString();
    }

    private CertificateInfo getActiveCertificate(String keyId) throws Exception {
        var token = SignerProxy.getTokenForKeyId(keyId);
        var certificates = token.getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getId().equals(keyId))
                .findFirst()
                .map(KeyInfo::getCerts)
                .orElseThrow();
        return certificates.stream().filter(CertificateInfo::isActive).findFirst().orElseThrow();
    }

    private String convertCertificateToPem(byte[] encodedCert) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            PemObject pemObject = new PemObject("CERTIFICATE", encodedCert);
            pemWriter.writeObject(pemObject);
        }
        return stringWriter.toString();
    }

}
