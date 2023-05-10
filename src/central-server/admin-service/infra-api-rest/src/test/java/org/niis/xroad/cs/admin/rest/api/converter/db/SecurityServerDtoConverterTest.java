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
package org.niis.xroad.cs.admin.rest.api.converter.db;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import io.vavr.control.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.rest.api.converter.AbstractDtoConverterTest;
import org.niis.xroad.cs.admin.rest.api.converter.model.SecurityServerIdDtoConverter;
import org.niis.xroad.cs.openapi.model.SecurityServerDto;
import org.niis.xroad.cs.openapi.model.SecurityServerIdDto;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class SecurityServerDtoConverterTest extends AbstractDtoConverterTest implements WithInOrder {

    @Mock
    private MemberId memberId;
    @Mock
    private SecurityServerId securityServerId;
    @Mock
    private SecurityServer securityServer;
    @Mock
    private XRoadMember xRoadMember;
    @Mock
    private SecurityServerIdDto securityServerIdDto;

    @Mock
    private SecurityServerService securityServerService;
    @Mock
    private MemberService memberService;
    @Mock
    private SecurityServerIdConverter securityServerIdConverter;
    @Mock
    private SecurityServerIdDtoConverter securityServerIdDtoConverter;

    @InjectMocks
    SecurityServerDtoConverter converter;

    @BeforeEach
    private void setZoneOffset() {
        ReflectionTestUtils.setField(converter, "dtoZoneOffset", dtoZoneOffset);
    }

    @Nested
    @DisplayName("toDto(SecurityServer source)")
    public class ToDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should test for sanity")
        public void shouldTestForSanity() {
            doReturn(securityServerId).when(securityServer).getServerId();
            doReturn(securityServerIdDto).when(securityServerIdDtoConverter).toDto(securityServerId);
            doReturn(xRoadMember).when(securityServer).getOwner();
            doReturn(MEMBER_NAME).when(xRoadMember).getName();
            doReturn(SERVER_ADDRESS).when(securityServer).getAddress();
            doReturn(createdAtInstance).when(securityServer).getCreatedAt();
            doReturn(updatedAtInstance).when(securityServer).getUpdatedAt();

            SecurityServerDto result = converter.toDto(securityServer);

            assertNotNull(result);
            assertEquals(securityServerIdDto, result.getServerId());
            assertEquals(MEMBER_NAME, result.getOwnerName());
            assertEquals(SERVER_ADDRESS, result.getServerAddress());
            assertEquals(createdAtOffsetDateTime, result.getCreatedAt());
            assertEquals(updatedAtOffsetDateTime, result.getUpdatedAt());
            inOrder().verify(inOrder -> {
                inOrder.verify(securityServer).getServerId();
                inOrder.verify(securityServerIdDtoConverter).toDto(securityServerId);
                inOrder.verify(securityServer).getOwner();
                inOrder.verify(xRoadMember).getName();
                inOrder.verify(securityServer).getAddress();
                inOrder.verify(securityServer).getCreatedAt();
                inOrder.verify(securityServer).getUpdatedAt();
            });
        }
    }

    @Nested
    @DisplayName("fromDto(SecurityServerDto source)")
    public class FromDtoMethod implements WithInOrder {

        @Mock
        private SecurityServerDto securityServerDto;

        @Test
        @DisplayName("should use persisted entity if present")
        public void shouldUsePersistedEntityIfPresent() {
            doReturn(securityServerIdDto).when(securityServerDto).getServerId();
            doReturn(securityServerId).when(securityServerIdConverter).convert(securityServerIdDto);
            doReturn(memberId).when(securityServerId).getOwner();
            doReturn(Option.of(xRoadMember)).when(memberService).findMember(memberId);
            doReturn(SERVER_CODE).when(securityServerIdDto).getServerCode();
            doReturn(Optional.of(securityServer)).when(securityServerService).findByOwnerAndServerCode(xRoadMember, SERVER_CODE);

            SecurityServer converted = converter.fromDto(securityServerDto);

            assertEquals(securityServer, converted);
            inOrder(securityServerDto).verify(inOrder -> {
                inOrder.verify(securityServerDto).getServerId();
                inOrder.verify(securityServerIdConverter).convert(securityServerIdDto);
                inOrder.verify(securityServerId).getOwner();
                inOrder.verify(memberService).findMember(memberId);
                inOrder.verify(securityServerIdDto).getServerCode();
                inOrder.verify(securityServerService).findByOwnerAndServerCode(xRoadMember, SERVER_CODE);
            });
        }

        @Test
        @DisplayName("should create new entity if missing")
        public void shouldCreateNewEntityIfMissing() {
            doReturn(securityServerIdDto).when(securityServerDto).getServerId();
            doReturn(securityServerId).when(securityServerIdConverter).convert(securityServerIdDto);
            doReturn(memberId).when(securityServerId).getOwner();
            doReturn(Option.of(xRoadMember)).when(memberService).findMember(memberId);
            doReturn(SERVER_CODE).when(securityServerIdDto).getServerCode();
            doReturn(Optional.empty()).when(securityServerService).findByOwnerAndServerCode(xRoadMember, SERVER_CODE);
            doReturn(SERVER_ADDRESS).when(securityServerDto).getServerAddress();

            SecurityServer converted = converter.fromDto(securityServerDto);

            assertNotNull(converted);
            assertEquals(xRoadMember, converted.getOwner());
            assertEquals(SERVER_CODE, converted.getServerCode());
            assertEquals(SERVER_ADDRESS, converted.getAddress());
            inOrder().verify(inOrder -> {
                inOrder.verify(securityServerDto).getServerId();
                inOrder.verify(securityServerIdConverter).convert(securityServerIdDto);
                inOrder.verify(securityServerId).getOwner();
                inOrder.verify(memberService).findMember(memberId);
                inOrder.verify(securityServerIdDto).getServerCode();
                inOrder.verify(securityServerService).findByOwnerAndServerCode(xRoadMember, SERVER_CODE);
                inOrder.verify(securityServerDto).getServerAddress();
            });
        }

        @Test
        @DisplayName("should fail if X-Road member not found")
        public void shouldFailIfXRoadMemberNotFound() {
            doReturn(securityServerIdDto).when(securityServerDto).getServerId();
            doReturn(securityServerId).when(securityServerIdConverter).convert(securityServerIdDto);
            doReturn(memberId).when(securityServerId).getOwner();
            doReturn(Option.none()).when(memberService).findMember(memberId);

            Executable testable = () -> converter.fromDto(securityServerDto);

            NotFoundException actualThrown = assertThrows(NotFoundException.class, testable);
            assertEquals(ErrorMessage.MEMBER_NOT_FOUND.getDescription(), actualThrown.getMessage());
            inOrder().verify(inOrder -> {
                inOrder.verify(securityServerDto).getServerId();
                inOrder.verify(securityServerIdConverter).convert(securityServerIdDto);
                inOrder.verify(securityServerId).getOwner();
                inOrder.verify(memberService).findMember(memberId);
            });
        }
    }
}
