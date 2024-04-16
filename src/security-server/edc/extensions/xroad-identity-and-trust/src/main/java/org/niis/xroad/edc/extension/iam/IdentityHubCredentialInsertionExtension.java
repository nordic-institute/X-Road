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
import org.eclipse.edc.boot.BootServicesExtension;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredentialContainer;
import org.eclipse.edc.identityhub.spi.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.model.KeyPairResource;
import org.eclipse.edc.identityhub.spi.model.KeyPairState;
import org.eclipse.edc.identityhub.spi.model.VcState;
import org.eclipse.edc.identityhub.spi.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.model.participant.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.model.participant.ParticipantManifest;
import org.eclipse.edc.identityhub.spi.store.CredentialStore;
import org.eclipse.edc.identityhub.spi.store.KeyPairResourceStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.time.Instant;
import java.util.Map;

import static org.eclipse.edc.iam.identitytrust.core.IdentityAndTrustExtension.CONNECTOR_DID_PROPERTY;
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

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private CredentialStore credentialStore;

    @Inject
    private KeyPairResourceStore keyPairResourceStore;

    @Override
    @SneakyThrows
    public void initialize(ServiceExtensionContext context) {
        Map<String, String> participantPubKeyMap = Map.of(
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
        Map<String, String> participantRawVCMap = Map.of(
                "did:web:did-server:cs", "{\"type\":\"VerifiableCredential\",\"id\":\"did:web:did-server:cs\",\"issuer\":\"did:web:did-server:cs\",\"issuanceDate\":\"2024-03-15T14:20:56.969Z\",\"credentialSubject\":{\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#clientId\":\"did:web:did-server:cs\",\"id\":\"did:web:did-server:cs\"},\"sec:proof\":{\"type\":\"JsonWebSignature2020\",\"created\":\"2024-04-05T12:55:06.969102Z\",\"proofPurpose\":\"assertionMethod\",\"verificationMethod\":{\"type\":\"JsonWebKey2020\",\"publicKeyJwk\":{\"kty\":\"RSA\",\"e\":\"AQAB\",\"n\":\"nh15T1B2vCpFDLbS4RAFmf3YfNhYj24NFtXBlNUOZr85heKcbTy4rZcwbRysNTC2M-fFFpGFB3HSozmvSShbziQ_6P427-1oaE_jf5YLLeGQbvZY1bFoUMr7aQ3LBG9MhFECaSB-dmOpBrAoqrz5JK1dtirpEuHX_TyV0f_i8gZkmX9UY-PdLvc79C78RopgRVH_FwBhDiq6CP8hAqXXbX-IJYyRou5a9R3ONhBdxtuHkQpekToIDjpst75A81tzraWqn3ACBbDxDQFQXnOiYGL_DDEPXueXVUTrg6anhIOb2nz1OeKc-gRliagM7qi8YgNwLZiecQrOdIV33vN1HQ\"},\"id\":\"did:web:did-server:cs\"},\"jws\":\"eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJSUzUxMiJ9..M7BgQQinYvMxXqhtnwEzweMGwg0vTRwbFEuYDR4-1qkUGWk8ZiSmKQ1E65MgKorxeARfrxouEXMdD9Z1zkNS-u3-XpE7-iOrFEbXZNqkpsWpzhrPDaCA_y0sIY1BfdsTd0Rb6lPPLuL7P8yw5xjQSx_19-TrhfUJ2wiAR5GR84DAzPcjTE_6OyJvqlXXBZN9Z6Ben8bXQ5OgmwjKwDjnu5c1XiS-dewyStuhHN3a4rfZTiiH6uq89OHVdIX0wpNQ8CKeLJWPI91vMrLt3SxpDq61tRAo4roKECIebQs83aOYIQ1FlvxBjnlWFSL99gn8z24RGyp1X9C1JEIhsPcAuw\"},\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://www.w3.org/ns/did/v1\"]}",
                "did:web:did-server:ss0", "{\"type\":\"VerifiableCredential\",\"id\":\"did:web:did-server:ss0\",\"issuer\":\"did:web:did-server:ss0\",\"issuanceDate\":\"2024-03-15T14:20:56.969Z\",\"credentialSubject\":{\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#clientId\":\"did:web:did-server:ss0\",\"id\":\"did:web:did-server:ss0\"},\"sec:proof\":{\"type\":\"JsonWebSignature2020\",\"created\":\"2024-03-19T13:44:47.560850Z\",\"proofPurpose\":\"assertionMethod\",\"verificationMethod\":{\"type\":\"JsonWebKey2020\",\"publicKeyJwk\":{\"kty\":\"RSA\",\"e\":\"AQAB\",\"n\":\"4U71KJpjRZ57UlpADF980ORfMr3ea3vuZhGGmODkLuePeN6fjd1oFyu_vdCcJo9iXEtnA6DE9DFOhS173QLa4TJPzXkA-PNa9uHA1r1LzGfd-QuAz8fCR7WoqeP2Oty7WJwn0XFgOM6XJlJ2UdVFoFIKZwnVZlmFfarsY9tdS8TWH_woWY1K3_hTjwnORwh-R9YKyPjZEB4KUxteytlKFVOoaOEvaIaygwa5d3vOTu8UuU1z1E_8Hz-4QjvkfAztmnoMZiMuRRfxD0c46Y0OCVcLDs6wF0tPXdXBiS_f6KKkqvKKi3Prky9Gv27XtARx9nVv5uim5NJCwKfK65pZ4Q\"},\"id\":\"did:web:did-server:ss0\"},\"jws\":\"eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJSUzUxMiJ9..fuqko7UhtqYtm6VBu7k4dopqqn-lIwh9LrkrKpFF6EvAZ-R-3ef6DprPn7agRpRvBNPtAhnyg2Oj2ANi6UtHQa1eInOPRAAiluq8o4IEKyrpPRh-btTXFYbOkskXY01TnDqgGdpn0sXSHO-5GIPyKKBMj4o2P5h39AbpIKveblPVmZTubtLqwguMhshwbkhFFO5l0cpeAu2Z4ul8fFKyyqQdlfzGXQSGT92P-kp_IY3O8J_TqObt-Tdx1kXDlh61LpO6ugRw0TWPfRdTGi_ab6N3vpKq_JjX5Yy-6D8odJGxrPNy-t1kV___uZRUEOUQDIjNNn0ipH8_TabYYCwZgA\"},\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://www.w3.org/ns/did/v1\"]}",
                "did:web:did-server:ss1", "{\"type\":\"VerifiableCredential\",\"id\":\"did:web:did-server:ss1\",\"issuer\":\"did:web:did-server:ss1\",\"issuanceDate\":\"2024-03-15T14:20:56.969Z\",\"credentialSubject\":{\"https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#clientId\":\"did:web:did-server:ss1\",\"id\":\"did:web:did-server:ss1\"},\"sec:proof\":{\"type\":\"JsonWebSignature2020\",\"created\":\"2024-03-19T13:47:22.933834Z\",\"proofPurpose\":\"assertionMethod\",\"verificationMethod\":{\"type\":\"JsonWebKey2020\",\"publicKeyJwk\":{\"kty\":\"RSA\",\"e\":\"AQAB\",\"n\":\"wpqoS8gue7JxYsWyzQMjeLK0q4FWyRLiP1UQCbbJaqhDZ_jUl_2UgzHeTyYNyeFJCRS_NCAuWe3hG-RUvKDeF6o1eVlpoVX0MRrNwggo465y9YlON-z7yXUGQvblmJDEbBEoYV51JHISFCTB4_tHdwSBg6zPHzxW4KMdQOdHnG0kuipettlCdvwf21LCs9TYK7H8_Gj30VilSJO4bBegtg75JjgDDipwE_duK1gRCwFfx1JmRq22CU8EpxoIXoRQfnzjUCG2VJJYeeJMW8iDvTp2SQ3JK4HJdMicmB_9jJPRs1ONEumjCwRF4IgtX5rnIePbOwF6gtISzQG4_WB0Aw\"},\"id\":\"did:web:did-server:ss1\"},\"jws\":\"eyJiNjQiOmZhbHNlLCJjcml0IjpbImI2NCJdLCJhbGciOiJSUzUxMiJ9..VJIwwRWjO6RWHyvFghBE7qn3F6nwml7XPhez4uscnKJPn94jOkPb01eztTImrDA8443qqnFCGl4fdgw3W7Mx4EuRiSGfKTNhrrT7cNAORt5AtRtof75yg9gQbqYxmAjYP7aIttXT7i09u3ViHtYzaNJpQd5hF7EF0qLbAEChzUlLIZWkPOg-tjOG2hTuJ8-HQYH4A6TzG3smiCVcfSqHiuP5ljghdvkH9_k270FLwX01OpzHGTIWsLlue2DcNovtvJmYqK4TiEIzgj24dSeYfYEUL1BFISidfgmRzDWuf0wl2f9tEfz1H5Y7D11WcLfuYzPz5BlIyH6KvEgLv8z0qQ\"},\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://w3id.org/security/suites/jws-2020/v1\",\"https://www.w3.org/ns/did/v1\"]}"
        );

        String participantId = context.getConfig().getString(BootServicesExtension.PARTICIPANT_ID);
        var manifest = ParticipantManifest.Builder.newInstance()
                .participantId(participantId)
                .did(participantId)
                .active(true)
                .key(KeyDescriptor.Builder.newInstance()
                        .publicKeyPem(participantPubKeyMap.get(participantId))
                        .build())
                .build();
        participantContextService.createParticipantContext(manifest);

        String connectorDid = context.getConfig().getString(CONNECTOR_DID_PROPERTY);
        var verifiableCredential = VerifiableCredential.Builder.newInstance()
                .credentialSubject(CredentialSubject.Builder.newInstance().id("test-subject").claim("test-key", "test-val").build())
                .issuanceDate(Instant.now())
                .type(CREDENTIAL_FORMAT)
                .issuer(new Issuer(connectorDid, Map.of()))
                .id(connectorDid)
                .build();

        var verifiableCredentialContainer = new VerifiableCredentialContainer(participantRawVCMap.get(participantId), CredentialFormat.JSON_LD, verifiableCredential);
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
                .keyId(participantId)
                .privateKeyAlias(participantId)
                .isDefaultPair(true)
                .participantId(participantId)
                .serializedPublicKey(participantPubKeyMap.get(participantId))
                .state(KeyPairState.ACTIVE)
                .build();
        keyPairResourceStore.create(keyPairResource);
    }


}
