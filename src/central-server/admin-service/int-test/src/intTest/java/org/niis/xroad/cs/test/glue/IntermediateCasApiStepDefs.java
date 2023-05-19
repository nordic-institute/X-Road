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
import org.bouncycastle.cert.X509CertificateHolder;
import org.niis.xroad.cs.openapi.model.CertificateAuthorityDto;
import org.niis.xroad.cs.openapi.model.OcspResponderDto;
import org.niis.xroad.cs.test.api.FeignCertificationServicesApi;
import org.niis.xroad.cs.test.api.FeignIntermediateCasApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static com.nortal.test.asserts.Assertions.notNullAssertion;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.CERTIFICATION_SERVICE_ID;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.ERROR_RESPONSE_BODY;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.NEW_OCSP_RESPONDER_URL;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.OCSP_RESPONDER_ID;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.RESPONSE;
import static org.niis.xroad.cs.test.glue.BaseStepDefs.StepDataKey.RESPONSE_STATUS;
import static org.niis.xroad.cs.test.utils.CertificateUtils.generateAuthCert;
import static org.niis.xroad.cs.test.utils.CertificateUtils.generateAuthCertHolder;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class IntermediateCasApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignCertificationServicesApi certificationServicesApi;
    @Autowired
    private FeignIntermediateCasApi intermediateCasApi;

    private ResponseEntity<List<CertificateAuthorityDto>> intermediateCaResponse;
    private Integer intermediateCaId;
    private X509CertificateHolder generatedCertificate;

    @Step("intermediate CA added to certification service")
    public void addIntermediateCa() throws Exception {
        final Integer certificationServiceId = getRequiredStepData(CERTIFICATION_SERVICE_ID);

        addIntermediateCa("Subject", certificationServiceId);
    }

    @Step("intermediate CA  with name {string} is added to certification service with id {}")
    public void addIntermediateCa(String name, Integer certificationServiceId) throws Exception {
        try {
            generatedCertificate = generateAuthCertHolder("CN=" + name);
            final MultipartFile certificate = new MockMultipartFile("certificate",
                    "certificate.der",
                    null,
                    generatedCertificate.getEncoded());
            final ResponseEntity<CertificateAuthorityDto> response = certificationServicesApi
                    .addCertificationServiceIntermediateCa(certificationServiceId, certificate);

            validate(response)
                    .assertion(equalsStatusCodeAssertion(CREATED))
                    .execute();

            intermediateCaId = response.getBody().getId();

            putStepData(RESPONSE, response);
            putStepData(RESPONSE_STATUS, response.getStatusCodeValue());
        } catch (FeignException feignException) {
            putStepData(RESPONSE_STATUS, feignException.status());
            putStepData(ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("intermediate CAs are retrieved")
    public void getIntermediateCa() throws Exception {
        final Integer certificationServiceId = getRequiredStepData(CERTIFICATION_SERVICE_ID);

        intermediateCaResponse = certificationServicesApi.getCertificationServiceIntermediateCas(certificationServiceId);

        validate(intermediateCaResponse)
                .assertion(equalsStatusCodeAssertion(OK))
                .execute();
    }

    @Step("deleted intermediate CA is not present")
    public void intermediateCasIsDeleted() {
        final Integer certificationServiceId = getRequiredStepData(CERTIFICATION_SERVICE_ID);
        intermediateCaResponse = certificationServicesApi.getCertificationServiceIntermediateCas(certificationServiceId);

        validate(Objects.requireNonNull(intermediateCaResponse))
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(0, "body.size()"))
                .execute();
    }

    @Step("intermediate CA is as follows")
    public void intermediateCasValidated(DataTable dataTable) {
        var values = dataTable.asMap();


        validate(Objects.requireNonNull(intermediateCaResponse))
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(1, "body.size()"))
                .assertion(equalsAssertion(intermediateCaId, "body[0].id"))
                .assertion(notNullAssertion("body[0].caCertificate.hash"))
                .assertion(equalsAssertion(values.get("$issuerDistinguishedName"),
                        "body[0].caCertificate.issuerDistinguishedName"))
                .assertion(equalsAssertion(values.get("$subjectDistinguishedName"),
                        "body[0].caCertificate.subjectDistinguishedName"))
                .assertion(equalsAssertion(values.get("$subjectCommonName"),
                        "body[0].caCertificate.subjectCommonName"))
                .assertion(equalsAssertion(generatedCertificate.getNotBefore().toInstant().atOffset(ZoneOffset.UTC),
                        "body[0].caCertificate.notBefore"))
                .assertion(equalsAssertion(generatedCertificate.getNotAfter().toInstant().atOffset(ZoneOffset.UTC),
                        "body[0].caCertificate.notAfter"))
                .execute();
    }

    @Step("OCSP responder is added to intermediate CA")
    public void ocspResponderIsAddedToIntermediateCA() throws Exception {
        final MultipartFile certificate = new MockMultipartFile("certificate", "certificate.der", null, generateAuthCert("CN=Subject"));
        final String url = "https://" + UUID.randomUUID();

        final ResponseEntity<OcspResponderDto> response = intermediateCasApi
                .addIntermediateCaOcspResponder(intermediateCaId, url, certificate);

        validate(response)
                .assertion(equalsStatusCodeAssertion(CREATED))
                .execute();

        putStepData(OCSP_RESPONDER_ID, response.getBody().getId());
    }

    @Step("intermediate CA has {int} OCSP responders")
    public void intermediateCAHasOCSPResponders(int count) {
        final ResponseEntity<List<OcspResponderDto>> response = intermediateCasApi.getIntermediateCaOcspResponders(intermediateCaId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(count, "body.size", "Response contains " + count + " items"))
                .execute();
    }

    @Step("intermediate CA has the updated OCSP responder")
    public void intermediateCAHasUpdatedOCSPResponder() {
        final ResponseEntity<List<OcspResponderDto>> response = intermediateCasApi
                .getIntermediateCaOcspResponders(intermediateCaId);

        final String newOcspResponderUrl = getRequiredStepData(NEW_OCSP_RESPONDER_URL);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(Boolean.TRUE, "body[0].hasCertificate", "Verify OCSP responder has certificate"))
                .assertion(equalsAssertion(newOcspResponderUrl, "body[0].url", "OCSP responder url matches"))
                .execute();
    }

    @Step("OCSP responder is deleted from intermediate CA")
    public void ocspResponderIsDeletedFromIntermediateCA() {
        final Integer ocspResponderId = getRequiredStepData(OCSP_RESPONDER_ID);

        final ResponseEntity<Void> response = intermediateCasApi
                .deleteIntermediateCaOcspResponder(intermediateCaId, ocspResponderId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                .execute();
    }

    @Step("intermediate CA is deleted")
    public void deleteIntermediateCa() {
        final ResponseEntity<Void> response = intermediateCasApi
                .deleteIntermediateCa(intermediateCaId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(NO_CONTENT))
                .execute();
    }
}
