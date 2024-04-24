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
package org.niis.xroad.edc.extension.iam;

import lombok.SneakyThrows;
import org.eclipse.edc.identityhub.spi.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.model.KeyPairResource;
import org.eclipse.edc.identityhub.spi.model.KeyPairState;
import org.eclipse.edc.identityhub.spi.model.VcState;
import org.eclipse.edc.identityhub.spi.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.model.participant.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.model.participant.ParticipantManifest;
import org.eclipse.edc.identityhub.spi.store.CredentialStore;
import org.eclipse.edc.identityhub.spi.store.KeyPairResourceStore;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredentialContainer;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.eclipse.edc.identityhub.core.CoreServicesExtension.OWN_DID_PROPERTY;
import static org.niis.xroad.edc.extension.iam.IatpScopeExtension.CREDENTIAL_FORMAT;
import static org.niis.xroad.edc.extension.iam.IdentityHubCredentialInsertionExtension.NAME;

/**
 * Identity Hub's management API is missing an endpoint for inserting credentials.
 * This extension is a "dirty" workaround to insert a Verifiable Credential into the Identity Hub.
 */
@Extension(NAME)
@SuppressWarnings("checkstyle:LineLength")
public class IdentityHubCredentialInsertionExtension implements ServiceExtension {

    static final String NAME = "Credential insertion extension for Identity Hub";

    private static final String CREDENTIALS_DIR_PATH = "edc.ih.credentials.path";

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private CredentialStore credentialStore;

    @Inject
    private KeyPairResourceStore keyPairResourceStore;


    private static final Map<String, String> PARTICIPANY_PUBKEY_MAP = Map.of(
            "did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com", """
                        -----BEGIN PUBLIC KEY-----
                        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAm/BUDsukPkt1OQTZWRIb
                        zylv1chElgsSTXAADbjTcqUkOZYF/kTprPwivMtHqb8i9Y+lDZSJGquyrXtMVjc3
                        IqQ6cI8HbY1sDa74A6tJ2bNHvlMKlY5Yc8p+dBaHICviDy2DhY/HLbpk/w6/zJX3
                        Tbw6qu5uVRrzTNoYLLvDmP2LEUN1mSYebRa5aQ3gP0F2QZZsrR0mt59Vh6aaiGg6
                        KktEpMMXPi4YTmvrbbvLvkyZbACCWj/PngDvzNrJx62b6CdbPzXjbE82qbZ/zIOL
                        UIuaeczRwp5EpMyUz/wuRQkfrvRV3uIYxjsoXY8ZYgJJ+iJ1oTkuvr245G4yUvyp
                        LQIDAQAB
                        -----END PUBLIC KEY-----
                        """,
            "did:web:did-server:cs", """
                        -----BEGIN PUBLIC KEY-----
                        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnh15T1B2vCpFDLbS4RAFmf3YfNhYj24N
                        FtXBlNUOZr85heKcbTy4rZcwbRysNTC2M+fFFpGFB3HSozmvSShbziQ/6P427+1oaE/jf5YLLeGQ
                        bvZY1bFoUMr7aQ3LBG9MhFECaSB+dmOpBrAoqrz5JK1dtirpEuHX/TyV0f/i8gZkmX9UY+PdLvc7
                        9C78RopgRVH/FwBhDiq6CP8hAqXXbX+IJYyRou5a9R3ONhBdxtuHkQpekToIDjpst75A81tzraWq
                        n3ACBbDxDQFQXnOiYGL/DDEPXueXVUTrg6anhIOb2nz1OeKc+gRliagM7qi8YgNwLZiecQrOdIV3
                        3vN1HQIDAQAB
                        -----END PUBLIC KEY-----
                        """,
            "did:web:did-server:ss0", """
                        -----BEGIN PUBLIC KEY-----
                        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4U71KJpjRZ57UlpADF98
                        0ORfMr3ea3vuZhGGmODkLuePeN6fjd1oFyu/vdCcJo9iXEtnA6DE9DFOhS173QLa
                        4TJPzXkA+PNa9uHA1r1LzGfd+QuAz8fCR7WoqeP2Oty7WJwn0XFgOM6XJlJ2UdVF
                        oFIKZwnVZlmFfarsY9tdS8TWH/woWY1K3/hTjwnORwh+R9YKyPjZEB4KUxteytlK
                        FVOoaOEvaIaygwa5d3vOTu8UuU1z1E/8Hz+4QjvkfAztmnoMZiMuRRfxD0c46Y0O
                        CVcLDs6wF0tPXdXBiS/f6KKkqvKKi3Prky9Gv27XtARx9nVv5uim5NJCwKfK65pZ
                        4QIDAQAB
                        -----END PUBLIC KEY-----
                        """,
            "did:web:did-server:ss1", """
                        -----BEGIN PUBLIC KEY-----
                        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwpqoS8gue7JxYsWyzQMj
                        eLK0q4FWyRLiP1UQCbbJaqhDZ/jUl/2UgzHeTyYNyeFJCRS/NCAuWe3hG+RUvKDe
                        F6o1eVlpoVX0MRrNwggo465y9YlON+z7yXUGQvblmJDEbBEoYV51JHISFCTB4/tH
                        dwSBg6zPHzxW4KMdQOdHnG0kuipettlCdvwf21LCs9TYK7H8/Gj30VilSJO4bBeg
                        tg75JjgDDipwE/duK1gRCwFfx1JmRq22CU8EpxoIXoRQfnzjUCG2VJJYeeJMW8iD
                        vTp2SQ3JK4HJdMicmB/9jJPRs1ONEumjCwRF4IgtX5rnIePbOwF6gtISzQG4/WB0
                        AwIDAQAB
                        -----END PUBLIC KEY-----
                        """
    );

    @Override
    @SneakyThrows
    public void initialize(ServiceExtensionContext context) {

        String participantId = context.getConfig().getString(OWN_DID_PROPERTY);
        var manifest = ParticipantManifest.Builder.newInstance()
                .participantId(participantId)
                .did(participantId)
                .active(true)
                .key(KeyDescriptor.Builder.newInstance()
                        .publicKeyPem(PARTICIPANY_PUBKEY_MAP.get(participantId))
                        .build())
                .build();
        participantContextService.createParticipantContext(manifest);

        File credentialDir = new File(context.getConfig().getString(CREDENTIALS_DIR_PATH));
        for (File credentialFile : credentialDir.listFiles((dir, name) -> name.endsWith(".json"))) {
            storeCredential(credentialFile.toPath(), participantId);
        }

        var keyPairResource = KeyPairResource.Builder.newInstance()
                .keyId(participantId)
                .privateKeyAlias(participantId)
                .isDefaultPair(true)
                .participantId(participantId)
                .serializedPublicKey(PARTICIPANY_PUBKEY_MAP.get(participantId))
                .state(KeyPairState.ACTIVE)
                .build();
        keyPairResourceStore.create(keyPairResource);
    }

    private void storeCredential(Path credentialPath, String participantId) throws IOException {
        String credentialContent = Files.readString(credentialPath);

        var verifiableCredential = VerifiableCredential.Builder.newInstance()
                .credentialSubject(CredentialSubject.Builder.newInstance().id("test-subject").claim("test-key", "test-val").build())
                .issuanceDate(Instant.now())
                .type(CREDENTIAL_FORMAT)
                .issuer(new Issuer(participantId, Map.of()))
                .id(participantId)
                .build();

        var verifiableCredentialContainer = new VerifiableCredentialContainer(credentialContent, CredentialFormat.JSON_LD, verifiableCredential);
        var verifiableCredentialResource = VerifiableCredentialResource.Builder.newInstance()
                .issuerId("test-issuer")
                .holderId("test-holder")
                .state(VcState.ISSUED)
                .participantId(participantId)
                .credential(verifiableCredentialContainer)
                .id(UUID.randomUUID().toString())
                .build();
        credentialStore.create(verifiableCredentialResource);
    }

}
