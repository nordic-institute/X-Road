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
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDetailedViewDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.cs.test.api.FeignManagementRequestsApi;
import org.niis.xroad.cs.test.utils.CertificateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static com.nortal.test.asserts.Assertions.equalsAssertion;
import static org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto.SECURITY_SERVER;
import static org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto.valueOf;
import static org.niis.xroad.cs.openapi.model.ManagementRequestStatusDto.fromValue;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.AUTH_CERT_DELETION_REQUEST;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.CLIENT_DELETION_REQUEST;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.OWNER_CHANGE_REQUEST;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;


@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class ManagementRequestsApiStepDefs extends BaseStepDefs {

    @Autowired
    private FeignManagementRequestsApi managementRequestsApi;

    private Integer managementRequestId;
    private Map<String, byte[]> certificates = new HashMap<>();

    @Step("new security server {string} authentication certificate registered with origin {string} and approved")
    public void newSecurityServerRegisteredAndApproved(String securityServerId, String origin) {
        newSecurityServerRegistered(securityServerId, origin);
        managementRequestIsApproved();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("new security server {string} authentication certificate registered with origin {string}")
    public void newSecurityServerRegistered(String securityServerId, String origin) {
        final String[] idParts = StringUtils.split(securityServerId, ':');
        final var managementRequest = new AuthenticationCertificateRegistrationRequestDto();
        managementRequest.setServerAddress("security-server-address-" + idParts[3]);
        managementRequest.setSecurityServerId(securityServerId);
        managementRequest.setAuthenticationCertificate(getExistingOrCreateNewCertificate(securityServerId));
        managementRequest.setType(AUTH_CERT_REGISTRATION_REQUEST);
        managementRequest.setOrigin(ManagementRequestOriginDto.valueOf(origin));

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
        this.managementRequestId = response.getBody().getId();
    }

    @Step("authentication certificate of {string} is deleted")
    public void deleteAuthCert(String serverId) {
        final var managementRequest = new AuthenticationCertificateDeletionRequestDto();
        managementRequest.setOrigin(SECURITY_SERVER);
        managementRequest.setType(AUTH_CERT_DELETION_REQUEST);
        managementRequest.setSecurityServerId(serverId);
        managementRequest.setAuthenticationCertificate(getExistingOrCreateNewCertificate(serverId));

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
        this.managementRequestId = response.getBody().getId();

        validate(response)
                .assertion(equalsStatusCodeAssertion(ACCEPTED))
                .execute();
    }

    @Step("client {string} is registered as security server {string} client from {string}")
    public void memberIsRegisteredAsSecurityServerClient(String memberId, String securityServerId, String origin) {
        final ClientRegistrationRequestDto managementRequest = new ClientRegistrationRequestDto();
        managementRequest.setType(CLIENT_REGISTRATION_REQUEST);
        managementRequest.setOrigin(valueOf(origin));
        managementRequest.setSecurityServerId(securityServerId);
        managementRequest.setClientId(memberId);

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
        this.managementRequestId = response.getBody().getId();
    }

    @Step("member {string} is deleted as security server {string} client")
    public void memberIsDeletedAsSecurityServerClient(String memberId, String securityServerId) {
        final ClientDeletionRequestDto managementRequest = new ClientDeletionRequestDto();
        managementRequest.setType(CLIENT_DELETION_REQUEST);
        managementRequest.setOrigin(SECURITY_SERVER);
        managementRequest.setSecurityServerId(securityServerId);
        managementRequest.setClientId(memberId);

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(managementRequest);
        this.managementRequestId = response.getBody().getId();

        validate(response)
                .assertion(equalsStatusCodeAssertion(ACCEPTED))
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
                .execute();
    }

    @Step("management request is approved")
    public void managementRequestIsApproved() {
        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.approveManagementRequest(managementRequestId);
        this.managementRequestId = response.getBody().getId();

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .execute();
    }

    @Step("management request is declined")
    public void managementRequestIsDeclined() {
        final ResponseEntity<Void> response = managementRequestsApi.revokeManagementRequest(managementRequestId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .execute();
    }

    @Step("management request is with status {string}")
    public void checkManagementRequestStatus(String status) {
        final ResponseEntity<ManagementRequestDetailedViewDto> response =
                managementRequestsApi.getManagementRequest(managementRequestId);

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion(fromValue(status), "body.status", "Verify status"))
                .execute();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("details of management request can be retrieved for security server {string}")
    public void getManagementRequestDetails(String securityServerId) {
        final ResponseEntity<ManagementRequestDetailedViewDto> response =
                managementRequestsApi.getManagementRequest(managementRequestId);
        final String[] securityServerIdParts = StringUtils.split(securityServerId, ':');

        validate(response)
                .assertion(equalsStatusCodeAssertion(OK))
                .assertion(equalsAssertion("security-server-address-" + securityServerIdParts[3],
                        "body.address", "Verify server address"))
                .assertion(equalsAssertion(securityServerIdParts[0], "body.securityServerId.instanceId",
                        "Verify server instance"))
                .assertion(equalsAssertion(securityServerIdParts[1], "body.securityServerId.memberClass",
                        "Verify server member class"))
                .assertion(equalsAssertion(securityServerIdParts[2], "body.securityServerId.memberCode",
                        "Verify server member code"))
                .assertion(equalsAssertion(AUTH_CERT_REGISTRATION_REQUEST, "body.type",
                        "Verify request type"))
                .assertion(equalsAssertion(SECURITY_SERVER, "body.origin",
                        "Verify request origin"))
                .execute();
    }

    @SneakyThrows
    private byte[] getExistingOrCreateNewCertificate(String serverId) {
        if (!certificates.containsKey(serverId)) {
            certificates.put(serverId, CertificateUtils.generateAuthCert("CN=Subject-" + serverId));
        }
        return certificates.get(serverId);
    }
}
