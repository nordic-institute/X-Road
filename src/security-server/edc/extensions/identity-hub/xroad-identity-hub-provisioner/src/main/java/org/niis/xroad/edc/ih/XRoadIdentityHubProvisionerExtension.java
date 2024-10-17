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
import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.SneakyThrows;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.eclipse.edc.boot.BootServicesExtension;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredentialContainer;
import org.eclipse.edc.identithub.spi.did.DidWebParser;
import org.eclipse.edc.identithub.spi.did.events.DidDocumentObservable;
import org.eclipse.edc.identityhub.spi.IdentityHubApiContext;
import org.eclipse.edc.identityhub.spi.keypair.KeyPairService;
import org.eclipse.edc.identityhub.spi.keypair.model.KeyPairResource;
import org.eclipse.edc.identityhub.spi.keypair.model.KeyPairState;
import org.eclipse.edc.identityhub.spi.participantcontext.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.identityhub.spi.store.CredentialStore;
import org.eclipse.edc.identityhub.spi.store.KeyPairResourceStore;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.jwt.signer.spi.JwsSignerProvider;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.security.token.jwt.CryptoConverter;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.web.spi.WebService;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.niis.xroad.edc.ih.DidWebCertificateChainController.CERTIFICATE_CHAIN_PATH;
import static org.niis.xroad.edc.ih.GaiaXCredentialPayloadGenerator.composeGaiaXParticipantDocument;
import static org.niis.xroad.edc.ih.GaiaXCredentialPayloadGenerator.composeGaiaXTermsAndConditionsDocument;
import static org.niis.xroad.edc.ih.GaiaXCredentialPayloadGenerator.composeXRoadCredentialDocument;

/**
 * Identity Hub's management API is missing an endpoint for inserting credentials.
 * This extension is a "dirty" workaround to insert a Verifiable Credential into the Identity Hub.
 */
@Extension(XRoadIdentityHubProvisionerExtension.NAME)
@SuppressWarnings({"checkstyle:LineLength", "checkstyle:MagicNumber"})
public class XRoadIdentityHubProvisionerExtension implements ServiceExtension {

    static final String NAME = "X-Road Credential insertion extension for Identity Hub";

    private static final String XROAD_CREDENTIAL_TYPE = "XRoadCredential";

    private static final String GAIAX_CREDENTIAL_TYPE = "GaiaXCredential";

    private static final String DID_KEY_ID = "edc.did.key.id";

    private static final Map<String, String> HOSTNAME_XRDIDENTIFIER_MAP = Map.of(
            "ss0", "DEV:COM:4321:TestService",
            "ss1", "DEV:COM:4321:TestClient"
    );

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private CredentialStore credentialStore;

    @Inject
    private KeyPairResourceStore keyPairResourceStore;

    //This is required to force a specific load order for VCs to get published status
    @Inject
    private DidDocumentObservable didDocumentObservable;

    @Inject
    private JwsSignerProvider jwsSignerProvider;

    @Inject
    private WebService webService;

    @Inject
    private KeyPairService keyPairService;

    @Inject
    GlobalConfProvider globalConfProvider;

    private Config config;
    private Monitor monitor;

    @Override
    public void initialize(ServiceExtensionContext context) {
        config = context.getConfig();
        monitor = context.getMonitor();

        webService.registerResource(
                IdentityHubApiContext.IH_DID,
                new DidWebCertificateChainController(new DidWebParser(), keyPairService, globalConfProvider));

        webService.registerResource(
                IdentityHubApiContext.RESOLUTION,
                new EnvelopedVerifiableCredentialsController(credentialStore, jwsSignerProvider, config.getString(DID_KEY_ID)));
    }

    @Override
    @SneakyThrows
    public void start() {
        String participantId = config.getString(BootServicesExtension.PARTICIPANT_ID);
        String hostname = System.getenv("EDC_HOSTNAME");
        String keyId = config.getString(DID_KEY_ID);
        PublicKey publicKey = getPublicKey(keyId);

        monitor.info("Inserting credentials for participant %s".formatted(participantId));
        createParticipantContext(hostname, participantId, keyId, convertToJWK(publicKey, hostname));
        createKeyPairs(participantId, keyId, convertPublicKeyToPem(publicKey));
        createCredentials(hostname, participantId, keyId);
    }


    private void createParticipantContext(String hostname, String participantId, String keyId, JWK publicKeyJWK) {
        //Manifest is used for did generation. Same principle as /api/management/v1/participants/
        var manifest = ParticipantManifest.Builder.newInstance()
                .active(true)
                .participantId(participantId)
                .did(participantId)
                .key(KeyDescriptor.Builder.newInstance()
                        .keyId(participantId + "#" + keyId)
                        .privateKeyAlias(keyId)
                        .publicKeyJwk(publicKeyJWK.toJSONObject())
                        .build())
                .serviceEndpoint(new Service("credentialService-1", "CredentialService", "https://%s:%d%s/v1/participants/%s".formatted(
                        hostname,
                        config.getInteger("web.http.resolution.port", 20001),
                        config.getString("web.http.resolution.path", "/resolution"),
                        Base64.getUrlEncoder().encodeToString(participantId.getBytes(StandardCharsets.UTF_8)))))
                .build();
        participantContextService.createParticipantContext(manifest);
    }

    private void createCredentials(String hostname, String participantId, String keyId) throws JOSEException {
        var signer = jwsSignerProvider.createJwsSigner(keyId)
                .orElseThrow(f -> new EdcException("JWSSigner cannot be generated for private key '%s': %s".formatted(keyId, f.getFailureDetail())));

        var participantDoc = composeGaiaXParticipantDocument(hostname);
        var participantVc = toVcJwt(participantDoc, signer, participantId, keyId);
        storeCredential("https://" + hostname + ":9396/participant.json", participantVc, participantId, GAIAX_CREDENTIAL_TYPE);

        var tsaandcsDoc = composeGaiaXTermsAndConditionsDocument(hostname);
        var tsaandcsVc = toVcJwt(tsaandcsDoc, signer, participantId, keyId);
        storeCredential("https://" + hostname + ":9396/tsandcs.json", tsaandcsVc, participantId, GAIAX_CREDENTIAL_TYPE);

        var xrdCredentialDoc = composeXRoadCredentialDocument(hostname, HOSTNAME_XRDIDENTIFIER_MAP.get(hostname));
        var xrdCredentialVc = toVcJwt(xrdCredentialDoc, signer, participantId, keyId);
        storeCredential("https://" + hostname + ":9396/xrd-cred.json", xrdCredentialVc, participantId, XROAD_CREDENTIAL_TYPE);
    }

    private String toVcJwt(String payload, JWSSigner signer, String did, String keyId) throws JOSEException {
        // Create and sign JWS
        var header = new JWSHeader.Builder(CryptoConverter.getRecommendedAlgorithm(signer))
                .base64URLEncodePayload(true)
                .customParam("iss", did)
                .keyID(did + "#" + keyId)
                .customParam("iat", Instant.now().toString())
                .customParam("exp", Instant.now().plus(90, DAYS).toString())
                .contentType("vc+ld")
                //.type(JOSEObjectType.JWT)
                .build();
        var detachedPayload = new Payload(payload);
        var jwsObject = new JWSObject(header, detachedPayload);
        jwsObject.sign(signer);
        return jwsObject.serialize();
    }

    private void storeCredential(String id, String credential, String participantId, String credentialType) {
        var verifiableCredential = VerifiableCredential.Builder.newInstance()
                .credentialSubject(CredentialSubject.Builder.newInstance().id("test-subject").claim("test-key", "test-val").build())
                .issuanceDate(Instant.now())
                .type(credentialType)
                .issuer(new Issuer(participantId, Map.of()))
                .id(participantId)
                .build();

        var verifiableCredentialContainer = new VerifiableCredentialContainer(credential, CredentialFormat.JWT, verifiableCredential);
        var verifiableCredentialResource = VerifiableCredentialResource.Builder.newInstance()
                .issuerId("test-issuer")
                .holderId("test-holder")
                .state(VcStatus.ISSUED)
                .participantId(participantId)
                .credential(verifiableCredentialContainer)
                .id(id)
                .build();
        credentialStore.create(verifiableCredentialResource);
    }

    private void createKeyPairs(String participantId, String keyId, String publicKeyPem) {
        var keyPairResource = KeyPairResource.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .keyId(participantId + "#" + keyId)
                .privateKeyAlias(keyId)
                .isDefaultPair(true)
                .participantId(participantId)
                .serializedPublicKey(publicKeyPem)
                .state(KeyPairState.ACTIVE)
                .build();
        keyPairResourceStore.create(keyPairResource);
    }

    private PublicKey getPublicKey(String keyId) throws Exception {
        var token = SignerProxy.getTokenForKeyId(keyId);
        String base64PublicKey = token.getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getId().equals(keyId))
                .findFirst()
                .map(KeyInfo::getPublicKey)
                .orElseThrow();
        byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private String convertPublicKeyToPem(PublicKey publicKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            PemObject pemObject = new PemObject("PUBLIC KEY", publicKey.getEncoded());
            pemWriter.writeObject(pemObject);
        }
        return stringWriter.toString();
    }

    public JWK convertToJWK(PublicKey publicKey, String hostname) {
        // Essential for Gaia-X Compliance to validate the key's certificate trustworthiness during the issuance of compliance credential
        var x509Url = URI.create("https://" + hostname + ":9396" + CERTIFICATE_CHAIN_PATH);
        return switch (publicKey) {
            case RSAPublicKey rsaPublicKey -> new RSAKey.Builder(rsaPublicKey).x509CertURL(x509Url).build();
            case ECPublicKey ecPublicKey -> new ECKey.Builder(getCurve(ecPublicKey), ecPublicKey).x509CertURL(x509Url).build();
            default -> throw new IllegalArgumentException("The provided key is neither an RSA nor an EC public key");
        };
    }

    private Curve getCurve(ECPublicKey ecPublicKey) {
        var ellipticCurve = ecPublicKey.getParams().getCurve();
        if (ellipticCurve.equals(Curve.P_256.toECParameterSpec().getCurve())) {
            return Curve.P_256;
        } else if (ellipticCurve.equals(Curve.SECP256K1.toECParameterSpec().getCurve())) {
            return Curve.SECP256K1;
        } else if (ellipticCurve.equals(Curve.P_384.toECParameterSpec().getCurve())) {
            return Curve.P_384;
        } else if (ellipticCurve.equals(Curve.P_521.toECParameterSpec().getCurve())) {
            return Curve.P_521;
        } else {
            throw new IllegalArgumentException("Unsupported curve: " + ellipticCurve);
        }
    }

}
