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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClientView;
import org.niis.xroad.centralserver.restapi.repository.FlattenedSecurityServerClientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class ClientServiceTest implements WithInOrder {

    @Mock
    private StableSortHelper stableSortHelper;

    @Mock
    private FlattenedSecurityServerClientRepository flattenedSecurityServerClientRepository;

    @InjectMocks
    private ClientService clientService;

    @Nested
    @DisplayName("find(FlattenedSecurityServerClientRepository.SearchParameters params, Pageable pageable)")
    class FindMethod {

        @Mock
        private Pageable pageable;
        @Mock
        private Pageable modifiedPageable;
        @Mock
        private Specification<FlattenedSecurityServerClientView> spec;
        @Mock
        private FlattenedSecurityServerClientRepository.SearchParameters params;
        @Mock
        private Page<FlattenedSecurityServerClientView> result;

        @Test
        @DisplayName("should just verify sanity")
        void shouldJustVerifySanity() {
            doReturn(spec).when(flattenedSecurityServerClientRepository).multiParameterSearch(params);
            doReturn(modifiedPageable).when(stableSortHelper).addSecondaryIdSort(pageable);
            doReturn(result).when(flattenedSecurityServerClientRepository).findAll(spec, modifiedPageable);

            clientService.find(params, pageable);

            inOrder().verify(inOrder -> {
                inOrder.verify(flattenedSecurityServerClientRepository).multiParameterSearch(params);
                inOrder.verify(stableSortHelper).addSecondaryIdSort(pageable);
                inOrder.verify(flattenedSecurityServerClientRepository).findAll(spec, modifiedPageable);
            });
        }
    }

}
