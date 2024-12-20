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

package org.niis.xroad.edc.extension.policy.dataplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import okhttp3.Request;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAccessControlService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.spi.PolicyContextImpl;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.jetty.http.HttpStatus;
import org.niis.xroad.edc.extension.policy.dataplane.util.PolicyContextData;

import java.io.IOException;
import java.util.Map;

import static java.lang.String.format;
import static org.niis.xroad.edc.extension.policy.dataplane.XRoadDataPlanePolicyExtension.XROAD_DATAPLANE_TRANSFER_SCOPE;

@RequiredArgsConstructor
public class XrdDataPlaneAccessControlService implements DataPlaneAccessControlService {
    private final EdcHttpClient httpClient;
    private final String contractAgreementApiUrl;
    private final ObjectMapper mapper;
    private final TypeTransformerRegistry typeTransformerRegistry;
    private final JsonLd jsonLd;
    private final PolicyEngine policyEngine;
    private final Monitor monitor;

    @Override
    public Result<Void> checkAccess(ClaimToken claimToken, DataAddress address, Map<String, Object> requestData,
                                    Map<String, Object> additionalData) {
        String contractId = additionalData.get("agreement_id").toString();

        var contractAgreementResult = getContractAgreement(contractId);
        if (contractAgreementResult.failed()) {
            return Result.failure("Error acquiring contact agreement with id: %s due to %s"
                    .formatted(contractId, contractAgreementResult.getFailureDetail()));
        }
        var contractAgreement = contractAgreementResult.getContent();

        String dataPath = "%s /%s".formatted(requestData.get("method"), requestData.get("resolvedPath"));
        String clientId = requestData.get("clientId").toString();
        monitor.debug("Checking access for %s".formatted(dataPath));
        var startTime = StopWatch.createStarted();
        var result = this.policyEngine.evaluate(XROAD_DATAPLANE_TRANSFER_SCOPE,
                contractAgreement.getPolicy(),
                PolicyContextImpl.Builder.newInstance()
                        .additional(PolicyContextData.class, new PolicyContextData(clientId, dataPath))
                        .build());
        monitor.debug("Access check for %s took %s ms".formatted(dataPath, startTime.getTime()));
        return result;
    }

    private Result<ContractAgreement> getContractAgreement(String contractId) {
        var request = new Request.Builder().url(contractAgreementApiUrl + "/" + contractId).build();

        try (var response = httpClient.execute(request)) {
            if (response.code() != HttpStatus.OK_200) {
                return Result.failure(format("Error getting contract agreement with id: %s. HTTP Code was: %s",
                        contractId, response.code()));
            }
            try (var body = response.body()) {
                if (body == null) {
                    return Result.failure("Contact agreement with id %s response contained an empty body: ".formatted(contractId));
                }
                var jsonObject = mapper.readValue(body.string(), JsonObject.class);
                return jsonLd.expand(jsonObject)
                        .compose(it -> typeTransformerRegistry.transform(it, ContractAgreement.class));
            }
        } catch (IOException e) {
            monitor.severe("Error getting contract agreement with id: " + contractId, e);
            return Result.failure("Error getting contract agreement with id: " + contractId + " - " + e.getMessage());
        }
    }


}
