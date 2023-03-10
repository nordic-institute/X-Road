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
package org.niis.xroad.cs.admin.core.service;

import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.api.exception.DataIntegrityException;
import org.niis.xroad.cs.admin.api.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.MemberClassMapper;
import org.niis.xroad.cs.admin.core.repository.MemberClassRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_IS_IN_USE;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;

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

    @Override
    public List<MemberClass> findAll() {
        Sort sort = Sort.by(Sort.Order.asc("code").ignoreCase());
        return memberClassRepository.findAllSortedBy(sort).stream()
                .map(memberClassMapper::toTarget)
                .collect(toList());
    }

    @Override
    public Option<MemberClass> findByCode(String code) {
        return memberClassRepository.findByCode(code)
                .map(memberClassMapper::toTarget);
    }

    @Override
    public MemberClass add(final MemberClass memberClass) {
        Consumer<MemberClassEntity> ensureNotExists = __ -> {
            boolean exists = memberClass.getId() > 0
                    || memberClassRepository.findByCode(memberClass.getCode()).isDefined();
            if (exists) {
                throw new DataIntegrityException(MEMBER_CLASS_EXISTS, memberClass.getCode());
            }
        };

        var memberClassEntity = new MemberClassEntity(memberClass.getCode(), memberClass.getDescription());
        return Try.success(memberClassEntity)
                .andThen(ensureNotExists)
                .map(memberClassRepository::save)
                .map(memberClassMapper::toTarget)
                .get();
    }

    @Override
    public MemberClass update(final MemberClass memberClass) {
        final MemberClassEntity entity = memberClassRepository.findByCode(memberClass.getCode())
                .getOrElseThrow(() -> new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", memberClass.getCode()));

        entity.setDescription(memberClass.getDescription());
        final MemberClassEntity saved = memberClassRepository.save(entity);
        return memberClassMapper.toTarget(saved);
    }

    @Override
    public void delete(String code) {
        final MemberClassEntity entity = memberClassRepository.findByCode(code)
                .getOrElseThrow(() -> new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", code));

        if (members.existsByMemberClass(entity)) {
            throw new DataIntegrityException(MEMBER_CLASS_IS_IN_USE, "code", code);
        }

        memberClassRepository.delete(entity);
    }
}
