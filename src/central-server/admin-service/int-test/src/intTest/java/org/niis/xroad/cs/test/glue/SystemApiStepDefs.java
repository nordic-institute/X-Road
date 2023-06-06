/*
 * The MIT License
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
package org.niis.xroad.cs.test.glue;

import com.nortal.test.asserts.Assertion;
import feign.FeignException;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.CentralServerAddressDto;
import org.niis.xroad.cs.openapi.model.HighAvailabilityClusterNodeDto;
import org.niis.xroad.cs.openapi.model.HighAvailabilityClusterStatusDto;
import org.niis.xroad.cs.openapi.model.SystemStatusDto;
import org.niis.xroad.cs.openapi.model.VersionDto;
import org.niis.xroad.cs.test.api.FeignSystemApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static com.nortal.test.asserts.Assertions.notNullAssertion;
import static org.junit.Assert.fail;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class SystemApiStepDefs extends BaseStepDefs {
    @Autowired
    private FeignSystemApi feignSystemApi;

    private ResponseEntity<SystemStatusDto> savedResponse;

    @Step("system status is requested")
    public void systemStatusIsRequested() {
        savedResponse = feignSystemApi.getSystemStatus();
    }

    @Step("system status is validated")
    public void systemStatusIsValidated() {
        validateSystemStatusResponse(savedResponse);
    }

    @Step("system cluster status is requested")
    public void systemClusterStatusIsRequested() {
        var response = feignSystemApi.getHighAvailabilityClusterStatus();
        putStepData(StepDataKey.RESPONSE, response);
    }

    @Step("system cluster status is validated")
    public void systemClusterStatusIsValidated() {
        ResponseEntity<HighAvailabilityClusterStatusDto> response = getRequiredStepData(StepDataKey.RESPONSE);
        validate(response)
                .assertion(equalsStatusCodeAssertion(HttpStatus.OK))
                .assertion(isTrue("body.isHaConfigured"))
                .assertion(equalsAssertion("test_node", "body.nodeName"))
                .assertion(equalsAssertion(1, "body.nodes.size()"))
                .assertion(equalsAssertion("test_node", "body.nodes[0].nodeName"))
                .assertion(equalsAssertion("cs", "body.nodes[0].nodeAddress"))
                .assertion(equalsAssertion(OffsetDateTime.parse("2022-01-01T01:00Z"), "body.nodes[0].configurationGenerated"))
                .assertion(equalsAssertion(HighAvailabilityClusterNodeDto.StatusEnum.ERROR, "body.nodes[0].status"))
                .assertion(isFalse("body.allNodesOk"))
                .execute();
    }

    private void validateSystemStatusResponse(ResponseEntity<SystemStatusDto> response) {
        validate(response)
                .assertion(equalsStatusCodeAssertion(HttpStatus.OK))
                .assertion(notNullAssertion("body"))
                .assertion(notNullAssertion("body.initializationStatus"))
                .assertion(notNullAssertion("body.highAvailabilityStatus"))
                .assertion(equalsAssertion("test_node", "body.highAvailabilityStatus.nodeName"))
                .assertion(isTrue("body.highAvailabilityStatus.isHaConfigured"))
                .execute();
    }

    @Step("system version endpoint returns version")
    public void verifySystemVersionEndpoint() {
        final ResponseEntity<VersionDto> systemVersion = feignSystemApi.getSystemVersion();
        validate(systemVersion)
                .assertion(equalsStatusCodeAssertion(HttpStatus.OK))
                .assertion(notNullAssertion("body.info"))
                .assertion(isTrue("body.info.length > 0"))
                .assertion(isFalse("body.info.contains(\"@version@\")"))
                .assertion(isFalse("body.info.contains(\"@buildType@\")"))
                .assertion(isFalse("body.info.contains(\"@gitCommitDate@\")"))
                .assertion(isFalse("body.info.contains(\"@gitCommitHash@\")"))
                .execute();
    }

    @Step("updating central server address with url {string} should fail")
    public void updatingCentralServerAddressWithInvalidAddressShouldFail(String url) {
        final CentralServerAddressDto dto = new CentralServerAddressDto();
        dto.setCentralServerAddress(url);
        try {
            feignSystemApi.updateCentralServerAddress(dto);
            fail("Setting invalid url should fail");
        } catch (FeignException feignException) {
            validate(feignException.status())
                    .assertion(new Assertion.Builder()
                            .message("Verify status code")
                            .expression("=")
                            .actualValue(feignException.status())
                            .expectedValue(BAD_REQUEST.value())
                            .build())
                    .execute();
        }
    }

    @Step("updating central server address with {string} should succeed")
    public void updatingCentralServerAddressWithValidUrlShouldSucceed(String url) {
        final CentralServerAddressDto dto = new CentralServerAddressDto();
        dto.setCentralServerAddress(url);

        final ResponseEntity<SystemStatusDto> response = feignSystemApi.updateCentralServerAddress(dto);

        validateSystemStatusResponse(response);
    }

}
