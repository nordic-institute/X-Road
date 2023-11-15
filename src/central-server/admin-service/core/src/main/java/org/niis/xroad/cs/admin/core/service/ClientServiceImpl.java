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

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.paging.Page;
import org.niis.xroad.cs.admin.api.paging.PageRequestDto;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.core.converter.PageConverter;
import org.niis.xroad.cs.admin.core.converter.PageRequestDtoConverter;
import org.niis.xroad.cs.admin.core.entity.mapper.FlattenedSecurityServerClientViewMapper;
import org.niis.xroad.cs.admin.core.repository.FlattenedSecurityServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.paging.StableSortHelper;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Service for searching {@link FlattenedSecurityServerClientView}s
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {
    private final FlattenedSecurityServerClientRepository flattenedClientRepository;
    private final FlattenedSecurityServerClientViewMapper flattenedSecurityServerClientViewMapper;
    private final PageRequestDtoConverter pageRequestDtoConverter;
    private final PageConverter pageConverter;
    private final StableSortHelper stableSortHelper;

    public Page<FlattenedSecurityServerClientView> find(
            ClientService.SearchParameters params,
            PageRequestDto pageRequest) {
        var pageable = stableSortHelper.addSecondaryIdSort(pageRequestDtoConverter.convert(pageRequest));

        var result = flattenedClientRepository.findAll(params, pageable)
                .map(flattenedSecurityServerClientViewMapper::toTarget);
        return pageConverter.convert(result);
    }

    @Override
    public List<FlattenedSecurityServerClientView> find(SearchParameters params) {
        return flattenedClientRepository.findAll(params).stream()
                .map(flattenedSecurityServerClientViewMapper::toTarget)
                .collect(toList());
    }

    @Override
    public List<FlattenedSecurityServerClientView> findAll() {
        return flattenedClientRepository.findAll().stream()
                .map(flattenedSecurityServerClientViewMapper::toTarget)
                .collect(toList());
    }

}
