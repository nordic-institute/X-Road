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
package org.niis.xroad.edc.ih.vc;

import com.apicatalog.jsonld.loader.SchemeRouter;
import com.apicatalog.ld.signature.VerificationMethod;
import com.apicatalog.vc.method.resolver.MethodResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import org.eclipse.edc.iam.identitytrust.spi.verification.SignatureSuiteRegistry;
import org.eclipse.edc.iam.identitytrust.spi.verification.VerifierContext;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.security.signature.jws2020.Jws2020ProofDraft;
import org.eclipse.edc.security.signature.jws2020.Jws2020SignatureSuite;
import org.eclipse.edc.verifiablecredentials.linkeddata.LdpVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.eclipse.edc.jsonld.util.JacksonJsonLd.createObjectMapper;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.edc.ih.vc.TestFunctions.createKeyPair;

class VCGenerator {

    private static final String VP_HOLDER = "did:web:vp-holder";
    private static final String VC_CONTENT_CERTIFICATE_EXAMPLE = """
            {
              "@context": [
                "https://www.w3.org/2018/credentials/v1",
                "https://w3id.org/security/suites/jws-2020/v1",
                "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#"
              ],
              "type": [
                "VerifiableCredential"
              ],
              "id": "%s",
              "issuer": "%s",
              "issuanceDate": "2024-03-15T14:20:56.969Z",
              "credentialSubject": {
                "gx:clientId": "%s",
                "id": "%s"
              }
            }
            """;

    private static final String VP_CONTENT_TEMPLATE = """
            {
                "@context": [
                  "https://www.w3.org/2018/credentials/v1",
                  "https://www.w3.org/2018/credentials/examples/v1"
                ],
                "id": "https://exapmle.com/test-vp",
                "holder": "https://holder.test.com",
                "type": [
                  "VerifiablePresentation"
                ],
                "verifiableCredential": [
                  %s
                ]
            }
            """;

    private final ObjectMapper mapper = createObjectMapper();
    private final Jws2020SignatureSuite jwsSignatureSuite = new Jws2020SignatureSuite(mapper);
    private final TestDocumentLoader testDocLoader = new TestDocumentLoader("https://org.eclipse.edc/", "",
            SchemeRouter.defaultInstance());

    private final SignatureSuiteRegistry suiteRegistry = mock();
    private final MethodResolver mockDidResolver = mock();
    private VerifierContext context = null;
    private LdpVerifier ldpVerifier;
    private TitaniumJsonLd jsonLd;

    private KeyStore keyStore;

    @BeforeEach
    void setUp() throws URISyntaxException {
        jsonLd = new TitaniumJsonLd(mock());
        jsonLd.registerCachedDocument("https://www.w3.org/ns/odrl.jsonld",
                Thread.currentThread().getContextClassLoader().getResource("odrl.jsonld").toURI());
        jsonLd.registerCachedDocument("https://www.w3.org/ns/did/v1",
                Thread.currentThread().getContextClassLoader().getResource("jws2020.json").toURI());
        jsonLd.registerCachedDocument("https://w3id.org/security/suites/jws-2020/v1",
                Thread.currentThread().getContextClassLoader().getResource("jws2020.json").toURI());
        jsonLd.registerCachedDocument("https://www.w3.org/2018/credentials/v1",
                Thread.currentThread().getContextClassLoader().getResource("credentials.v1.json").toURI());
        jsonLd.registerCachedDocument("https://www.w3.org/2018/credentials/examples/v1",
                Thread.currentThread().getContextClassLoader().getResource("examples.v1.json").toURI());
        ldpVerifier = LdpVerifier.Builder.newInstance()
                .signatureSuites(suiteRegistry)
                .jsonLd(jsonLd)
                .objectMapper(mapper)
                .methodResolvers(List.of(mockDidResolver))
                .loader(testDocLoader)
                .build();
        context = VerifierContext.Builder.newInstance().verifier(ldpVerifier).build();

        when(suiteRegistry.getAllSuites()).thenReturn(List.of(jwsSignatureSuite));
    }

    @Test
    void prepareKeys() throws Exception {
        var csPath = "../../../../../Docker/centralserver/files/edc/etc/certs/cert.pfx";
        var ssPath = "../../../../../Docker/securityserver/files/edc/etc/certs/cert.pfx";

        prepareKeys(csPath, "alias_cs", "did:web:cs%3A9396:cs");
        prepareKeys(ssPath, "alias_ss0", "did:web:ss0%3A9396:ss0");
        prepareKeys(ssPath, "alias_ss1", "did:web:ss1%3A9396:ss1");
    }

    void prepareKeys(String keyStorePath, String keyAlias, String did) throws Exception {
        keyStore = loadKeyStore(keyStorePath, "123456");

        // create signed VC
        Key key = keyStore.getKey(keyAlias, "123456".toCharArray());

        var publicKey = keyStore.getCertificate(keyAlias).getPublicKey();
        var vcKey = new RSAKey.Builder((RSAPublicKey) publicKey)
                .privateKey((PrivateKey) key)
                .build();

        var vcVerificationMethod = createKeyPair(vcKey, did);
        var rawVc = TestFunctions.signDocument(
                VC_CONTENT_CERTIFICATE_EXAMPLE.formatted(UUID.randomUUID().toString(), did, did, did), vcKey,
                generateEmbeddedProof(vcVerificationMethod), testDocLoader);

        var input = VP_CONTENT_TEMPLATE.formatted(rawVc);

        var vpVerificationMethod = createKeyPair(vcKey, VP_HOLDER);
        var rawVp = TestFunctions.signDocument(input, vcKey, generateEmbeddedProof(vpVerificationMethod),
                testDocLoader);

        var res = ldpVerifier.verify(rawVp, context);
        Assertions.assertFalse(res.failed());

        System.out.printf("%S rawVc: %s%n", did, rawVc);
    }


    private Jws2020ProofDraft generateEmbeddedProof(VerificationMethod verificationMethod) {
        return proofBuilder(verificationMethod)
                .build();
    }

    private Jws2020ProofDraft.Builder proofBuilder(VerificationMethod verificationMethod) {
        return Jws2020ProofDraft.Builder.newInstance()
                .mapper(mapper)
                .created(Instant.now())
                .verificationMethod(verificationMethod)
                .proofPurpose(URI.create("https://w3id.org/security#assertionMethod"));
    }

    private KeyStore loadKeyStore(String file, String password) {
        try {
            KeyStore pkcsKeyStore = KeyStore.getInstance("pkcs12");
            InputStream fis = new FileInputStream(file);
            pkcsKeyStore.load(fis, password.toCharArray());
            fis.close();

            return pkcsKeyStore;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
