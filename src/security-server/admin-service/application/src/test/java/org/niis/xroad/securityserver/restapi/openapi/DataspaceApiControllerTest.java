/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.openapi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.proto.dto.AgreementTokenKeyStatus;
import org.niis.xroad.restapi.auth.GrantedAuthorityMapper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.exceptions.ExceptionTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class DataspaceApiControllerTest extends AbstractApiControllerTestContext {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private DataspaceApiController dataspaceApiController;
    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @AfterEach
    void clearRole() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @WithMockUser(authorities = "VIEW_AGREEMENT_TOKEN_KEY")
    void shouldReturnAgreementTokenKeyStatus() {
        when(proxyRpcClient.getAgreementTokenKeyStatus())
                .thenReturn(new AgreementTokenKeyStatus(true, "1", CREATED_AT, 2, "2"));

        var response = dataspaceApiController.getAgreementTokenKeyStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var status = response.getBody();
        assertThat(status.getAvailable()).isTrue();
        assertThat(status.getActiveKeyId()).isEqualTo("1");
        assertThat(status.getActiveKeyCreatedAt()).isEqualTo(CREATED_AT.atOffset(ZoneOffset.UTC));
        assertThat(status.getRetainedKeyCount()).isEqualTo(2);
        assertThat(status.getPendingKeyId()).isEqualTo("2");
    }

    @Test
    @WithMockUser(authorities = "VIEW_AGREEMENT_TOKEN_KEY")
    void shouldReturnUnavailableAgreementTokenKeyStatus() {
        when(proxyRpcClient.getAgreementTokenKeyStatus())
                .thenReturn(new AgreementTokenKeyStatus(false, null, null, 0, null));

        var status = dataspaceApiController.getAgreementTokenKeyStatus().getBody();

        assertThat(status.getAvailable()).isFalse();
        assertThat(status.getActiveKeyId()).isNull();
        assertThat(status.getPendingKeyId()).isNull();
    }

    @Test
    @WithMockUser(authorities = "ROTATE_AGREEMENT_TOKEN_KEY")
    void shouldReturnNewKeyIdOnRotate() {
        when(proxyRpcClient.rotateAgreementTokenSigningKey()).thenReturn("3");

        var response = dataspaceApiController.rotateAgreementTokenKey();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getKeyId()).isEqualTo("3");
    }

    @Test
    @WithMockUser(authorities = "VIEW_AGREEMENT_TOKEN_KEY")
    void viewPermissionDoesNotAllowRotate() {
        assertThatThrownBy(() -> dataspaceApiController.rotateAgreementTokenKey())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROTATE_AGREEMENT_TOKEN_KEY")
    void rotatePermissionDoesNotAllowViewingStatus() {
        assertThatThrownBy(() -> dataspaceApiController.getAgreementTokenKeyStatus())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void observerCanReadStatusButNotRotate() {
        when(proxyRpcClient.getAgreementTokenKeyStatus())
                .thenReturn(new AgreementTokenKeyStatus(true, "1", CREATED_AT, 1, null));
        actAs(Role.XROAD_SECURITYSERVER_OBSERVER);

        assertThat(dataspaceApiController.getAgreementTokenKeyStatus().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThatThrownBy(() -> dataspaceApiController.rotateAgreementTokenKey())
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"XROAD_SECURITY_OFFICER", "XROAD_SYSTEM_ADMINISTRATOR"})
    void securityOfficerAndSystemAdministratorCanReadStatusAndRotate(Role role) {
        when(proxyRpcClient.getAgreementTokenKeyStatus())
                .thenReturn(new AgreementTokenKeyStatus(true, "1", CREATED_AT, 1, null));
        when(proxyRpcClient.rotateAgreementTokenSigningKey()).thenReturn("2");
        actAs(role);

        assertThat(dataspaceApiController.getAgreementTokenKeyStatus().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataspaceApiController.rotateAgreementTokenKey().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"XROAD_REGISTRATION_OFFICER", "XROAD_SERVICE_ADMINISTRATOR"})
    void rolesWithoutTheKeyPermissionsAreRefused(Role role) {
        actAs(role);

        assertThatThrownBy(() -> dataspaceApiController.getAgreementTokenKeyStatus())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dataspaceApiController.rotateAgreementTokenKey())
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"AGREEMENT_TOKEN_KEY_NOT_AVAILABLE", "AGREEMENT_TOKEN_KEY_STORE_FAILED"})
    @WithMockUser(authorities = "ROTATE_AGREEMENT_TOKEN_KEY")
    void rotateErrorsReachTheCallerWithTheirErrorCodeAndANon500Status(ErrorCode errorCode) {
        when(proxyRpcClient.rotateAgreementTokenSigningKey())
                .thenThrow(XrdRuntimeException.systemException(errorCode).details("proxy failure").build());

        Exception thrown = null;
        try {
            dataspaceApiController.rotateAgreementTokenKey();
        } catch (Exception e) {
            thrown = e;
        }

        var response = exceptionTranslator.toResponseEntity(thrown, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getError().getCode()).isEqualTo("core." + errorCode.code());
    }

    @Test
    void rotateIsRecordedAsTheRotateAgreementTokenKeyAuditEvent() throws NoSuchMethodException {
        var rotate = DataspaceApiController.class.getMethod("rotateAgreementTokenKey");

        assertThat(rotate.getAnnotation(AuditEventMethod.class).event())
                .isEqualTo(RestApiAuditEvent.ROTATE_AGREEMENT_TOKEN_KEY);
    }

    private static void actAs(Role role) {
        var authorities = new GrantedAuthorityMapper().getAuthorities(List.of(role));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "password", List.copyOf(authorities)));
    }
}
