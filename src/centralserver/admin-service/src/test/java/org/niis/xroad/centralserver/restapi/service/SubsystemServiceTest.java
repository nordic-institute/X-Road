/**
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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClient;
import org.niis.xroad.centralserver.restapi.entity.SecurityServerClientName;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.entity.SubsystemId;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerClientNameRepository;
import org.niis.xroad.centralserver.restapi.repository.SubsystemRepository;
import org.niis.xroad.centralserver.restapi.service.exception.EntityExistsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.SUBSYSTEM_EXISTS;

@ExtendWith(MockitoExtension.class)
public class SubsystemServiceTest implements WithInOrder {

    @Mock
    private SubsystemRepository subsystemRepository;

    @Mock
    private SecurityServerClientNameRepository securityServerClientNameRepository;

    @InjectMocks
    private SubsystemService subsystemService;

    @Nested
    @DisplayName("add(Client clientDto)")
    class AddMethod implements WithInOrder {

        @Mock
        private Subsystem subsystem;
        private SubsystemId subsystemId = SubsystemId.create(
                "TEST", "CLASS", "MEMBER", "SUBSYSTEM"
        );

        @Test
        @DisplayName("should create client when not already present")
        void shouldCreateClientWhenNotAlreadyPresent() {
            Subsystem persistedSubsystem = mock(Subsystem.class);
            XRoadMember member = mock(XRoadMember.class);
            String memberName = "subsystem's member name";
            doReturn(member).when(persistedSubsystem).getXroadMember();
            doReturn(memberName).when(member).getName();
            doReturn(subsystemId).when(subsystem).getIdentifier();
            doReturn(subsystemId).when(persistedSubsystem).getIdentifier();
            doReturn(Option.none()).when(subsystemRepository).findOneBy(subsystemId);
            doReturn(persistedSubsystem).when(subsystemRepository).save(subsystem);
            SecurityServerClient result = subsystemService.add(subsystem);

            assertEquals(persistedSubsystem, result);
            ArgumentCaptor<SecurityServerClientName> captor = ArgumentCaptor.forClass(SecurityServerClientName.class);
            inOrder(persistedSubsystem).verify(inOrder -> {
                inOrder.verify(subsystemRepository).findOneBy(subsystemId);
                inOrder.verify(subsystemRepository).save(subsystem);
                inOrder.verify(securityServerClientNameRepository).save(captor.capture());
            });
            assertThat(captor.getValue().getName()).isEqualTo(memberName);
            assertThat(captor.getValue().getIdentifier()).isEqualTo(subsystemId);
        }

        @Test
        @DisplayName("should not create client when already present")
        void shouldNotCreateClientWhenAlreadyPresent() {
            SecurityServerClient presentSecurityServerClient = mock(SecurityServerClient.class);
            doReturn(subsystemId).when(subsystem).getIdentifier();
            doReturn(Option.of(presentSecurityServerClient)).when(subsystemRepository).findOneBy(subsystemId);
            String clientIdentifier = subsystemId.toShortString();

            Executable testable = () -> subsystemService.add(subsystem);

            EntityExistsException exception = assertThrows(EntityExistsException.class, testable);
            assertEquals(SUBSYSTEM_EXISTS.getDescription(), exception.getMessage());
            assertThat(exception.getErrorDeviation().getMetadata())
                    .hasSize(1)
                    .containsExactly(clientIdentifier);
            inOrder(presentSecurityServerClient).verify(inOrder -> {
                inOrder.verify(subsystem).getIdentifier();
                inOrder.verify(subsystemRepository).findOneBy(subsystemId);
                inOrder.verify(subsystem).getIdentifier();
            });
        }
    }

}
