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

package org.niis.xroad.cs.test.ui.glue;

import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestOriginDto;
import org.niis.xroad.cs.test.ui.api.FeignManagementRequestsApi;
import org.niis.xroad.cs.test.ui.utils.CertificateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.CLIENT_REGISTRATION_REQUEST;
import static org.niis.xroad.cs.test.ui.constants.Constants.CN_SUBJECT_PREFIX;
import static org.niis.xroad.cs.test.ui.constants.Constants.INSTANCE_IDENTIFIER;
import static org.niis.xroad.cs.test.ui.constants.Constants.MEMBER_CLASS_CODE;
import static org.niis.xroad.cs.test.ui.constants.Constants.getSecurityServerId;
import static org.niis.xroad.cs.test.ui.glue.BaseUiStepDefs.StepDataKey.MANAGEMENT_REQUEST_ID;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class TestDataCreationStepDefs extends BaseUiStepDefs {
    private static final String SECURITY_SERVER_ADDRESS_PREF = "security-server-address-";
    @Autowired
    private FeignManagementRequestsApi managementRequestsApi;
    private final Map<String, byte[]> certificates = new HashMap<>();

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("a new security server {} with authentication certificate is registered with owner code {}")
    public void newAuthenticationCertificateRegistered(String securityServerCode, String ownerCode) {
        final var securityServerId = getSecurityServerId(securityServerCode, ownerCode);
        final var authenticationCertificateRegistrationRequest = new AuthenticationCertificateRegistrationRequestDto();
        authenticationCertificateRegistrationRequest.setServerAddress(SECURITY_SERVER_ADDRESS_PREF + securityServerCode);
        authenticationCertificateRegistrationRequest.setSecurityServerId(securityServerId);
        authenticationCertificateRegistrationRequest.setAuthenticationCertificate(getExistingOrCreateNewCertificate(securityServerCode));
        authenticationCertificateRegistrationRequest.setType(AUTH_CERT_REGISTRATION_REQUEST);
        authenticationCertificateRegistrationRequest.setOrigin(ManagementRequestOriginDto.CENTER);

        final ResponseEntity<ManagementRequestDto> response =
                managementRequestsApi.addManagementRequest(authenticationCertificateRegistrationRequest);
        final var managementRequestId = Objects.requireNonNull(response.getBody()).getId();
        putStepData(MANAGEMENT_REQUEST_ID, managementRequestId);
    }

    @Step("new authentication certificate for a security server {} is registered with owner code {}")
    public void addAuthenticationCertificateRegistered(String securityServerCode, String ownerCode) {
        final var securityServerId = getSecurityServerId(securityServerCode, ownerCode);
        final var authenticationCertificateRegistrationRequest = new AuthenticationCertificateRegistrationRequestDto();
        authenticationCertificateRegistrationRequest.setServerAddress(SECURITY_SERVER_ADDRESS_PREF + securityServerCode);
        authenticationCertificateRegistrationRequest.setSecurityServerId(securityServerId);
        authenticationCertificateRegistrationRequest.setAuthenticationCertificate(createNewCertificateForCA2(securityServerCode));
        authenticationCertificateRegistrationRequest.setType(AUTH_CERT_REGISTRATION_REQUEST);
        authenticationCertificateRegistrationRequest.setOrigin(ManagementRequestOriginDto.CENTER);

        final ResponseEntity<ManagementRequestDto> response =
                managementRequestsApi.addManagementRequest(authenticationCertificateRegistrationRequest);
        final var managementRequestId = Objects.requireNonNull(response.getBody()).getId();
        putStepData(MANAGEMENT_REQUEST_ID, managementRequestId);
    }

    @Step("a client with code {} and subsystem code {} is registered in security server {} with owner code {}")
    public void memberIsRegisteredAsSecurityServerClient(String clientCode,
                                                         String subsystemCode,
                                                         String securityServerCode,
                                                         String ownerCode) {
        final var securityServerId = getSecurityServerId(securityServerCode, ownerCode);
        final var clientId = String.join(":", INSTANCE_IDENTIFIER, MEMBER_CLASS_CODE, clientCode, subsystemCode);
        final var clientRegistrationRequest = new ClientRegistrationRequestDto();
        clientRegistrationRequest.setType(CLIENT_REGISTRATION_REQUEST);
        clientRegistrationRequest.setOrigin(ManagementRequestOriginDto.CENTER);
        clientRegistrationRequest.setSecurityServerId(securityServerId);
        clientRegistrationRequest.setClientId(clientId);

        final ResponseEntity<ManagementRequestDto> response = managementRequestsApi.addManagementRequest(clientRegistrationRequest);
        final var managementRequestId = Objects.requireNonNull(response.getBody()).getId();
        putStepData(MANAGEMENT_REQUEST_ID, managementRequestId);
    }

    @SneakyThrows
    private byte[] getExistingOrCreateNewCertificate(String serverId) {
        if (!certificates.containsKey(serverId)) {
            certificates.put(serverId, CertificateUtils.generateAuthCert(CN_SUBJECT_PREFIX + serverId));
        }
        return certificates.get(serverId);
    }

    @SneakyThrows
    private byte[] createNewCertificateForCA2(String serverId) {
        return CertificateUtils.generateAuthCertForCA2(CN_SUBJECT_PREFIX + "2-" + serverId);
    }
}
