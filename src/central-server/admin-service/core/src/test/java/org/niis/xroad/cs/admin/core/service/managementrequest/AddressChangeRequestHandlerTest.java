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

import io.vavr.control.Option;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.AddressChangeRequest;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.core.entity.AddressChangeRequestEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.RequestMapper;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;

@ExtendWith(MockitoExtension.class)
class AddressChangeRequestHandlerTest {
    private static final String ADDRESS = "server.address";

    @Mock
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Mock
    private RequestRepository<AddressChangeRequestEntity> addressChangeRequests;
    @Mock
    private SecurityServerRepository securityServerRepository;
    @Mock
    private RequestMapper requestMapper;

    private final SecurityServerId securityServerId = SecurityServerId.create("INSTANCE", "MEMBER_CLASS", "MEMBER_CODE", "SERVER-CODE");

    @InjectMocks
    private AddressChangeRequestHandler handler;

    @Captor
    private ArgumentCaptor<AddressChangeRequestEntity> argumentCaptor;

    @Test
    void canAutoApprove() {
        var request = new AddressChangeRequest(SECURITY_SERVER, securityServerId, ADDRESS);
        assertThat(handler.canAutoApprove(request)).isFalse();
    }

    @Test
    void addInvalidAddress() {
        assertThatThrownBy(() -> handler.add(new AddressChangeRequest(SECURITY_SERVER, securityServerId, null)))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Invalid server address");
        assertThatThrownBy(() -> handler.add(new AddressChangeRequest(SECURITY_SERVER, securityServerId, "")))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Invalid server address");
    }

    @Test
    void add() {
        var request = new AddressChangeRequest(SECURITY_SERVER, securityServerId, ADDRESS);

        SecurityServerEntity securityServerEntity = mock(SecurityServerEntity.class);
        AddressChangeRequestEntity requestEntity = mock(AddressChangeRequestEntity.class);

        SecurityServerIdEntity securityServerIdEntity = mock(SecurityServerIdEntity.class);
        when(serverIds.findOne(SecurityServerIdEntity.create(securityServerId))).thenReturn(securityServerIdEntity);
        when(securityServerRepository.findBy(securityServerIdEntity)).thenReturn(Option.of(securityServerEntity));
        when(securityServerEntity.getAddress()).thenReturn("ss.old");
        when(addressChangeRequests.save(argumentCaptor.capture())).thenReturn(requestEntity);

        handler.add(request);

        verify(securityServerEntity).setAddress(ADDRESS);
        assertThat(argumentCaptor.getValue().getComments()).isEqualTo("Changing from 'ss.old'.");
        verify(requestMapper).toDto(requestEntity);
    }

}
