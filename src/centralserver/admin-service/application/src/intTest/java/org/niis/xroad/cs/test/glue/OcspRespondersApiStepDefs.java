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
import com.nortal.test.asserts.AssertionOperation;
import com.nortal.test.asserts.Validation;
import io.cucumber.java.en.And;
import io.cucumber.java.en.When;
import org.niis.xroad.centralserver.openapi.model.CertificateDetailsDto;
import org.niis.xroad.centralserver.openapi.model.OcspResponderDto;
import org.niis.xroad.cs.test.api.FeignOcspRespondersApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.NEW_OCSP_RESPONDER_URL;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.OCSP_RESPONDER_ID;
import static org.niis.xroad.cs.test.utils.CertificateUtils.generateAuthCert;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public class OcspRespondersApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignOcspRespondersApi ocspRespondersApi;

    private String getKeyOldOcspResponderCertHash;

    @When("OCSP responder url is updated")
    public void updateOcspResponderUrl() {
        Integer ocspResponderId = getRequiredStepData(OCSP_RESPONDER_ID);

        final String newUrl = "https://updated-ocsp-responder-url-" + UUID.randomUUID();

        final ResponseEntity<OcspResponderDto> response = ocspRespondersApi
                .updateOcspResponder(ocspResponderId, newUrl, null);

        final Validation.Builder validationBuilder = new Validation.Builder()
                .context(response)
                .title("Validate response")
                .assertion(equalsAssertion(OK, response.getStatusCode(), "Verify status code"));
        validationService.validate(validationBuilder.build());

        putStepData(NEW_OCSP_RESPONDER_URL, newUrl);
    }


    @When("OCSP responder url and certificate is updated")
    public void ocspResponderUrlAndCertificateIsUpdated() throws Exception {
        Integer ocspResponderId = getRequiredStepData(OCSP_RESPONDER_ID);

        final ResponseEntity<CertificateDetailsDto> certificateResponse = ocspRespondersApi
                .getOcspRespondersCertificate(ocspResponderId);

        getKeyOldOcspResponderCertHash = certificateResponse.getBody().getHash();

        final String newUrl = "https://updated-ocsp-responder-url-" + UUID.randomUUID();
        MultipartFile newCertificate = new MockMultipartFile("certificate", generateAuthCert());

        final ResponseEntity<OcspResponderDto> response = ocspRespondersApi
                .updateOcspResponder(ocspResponderId, newUrl, newCertificate);

        final Validation.Builder validationBuilder = new Validation.Builder()
                .context(response)
                .title("Validate response")
                .assertion(equalsAssertion(OK, response.getStatusCode(), "Verify status code"));
        validationService.validate(validationBuilder.build());

        putStepData(NEW_OCSP_RESPONDER_URL, newUrl);
    }

    @And("the OCSP responder certificate was updated")
    public void theOCSPResponderCertificateWasUpdated() {
        Integer ocspResponderId = getRequiredStepData(OCSP_RESPONDER_ID);

        final ResponseEntity<CertificateDetailsDto> certificateResponse = ocspRespondersApi
                .getOcspRespondersCertificate(ocspResponderId);

        final Validation.Builder validationBuilder = new Validation.Builder()
                .context(certificateResponse)
                .title("Validate response")
                .assertion(equalsAssertion(OK, certificateResponse.getStatusCode(), "Verify status code"))
                .assertion(new Assertion.Builder()
                        .message("Certificate hash differs")
                        .expression("!=")
                        .operation(AssertionOperation.NOT_EQUALS)
                        .actualValue(certificateResponse.getBody().getHash())
                        .expectedValue(getKeyOldOcspResponderCertHash)
                        .build());
        validationService.validate(validationBuilder.build());
    }
}
