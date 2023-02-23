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
import org.niis.xroad.cs.openapi.model.ClientIdDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDetailsDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.cs.openapi.model.SecurityServerIdDto;
import org.niis.xroad.cs.test.api.FeignManagementRequestsApi;
import org.niis.xroad.cs.test.utils.CertificateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto.SECURITY_SERVER;
import static org.niis.xroad.cs.openapi.model.ManagementRequestStatusDto.APPROVED;
import static org.niis.xroad.cs.openapi.model.ManagementRequestStatusDto.WAITING;
import static org.niis.xroad.cs.openapi.model.ManagementRequestStatusDto.fromValue;
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

    private byte[] authenticationCertificate;

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("new security server {string} authentication certificate registered with origin {string}")
    public void newSecurityServerRegistered(String securityServerId, String origin) throws Exception {
        generateAuthenticationCertificate();
        final String[] idParts = StringUtils.split(securityServerId, ':');
        final var managementRequest = new AuthenticationCertificateRegistrationRequestDto();
        managementRequest.setServerAddress("security-server-" + idParts[3]);
        managementRequest.setSecurityServerId(toSecurityServerIdDto(securityServerId));
        managementRequest.setAuthenticationCertificate(authenticationCertificate);
        managementRequest.setType(AUTH_CERT_REGISTRATION_REQUEST);
        managementRequest.setOrigin(ManagementRequestOriginDto.valueOf(origin));

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
        managementRequest.setSecurityServerId(toSecurityServerIdDto(securityServerId));
        managementRequest.setClientId(toClientIdDto(memberId));

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
        managementRequest.setSecurityServerId(toSecurityServerIdDto(securityServerId));
        managementRequest.setClientId(toClientIdDto(memberId));

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
        this.managementRequestId = response.getBody().getId();

        validate(response)
                .assertion(equalsStatusCodeAssertion(ACCEPTED))
                .assertion(equalsAssertion(WAITING, "body.status", "Verify status"))
                .execute();
    }

    @Step("management request is with status {string}")
    public void checkManagementRequestStatus(String status) {
        final ResponseEntity<ManagementRequestDetailsDto> response =
                managementRequestsApi.getManagementRequest(managementRequestId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(fromValue(status), "body.status", "Verify status"))
                .execute();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("details of management request can be retrieved for security server {string}")
    public void getManagementRequestDetails(String securityServerId) {
        final ResponseEntity<ManagementRequestDetailsDto> response =
                managementRequestsApi.getManagementRequest(managementRequestId);
        final String[] securityServerIdParts = StringUtils.split(securityServerId, ':');

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion("security-server-" + securityServerIdParts[3],
                        "body.securityServerAddress", "Verify server address"))
                .assertion(equalsAssertion(securityServerIdParts[0], "body.securityServerId.instanceId",
                        "Verify server instance"))
                .assertion(equalsAssertion(securityServerIdParts[1], "body.securityServerId.memberClass",
                        "Verify server member class"))
                .assertion(equalsAssertion(securityServerIdParts[2], "body.securityServerId.memberCode",
                        "Verify server member code"))
                .assertion(equalsAssertion(AUTH_CERT_REGISTRATION_REQUEST.name(), "body.type",
                        "Verify request type"))
                .assertion(equalsAssertion(SECURITY_SERVER, "body.origin",
                        "Verify request origin"))
                .execute();
    }

    private void generateAuthenticationCertificate() throws Exception {
        if (authenticationCertificate == null) {
            authenticationCertificate = CertificateUtils.generateAuthCert();
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private SecurityServerIdDto toSecurityServerIdDto(String securityServerId) {
        final String[] securityServerIdParts = StringUtils.split(securityServerId, ':');
        var clientIdDto = new SecurityServerIdDto();
        clientIdDto.instanceId(securityServerIdParts[0]);
        return clientIdDto.memberClass(securityServerIdParts[1])
                .memberCode(securityServerIdParts[2]).serverCode(securityServerIdParts[3]);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private ClientIdDto toClientIdDto(String clientId) {
        final String[] clientIdParts = StringUtils.split(clientId, ':');
        var clientIdDto = new ClientIdDto();
        clientIdDto.instanceId(clientIdParts[0]);
        return clientIdDto.memberClass(clientIdParts[1])
                .memberCode(clientIdParts[2]).subsystemCode(clientIdParts[3]);
    }
}
