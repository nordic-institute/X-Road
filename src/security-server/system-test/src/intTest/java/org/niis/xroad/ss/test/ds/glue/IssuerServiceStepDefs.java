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

package org.niis.xroad.ss.test.ds.glue;

import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.addons.glue.BaseStepDefs;
import org.niis.xroad.ss.test.ds.api.FeignIssuerServiceAdminApi;
import org.niis.xroad.ss.test.ds.api.FeignIssuerServiceIdentityApi;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class IssuerServiceStepDefs extends BaseStepDefs {

    @Autowired
    private FeignIssuerServiceAdminApi issuerServiceAdminApi;

    @Autowired
    private FeignIssuerServiceIdentityApi issuerServiceIdentityApi;

    @Step("Issuer Service participant context {string} with DID {string} is initialized "
            + "and keypair is generated with private key alias {string}")
    public void issuerServiceParticipantContextIsInitialized(String participantId, String did, String privateKeyAlias) {
        String request = """
                {
                    "roles": ["admin"],
                    "serviceEndpoints": [
                        {
                            "type": "IssuerService",
                            "serviceEndpoint": "http://ds-issuer-service:%s/api/issuance/v1alpha/participants/%s",
                            "id": "%s-issuer-service"
                        }
                    ],
                    "active": true,
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "%s",
                        "keyGeneratorParams": {
                            "algorithm": "EdDSA"
                        }
                    }
                }
                """.formatted(
                participantId, did, participantId, did, did, privateKeyAlias);

        var response = issuerServiceIdentityApi.createParticipant(AuthTokens.PROVISIONER, request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .execute();
    }

    @Step("Holder {string} with DID {string} and member identifier {string} is created in issuer service participant {string}")
    public void holderIsCreated(String holderId, String holderDid, String memberIdentifier, String participantId) {
        String request = """
                {
                    "did": "%s",
                    "holderId": "%s",
                    "name": "Test Holder",
                    "properties": {
                        "membershipType": "X-Road",
                        "xrdMemberIdentifier": "%s"
                    }
                }
                """.formatted(holderDid, holderId, memberIdentifier);

        var response = issuerServiceAdminApi.createHolder(
                AuthTokens.PARTICIPANT,
                participantId,
                request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .execute();
    }

    @Step("Attestation definition {string} of type {string} is created in issuer service participant {string}")
    public void attestationDefinitionIsCreated(String attestationId, String attestationType, String participantId) {
        String request = """
                {
                    "attestationType": "%s",
                    "configuration": {},
                    "id": "%s"
                }
                """.formatted(attestationType, attestationId);

        var response = issuerServiceAdminApi.createAttestationDefinition(
                AuthTokens.PARTICIPANT,
                participantId,
                request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .execute();
    }

    @Step("Credential definition {string} of type {string} with format {string} "
            + "is created in issuer service participant {string} with attestation {string}")
    public void credentialDefinitionIsCreated(String credDefId, String credType, String format,
                                              String participantId, String attestationId) {
        String request = """
                {
                    "attestations": ["%s"],
                    "credentialType": "%s",
                    "id": "%s",
                    "jsonSchema": "{}",
                    "jsonSchemaUrl": "https://example.com/schema/%s.json",
                    "mappings": [
                        {
                            "input": "membershipType",
                            "output": "credentialSubject.membershipType",
                            "required": "true"
                        },
                        {
                            "input": "xrdMemberIdentifier",
                            "output": "credentialSubject.xrdMemberIdentifier",
                            "required": "true"
                        }
                    ],
                    "rules": [],
                    "format": "%s",
                    "validity": "604800"
                }
                """.formatted(attestationId, credType, credDefId, credType, format);

        var response = issuerServiceAdminApi.createCredentialDefinition(
                AuthTokens.PARTICIPANT,
                participantId,
                request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .execute();
    }

    static class AuthTokens {
        static final String PROVISIONER = "Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhl"
                + "N2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOi"
                + "I3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoiaXNzdWVyLWFkbWluLWFwaT"
                + "p3cml0ZSBpc3N1ZXItYWRtaW4tYXBpOnJlYWQgaWRlbnRpdHktYXBpOndyaXRlIGlkZW50aXR5LWFwaTpyZWFkIn0.bjTA0NoQ-2LDsBM5"
                + "HncXkhe2jM96wekxmE1dj09kQv_neQTP11yrbDInmmNbdTaqnowqRQGSkjRE44Hg-4OmbwHd00LbIWRD1zSOrLeZRXCa1BEym995IJYICKOex"
                + "SYPiGXcu0CCBAtokTjzA5dZAZgALlNIVfAOLh_3WHlAOMYbcUTZZ8yghOhJoy859BnfiVA-b7HERwo-0CboryTvbfYsUN6zyHq-2idTjP10LR"
                + "Tv8BQbQv81hXE9fwwGwIyGCp6vPKP0BdZ50zLy25qdpWOurblH4LcSwkoRaE9SHNn3LpTbxzUv4Zq4X-KVEBMsTwthTgA95vjfINq9KsGxWw";

        static final String PARTICIPANT = "Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1Y"
                + "Tc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTU"
                + "wZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJpc3N1ZXIiLC"
                + "JzY29wZSI6Imlzc3Vlci1hZG1pbi1hcGk6d3JpdGUgaXNzdWVyLWFkbWluLWFwaTpyZWFkIn0.dwRKoVpIwSO0DKX6YDQDVT-9ssYH4L93Iaea"
                + "9PA4QISUIZZwvF-UvYPzvNHJ3VpJOQgSK35h-dMxbQ3aEdCs7dAV-3i0DKH4k1TNtV1ObDFcHIJ3d9Rl21Ob-U2K7Gj1zy9qDRE6_hh32Gc6xiXK"
                + "Wicy4wQkzN6Lsi1yyayLJlCHiCjPDrjneYl81c2lRrSJ2tsN6XYPvNE7ctjAnk9ubCu8j7od7XTGNpfcwblsr2PX1W6Il-vtCh8hWyZgOxn-NN4F"
                + "U8Q6rHVMQ7bwaLXbw93mz3A4jvu_i3ID6PLnRGkWZEt3QiHIBwPUzCJ8PWgDem-BO7ck6GqvYvH64m1bYw";
    }
}
