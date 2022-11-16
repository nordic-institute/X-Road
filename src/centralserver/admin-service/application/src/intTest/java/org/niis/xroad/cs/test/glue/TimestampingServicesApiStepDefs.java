/*
 * The MIT License
 *
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

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.niis.xroad.centralserver.openapi.TimestampingServicesApi;
import org.springframework.beans.factory.annotation.Autowired;

public class TimestampingServicesApiStepDefs extends BaseStepDefs {

    @Autowired
    private TimestampingServicesApi timestampingServicesApi;

    private String timestampingServiceId;

    @When("timestamping service is added")
    public void timestampingServiceIsAdded() throws Exception {
        // TODO enable the test after implementing the endpoint
//        final String url = "https://timestamping-service-" + UUID.randomUUID();
//        final MultipartFile certificate = new MockMultipartFile("certificate", CertificateUtils.generateAuthCert());
//
//        final ResponseEntity<TimestampingServiceDto> response = timestampingServicesApi.addTimestampingService(url, certificate);
//
//        final Validation.Builder validationBuilder = new Validation.Builder()
//                .context(response)
//                .title("Validate response")
//                .assertion(equalsAssertion(CREATED, response.getStatusCode(), "Verify status code"));
//        validationService.validate(validationBuilder.build());
//
//        this.timestampingServiceId = response.getBody().getId();
    }


    @Then("timestamping services list contains added timestamping service")
    public void timestampingServicesListContainsNewTimestampingService() {
        // TODO enable the test after implementing the addTimestampingService endpoint

//        final ResponseEntity<Set<TimestampingServiceDto>> response = timestampingServicesApi.getTimestampingServices();
//
//        final Validation.Builder validationBuilder = new Validation.Builder()
//                .context(response)
//                .title("Validate response")
//                .assertion(equalsAssertion(OK, response.getStatusCode(), "Verify status code"))
//                .assertion(new Assertion.Builder()
//                        .message("Timestamping services list contains the added service")
//                        .expression("body.?[id=='" + timestampingServiceId + "'].size()")
//                        .operation(EQUALS)
//                        .expectedValue(1)
//                        .build());
//        validationService.validate(validationBuilder.build());
    }
}
