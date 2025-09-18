/*
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
import org.apache.commons.io.IOUtils;
import org.niis.xroad.cs.openapi.model.CertificateDetailsDto;
import org.niis.xroad.cs.openapi.model.DistinguishedNameDto;
import org.niis.xroad.cs.openapi.model.ManagementServicesConfigurationDto;
import org.niis.xroad.cs.openapi.model.RegisterServiceProviderRequestDto;
import org.niis.xroad.cs.openapi.model.ServiceProviderIdDto;
import org.niis.xroad.cs.test.api.FeignManagementServicesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ManagementServicesApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignManagementServicesApi managementServicesApi;

    private ResponseEntity<ManagementServicesConfigurationDto> response;
    private ResponseEntity<CertificateDetailsDto> tlsCertificateResponse;
    private ResponseEntity<Resource> resourceResponse;
    private ResponseEntity<Void> voidResponse;

    @Step("Management services provider id is set to {string}")
    public void updateManagementServicesConfiguration(String serviceProviderId) {
        final var request = new ServiceProviderIdDto();
        request.setServiceProviderId(serviceProviderId);

        try {
            response = managementServicesApi.updateManagementServicesConfiguration(request);
            putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCode().value());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Management services configuration is retrieved")
    public void getManagementServicesConfiguration() {
        try {
            response = managementServicesApi.getManagementServicesConfiguration();
            putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCode().value());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Management service TLS certificate is retrieved")
    public void getCertificate() {
        try {
            tlsCertificateResponse = managementServicesApi.getCertificate();
            putStepData(StepDataKey.RESPONSE_STATUS, tlsCertificateResponse.getStatusCode().value());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Management service TLS certificate is downloaded")
    public void downloadCertificate() {
        try {
            resourceResponse = managementServicesApi.downloadCertificate();
            putStepData(StepDataKey.RESPONSE_STATUS, resourceResponse.getStatusCode().value());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Management service TLS key and certificate is created")
    public void generateKeyAndCertificate() {
        try {
            voidResponse = managementServicesApi.generateKeyAndCertificate();
            putStepData(StepDataKey.RESPONSE_STATUS, voidResponse.getStatusCode().value());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Management service certificate CSR is generated")
    public void generateCertificateRequest() {
        var distinguishedName = new DistinguishedNameDto();
        distinguishedName.setName("CN=cs");
        try {
            resourceResponse = managementServicesApi.generateCertificateRequest(distinguishedName);
            putStepData(StepDataKey.RESPONSE_STATUS, resourceResponse.getStatusCode().value());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Management service TLS certificate is uploaded")
    public void uploadCertificate() throws Exception {
        var newCert = IOUtils.toByteArray(getClass().getResourceAsStream("/test-data/management-service-new.crt"));
        MultipartFile certificate = new MockMultipartFile("certificate", "certificate.cer", null, newCert);
        try {
            tlsCertificateResponse = managementServicesApi.uploadCertificate(certificate);
            putStepData(StepDataKey.RESPONSE_STATUS, tlsCertificateResponse.getStatusCode().value());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Management services configuration is as follows")
    public void managementRequestIsApproved(DataTable dataTable) {
        var values = dataTable.asMap();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(values.get("$securityServerId"), "body.securityServerId"))
                .assertion(equalsAssertion(values.get("$securityServerOwnersGlobalGroupCode"), "body.securityServerOwnersGlobalGroupCode"))
                .assertion(equalsAssertion(values.get("$serviceProviderName"), "body.serviceProviderName"))
                .assertion(equalsAssertion(values.get("$servicesAddress"), "body.servicesAddress"))
                .assertion(equalsAssertion(values.get("$wsdlAddress"), "body.wsdlAddress"))
                .assertion(equalsAssertion(values.get("$serviceProviderId"), "body.serviceProviderId"))
                .execute();
    }

    @Step("security server {string} is registered as management service provider")
    public void securityServerIsRegisteredAsManagementServiceProvider(String securityServerId) {
        final RegisterServiceProviderRequestDto dto = new RegisterServiceProviderRequestDto();
        dto.setSecurityServerId(securityServerId);

        try {
            response = managementServicesApi.registerServiceProvider(dto);
            putStepData(StepDataKey.RESPONSE_STATUS, response.getStatusCode().value());
        } catch (FeignException feignException) {
            putStepData(StepDataKey.RESPONSE_STATUS, feignException.status());
            putStepData(StepDataKey.ERROR_RESPONSE_BODY, feignException.contentUTF8());
        }
    }

    @Step("Vault's TLS credentials issuance response is mocked")
    public void mockVaultTlsCredentialsIssuanceResponse() throws IOException {
        mockServerService.client()
                .when(request()
                        .withMethod("POST")
                        .withPath("/v1/xrd-pki/issue/xrd-internal"))
                .respond(response()
                        .withStatusCode(OK.value())
                        .withContentType(APPLICATION_JSON)
                        .withBody(IOUtils.toByteArray(getClass().getResourceAsStream("/test-data/vault-issue-tls-creds-response.json"))));

        mockServerService.client()
                .when(request()
                        .withMethod("POST")
                        .withPath("/v1/xrd-secret/tls/management-services"))
                .respond(response()
                        .withStatusCode(OK.value())
                        .withContentType(APPLICATION_JSON));
    }

    @Step("Vault's TLS credentials addition response is mocked")
    public void mockVaultTlsCredentialsAdditionResponse() throws IOException {
        mockServerService.client()
                .when(request()
                        .withMethod("POST")
                        .withPath("/v1/xrd-pki/issue/xrd-internal"))
                .respond(response()
                        .withStatusCode(OK.value())
                        .withContentType(APPLICATION_JSON)
                        .withBody(IOUtils.toByteArray(getClass().getResourceAsStream("/test-data/vault-issue-tls-creds-response.json"))));

        mockServerService.client()
                .when(request()
                        .withMethod("POST")
                        .withPath("/v1/xrd-secret/tls/management-services"))
                .respond(response()
                        .withStatusCode(OK.value())
                        .withContentType(APPLICATION_JSON));
    }

    @Step("Vault's TLS credentials retrieval response is mocked")
    public void mockVaultTlsCredentialsRetrievalResponse() throws IOException {
        mockServerService.client()
                .when(request()
                        .withMethod("GET")
                        .withPath("/v1/xrd-secret/tls/management-services"))
                .respond(response()
                        .withStatusCode(OK.value())
                        .withContentType(APPLICATION_JSON)
                        .withBody(IOUtils.toByteArray(getClass().getResourceAsStream("/test-data/vault-get-tls-creds.response.json"))));
    }
}
