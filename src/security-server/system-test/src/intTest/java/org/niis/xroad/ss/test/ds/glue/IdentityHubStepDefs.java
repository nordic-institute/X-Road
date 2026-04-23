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

import com.codeborne.selenide.Selenide;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.addons.glue.BaseStepDefs;
import org.niis.xroad.ss.test.ds.api.FeignIdentityHubManagementApi;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class IdentityHubStepDefs extends BaseStepDefs {

    @Autowired
    private FeignIdentityHubManagementApi feignIdentityHubManagementApi;

    @Step("Identity Hub participant context {string} with DID {string} is initialized "
            + "and keypair is generated with private key alias {string}")
    public void identityHubParticipantContextIsInitialized(String participantId, String did, String privateKeyAlias) {
        var credentialServiceUrl = "http://ds-identity-hub:10001/api/credentials/v1/participants/"
                + participantId;

        String createParticipantRequest = """
                {
                    "roles": [],
                    "serviceEndpoints": [
                        {
                            "type": "CredentialService",
                            "serviceEndpoint": "%s",
                            "id": "%s-credential-service"
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
                """.formatted(credentialServiceUrl, did, participantId, did, did, privateKeyAlias);

        var ihResponse = feignIdentityHubManagementApi.createParticipant(AuthTokens.PROVISIONER, createParticipantRequest);
        validate(ihResponse)
                .assertion(equalsStatusCodeAssertion(OK))
                .execute();
    }

    @Step("Identity Hub participant context {string} with DID {string} is initialized "
            + "with existing private key in vault with alias {string} and public key {string}")
    public void identityHubParticipantContextIsInitialized(String participantId, String did, String privateKeyAlias, String publicKey) {
        var credentialServiceUrl = "http://ds-identity-hub:10001/api/credentials/v1/participants/"
                + participantId;

        String createParticipantRequest = """
                {
                    "roles": [],
                    "serviceEndpoints": [
                        {
                            "type": "CredentialService",
                            "serviceEndpoint": "%s",
                            "id": "%s-credential-service"
                        }
                    ],
                    "active": true,
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "%s",
                        "publicKeyPem": "%s"
                    }
                }
                """.formatted(credentialServiceUrl, did, participantId, did, did, privateKeyAlias, publicKey);

        var ihResponse = feignIdentityHubManagementApi.createParticipant(AuthTokens.PROVISIONER, createParticipantRequest);
        validate(ihResponse)
                .assertion(equalsStatusCodeAssertion(OK))
                .execute();
    }

    @Step("Credential request {string} for credential definition {string} of type {string} "
            + "from issuer {string} is submitted for participant {string}")
    public void credentialRequestIsSubmitted(String holderPid, String credentialDefId,
                                             String credentialType, String issuerDid, String participantContextId) {
        String request = """
                {
                    "issuerDid": "%s",
                    "holderPid": "%s",
                    "credentials": [
                        {
                            "format": "VC1_0_JWT",
                            "type": "%s",
                            "id": "%s"
                        }
                    ]
                }
                """.formatted(issuerDid, holderPid, credentialType, credentialDefId);

        var response = feignIdentityHubManagementApi.requestCredential(
                AuthTokens.ADMIN,
                participantContextId,
                request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .execute();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("Credential request {string} for participant {string} reaches status {string}")
    public void credentialRequestReachesStatus(String holderPid, String participantContextId, String expectedStatus) {
        var maxRetries = 5;
        var retryInterval = 3000L;
        for (int i = 0; i < maxRetries; i++) {
            var response = feignIdentityHubManagementApi.getCredentialRequestStatus(
                    AuthTokens.ADMIN,
                    participantContextId,
                    holderPid);
            validate(response)
                    .assertion(equalsStatusCodeAssertion(OK))
                    .execute();
            var status = response.getBody().get("status");
            if (expectedStatus.equals(status)) {
                return;
            }
            if ("ERROR".equals(status)) {
                throw new AssertionError("Credential request reached ERROR status. Response: " + response.getBody());
            }
            Selenide.sleep(retryInterval);
        }
        throw new AssertionError("Credential request did not reach status '" + expectedStatus + "' after " + maxRetries + " attempts");
    }

    static class AuthTokens {
        static final String PROVISIONER = "Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhl"
                + "N2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOi"
                + "I3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoiaWRlbnRpdHktYXBpOndya"
                + "XRlIGlkZW50aXR5LWFwaTpyZWFkIn0.nujdj1AdxrI4CqPKruY48nx9itkh_Uf_vB4xCgEssOHdtlwGim_l5KFFxCAFYOllBmj4A91Qdhs0"
                + "04jcQ1pF3Ag7wSoVpYszbWDyJv2zamS72862fuhx0h3BCxQxS4CAsOogxR_kQEqMBnhgAKK5ndTf66kbAS83OpvtaA3DKKuVmByYZAvncLl"
                + "AAgbBf0ATGI3pG1sbHhTJ58AVBi300sp-7-B9uIijw4S-Pd-ww1ah-xc8ep3kr4YpEgODaUKnNOCXPA_vnZa-9BwYOi94kWM_DCzfZTNV2O"
                + "lb3WQojrhZbPiUCALmSmSUFJMvfMp18Z15bDQM0iTLUsVRFZMLTA";

        static final String ADMIN = "Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1Y"
                + "Tc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTU"
                + "wZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJhZG1pbiIsInNjb3BlIjoiaWRlbnRpdHktYXBpOndyaXRlIGlkZW50aXR5LWFwaT"
                + "pyZWFkIn0.vKPkIJ47atCdUnH60TsGSihx54xqbpvmI-QYSIRZprkmY3Fb8zPZ4PYMrTUCu6mw-ISPyzV1fu54s5bYD4q5trjZN8hrocgWyYbPP"
                + "kk-sZDqcKFwFfxcVWP7wPuRZOus_AxcxrSny0IQYzlTmmvhVqPq1MJjLSximHnY3Hg7yti_b0OZE5iBMKuoww8xlr6OXY4dpJHAvwK6vJhscoQa"
                + "5Mm56GsSVto38Y4HHsKlNjJlYPpILdiiytfMS5x6AhCALRoY15_0upP-MWWe7eq_2sAv7TuEZJFIdpfpWKQG4-eMHBTNim17FZx76CG9xoYhlhrJ"
                + "5WGSel_8xTJD3xSVzg";
    }

}
