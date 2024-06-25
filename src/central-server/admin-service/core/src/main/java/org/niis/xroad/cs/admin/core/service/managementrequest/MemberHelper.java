/*
 * The MIT License
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
package org.niis.xroad.cs.admin.core.service.managementrequest;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.MemberClassRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Component;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_NAME;

@Component
@RequiredArgsConstructor
public class MemberHelper {

    private final IdentifierRepository<MemberIdEntity> memberIds;
    private final XRoadMemberRepository xRoadMemberRepository;
    private final MemberClassRepository memberClassRepository;
    private final AuditDataHelper auditData;

    public XRoadMemberEntity findOrCreate(ClientId clientId) {
        var memberId = MemberId.ensure(clientId);
        return xRoadMemberRepository.findOneBy(memberId)
                .orElseGet(() -> createNew(memberId));
    }

    private XRoadMemberEntity createNew(MemberId clientId) {
        auditData.put(MEMBER_NAME, clientId.getMemberCode());
        auditData.put(MEMBER_CLASS, clientId.getMemberClass());
        auditData.put(MEMBER_CODE, clientId.getMemberCode());

        var memberId = memberIds.findOrCreate(MemberIdEntity.ensure(clientId));
        var memberClass = memberClassRepository.findByCode(memberId.getMemberClass())
                .orElseThrow(() -> new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", memberId.getMemberClass()));
        return xRoadMemberRepository.save(new XRoadMemberEntity(memberId.getMemberCode(), memberId, memberClass));
    }
}
