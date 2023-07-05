/**
 * The MIT License
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
package org.niis.xroad.cs.admin.rest.api.converter.model;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.rest.api.converter.AbstractDtoConverterTest;
import org.niis.xroad.cs.openapi.model.SecurityServerIdDto;
import org.niis.xroad.cs.openapi.model.XRoadIdDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class SecurityServerIdDtoConverterTest extends AbstractDtoConverterTest implements WithInOrder {
    private static final XRoadIdDto.TypeEnum SERVER_TYPE = XRoadIdDto.TypeEnum.SERVER;

    private static final SubsystemId SUBSYSTEM_ID = SubsystemId.create(INSTANCE_ID, MEMBER_CLASS_CODE, MEMBER_CODE, SUBSYSTEM_CODE);
    private static final SecurityServerId SECURITY_SERVER_ID = SecurityServerId.create(SUBSYSTEM_ID, SERVER_CODE);

    @Mock
    private SecurityServerIdDto securityServerIdDto;

    private final SecurityServerIdDtoConverter converter = new SecurityServerIdDtoConverter();

    @Nested
    @DisplayName("toDto(SecurityServerId source)")
    public class ToDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should check for sanity")
        public void shouldCheckForSanity() {

            SecurityServerIdDto converted = converter.toDto(SECURITY_SERVER_ID);

            assertNotNull(converted);
            assertEquals(INSTANCE_ID, converted.getInstanceId());
            assertEquals(MEMBER_CLASS_CODE, converted.getMemberClass());
            assertEquals(MEMBER_CODE, converted.getMemberCode());
            assertEquals(SERVER_CODE, converted.getServerCode());
            assertEquals(SERVER_TYPE, converted.getType());
            assertEquals(SECURITY_SERVER_ID.asEncodedId(), converted.getEncodedId());
            inOrder().verifyNoMoreInteractions();
        }
    }

    @Nested
    @DisplayName("fromDto(SecurityServerIdDto source)")
    public class FromDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should check for sanity")
        public void shouldCheckForSanity() {
            doReturn(INSTANCE_ID).when(securityServerIdDto).getInstanceId();
            doReturn(MEMBER_CLASS_CODE).when(securityServerIdDto).getMemberClass();
            doReturn(MEMBER_CODE).when(securityServerIdDto).getMemberCode();
            doReturn(SERVER_CODE).when(securityServerIdDto).getServerCode();

            SecurityServerId converted = converter.fromDto(securityServerIdDto);

            assertNotNull(converted);
            assertEquals(INSTANCE_ID, converted.getXRoadInstance());
            assertEquals(MEMBER_CLASS_CODE, converted.getMemberClass());
            assertEquals(MEMBER_CODE, converted.getMemberCode());
            assertEquals(SERVER_CODE, converted.getServerCode());
            inOrder().verify(inOrder -> {
                inOrder.verify(securityServerIdDto).getInstanceId();
                inOrder.verify(securityServerIdDto).getMemberClass();
                inOrder.verify(securityServerIdDto).getMemberCode();
                inOrder.verify(securityServerIdDto).getServerCode();
            });
        }
    }
}
