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

import com.nortal.test.asserts.Assertion;
import feign.FeignException;
import io.cucumber.java.en.Step;
import org.niis.xroad.centralserver.openapi.TimestampingServicesApi;
import org.niis.xroad.centralserver.openapi.model.TimestampingServiceDto;
import org.niis.xroad.cs.test.utils.CertificateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static com.nortal.test.asserts.Assertions.notNullAssertion;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

public class TimestampingServicesApiStepDefs extends BaseStepDefs {

    @Autowired
    private TimestampingServicesApi timestampingServicesApi;

    private Integer timestampingServiceId;
    private int responseStatusCode;

    @Step("timestamping service is added")
    public void timestampingServiceIsAdded() throws Exception {
        final String url = "https://timestamping-service-" + UUID.randomUUID();
        final MultipartFile certificate = new MockMultipartFile("certificate", CertificateUtils.generateAuthCert());

        final ResponseEntity<TimestampingServiceDto> response = timestampingServicesApi.addTimestampingService(url, certificate);

        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .assertion(notNullAssertion("body.id"))
                .execute();

        this.timestampingServiceId = response.getBody().getId();
    }

    @Step("timestamping services list contains added timestamping service")
    public void timestampingServicesListContainsNewTimestampingService() {
        final ResponseEntity<Set<TimestampingServiceDto>> response = timestampingServicesApi.getTimestampingServices();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1, "body.?[id==" + timestampingServiceId + "].size()",
                        "Timestamping services list contains the added service"))
                .execute();
    }

    @Step("user tries to add timestamping service with invalid url")
    public void userTriesToAddTimestampingServiceWithInvalidUrl() throws Exception {
        final String url = "not valid url";
        final MultipartFile certificate = new MockMultipartFile("certificate", CertificateUtils.generateAuthCert());

        try {
            timestampingServicesApi.addTimestampingService(url, certificate);
        } catch (FeignException feignException) {
            responseStatusCode = feignException.status();
        }
    }

    @Step("creating timestamping service fails with exception")
    public void exceptionIsReturned() {
        validate(this.responseStatusCode)
                .assertion(new Assertion.Builder()
                        .message("Verify status code")
                        .expression("=")
                        .actualValue(responseStatusCode)
                        .expectedValue(BAD_REQUEST.value())
                        .build())
                .execute();
    }

}
