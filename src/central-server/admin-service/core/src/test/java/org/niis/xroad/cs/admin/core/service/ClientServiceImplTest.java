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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.junit.helper.WithInOrder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.paging.PageRequestDto;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.core.converter.PageConverter;
import org.niis.xroad.cs.admin.core.converter.PageRequestDtoConverter;
import org.niis.xroad.cs.admin.core.entity.mapper.FlattenedSecurityServerClientViewMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.FlattenedSecurityServerClientViewMapperImpl;
import org.niis.xroad.cs.admin.core.repository.FlattenedSecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.paging.StableSortHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientServiceImplTest implements WithInOrder {

    @Mock
    private StableSortHelper stableSortHelper;

    @Mock
    private FlattenedSecurityServerClientRepository flattenedSecurityServerClientRepository;

    @Mock
    private PageRequestDtoConverter pageRequestDtoConverter;

    @Mock
    private PageConverter pageConverter;

    @Spy
    private FlattenedSecurityServerClientViewMapper flattenedSecurityServerClientViewMapper =
            new FlattenedSecurityServerClientViewMapperImpl();

    @InjectMocks
    private ClientServiceImpl clientServiceImpl;

    @Nested
    @DisplayName("find(FlattenedSecurityServerClientRepository.SearchParameters params, Pageable pageable)")
    class FindMethod {

        @Mock
        private Pageable pageable;
        @Mock
        private PageRequestDto pageRequest;
        @Mock
        private Pageable modifiedPageable;
        @Mock
        private ClientService.SearchParameters params;
        @Mock
        private Page<FlattenedSecurityServerClientView> result;

        @Test
        @DisplayName("should just verify sanity")
        void shouldJustVerifySanity() {
            when(pageRequestDtoConverter.convert(pageRequest)).thenReturn(pageable);
            doReturn(modifiedPageable).when(stableSortHelper).addSecondaryIdSort(pageable);
            doReturn(result).when(flattenedSecurityServerClientRepository).findAll(params, modifiedPageable);

            clientServiceImpl.find(params, pageRequest);

            inOrder().verify(inOrder -> {
                inOrder.verify(stableSortHelper).addSecondaryIdSort(pageable);
                inOrder.verify(flattenedSecurityServerClientRepository).findAll(params, modifiedPageable);
                inOrder.verify(pageConverter).convert(any());
            });
        }
    }

}
