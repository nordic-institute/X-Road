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

package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.core.entity.AuthCertEntity;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FINAL_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_DID_FIXABLE_CONFLICT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MR_INVALID_AUTH_CERTIFICATE;

@ExtendWith(MockitoExtension.class)
class AuthCertServerCheckTest {
    private static final byte[] CERTIFICATE = {1, 2, 3};
    private static final String ADDRESS = "ss0.example.org";

    private final SecurityServerId serverId = SecurityServerId.create("DEV", "COM", "222", "SS0");

    @Mock
    private AuthCertRepository authCertRepository;
    @Mock
    private SecurityServerRepository securityServerRepository;

    @InjectMocks
    private AuthCertServerCheck check;

    @Test
    void returnsTheServerRowIdAndAddressWhenTheCertificateBelongsToTheServer() {
        var server = server(7, "DEV", "222", "SS0");
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(server));
        var cert = certOf(server);
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.of(cert));

        var checked = check.requireServer(serverId, CERTIFICATE);

        assertThat(checked).isEqualTo(new AuthCertServerCheck.CheckedServer(7, ADDRESS));
    }

    @Test
    void ownerChangedIsFixableWhenTheNamedServerIsGoneAndTheCertificateServerHasTheSameInstanceAndServerCode() {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.empty());
        var cert = certOf(server(8, "DEV", "333", "SS0"));
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> check.requireServer(serverId, CERTIFICATE))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getErrorDeviation())
                        .isEqualTo(MR_DID_FIXABLE_CONFLICT.build(SystemDidRegistrationOutcome.OWNER_CHANGED)));
    }

    @Test
    void certificateOfAnotherServerIsFinalWhenTheNamedServerExists() {
        var named = server(7, "DEV", "222", "SS0");
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(named));
        var cert = certOf(server(8, "DEV", "333", "SS0"));
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> check.requireServer(serverId, CERTIFICATE))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getErrorDeviation())
                        .isEqualTo(MR_DID_FINAL_CONFLICT.build(SystemDidRegistrationOutcome.CERTIFICATE_OF_ANOTHER_SERVER)));
    }

    @Test
    void certificateOfAnotherServerIsFinalWhenTheNamedServerIsGoneAndTheServerCodeDiffers() {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.empty());
        var cert = certOf(server(8, "DEV", "222", "SS1"));
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> check.requireServer(serverId, CERTIFICATE))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getErrorDeviation())
                        .isEqualTo(MR_DID_FINAL_CONFLICT.build(SystemDidRegistrationOutcome.CERTIFICATE_OF_ANOTHER_SERVER)));
    }

    @Test
    void certificateOfAnotherServerIsFinalWhenTheNamedServerIsGoneAndTheInstanceDiffers() {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.empty());
        var cert = certOf(server(8, "OTHER", "333", "SS0"));
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> check.requireServer(serverId, CERTIFICATE))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getErrorDeviation())
                        .isEqualTo(MR_DID_FINAL_CONFLICT.build(SystemDidRegistrationOutcome.CERTIFICATE_OF_ANOTHER_SERVER)));
    }

    @Test
    void rejectsAnUnknownCertificateOfAKnownServer() {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(mock(SecurityServerEntity.class)));
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> check.requireServer(serverId, CERTIFICATE))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getErrorDeviation())
                        .isEqualTo(MR_INVALID_AUTH_CERTIFICATE.build()));
    }

    @Test
    void rejectsAnUnknownServerWithAnUnknownCertificate() {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.empty());
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> check.requireServer(serverId, CERTIFICATE)).isInstanceOf(NotFoundException.class);
    }

    private static SecurityServerEntity server(int id, String instance, String ownerCode, String serverCode) {
        var owner = new XRoadMemberEntity("Member " + ownerCode, ClientId.Conf.create(instance, "COM", ownerCode),
                new MemberClassEntity("COM", "Companies"));
        var server = new SecurityServerEntity(owner, serverCode);
        ReflectionTestUtils.setField(server, "id", id);
        server.setAddress(ADDRESS);
        return server;
    }

    private static AuthCertEntity certOf(SecurityServerEntity server) {
        var cert = mock(AuthCertEntity.class);
        when(cert.getSecurityServer()).thenReturn(server);
        return cert;
    }
}
