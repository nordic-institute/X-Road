/*
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.rest.api.converter.AbstractDtoConverterTest;
import org.niis.xroad.cs.openapi.model.MemberClassDto;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class MemberClassDtoConverterTest extends AbstractDtoConverterTest implements WithInOrder {
    private static final String DESCRIPTION = "DESCRIPTION";

    @Mock
    private MemberClass memberClass;
    @Mock
    private MemberClassDto memberClassDto;

    @Mock
    private MemberClassService memberClassService;

    @InjectMocks
    private MemberClassDtoConverter converter;

    @Nested
    @DisplayName("toDto(MemberClass source)")
    public class ToDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should check for sanity")
        public void shouldCheckForSanity() {
            doReturn(MEMBER_CLASS_CODE).when(memberClass).getCode();
            doReturn(DESCRIPTION).when(memberClass).getDescription();

            MemberClassDto converted = converter.toDto(memberClass);

            assertNotNull(converted);
            assertEquals(MEMBER_CLASS_CODE, converted.getCode());
            assertEquals(DESCRIPTION, converted.getDescription());
            inOrder().verify(inOrder -> {
                inOrder.verify(memberClass).getCode();
                inOrder.verify(memberClass).getDescription();
            });
        }
    }

    @Nested
    @DisplayName("fromDto(MemberClassDto source)")
    public class FromDtoMethod implements WithInOrder {

        @Test
        @DisplayName("should use persisted entity if present")
        public void shouldUsePersistedEntityIfPresent() {
            doReturn(MEMBER_CLASS_CODE).when(memberClassDto).getCode();
            doReturn(Optional.of(memberClass)).when(memberClassService).findByCode(MEMBER_CLASS_CODE);

            MemberClass converted = converter.fromDto(memberClassDto);

            assertEquals(memberClass, converted);
            inOrder().verify(inOrder -> {
                inOrder.verify(memberClassDto).getCode();
                inOrder.verify(memberClassService).findByCode(MEMBER_CLASS_CODE);
                inOrder.verify(memberClassDto).getDescription();
                inOrder.verify(memberClass).setDescription(null);
            });
        }

        @Test
        @DisplayName("should create new entity if missing")
        public void shouldCreateNewEntityIfMissing() {
            doReturn(MEMBER_CLASS_CODE).when(memberClassDto).getCode();
            doReturn(Optional.empty()).when(memberClassService).findByCode(MEMBER_CLASS_CODE);
            doReturn(DESCRIPTION).when(memberClassDto).getDescription();

            MemberClass converted = converter.fromDto(memberClassDto);

            assertNotNull(converted);
            assertEquals(MEMBER_CLASS_CODE, converted.getCode());
            assertEquals(DESCRIPTION, converted.getDescription());
            inOrder().verify(inOrder -> {
                inOrder.verify(memberClassDto).getCode();
                inOrder.verify(memberClassService).findByCode(MEMBER_CLASS_CODE);
                inOrder.verify(memberClassDto).getDescription();
            });
        }
    }
}
