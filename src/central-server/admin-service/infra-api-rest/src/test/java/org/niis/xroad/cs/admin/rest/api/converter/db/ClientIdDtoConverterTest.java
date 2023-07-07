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

import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.junit.helper.WithInOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.rest.api.converter.AbstractDtoConverterTest;
import org.niis.xroad.cs.admin.rest.api.converter.model.XRoadObjectTypeDtoConverter;
import org.niis.xroad.cs.openapi.model.ClientIdDto;
import org.niis.xroad.cs.openapi.model.XRoadIdDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class ClientIdDtoConverterTest extends AbstractDtoConverterTest implements WithInOrder {

    private final MemberId memberId = MemberId.create(INSTANCE_ID, MEMBER_CLASS_CODE, MEMBER_CODE);
    private final SubsystemId subsystemId = SubsystemId.create(INSTANCE_ID, MEMBER_CLASS_CODE, MEMBER_CODE, SUBSYSTEM_CODE);

    @Mock
    private ClientIdDto clientIdDto;

    @Mock
    private XRoadObjectTypeDtoConverter xRoadObjectTypeConverter;

    @InjectMocks
    private ClientIdDtoConverter converter;

    @Nested
    @DisplayName("toDto(ClientId source)")
    public class ToDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should convert member ID")
        public void shouldConvertMemberId() {
            XRoadObjectType inputType = memberId.getObjectType();
            XRoadIdDto.TypeEnum expectedType = XRoadIdDto.TypeEnum.SUBSYSTEM;
            doReturn(expectedType).when(xRoadObjectTypeConverter).convert(inputType);

            ClientIdDto converted = converter.toDto(memberId);

            assertNotNull(converted);
            assertEquals(INSTANCE_ID, converted.getInstanceId());
            assertEquals(MEMBER_CLASS_CODE, converted.getMemberClass());
            assertEquals(MEMBER_CODE, converted.getMemberCode());
            assertNull(converted.getSubsystemCode());
            assertEquals(expectedType, converted.getType());
            assertEquals(memberId.asEncodedId(), converted.getEncodedId());
            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadObjectTypeConverter).convert(inputType);
            });
        }

        @Test
        @DisplayName("should convert subsystem ID")
        public void shouldConvertSubsystemId() {
            XRoadObjectType inputType = subsystemId.getObjectType();
            XRoadIdDto.TypeEnum expectedType = XRoadIdDto.TypeEnum.SUBSYSTEM;
            doReturn(expectedType).when(xRoadObjectTypeConverter).convert(inputType);

            ClientIdDto converted = converter.toDto(subsystemId);

            assertNotNull(converted);
            assertEquals(INSTANCE_ID, converted.getInstanceId());
            assertEquals(MEMBER_CLASS_CODE, converted.getMemberClass());
            assertEquals(MEMBER_CODE, converted.getMemberCode());
            assertEquals(SUBSYSTEM_CODE, converted.getSubsystemCode());
            assertEquals(expectedType, converted.getType());
            assertEquals(subsystemId.asEncodedId(), converted.getEncodedId());
            inOrder().verify(inOrder -> {
                inOrder.verify(xRoadObjectTypeConverter).convert(inputType);
            });
        }
    }

    @Nested
    @DisplayName("fromDto(ClientIdDto source)")
    public class FromDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should concert member ID")
        public void shouldConvertMemberId() {
            doReturn(XRoadIdDto.TypeEnum.MEMBER).when(clientIdDto).getType();
            doReturn(INSTANCE_ID).when(clientIdDto).getInstanceId();
            doReturn(MEMBER_CLASS_CODE).when(clientIdDto).getMemberClass();
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();

            ClientId converted = converter.fromDto(clientIdDto);

            assertThat(converted).isInstanceOfSatisfying(MemberId.class, convertedMemberId -> {
                assertEquals(INSTANCE_ID, convertedMemberId.getXRoadInstance());
                assertEquals(MEMBER_CLASS_CODE, convertedMemberId.getMemberClass());
                assertEquals(MEMBER_CODE, convertedMemberId.getMemberCode());
                assertEquals(XRoadObjectType.MEMBER, converted.getObjectType());
            });
            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdDto).getType();
                inOrder.verify(clientIdDto).getInstanceId();
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(clientIdDto).getMemberCode();
            });
        }

        @Test
        @DisplayName("should convert subsystem ID")
        public void shouldConvertSubsystemId() {
            doReturn(XRoadIdDto.TypeEnum.SUBSYSTEM).when(clientIdDto).getType();
            doReturn(INSTANCE_ID).when(clientIdDto).getInstanceId();
            doReturn(MEMBER_CLASS_CODE).when(clientIdDto).getMemberClass();
            doReturn(MEMBER_CODE).when(clientIdDto).getMemberCode();
            doReturn(SUBSYSTEM_CODE).when(clientIdDto).getSubsystemCode();

            ClientId converted = converter.fromDto(clientIdDto);

            assertThat(converted).isInstanceOfSatisfying(SubsystemId.class, convertedSubsystemId -> {
                assertEquals(INSTANCE_ID, convertedSubsystemId.getXRoadInstance());
                assertEquals(MEMBER_CLASS_CODE, convertedSubsystemId.getMemberClass());
                assertEquals(MEMBER_CODE, convertedSubsystemId.getMemberCode());
                assertEquals(SUBSYSTEM_CODE, convertedSubsystemId.getSubsystemCode());
                assertEquals(XRoadObjectType.SUBSYSTEM, converted.getObjectType());
            });
            inOrder().verify(inOrder -> {
                inOrder.verify(clientIdDto).getType();
                inOrder.verify(clientIdDto).getInstanceId();
                inOrder.verify(clientIdDto).getMemberClass();
                inOrder.verify(clientIdDto).getMemberCode();
                inOrder.verify(clientIdDto).getSubsystemCode();
            });
        }
    }
}
