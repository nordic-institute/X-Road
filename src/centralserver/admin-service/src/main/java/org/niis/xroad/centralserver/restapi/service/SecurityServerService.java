/**
 * The MIT License
 *
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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.identifier.SecurityServerId;

import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.repository.ManagementRequestViewRepository;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.centralserver.restapi.service.managementrequest.ManagementRequestService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;

import static org.niis.xroad.centralserver.restapi.domain.ManagementRequestType.CLIENT_REGISTRATION_REQUEST;

@Service
@Transactional
@RequiredArgsConstructor
public class SecurityServerService {

    private final StableSortHelper stableSortHelper;
    private final SecurityServerRepository securityServerRepository;

    private final ManagementRequestService managementRequestService;

    public Page<SecurityServer> findSecurityServers(String q, Pageable pageable) {
        return securityServerRepository
                .findAll(SecurityServerRepository.multifieldSearch(q), stableSortHelper.addSecondaryIdSort(pageable));
    }

    public Option<SecurityServer> find(SecurityServerId id) {
        return securityServerRepository.findBy(id);
    }

    public ManagementRequestStatus findSecurityServerRegistrationStatus(SecurityServerId serverId) {
        return managementRequestService.findRequests(
                        ManagementRequestViewRepository.Criteria.builder()
                                .origin(Origin.SECURITY_SERVER)
                                .serverId(serverId)
                                .types(List.of(CLIENT_REGISTRATION_REQUEST))
                                .build(), Pageable.unpaged())
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException(ErrorMessage.MANAGEMENT_REQUEST_NOT_FOUND))
                .getStatus();
    }
}
