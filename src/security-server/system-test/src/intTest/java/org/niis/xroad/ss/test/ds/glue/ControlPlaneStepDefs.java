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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ControlPlaneStepDefs extends BaseStepDefs {

    private static final String SERVERCONF_ASSET_ID_PREFIX = "DEV:COM:1234:TestService:";

    @Autowired
    private FeignControlPlaneManagementApi controlPlaneManagementApi;

    @Step("Participant context {string} can be retrieved")
    public void participantContextCanBeRetrieved(String participantContextId) {
        var response = controlPlaneManagementApi.getParticipantContext(AuthTokens.PROVISIONER, participantContextId);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(Assertions.equalsAssertion(participantContextId, "body['@id']"))
                .execute();
    }

    @Step("Catalog can be retrieved using participant context {string} with DID {string}")
    public void catalogCanBeRetrievedFromParticipantContext(String participantContextId, String participantDid) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "CatalogRequest",
                    "counterPartyId": "%s",
                    "counterPartyAddress": "https://ds-control-plane:8183/api/dsp/%s/2025-1",
                    "protocol": "dataspace-protocol-http:2025-1"
                }
                """.formatted(participantDid, participantContextId);

        var response = controlPlaneManagementApi.requestCatalog(AuthTokens.PARTICIPANT, participantContextId, request);
        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .execute();

        var datasets = extractDatasets(response.getBody());
        assertFalse(datasets.isEmpty(), "Catalog must contain at least one dataset");
        assertTrue(datasets.stream().anyMatch(this::isServerconfService),
                "Catalog must contain at least one serverconf-derived service with id prefix " + SERVERCONF_ASSET_ID_PREFIX);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDatasets(Map<String, Object> catalogBody) {
        assertNotNull(catalogBody, "Catalog response body must not be null");
        Object datasetObj = catalogBody.get("dataset");
        if (datasetObj == null) {
            datasetObj = catalogBody.get("dcat:dataset");
        }
        if (datasetObj == null) {
            return List.of();
        }
        if (datasetObj instanceof List<?> list) {
            return list.stream().map(o -> (Map<String, Object>) o).toList();
        }
        return List.of((Map<String, Object>) datasetObj);
    }

    private boolean isServerconfService(Map<String, Object> dataset) {
        var id = dataset.get("@id");
        if (id == null) {
            id = dataset.get("id");
        }
        return id != null && id.toString().startsWith(SERVERCONF_ASSET_ID_PREFIX);
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

        static final String PARTICIPANT = "Bearer eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODh"
                + "lN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3Z"
                + "DM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJzczA"
                + "iLCJzY29wZSI6Im1hbmFnZW1lbnQtYXBpOndyaXRlIG1hbmFnZW1lbnQtYXBpOnJlYWQifQ.A46bvId4X3RF7UFCrrhKqcaAMiSjije0Qab1ao"
                + "RtEMW_OikA4MVlBnEEj8n_FwS1i0RXor08C2IAf8yEM036X86QMcOv1I8eUsOg_yBVbHfd6nw3HDtI1fJvyuIDJ2FbDAQsDj6UxXoUTe1KjXml"
                + "ry0184cnRTDDg3OGwFpMWOMedeMdWeHb7NQ9tqUm47WmPecNnB4JeRNkUPiVSay6XpSKtok9yizv-BDVDPh8mxt91CxWt6Eh1Rz4FNNNWKHlH2"
                + "oejyQsMz76bxf_7P3X5gSE8Q5q1KIM8ZrhzfNig4ZMhrBwxYYNexBvviVoKYb1NJhJHrepBwCMw9hlefVMPQ";
    }
}
