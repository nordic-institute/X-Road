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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.dto.SecurityServerDto;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class SecurityServerService {

    private final SecurityServerRepository securityServerRepository;

    public Page<SecurityServerDto> findSecurityServers(String q, Pageable pageable) {
        // add first sorting by id to guarantee the predictable operation.
        Sort.Order idSortingOrder = Sort.Order.asc("id");
        Sort.Order parameterSortingOrder = pageable.getSort().stream().findFirst()
                .orElseThrow(() -> new RuntimeException("Sort Parameter handling problem"));

        Sort refinedSorting = Sort.by(parameterSortingOrder, idSortingOrder);
        Pageable refinedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), refinedSorting);
        return securityServerRepository
                .findAll(SecurityServerRepository.multifieldSearch(q), refinedPageable)
                .map(SecurityServerService::toDto);
    }

    private static SecurityServerDto toDto(SecurityServer server) {
        return SecurityServerDto.builder()
                .serverId(server.getServerId())
                .ownerName(server.getOwner().getName())
                .serverAddress(server.getAddress())
                .updatedAt(server.getUpdatedAt())
                .createdAt(server.getCreatedAt())
                .build();
    }

}
