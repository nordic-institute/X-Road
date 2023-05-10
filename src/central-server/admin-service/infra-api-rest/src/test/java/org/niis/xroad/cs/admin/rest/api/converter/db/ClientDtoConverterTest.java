/**
 * The MIT License
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
package org.niis.xroad.cs.admin.rest.api.converter.db;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SecurityServerClient;
import org.niis.xroad.cs.admin.api.domain.Subsystem;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.rest.api.converter.AbstractDtoConverterTest;
import org.niis.xroad.cs.openapi.model.ClientDto;
import org.niis.xroad.cs.openapi.model.ClientIdDto;
import org.niis.xroad.cs.openapi.model.XRoadIdDto;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_NOT_FOUND;

@ExtendWith(MockitoExtension.class)
public class ClientDtoConverterTest extends AbstractDtoConverterTest implements WithInOrder {

    @Mock
    private MemberId memberClientId;
    @Mock
    private SubsystemId subsystemClientId;
    @Mock
    private ClientIdDto clientIdDto;
    @Mock
    private XRoadMember xRoadMember;
    @Mock
    private Subsystem subsystem;

    @Mock
    private MemberService memberService;
    @Mock
    private MemberClassService memberClassService;

    @Mock
    private ClientIdDtoConverter clientIdDtoConverter;

    @InjectMocks
    private ClientDtoConverter converter;

    @Nested
    @DisplayName("toDto(Client source)")
    public class ToDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should convert XRoadMember")
        public void shouldConvertXRoadMember() {
            doReturn(memberClientId).when(xRoadMember).getIdentifier();
            doReturn(clientIdDto).when(clientIdDtoConverter).toDto(memberClientId);
            doReturn(MEMBER_NAME).when(xRoadMember).getName();

            ClientDto clientDto = converter.toDto(xRoadMember);

            assertNotNull(clientDto);
            assertEquals(clientIdDto, clientDto.getClientId());
            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMember).getIdentifier();
                inOrder.verify(clientIdDtoConverter).toDto(memberClientId);
                inOrder.verify(xRoadMember).getName();
            });
        }

        @Test
        @DisplayName("should convert XRoadMember without timestamps")
        public void shouldConvertXRoadMemberWithoutTimestamps() {
            doReturn(memberClientId).when(xRoadMember).getIdentifier();
            doReturn(clientIdDto).when(clientIdDtoConverter).toDto(memberClientId);
            doReturn(MEMBER_NAME).when(xRoadMember).getName();

            ClientDto clientDto = converter.toDto(xRoadMember);

            assertNotNull(clientDto);
            assertEquals(clientIdDto, clientDto.getClientId());
            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadMember).getIdentifier();
                inOrder.verify(clientIdDtoConverter).toDto(memberClientId);
                inOrder.verify(xRoadMember).getName();
            });
        }

        @Test
        @DisplayName("should convert Subsystem")
        public void shouldConvertSubsystem() {
            doReturn(subsystemClientId).when(subsystem).getIdentifier();
            doReturn(clientIdDto).when(clientIdDtoConverter).toDto(subsystemClientId);

            ClientDto clientDto = converter.toDto(subsystem);

            assertNotNull(clientDto);
            assertEquals(clientIdDto, clientDto.getClientId());
            inOrder().verify(inOrder -> {
                inOrder.verify(subsystem).getIdentifier();
                inOrder.verify(clientIdDtoConverter).toDto(subsystemClientId);
            });
        }

        @Test
        @DisplayName("should convert Subsystem without timestamps")
        public void shouldConvertSubsystemWithoutTimestamps() {
            doReturn(subsystemClientId).when(subsystem).getIdentifier();
            doReturn(clientIdDto).when(clientIdDtoConverter).toDto(subsystemClientId);

            ClientDto clientDto = converter.toDto(subsystem);

            assertNotNull(clientDto);
            assertEquals(clientIdDto, clientDto.getClientId());
            inOrder().verify(inOrder -> {
                inOrder.verify(subsystem).getIdentifier();
                inOrder.verify(clientIdDtoConverter).toDto(subsystemClientId);
            });
        }

        @Test
        @DisplayName("should fail if unknown SecurityServerClient provided")
        public void shouldFailIfUnknownSecurityServerClientProvided() {
            SecurityServerClient unknownSecurityServerClient = mock(SecurityServerClient.class);
            doReturn(subsystemClientId).when(unknownSecurityServerClient).getIdentifier();
            doReturn(clientIdDto).when(clientIdDtoConverter).toDto(subsystemClientId);

            ThrowingCallable testable = () -> converter.toDto(unknownSecurityServerClient);

            assertThatThrownBy(testable)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Unknown client type: " + SecurityServerClient.class.getName());
            inOrder(unknownSecurityServerClient).verify(inOrder -> {
                inOrder.verify(unknownSecurityServerClient).getIdentifier();
                inOrder.verify(clientIdDtoConverter).toDto(subsystemClientId);
            });
        }
    }

    @Nested
    @DisplayName("fromDto(ClientDto source)")
    public class FromDtoMethod implements WithInOrder {

        @Mock
        private ClientDto clientDto;
        @Mock
        private MemberClass memberClass;

        @Test
        @DisplayName("should create new XRoadMember entity if missing")
        public void shouldCreateNewXRoadMemberEntity() {
            doReturn(clientIdDto).when(clientDto).getClientId();
            doReturn(memberClientId).when(clientIdDtoConverter).fromDto(clientIdDto);
            doReturn(XRoadIdDto.TypeEnum.MEMBER).when(clientIdDto).getType();
            doReturn(MEMBER_CLASS_CODE).when(clientIdDto).getMemberClass();
            doReturn(Optional.of(memberClass)).when(memberClassService).findByCode(MEMBER_CLASS_CODE);
            doReturn(MEMBER_NAME).when(clientDto).getMemberName();
            doReturn(MEMBER_CLASS_CODE).when(memberClientId).getMemberClass();
            doReturn(MEMBER_CLASS_CODE).when(memberClass).getCode();
            doReturn(MEMBER_CLASS_CODE).when(memberClientId).getMemberCode();

            SecurityServerClient converted = converter.fromDto(clientDto);

            assertThat(converted)
                    .isNotNull()
                    .isInstanceOfSatisfying(XRoadMember.class, actualXRoadMember -> {
                        assertEquals(MEMBER_NAME, actualXRoadMember.getName());
                        assertEquals(memberClientId, actualXRoadMember.getIdentifier());
                        assertEquals(memberClass, actualXRoadMember.getMemberClass());
                    });
            inOrder().verify(inOrder -> {
                inOrder.verify(clientDto).getClientId();
                inOrder.verify(clientIdDtoConverter).fromDto(clientIdDto);
                inOrder.verify(clientIdDto).getType();
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(memberClassService).findByCode(MEMBER_CLASS_CODE);
                inOrder.verify(clientDto).getMemberName();
                inOrder.verify(memberClientId).getMemberClass();
                inOrder.verify(memberClass).getCode();
                inOrder.verify(memberClientId).getMemberCode();
            });
        }

        @Test
        @DisplayName("should fail creating new XRoadMember entity if MemberCLass entity missing")
        public void shouldFailCreateNewXRoadMemberEntityIfMemberClassEntityMissing() {
            doReturn(clientIdDto).when(clientDto).getClientId();
            doReturn(memberClientId).when(clientIdDtoConverter).fromDto(clientIdDto);
            doReturn(XRoadIdDto.TypeEnum.MEMBER).when(clientIdDto).getType();
            doReturn(MEMBER_CLASS_CODE).when(clientIdDto).getMemberClass();
            doReturn(Optional.empty()).when(memberClassService).findByCode(MEMBER_CLASS_CODE);

            ThrowingCallable testable = () -> converter.fromDto(clientDto);

            assertThatThrownBy(testable)
                    .usingRecursiveComparison()
                    .isEqualTo(new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", MEMBER_CLASS_CODE));
            inOrder().verify(inOrder -> {
                inOrder.verify(clientDto).getClientId();
                inOrder.verify(clientIdDtoConverter).fromDto(clientIdDto);
                inOrder.verify(clientIdDto).getType();
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(memberClassService).findByCode(MEMBER_CLASS_CODE);
            });
        }

        @Test
        @DisplayName("should create new Subsystem entity if missing")
        public void shouldCreateNewSubsystemEntityIfMissing() {
            doReturn(clientIdDto).when(clientDto).getClientId();
            doReturn(subsystemClientId).when(clientIdDtoConverter).fromDto(clientIdDto);
            doReturn(XRoadIdDto.TypeEnum.SUBSYSTEM).when(clientIdDto).getType();
            doReturn(memberClientId).when(subsystemClientId).getMemberId();
            doReturn(Option.of(xRoadMember)).when(memberService).findMember(memberClientId);
            doReturn(INSTANCE_ID).when(subsystemClientId).getXRoadInstance();
            doReturn(MEMBER_CLASS_CODE).when(subsystemClientId).getMemberClass();
            doReturn(MEMBER_CODE).when(subsystemClientId).getMemberCode();
            doReturn(SUBSYSTEM_CODE).when(subsystemClientId).getSubsystemCode();
            doReturn(memberClientId).when(xRoadMember).getIdentifier();
            doReturn(true).when(subsystemClientId).subsystemContainsMember(memberClientId);

            SecurityServerClient converted = converter.fromDto(clientDto);

            assertThat(converted)
                    .isNotNull()
                    .isInstanceOfSatisfying(Subsystem.class, actualSubsystem -> {
                        assertEquals(xRoadMember, actualSubsystem.getXroadMember());
                        assertEquals(SubsystemId.create(INSTANCE_ID,
                                MEMBER_CLASS_CODE,
                                MEMBER_CODE,
                                SUBSYSTEM_CODE), actualSubsystem.getIdentifier());
                        assertEquals(SUBSYSTEM_CODE, actualSubsystem.getSubsystemCode());
                    });
            inOrder().verify(inOrder -> {
                inOrder.verify(clientDto).getClientId();
                inOrder.verify(clientIdDtoConverter).fromDto(clientIdDto);
                inOrder.verify(clientIdDto).getType();
                inOrder.verify(subsystemClientId).getMemberId();
                inOrder.verify(memberService).findMember(memberClientId);
                inOrder.verify(subsystemClientId).getXRoadInstance();
                inOrder.verify(subsystemClientId).getMemberClass();
                inOrder.verify(subsystemClientId).getMemberCode();
                inOrder.verify(subsystemClientId).getSubsystemCode();
                inOrder.verify(xRoadMember).getIdentifier();
                inOrder.verify(subsystemClientId).subsystemContainsMember(memberClientId);
                inOrder.verify(subsystemClientId).getSubsystemCode();
            });
        }

        @Test
        @DisplayName("should fail creating new Subsystem entity if XRoadMember entity missing")
        public void shouldFailCreateNewSubsystemEntityIfXRoadMemberEntityMissing() {
            doReturn(clientIdDto).when(clientDto).getClientId();
            doReturn(subsystemClientId).when(clientIdDtoConverter).fromDto(clientIdDto);
            doReturn(XRoadIdDto.TypeEnum.SUBSYSTEM).when(clientIdDto).getType();
            doReturn(memberClientId).when(subsystemClientId).getMemberId();
            doReturn(Option.none()).when(memberService).findMember(memberClientId);
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();

            ThrowingCallable testable = () -> converter.fromDto(clientDto);

            assertThatThrownBy(testable)
                    .usingRecursiveComparison()
                    .isEqualTo(new NotFoundException(MEMBER_NOT_FOUND, "code", MEMBER_CODE));
            inOrder().verify(inOrder -> {
                inOrder.verify(clientDto).getClientId();
                inOrder.verify(clientIdDtoConverter).fromDto(clientIdDto);
                inOrder.verify(clientIdDto).getType();
                inOrder.verify(subsystemClientId).getMemberId();
                inOrder.verify(memberService).findMember(memberClientId);
                inOrder.verify(clientIdDto).getMemberCode();
            });
        }


        @Test
        @DisplayName("should fail if unknown ClientDto provided")
        public void shouldFailIfUnknownSecurityServerClientProvided() {
            doReturn(clientIdDto).when(clientDto).getClientId();
            doReturn(subsystemClientId).when(clientIdDtoConverter).fromDto(clientIdDto);
            doReturn(null).when(clientIdDto).getType();

            ThrowingCallable testable = () -> converter.fromDto(clientDto);

            assertThatThrownBy(testable)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid client type: null");
            inOrder().verify(inOrder -> {
                inOrder.verify(clientDto).getClientId();
                inOrder.verify(clientIdDtoConverter).fromDto(clientIdDto);
                inOrder.verify(clientIdDto).getType();
            });
        }
    }
}
