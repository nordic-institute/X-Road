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

import io.cucumber.java.en.Step;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.cs.test.api.FeignManagementRequestsApi;
import org.niis.xroad.cs.test.utils.CertificateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto.SECURITY_SERVER;
import static org.niis.xroad.cs.openapi.model.ManagementRequestStatusDto.APPROVED;
import static org.niis.xroad.cs.openapi.model.ManagementRequestStatusDto.WAITING;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.OWNER_CHANGE_REQUEST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ManagementRequestsApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignManagementRequestsApi managementRequestsApi;

    private Integer managementRequestId;

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("new security server {string} authentication certificate registered")
    public void newSecurityServerRegistered(String serverId) throws Exception {
        final String[] idParts = StringUtils.split(serverId, ':');
        final var managementRequest = new AuthenticationCertificateRegistrationRequestDto();
        managementRequest.setServerAddress("security-server-address-" + idParts[3]);
        managementRequest.setSecurityServerId(serverId);
        managementRequest.setAuthenticationCertificate(CertificateUtils.generateAuthCert());
        managementRequest.setType(AUTH_CERT_REGISTRATION_REQUEST);
        managementRequest.setOrigin(SECURITY_SERVER);

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
        this.managementRequestId = response.getBody().getId();

        validate(response)
                .assertion(equalsStatusCodeAssertion(ACCEPTED))
                .assertion(equalsAssertion(WAITING, "body.status", "Verify status"))
                .execute();
    }

    @Step("management request is approved")
    public void managementRequestIsApproved() {
        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.approveManagementRequest(managementRequestId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(APPROVED, "body.status", "Verify status"))
                .execute();
    }

    @Step("member {string} is registered as security server {string} client")
    public void memberIsRegisteredAsSecurityServerClient(String memberId, String securityServerId) {
        final ClientRegistrationRequestDto managementRequest = new ClientRegistrationRequestDto();
        managementRequest.setType(CLIENT_REGISTRATION_REQUEST);
        managementRequest.setOrigin(SECURITY_SERVER);
        managementRequest.setSecurityServerId(securityServerId);
        managementRequest.setClientId(memberId);

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
        this.managementRequestId = response.getBody().getId();

        validate(response)
                .assertion(equalsStatusCodeAssertion(ACCEPTED))
                .assertion(equalsAssertion(WAITING, "body.status", "Verify status"))
                .execute();
    }

    @Step("owner of security server {string} can be changed to {string}")
    public void ownedOfSecurityServerCanBeSetToMember(String securityServerId, String memberId) {
        final OwnerChangeRequestDto managementRequest = new OwnerChangeRequestDto();
        managementRequest.setType(OWNER_CHANGE_REQUEST);
        managementRequest.setOrigin(SECURITY_SERVER);
        managementRequest.setSecurityServerId(securityServerId);
        managementRequest.setClientId(memberId);

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
        this.managementRequestId = response.getBody().getId();

        validate(response)
                .assertion(equalsStatusCodeAssertion(ACCEPTED))
                .assertion(equalsAssertion(WAITING, "body.status", "Verify status"))
                .execute();
    }

}
