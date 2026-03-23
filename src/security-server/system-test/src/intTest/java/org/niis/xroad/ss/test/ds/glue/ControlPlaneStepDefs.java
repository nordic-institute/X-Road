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
import org.niis.xroad.ss.test.ds.api.FeignControlPlaneManagementApi;
import org.niis.xroad.test.framework.core.asserts.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ControlPlaneStepDefs extends BaseStepDefs {

    @Autowired
    private FeignControlPlaneManagementApi controlPlaneManagementApi;

    @Step("Participant context {string} is created")
    public void participantContextIsCreated(String contextName) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "ParticipantContext",
                    "identity": "test-identity-1",
                    "@id": "%s"
                }
                """.formatted(contextName);
        var response = controlPlaneManagementApi.createParticipantContext(AuthTokens.PROVISIONER, request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK));
    }

    @Step("Participant context {string} can be retrieved")
    public void participantContextCanBeRetrieved(String participantContextId) {
        var response = controlPlaneManagementApi.getParticipantContext(AuthTokens.PROVISIONER, participantContextId);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(Assertions.equalsAssertion(participantContextId, "@id"));
    }

    @Step("Participant context {string} config is created")
    public void participantContextConfigIsCreated(String participantContextId) {
        String request = """
                {
                     "@context": [
                         "https://w3id.org/edc/connector/management/v2"
                     ],
                     "@type": "ParticipantContextConfig",
                     "entries": {
                         "edc.participant.id": "test-participant-id"
                     },
                     "privateEntries": {}
                 }
                """;
        var response = controlPlaneManagementApi.createParticipantContextConfig(AuthTokens.PROVISIONER, participantContextId, request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK));
    }

    @Step("Asset is created in participant context {string}")
    public void assetIsCreatedInParticipantContext(String participantContextId) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@id": "assetId-1",
                    "@type": "Asset",
                    "properties": {
                        "name": "sample rest service description",
                        "contenttype": "application/json"
                    },
                    "dataAddress": {
                        "@type": "DataAddress",
                        "type": "HttpData",
                        "name": "Test asset",
                        "baseUrl": "https://jsonplaceholder.typicode.com/users",
                        "proxyPath": "true"
                    }
                }
                """;

        var response = controlPlaneManagementApi.createAsset(AuthTokens.PARTICIPANT, participantContextId, request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK));
    }

    @Step("Policy definition is created in participant context {string}")
    public void policyDefinitionIsCreatedInParticipantContext(String participantContextId) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@id": "policy-allow-all",
                    "@type": "PolicyDefinition",
                    "policy": {
                        "@context": "http://www.w3.org/ns/odrl.jsonld",
                        "@type": "Set",
                        "permission": [
                            {
                                "action": "use"
                            }
                        ]
                    }
                }
                """;

        var response = controlPlaneManagementApi.createPolicyDefinition(AuthTokens.PARTICIPANT, participantContextId, request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK));
    }

    @Step("Contract definition is created in participant context {string}")
    public void contractDefinitionIsCreatedInParticipantContext(String participantContextId) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@id": "contract-def-1",
                    "@type": "ContractDefinition",
                    "accessPolicyId": "policy-allow-all",
                    "contractPolicyId": "policy-allow-all",
                    "assetsSelector": [
                        {
                            "@type": "Criterion",
                            "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
                            "operator": "=",
                            "operandRight": "assetId-1"
                        }
                    ]
                }
                """;

        var response = controlPlaneManagementApi.createContractDefinition(AuthTokens.PARTICIPANT, participantContextId, request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK));
    }

    @Step("Catalog can be retrieved from participant context {string}")
    public void catalogCanBeRetrievedFromParticipantContext(String participantContextId) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "CatalogRequest",
                    "counterPartyId": "test-participant-context-id",
                    "counterPartyAddress": "http://localhost:8282/api/dsp/test-part-ctx/2025-1",
                    "protocol": "dataspace-protocol-http:2025-1"
                }
                """;

        var response = controlPlaneManagementApi.requestCatalog(AuthTokens.PARTICIPANT, participantContextId, request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK));
        assertEquals("assetId-1", ((LinkedHashMap) ((ArrayList) response.getBody().get("dataset")).getFirst()).get("id"));
    }


    static class AuthTokens {
        static final String PROVISIONER = "Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYz"
                + "g2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LC"
                + "JqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoibWFuYWdlbW"
                + "VudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.VtgeUBJXWdZSsemdWTtvSDqdCUa1eBaqMlxbBVAAPsSjyVOb8wiDmxpTqv"
                + "yLKTw9WE2WznmaOUPpWh3s4nDTjHQ51-ke_H__5WHVkwK-E97AFvInue-1lPMdIC1rNGLyZKYmQQ8DtHwZDWkgl-F4zhiyTk8Z3OBzgZp"
                + "Dz3BcyyJT7WLvAHp6Pk0SdHmFhA5ctvXfra4-ZkfUUudXklOEe-8Jj42v2EjF0woUk9nHoNYA_ca2Gi3kHtJrpHhR4_3Ab7KU046-p0dF5"
                + "bVLLhYh3HEg-71R0tO9eytzbHkMZMY353aKF0bUqK4UrKnstDT55yo5j5oLpP0xGA9KGai6Kg";

        static final String ADMIN = "Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2"
                + "Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUw"
                + "ZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJhZG1pbiIsInNjb3BlIjoibWFuYWdlbWVudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcG"
                + "k6cmVhZCJ9.KNlPjFo4AdTbOVMtDPbNo2K1r76E3rvpl_mwNSuUrxHqRPMST4jxpccUsi706XcljRTs41JknmaZ5sX1fLs38RqjRsw4owCCQVublwY"
                + "m_I4RT9kHuCupMATZ-DbvlzSfEuK_qB_g6OLMOB35PTCC7MF8n9gZDx8TIFhyKJe1Rviq-9pU3fpVHZo6ZYg7szUNt8ldbM7oyLqA-GIdIQGrAhJTH"
                + "dGJawEYhtqSUps2q0yT-LWYm7JBGTKX0BBD4N7joWIM_c1W8QfwRSVW_JurrknVqEuyyNDFkAykJv0pEpA6l2U6SDdFWUbuS7IcnHpnM2ZcRKSxceH"
                + "EaQGAQQbi1g";

        static final String PARTICIPANT = "Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1Y"
                + "Tc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTU"
                + "wZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhc"
                + "nQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAP"
                + "jfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRp"
                + "ni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtG"
                + "YDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw";
    }
}

