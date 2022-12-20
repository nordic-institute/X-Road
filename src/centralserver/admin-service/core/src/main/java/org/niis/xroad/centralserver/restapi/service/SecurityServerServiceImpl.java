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

import ee.ria.xroad.common.identifier.SecurityServerId;

import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestStatus;
import org.niis.xroad.centralserver.restapi.domain.Origin;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.dto.ManagementRequestInfoDto;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.StableSortHelper;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class SecurityServerServiceImpl implements SecurityServerService {

    private final StableSortHelper stableSortHelper;
    private final SecurityServerRepository securityServerRepository;
    private final ManagementRequestService managementRequestService;
    private final SecurityServerMapper securityServerMapper;

    @Override
    public Page<SecurityServer> findSecurityServers(String q, Pageable pageable) {
        return securityServerRepository
                .findAllByQuery(q, stableSortHelper.addSecondaryIdSort(pageable))
                .map(securityServerMapper::toTarget);
    }

    @Override
    public Option<SecurityServer> find(SecurityServerId id) {
        return securityServerRepository.findBy(id)
                .map(securityServerMapper::toTarget);
    }

    @Override
    public ManagementRequestStatus findSecurityServerRegistrationStatus(SecurityServerId serverId) {
        return managementRequestService.findRequests(
                        ManagementRequestService.Criteria.builder()
                                .origin(Origin.SECURITY_SERVER)
                                .serverId(serverId)
                                .types(List.of(ManagementRequestType.CLIENT_REGISTRATION_REQUEST))
                                .build(), Pageable.unpaged())
                .stream()
                .map(ManagementRequestInfoDto::getStatus)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Option<SecurityServer> findByOwnerAndServerCode(XRoadMember owner, String serverCode) {
        //TODO we should map back to entities just for lookups.
        return securityServerRepository.findByOwnerIdAndServerCode(owner.getId(), serverCode)
                .map(securityServerMapper::toTarget);
    }
}
