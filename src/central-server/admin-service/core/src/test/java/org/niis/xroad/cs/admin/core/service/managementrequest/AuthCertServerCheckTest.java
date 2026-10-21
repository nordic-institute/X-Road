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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.core.entity.AuthCertEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthCertServerCheckTest {
    private static final byte[] CERTIFICATE = {1, 2, 3};

    private final SecurityServerId serverId = SecurityServerId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SERVER-CODE");

    @Mock
    private AuthCertRepository authCertRepository;
    @Mock
    private SecurityServerRepository securityServerRepository;

    @InjectMocks
    private AuthCertServerCheck check;

    @Test
    void returnsTheServerRowIdWhenTheCertificateBelongsToTheServer() {
        var server = server(7);
        var cert = certOf(server);
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(server));
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.of(cert));

        assertThat(check.requireServerRowId(serverId, CERTIFICATE)).isEqualTo(7);
    }

    @Test
    void rejectsACertificateOfAnotherServer() {
        var server = server(7);
        var cert = certOf(server(8));
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(server));
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.of(cert));

        assertThatThrownBy(() -> check.requireServerRowId(serverId, CERTIFICATE)).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsAnUnknownCertificate() {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.of(mock(SecurityServerEntity.class)));
        when(authCertRepository.findByCert(CERTIFICATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> check.requireServerRowId(serverId, CERTIFICATE)).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsAnUnknownServer() {
        when(securityServerRepository.findBy(serverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> check.requireServerRowId(serverId, CERTIFICATE)).isInstanceOf(NotFoundException.class);
    }

    private static SecurityServerEntity server(int id) {
        var server = mock(SecurityServerEntity.class);
        when(server.getId()).thenReturn(id);
        return server;
    }

    private static AuthCertEntity certOf(SecurityServerEntity server) {
        var cert = mock(AuthCertEntity.class);
        when(cert.getSecurityServer()).thenReturn(server);
        return cert;
    }
}
