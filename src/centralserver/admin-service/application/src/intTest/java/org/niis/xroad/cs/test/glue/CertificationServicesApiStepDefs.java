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

import com.nortal.test.asserts.Validation;
import io.cucumber.java.en.When;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationServiceDto;
import org.niis.xroad.cs.test.api.FeignCertificationServicesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.CERTIFICATION_SERVICE_ID;
import static org.niis.xroad.cs.test.utils.CertificateUtils.generateAuthCert;
import static org.springframework.http.HttpStatus.CREATED;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
public class CertificationServicesApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignCertificationServicesApi certificationServicesApi;

    @When("Certification service is created")
    public void createCertificationService() throws Exception {
        MultipartFile certificate = new MockMultipartFile("certificate", generateAuthCert());
        String certificateProfileInfo = "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider";

        final ResponseEntity<ApprovedCertificationServiceDto> response = certificationServicesApi
                .addCertificationService(certificate, certificateProfileInfo, "false");

        final Validation.Builder validationBuilder = new Validation.Builder()
                .context(response)
                .title("Validate response")
                .assertion(equalsAssertion(CREATED, response.getStatusCode(), "Verify status code"));
        validationService.validate(validationBuilder.build());

        putStepData(CERTIFICATION_SERVICE_ID, response.getBody().getId());
    }

}
