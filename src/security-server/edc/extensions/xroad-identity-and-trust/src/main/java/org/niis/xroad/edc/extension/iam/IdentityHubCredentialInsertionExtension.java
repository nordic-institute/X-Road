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
import org.eclipse.edc.identityhub.spi.model.VcState;
import org.eclipse.edc.identityhub.spi.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.model.participant.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.model.participant.ParticipantManifest;
import org.eclipse.edc.identityhub.spi.store.CredentialStore;
import org.eclipse.edc.identityhub.spi.store.KeyPairResourceStore;
import org.eclipse.edc.identitytrust.model.CredentialFormat;
import org.eclipse.edc.identitytrust.model.CredentialSubject;
import org.eclipse.edc.identitytrust.model.Issuer;
import org.eclipse.edc.identitytrust.model.VerifiableCredential;
import org.eclipse.edc.identitytrust.model.VerifiableCredentialContainer;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.time.Instant;
import java.util.Map;

import static org.niis.xroad.edc.extension.iam.IdentityHubCredentialInsertionExtension.NAME;

/**
 * Identity Hub's management API is missing an endpoint for inserting credentials. This extension is a "dirty"
 * workaround to insert a Verifiable Credential into the Identity Hub's store.
 */
@Extension(NAME)
public class IdentityHubCredentialInsertionExtension implements ServiceExtension {

    static final String NAME = "Credential insertion extension for Identity Hub";

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private CredentialStore credentialStore;

    @Inject
    private KeyPairResourceStore keyPairResourceStore;

    @Override
    @SneakyThrows
    public void initialize(ServiceExtensionContext context) {
        var participantId = "did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com";
        var manifest = ParticipantManifest.Builder.newInstance()
                .participantId(participantId)
                .did("did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com")
                .active(true)
                .key(KeyDescriptor.Builder.newInstance()
                        .publicKeyPem("""
                                -----BEGIN PUBLIC KEY-----
                                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAm/BUDsukPkt1OQTZWRIb
                                zylv1chElgsSTXAADbjTcqUkOZYF/kTprPwivMtHqb8i9Y+lDZSJGquyrXtMVjc3
                                IqQ6cI8HbY1sDa74A6tJ2bNHvlMKlY5Yc8p+dBaHICviDy2DhY/HLbpk/w6/zJX3
                                Tbw6qu5uVRrzTNoYLLvDmP2LEUN1mSYebRa5aQ3gP0F2QZZsrR0mt59Vh6aaiGg6
                                KktEpMMXPi4YTmvrbbvLvkyZbACCWj/PngDvzNrJx62b6CdbPzXjbE82qbZ/zIOL
                                UIuaeczRwp5EpMyUz/wuRQkfrvRV3uIYxjsoXY8ZYgJJ+iJ1oTkuvr245G4yUvyp
                                LQIDAQAB
                                -----END PUBLIC KEY-----
                                """)
                        .build())
                .build();
        participantContextService.createParticipantContext(manifest);

        var verifiableCredential = VerifiableCredential.Builder.newInstance()
                .credentialSubject(CredentialSubject.Builder.newInstance().id("test-subject").claim("test-key", "test-val").build())
                .issuanceDate(Instant.now())
                .type("VerifiableCredential")
                .issuer(new Issuer("did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com", Map.of()))
                .id("https://xroad-8-member1.s3.eu-west-1.amazonaws.com/participant.json")
                .build();

        var rawVc = "{\"type\":\"VerifiableCredential\",\"id\":\"https://xroad-8-member1.s3.eu-west-1.amazonaws.com/participant.json\",\"issuer\":\"did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com\",\"issuanceDate\":\"2024-02-15T14:20:56.969Z\",\"credentialSubject\":{\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#clientId\":\"did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com\",\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalName\":\"NIIS\",\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#headquarterAddress\":{\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#countrySubdivisionCode\":\"EE-37\"},\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalRegistrationNumber\":{\"id\":\"https://xroad-8-member1.s3.eu-west-1.amazonaws.com/lrn.json#cs\"},\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalAddress\":{\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#countrySubdivisionCode\":\"EE-37\"},\"type\":\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#LegalParticipant\",\"gx-terms-and-conditions:gaiaxTermsAndConditions\":\"70c1d713215f95191a11d38fe2341faed27d19e083917bc8732ca4fea4976700\",\"id\":\"did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com\"},\"sec:proof\":{\"type\":\"JsonWebSignature2020\",\"created\":\"2024-03-15T09:16:16.303556Z\",\"proofPurpose\":\"assertionMethod\",\"verificationMethod\":{\"type\":\"JsonWebKey2020\",\"publicKeyJwk\":{\"kty\":\"RSA\",\"e\":\"AQAB\",\"n\":\"m_BUDsukPkt1OQTZWRIbzylv1chElgsSTXAADbjTcqUkOZYF_kTprPwivMtHqb8i9Y-lDZSJGquyrXtMVjc3IqQ6cI8HbY1sDa74A6tJ2bNHvlMKlY5Yc8p-dBaHICviDy2DhY_HLbpk_w6_zJX3Tbw6qu5uVRrzTNoYLLvDmP2LEUN1mSYebRa5aQ3gP0F2QZZsrR0mt59Vh6aaiGg6KktEpMMXPi4YTmvrbbvLvkyZbACCWj_PngDvzNrJx62b6CdbPzXjbE82qbZ_zIOLUIuaeczRwp5EpMyUz_wuRQkfrvRV3uIYxjsoXY8ZYgJJ-iJ1oTkuvr245G4yUvypLQ\"},\"id\":\"did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com\"},\"jws\":\"eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJSUzUxMiJ9..R3LOIeqe1y5TM6gtphe8Vu6LgAQ7TZfGgVgHmj_E6U00eQGllHaH5INNhWuxjkXdGiJEEiY350EMG5FILDigdilTjlSoRVq2CfwthWI9lIMGGyeTwMLhHLxJZDJEzEpuakr3tYuuz9kvUoFtxNuaiMvjZ4w3hrnbg5p0yhNS232T-z4YBt01YuQm7cUbHLmoDFFIcWMYuYa1AdKn2KhPnmSHF-m3DlEhicYISJO92PP_Ly78GxhygaNOOhYk0ay7W3-lozZUDPbOnm3Ux5pSEuSxNaivOQ2tt3cLsSmziMxn3TDsC6RIxD9xRhDIboFYb8qIKusuWbwS-Gzf0BGbYQ\"},\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://www.w3.org/ns/did/v1\"]}";
        var verifiableCredentialContainer = new VerifiableCredentialContainer(rawVc, CredentialFormat.JSON_LD,
                verifiableCredential);
        var verifiableCredentialResource = VerifiableCredentialResource.Builder.newInstance()
                .issuerId("test-issuer")
                .holderId("test-holder")
                .state(VcState.ISSUED)
                .participantId(participantId)
                .credential(verifiableCredentialContainer)
                .id("test-id")
                .build();
        credentialStore.create(verifiableCredentialResource);

        var keyPairResource = KeyPairResource.Builder.newInstance()
                .keyId("did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com")
                .privateKeyAlias("did:web:xroad-8-member1.s3.eu-west-1.amazonaws.com")
                .isDefaultPair(true)
                .participantId(participantId)
                .serializedPublicKey("""
                                -----BEGIN PUBLIC KEY-----
                                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAm/BUDsukPkt1OQTZWRIb
                                zylv1chElgsSTXAADbjTcqUkOZYF/kTprPwivMtHqb8i9Y+lDZSJGquyrXtMVjc3
                                IqQ6cI8HbY1sDa74A6tJ2bNHvlMKlY5Yc8p+dBaHICviDy2DhY/HLbpk/w6/zJX3
                                Tbw6qu5uVRrzTNoYLLvDmP2LEUN1mSYebRa5aQ3gP0F2QZZsrR0mt59Vh6aaiGg6
                                KktEpMMXPi4YTmvrbbvLvkyZbACCWj/PngDvzNrJx62b6CdbPzXjbE82qbZ/zIOL
                                UIuaeczRwp5EpMyUz/wuRQkfrvRV3uIYxjsoXY8ZYgJJ+iJ1oTkuvr245G4yUvyp
                                LQIDAQAB
                                -----END PUBLIC KEY-----
                                """)
                .build();
        keyPairResourceStore.create(keyPairResource);
    }


}
