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

import feign.FeignException;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.TimestampingServicesApi;
import org.niis.xroad.cs.openapi.model.TimestampingServiceDto;
import org.niis.xroad.cs.test.utils.CertificateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static com.nortal.test.asserts.Assertions.notNullAssertion;
import static java.lang.Integer.MIN_VALUE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class TimestampingServicesApiStepDefs extends BaseStepDefs {

    @Autowired
    private TimestampingServicesApi timestampingServicesApi;

    private Integer timestampingServiceId;
    private String timestampingURL;
    private int responseStatusCode;

    @Step("timestamping service is added")
    public void timestampingServiceIsAdded() throws Exception {
        final String url = "https://timestamping-service-" + UUID.randomUUID();
        final MultipartFile certificate = new MockMultipartFile("certificate",
                "certificate.cer",
                null,
                CertificateUtils.generateAuthCert("CN=Subject"));

        final ResponseEntity<TimestampingServiceDto> response = timestampingServicesApi.addTimestampingService(url, certificate);

        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .assertion(notNullAssertion("body.id"))
                .execute();

        this.timestampingServiceId = response.getBody().getId();
        this.timestampingURL = response.getBody().getUrl();
    }

    @Step("timestamping services returns added timestamping service by id")
    public void timestampingServicesReturnsTimestampingServiceById() {
        final ResponseEntity<TimestampingServiceDto> response = timestampingServicesApi.getTimestampingService(timestampingServiceId);

        validate(response).assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(timestampingURL, "body.url",
                        "Verify Timestamping Service URL"))
                .execute();
    }

    @Step("timestamping services list contains added timestamping service")
    public void timestampingServicesListContainsNewTimestampingService() {
        final ResponseEntity<List<TimestampingServiceDto>> response = timestampingServicesApi.getTimestampingServices();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1, "body.?[id==" + timestampingServiceId + "].size()",
                        "Timestamping services list contains the added service"))
                .execute();
    }

    @Step("user tries to add timestamping service with invalid url")
    public void userTriesToAddTimestampingServiceWithInvalidUrl() throws Exception {
        final String url = "not valid url";
        final MultipartFile certificate = new MockMultipartFile("certificate",
                "certificate.pem",
                null,
                CertificateUtils.generateAuthCert("CN=Subject"));

        try {
            final ResponseEntity<TimestampingServiceDto> response = timestampingServicesApi.addTimestampingService(url, certificate);
            this.responseStatusCode = response.getStatusCodeValue();
        } catch (FeignException feignException) {
            this.responseStatusCode = feignException.status();
        }
    }

    @Step("creating timestamping service fails with exception")
    public void exceptionIsReturned() {
        validate(this.responseStatusCode)
                .assertion(equalsStatusCodeAssertion(this.responseStatusCode, BAD_REQUEST))
                .execute();
    }

    @Step("user tries to delete timestamping service with not existing id")
    public void userTriesToDeleteTimestampingServiceWithNonExistingId() {
        try {
            final ResponseEntity<Void> response = timestampingServicesApi.deleteTimestampingService(MIN_VALUE);
            this.responseStatusCode = response.getStatusCodeValue();
        } catch (FeignException feignException) {
            this.responseStatusCode = feignException.status();
        }
    }

    @Step("timestamping service is not found")
    public void errorCodeIsReturned() {
        validate(this.responseStatusCode)
                .assertion(equalsStatusCodeAssertion(this.responseStatusCode, NOT_FOUND))
                .execute();
    }

    @Step("user deletes the added timestamping service")
    public void userDeletesTheAddedTimestampingService() {
        final ResponseEntity<Void> responseEntity = timestampingServicesApi.deleteTimestampingService(timestampingServiceId);

        validate(responseEntity)
                .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                .execute();
    }

    @Step("timestamping services list does not contain added timestamping service")
    public void timestampingServicesListDoesNotContainAddedTimestampingService() {
        final ResponseEntity<List<TimestampingServiceDto>> response = timestampingServicesApi.getTimestampingServices();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(0, "body.?[id==" + timestampingServiceId + "].size()",
                        "Timestamping services list contains the added service"))
                .execute();
    }

    @Step("timestamping service URL and certificate are updated")
    public void timestampingServiceIsUpdated() throws Exception {
        final String url = "https://timestamping-service-" + UUID.randomUUID();
        final MultipartFile certificate = new MockMultipartFile("certificate",
                "certificate.cer",
                null,
                CertificateUtils.generateAuthCert("CN=Subject"));

        final ResponseEntity<TimestampingServiceDto> response =
                timestampingServicesApi.updateTimestampingService(this.timestampingServiceId, url, certificate);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(url, "body.url",
                        "Verify Timestamping Service URL"))
                .assertion(notNullAssertion("body.certificate"))
                .execute();
    }

    @Step("timestamping service URL is updated")
    public void timestampingServiceUrlIsUpdated() {
        final String url = "https://timestamping-service-" + UUID.randomUUID();

        final ResponseEntity<TimestampingServiceDto> response =
                timestampingServicesApi.updateTimestampingService(this.timestampingServiceId, url, null);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(url, "body.url",
                        "Verify Timestamping Service URL"))
                .assertion(notNullAssertion("body.certificate"))
                .execute();
    }
}
