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

import lombok.SneakyThrows;
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
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Identity Hub's management API is missing an endpoint for inserting credentials.
 * This extension is a "dirty" workaround to insert Verifiable Credentials into the Identity Hub.
 */
@Extension(IdentityHubCredentialInsertionExtension.NAME)
@SuppressWarnings({"checkstyle:LineLength", "checkstyle:MagicNumber"})
public class IdentityHubCredentialInsertionExtension implements ServiceExtension {

    static final String NAME = "X-Road Credential insertion extension for Identity Hub";

    private static final String CREDENTIAL_TYPE = "XRoadCredential";

    private static final String CREDENTIALS_DIR_PATH = "edc.ih.credentials.path";

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private CredentialStore credentialStore;

    @Inject
    private KeyPairResourceStore keyPairResourceStore;

    //This is required to force a specific load order for VCs to get published status
    @Inject
    private DidDocumentObservable didDocumentObservable;

    @Override
    @SneakyThrows
    public void initialize(ServiceExtensionContext context) {
        File credentialDir = new File(context.getConfig().getString(CREDENTIALS_DIR_PATH));
        for (File participantDir : credentialDir.listFiles(File::isDirectory)) {
            var participantId = resolveParticipantId(participantDir);
            context.getMonitor().info("Initializing DID & credentials for participant %s".formatted(participantId));
            String publicKey = getPublicKey(participantDir);
            createParticipantContext(context, participantId, publicKey);
            createCredentials(participantDir, participantId);
            createKeyPairs(participantId, publicKey);
        }
    }

    private String resolveParticipantId(File participantDir) {
        if (participantDir.getName().equalsIgnoreCase(System.getenv("EDC_HOSTNAME"))) {
            return "did:web:" + System.getenv("EDC_HOSTNAME") + "%3A9396";
        } else {
            return "did:web:" + System.getenv("EDC_HOSTNAME") + "%3A9396:" + participantDir.getName();
        }
    }

    private void createParticipantContext(ServiceExtensionContext context, String participantId, String publicKey) {
        //Manifest is used for did generation. Same principle as /api/management/v1/participants/
        var manifest = ParticipantManifest.Builder.newInstance()
                .active(true)
                .participantId(participantId)
                .did(participantId)
                .key(KeyDescriptor.Builder.newInstance()
                        .keyId(participantId + "#" + System.getenv("EDC_DID_KEY_ID"))
                        .privateKeyAlias(participantId + "-private-key")
                        .publicKeyPem(publicKey)
                        .build())
                .serviceEndpoint(new Service("credentialService-1", "CredentialService", "https://%s:%d%s/v1/participants/%s".formatted(
                        System.getenv("EDC_HOSTNAME"),
                        context.getConfig().getInteger("web.http.resolution.port", 20001),
                        context.getConfig().getString("web.http.resolution.path", "/resolution"),
                        Base64.getUrlEncoder().encodeToString(participantId.getBytes(StandardCharsets.UTF_8)))))
                .build();
        participantContextService.createParticipantContext(manifest);
    }

    private void createCredentials(File participantDir, String participantId) throws IOException {
        for (File credentialFile : participantDir.listFiles((dir, name) -> name.endsWith(".json"))) {
            storeCredential(credentialFile.toPath(), participantId);
        }
    }

    private void storeCredential(Path credentialPath, String participantId) throws IOException {
        String credentialContent = Files.readString(credentialPath);

        var verifiableCredential = VerifiableCredential.Builder.newInstance()
                .credentialSubject(CredentialSubject.Builder.newInstance().id("test-subject").claim("test-key", "test-val").build())
                .issuanceDate(Instant.now())
                .type(CREDENTIAL_TYPE)
                .issuer(new Issuer(participantId, Map.of()))
                .id(participantId)
                .build();

        var verifiableCredentialContainer = new VerifiableCredentialContainer(credentialContent, CredentialFormat.JSON_LD, verifiableCredential);
        var verifiableCredentialResource = VerifiableCredentialResource.Builder.newInstance()
                .issuerId("test-issuer")
                .holderId("test-holder")
                .state(VcStatus.ISSUED)
                .participantId(participantId)
                .credential(verifiableCredentialContainer)
                .id(UUID.randomUUID().toString())
                .build();
        credentialStore.create(verifiableCredentialResource);
    }

    private void createKeyPairs(String participantId, String publicKey) {
        var keyPairResource = KeyPairResource.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .keyId(participantId + "#" + System.getenv("EDC_DID_KEY_ID"))
                .privateKeyAlias(participantId + "-private-key")
                .isDefaultPair(true)
                .participantId(participantId)
                .serializedPublicKey(publicKey)
                .state(KeyPairState.ACTIVE)
                .build();
        keyPairResourceStore.create(keyPairResource);
    }

    private String getPublicKey(File participantDir) throws IOException {
        var publicKeyFile = participantDir.toPath().resolve("public.pem");
        return Files.readString(publicKeyFile);
    }

}
