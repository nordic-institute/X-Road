/**
 * The MIT License
 * <p>
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
import org.niis.xroad.cs.openapi.model.InitialServerConfDto;
import org.niis.xroad.cs.openapi.model.InitializationStatusDto;
import org.niis.xroad.cs.openapi.model.TokenInitStatusDto;
import org.niis.xroad.cs.test.api.FeignInitializationApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class InitializationStepDefs extends BaseStepDefs {
    @Autowired
    private FeignInitializationApi initializationApi;

    private ResponseEntity<InitializationStatusDto> response;

    @Step("Server is initialized with address {string}, instance-identifier {string}, token pin {string}")
    public void initCentralServer(String address, String identifier, String token) {
        var request = new InitialServerConfDto();
        request.setCentralServerAddress(address);
        request.setInstanceIdentifier(identifier);
        request.setSoftwareTokenPin(token);

        try {
            var result = initializationApi.initCentralServer(request);
            putStepData(StepDataKey.RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Server initialization status is requested")
    public void getInitializationStatus() {
        try {
            response = initializationApi.getInitializationStatus();
            putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
        }
    }

    @Step("Server initialization status is as follows")
    public void managementRequestIsApproved(DataTable dataTable) {
        var values = dataTable.asMap();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(TokenInitStatusDto.fromValue(values.get("$softwareTokenInitStatus")),
                        "body.softwareTokenInitStatus"))
                .assertion(equalsAssertion(values.get("$instanceIdentifier"), "body.instanceIdentifier"))
                .assertion(equalsAssertion(values.get("$centralServerAddress"), "body.centralServerAddress"))
                .execute();
    }

    @Step("Signer.initSoftwareToken is mocked to accept password")
    public void signerGetTokenResponseIsMockedForTokenTokenId() {
        mockServerService.client()
                .when(request()
                        .withMethod("PUT")
                        .withPath("/initSoftwareToken/"))
                .respond(response().withStatusCode(NO_CONTENT.value()));
    }
}
