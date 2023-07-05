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
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Step;
import org.niis.xroad.cs.openapi.model.ApprovedCertificationServiceDto;
import org.niis.xroad.cs.openapi.model.CertificationServiceSettingsDto;
import org.niis.xroad.cs.openapi.model.OcspResponderDto;
import org.niis.xroad.cs.test.api.FeignCertificationServicesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.atomic.AtomicInteger;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static com.nortal.test.asserts.Assertions.notNullAssertion;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.CERTIFICATION_SERVICE_ID;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.ERROR_RESPONSE_BODY;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.OCSP_RESPONDER_ID;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.RESPONSE;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.RESPONSE_STATUS;
import static org.niis.xroad.cs.test.utils.CertificateUtils.generateAuthCert;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class CertificationServicesApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignCertificationServicesApi certificationServicesApi;

    @Step("Certification service with id {int} is retrieved")
    public void getCertificationServiceById(Integer id) {
        try {
            var result = certificationServicesApi.getCertificationService(id);
            putStepData(RESPONSE, result);
            putStepData(RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("certification service is retrieved")
    public void certificationServiceIsRetrieved() {
        getCertificationServiceById(getRequiredStepData(CERTIFICATION_SERVICE_ID));
    }

    @Step("Certification service certificate with id {int} is retrieved")
    public void getCertificationServiceCertificateById(Integer id) {
        try {
            var result = certificationServicesApi.getCertificationServiceCertificate(id);
            putStepData(RESPONSE, result);
            putStepData(RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Certification service with id {int} is deleted")
    public void deleteCertificationServiceById(Integer id) {
        try {
            var result = certificationServicesApi.deleteCertificationService(id);
            putStepData(RESPONSE, result);
            putStepData(RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }


    @Step("Certification service is as follows")
    public void validateCertificationServiceById(DataTable table) {
        var params = table.asMap();
        validate(getRequiredStepData(RESPONSE))
                .assertion(equalsAssertion(safeToInt(params.get("$id")), "body.id"))
                .assertion(equalsAssertion(params.get("$name"), "body.name"))
                .assertion(equalsAssertion(params.get("$issuerDistinguishedName"), "body.issuerDistinguishedName"))
                .assertion(equalsAssertion(params.get("$subjectDistinguishedName"), "body.subjectDistinguishedName"))
                .assertion(notNullAssertion("body.notAfter"))
                .assertion(notNullAssertion("body.notBefore"))
                .assertion(equalsAssertion(params.get("$certificateProfileInfo"), "body.certificateProfileInfo"))
                .assertion(equalsAssertion(parseBoolean(params.get("$tlsAuth")), "body.tlsAuth"))
                .execute();
    }

    @Step("Certification service certificate is as follows")
    public void validateCertificationServiceCertificateById(DataTable table) {
        var params = table.asMap();
        validate(getRequiredStepData(RESPONSE))
                .assertion(notNullAssertion("body.hash"))
                .assertion(equalsAssertion(params.get("$issuerCommonName"), "body.issuerCommonName"))
                .assertion(equalsAssertion(params.get("$issuerDistinguishedName"), "body.issuerDistinguishedName"))
                .assertion(equalsAssertion(params.get("$keyUsages"), "body.keyUsages[0].toString()"))
                .assertion(notNullAssertion("body.notAfter"))
                .assertion(notNullAssertion("body.notBefore"))
                .assertion(notNullAssertion("body.publicKeyAlgorithm"))
                .assertion(notNullAssertion("body.rsaPublicKeyExponent"))
                .assertion(notNullAssertion("body.rsaPublicKeyModulus"))
                .assertion(equalsAssertion(params.get("$serial"), "body.serial"))
                .assertion(equalsAssertion(params.get("$signatureAlgorithm"), "body.signatureAlgorithm"))
                .assertion(equalsAssertion(params.get("$subjectCommonName"), "body.subjectCommonName"))
                .assertion(equalsAssertion(params.get("$subjectDistinguishedName"), "body.subjectDistinguishedName"))
                .assertion(equalsAssertion(safeToInt(params.get("$version")), "body.version"))
                .execute();
    }

    @Step("Certification services are listed")
    public void listCertificationServices() {
        try {
            var result = certificationServicesApi.getCertificationServices();
            putStepData(RESPONSE, result);
            putStepData(RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Certification services are as follows")
    public void validateCertificationServices(DataTable table) {
        var entries = table.asMaps();
        var validation = validate(getRequiredStepData(StepDataKey.RESPONSE))
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(entries.size(), "body.size()"));

        final var index = new AtomicInteger();
        entries.forEach(params -> {
            var currIndex = index.getAndIncrement();
            validation.assertion(equalsAssertion(safeToInt(params.get("$id")), format("body[%d].id", currIndex)))
                    .assertion(equalsAssertion(params.get("$name"), format("body[%d].name", currIndex)))
                    .assertion(notNullAssertion(format("body[%d].notAfter", currIndex)))
                    .assertion(notNullAssertion(format("body[%d].notBefore", currIndex)));
        });

        validation.execute();
    }

    @Step("Certification service is created")
    public void createCertificationService() throws Exception {
        createCertificationService("Subject",
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider");
    }

    @Step("Certification service with name {string} and certificateProfileInfo {string} is created")
    public void createCertificationService(String name, String certificateProfileInfo) throws Exception {
        MultipartFile certificate = new MockMultipartFile("certificate", "certificate.cer", null, generateAuthCert("CN=" + name));

        try {
            final ResponseEntity<ApprovedCertificationServiceDto> result = certificationServicesApi
                    .addCertificationService(certificate, certificateProfileInfo, null);

            validate(result)
                    .assertion(equalsStatusCodeAssertion(CREATED))
                    .assertion(notNullAssertion("body.id"))
                    .execute();

            putStepData(CERTIFICATION_SERVICE_ID, result.getBody().getId());
            putStepData(RESPONSE, result);
            putStepData(RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Certification service with id {int} is updated with tlsAuth {} and certificateProfileInfo {string}")
    public void createCertificationService(Integer id, String tlsAuth, String certificateProfileInfo) {
        try {
            var request = new CertificationServiceSettingsDto()
                    .tlsAuth(tlsAuth)
                    .certificateProfileInfo(certificateProfileInfo);

            final ResponseEntity<ApprovedCertificationServiceDto> result = certificationServicesApi
                    .updateCertificationService(id, request);

            putStepData(RESPONSE, result);
            putStepData(RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Returned certification service has id {int}, tlsAuth {} and certificateProfileInfo {string}")
    public void validateCertificationService(Integer id, String tlsAuth, String certificateProfileInfo) {
        validate(getRequiredStepData(RESPONSE))
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(id, "body.id"))
                .assertion(equalsAssertion(parseBoolean(tlsAuth), "body.tlsAuth"))
                .assertion(equalsAssertion(certificateProfileInfo, "body.certificateProfileInfo"))
                .execute();
    }

    @Step("OCSP responder with url {string} is added to certification service with id {}")
    public void ocspResponderIsAddedToIntermediateCA(String url, Integer id) throws Exception {
        final MultipartFile certificate = new MockMultipartFile("certificate", "certificate.pem", null, generateAuthCert("CN=Subject"));


        final ResponseEntity<OcspResponderDto> response = certificationServicesApi
                .addCertificationServiceOcspResponder(id, url, certificate);

        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .execute();

        putStepData(OCSP_RESPONDER_ID, response.getBody().getId());
    }

    @Step("Certification service with id {} OCSP responders are listed")
    public void listCertificationServicesOcspResponders(Integer id) {
        try {
            var result = certificationServicesApi.getCertificationServiceOcspResponders(id);
            putStepData(RESPONSE, result);
            putStepData(RESPONSE_STATUS, result.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Certification service OCSP responders are as follows")
    public void validateCertificationServicesOcspResponders(DataTable table) {
        var entries = table.asMaps();
        var validation = validate(getRequiredStepData(StepDataKey.RESPONSE))
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(entries.size(), "body.size()"));

        final var index = new AtomicInteger();
        entries.forEach(params -> {
            var currIndex = index.getAndIncrement();
            validation.assertion(equalsAssertion(safeToInt(params.get("$id")), format("body[%d].id", currIndex)))
                    .assertion(equalsAssertion(params.get("$url"), format("body[%d].url", currIndex)))
                    .assertion(equalsAssertion(parseBoolean(params.get("$hasCertificate")), format("body[%d].hasCertificate", currIndex)));
        });

        validation.execute();
    }

    @Step("certification service is deleted")
    public void certificationServiceIsDeleted() {
        final Integer certServiceId = getRequiredStepData(CERTIFICATION_SERVICE_ID);

        final ResponseEntity<Void> response = certificationServicesApi.deleteCertificationService(certServiceId);
        validate(response)
                .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                .execute();
    }
}
