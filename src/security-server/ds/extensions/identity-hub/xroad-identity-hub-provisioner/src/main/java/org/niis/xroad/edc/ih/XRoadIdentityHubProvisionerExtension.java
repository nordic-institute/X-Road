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

import ee.ria.xroad.signer.SignerRpcClient;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;

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
import org.eclipse.edc.identithub.spi.did.events.DidDocumentObservable;
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
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Extension(XRoadIdentityHubProvisionerExtension.NAME)
@SuppressWarnings({"checkstyle:LineLength", "checkstyle:MagicNumber"})
public class XRoadIdentityHubProvisionerExtension implements ServiceExtension {

    static final String NAME = "X-Road Provisioner for Identity Hub";

    private static final String XROAD_SELF_DESCRIPTION_TYPE = "XRoadSelfDescription";

    private static final String EDC_DID_KEY_ID = "edc.did.key.id";
    private static final String EDC_HOSTNAME = "edc.hostname";
    private static final String EDC_XROAD_MEMBER_ID = "edc.xroad-member-id";

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private KeyPairResourceStore keyPairResourceStore;

    @Inject
    private CredentialStore credentialStore;

    @Inject
    private JwsSignerProvider jwsSignerProvider;

    //This is required to force a specific load order for VCs to get published status
    @Inject
    private DidDocumentObservable didDocumentObservable;

    @Inject
    private SignerRpcClient signerRpcClient;

    private Config config;
    private Monitor monitor;

    @Override
    public void initialize(ServiceExtensionContext context) {
        config = context.getConfig();
        monitor = context.getMonitor();
    }

    @Override
    @SneakyThrows
    public void start() {
        String participantId = config.getString(BootServicesExtension.PARTICIPANT_ID);
        String hostname = config.getString(EDC_HOSTNAME);
        String keyId = config.getString(EDC_DID_KEY_ID);
        PublicKey publicKey = getPublicKey(keyId);
        String publicKeyPem = convertPublicKeyToPem(publicKey);

        monitor.info("Creating participant context for %s".formatted(participantId));
        createParticipantContext(hostname, participantId, keyId, publicKeyPem);
        createKeyPairs(participantId, keyId, publicKeyPem);
        createCredentials(config.getString(EDC_XROAD_MEMBER_ID), participantId, keyId);
    }


    private void createParticipantContext(String hostname, String participantId, String keyId, String publicKeyPem) {
        //Manifest is used for did generation. Same principle as /api/management/v1/participants/
        var manifest = ParticipantManifest.Builder.newInstance()
                .active(true)
                .participantId(participantId)
                .did(participantId)
                .key(KeyDescriptor.Builder.newInstance()
                        .keyId(participantId + "#" + keyId)
                        .privateKeyAlias(keyId)
                        .publicKeyPem(publicKeyPem)
                        .build())
                .serviceEndpoint(new Service("credentialService-1", "CredentialService", "https://%s:%d%s/v1/participants/%s".formatted(
                        hostname,
                        config.getInteger("web.http.resolution.port", 20001),
                        config.getString("web.http.resolution.path", "/resolution"),
                        Base64.getUrlEncoder().encodeToString(participantId.getBytes(StandardCharsets.UTF_8)))))
                .build();
        participantContextService.createParticipantContext(manifest);
    }

    private void createKeyPairs(String participantId, String keyId, String publicKeyPem) {
        var keyPairResource = KeyPairResource.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .keyId(participantId + "#" + keyId)
                .privateKeyAlias(keyId)
                .isDefaultPair(true)
                .participantId(participantId)
                .serializedPublicKey(publicKeyPem)
                .state(KeyPairState.ACTIVATED)
                .build();
        keyPairResourceStore.create(keyPairResource);
    }

    private void createCredentials(String xroadMemberIdentifier, String participantId, String keyId) throws Exception {
        var signer = jwsSignerProvider.createJwsSigner(keyId)
                .orElseThrow(f -> new EdcException("JWSSigner cannot be generated for private key '%s': %s".formatted(keyId, f.getFailureDetail())));
        var selfDescription = new XRoadSelfDescriptionGenerator(signerRpcClient)
                .generate(xroadMemberIdentifier, signer, participantId, keyId);
        storeCredential("xroad-self-description", selfDescription.serialize(), participantId,
                XROAD_SELF_DESCRIPTION_TYPE);
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

    private PublicKey getPublicKey(String keyId) throws Exception {
        var token = signerRpcClient.getTokenForKeyId(keyId);
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

}
