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
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.MemberClassMapper;
import org.niis.xroad.cs.admin.core.repository.MemberClassRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_IS_IN_USE;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DESCRIPTION;

/**
 * MemberClass Service
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MemberClassServiceImpl implements MemberClassService {

    private final MemberClassRepository memberClassRepository;
    private final XRoadMemberRepository members;

    private final MemberClassMapper memberClassMapper;
    private final AuditDataHelper auditData;

    @Override
    public List<MemberClass> findAll() {
        Sort sort = Sort.by(Sort.Order.asc("code").ignoreCase());
        return memberClassRepository.findAllSortedBy(sort).stream()
                .map(memberClassMapper::toTarget)
                .collect(toList());
    }

    @Override
    public Optional<MemberClass> findByCode(String code) {
        return memberClassRepository.findByCode(code)
                .map(memberClassMapper::toTarget);
    }

    @Override
    public MemberClass add(final MemberClass memberClass) {
        auditData.put(CODE, memberClass.getCode());
        auditData.put(DESCRIPTION, memberClass.getDescription());

        boolean exists = memberClass.getId() > 0
                || memberClassRepository.findByCode(memberClass.getCode()).isPresent();
        if (exists) {
            throw new DataIntegrityException(MEMBER_CLASS_EXISTS, memberClass.getCode());
        }

        var memberClassEntity = new MemberClassEntity(memberClass.getCode(), memberClass.getDescription());
        final MemberClassEntity savedEntity = memberClassRepository.save(memberClassEntity);
        return memberClassMapper.toTarget(savedEntity);
    }

    private MemberClassEntity get(String code) {
        return memberClassRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", code));
    }

    @Override
    public MemberClass updateDescription(String code, String description) {
        auditData.put(CODE, code);
        auditData.put(DESCRIPTION, description);

        final MemberClassEntity entity = get(code);
        entity.setDescription(description);
        final MemberClassEntity saved = memberClassRepository.save(entity);
        return memberClassMapper.toTarget(saved);
    }

    @Override
    public void delete(String code) {
        auditData.put(CODE, code);
        final MemberClassEntity entity = get(code);
        if (members.existsByMemberClass(entity)) {
            throw new DataIntegrityException(MEMBER_CLASS_IS_IN_USE, "code", code);
        }

        memberClassRepository.delete(entity);
    }
}
