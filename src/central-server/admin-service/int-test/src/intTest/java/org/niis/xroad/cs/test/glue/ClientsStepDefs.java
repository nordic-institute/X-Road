/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.test.glue;

import feign.FeignException;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.ClientTypeDto;
import org.niis.xroad.cs.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.cs.test.api.FeignClientsApi;
import org.niis.xroad.cs.test.api.FeignGlobalGroupsApi;
import org.niis.xroad.cs.test.utils.ScenarioValueEvaluator;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ClientsStepDefs extends BaseStepDefs {
    @Autowired
    private FeignClientsApi clientsApi;

    @Autowired
    private FeignGlobalGroupsApi globalGroupsApi;

    @Step("Clients are queried and validated with following parameters")
    public void listClients(DataTable table) {
        var params = table.asMaps().get(0);
        try {
            var response = clientsApi.findClients(
                    getStr(params, "$query"),
                    pagingSortingParametersDto(
                            getStr(params, "$desc"),
                            getStr(params, "$sortBy"),
                            params.get("$limit"),
                            params.get("$offset")),
                    getStr(params, "$name"),
                    getStr(params, "$instance"),
                    getStr(params, "$memberClass"),
                    getStr(params, "$memberCode"),
                    getStr(params, "$subsystemCode"),
                    clientType(params.get("$clientType")),
                    getStr(params, "$securityServer"),
                    params.get("$excludingGroup"));
            putStepData(StepDataKey.RESPONSE, response);
            putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    private String getStr(Map<String, String> params, String key) {
        return ScenarioValueEvaluator.evaluateValue(params.get(key));
    }

    private PagingSortingParametersDto pagingSortingParametersDto(String desc, String sortBy, String limit, String offset) {
        var pagingParams = new PagingSortingParametersDto();
        pagingParams.setDesc(Boolean.valueOf(desc));
        pagingParams.setSort(sortBy);
        pagingParams.setLimit(safeToInt(limit));
        pagingParams.setOffset(safeToInt(offset));
        return pagingParams;
    }

    private ClientTypeDto clientType(String value) {
        if (value == null) {
            return null;
        }
        return ClientTypeDto.fromValue(value);
    }

    @Step("Clients response is as follows")
    public void validateListClients(DataTable table) {
        var params = table.asMaps().get(0);

        var validation = validate(getRequiredStepData(StepDataKey.RESPONSE))
                .assertion(equalsStatusCodeAssertion(OK));

        if (params.get("$jsonPath1") != null) {
            validation.assertion(isTrue("body." + params.get("$jsonPath1")));
        }

        validation.assertion(equalsAssertion(safeToInt(params.get("$items")), "body.pagingMetadata.items"))
                .assertion(equalsAssertion(safeToInt(params.get("$totalItems")), "body.pagingMetadata.totalItems"))
                .assertion(equalsAssertion(safeToInt(params.get("$limit")), "body.pagingMetadata.limit"))
                .assertion(equalsAssertion(safeToInt(params.get("$offset")), "body.pagingMetadata.offset"))
                .execute();
    }
}
