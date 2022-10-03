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

import io.vavr.collection.Seq;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.restapi.entity.MemberClass;
import org.niis.xroad.centralserver.restapi.repository.MemberClassRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.DataIntegrityException;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_EXISTS;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_IS_IN_USE;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;

/**
 * MemberClass Service
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MemberClassService {

    private final MemberClassRepository memberClassRepository;
    private final XRoadMemberRepository members;

    /**
     * List all member classes
     */
    public Seq<MemberClass> findAll() {
        Sort sort = Sort.by(Sort.Order.asc("code").ignoreCase());
        return memberClassRepository.findAllSortedBy(sort);
    }

    /**
     * Find a member class corresponding to the code
     * @param code member class code
     */
    public MemberClass find(String code) {
        return memberClassRepository.findByCode(code)
                .getOrNull();
    }

    /**
     * Add a new member class
     * @param memberClass member class to add
     * @throws DataIntegrityException if the member class already exists
     */
    public MemberClass add(final MemberClass memberClass) {
        Consumer<org.niis.xroad.centralserver.restapi.entity.MemberClass> ensureNotExists = __ -> {
            boolean exists = memberClass.exists()
                    || memberClassRepository.findByCode(memberClass.getCode()).isDefined();
            if (exists) {
                throw new DataIntegrityException(MEMBER_CLASS_EXISTS, memberClass.getCode());
            }
        };

        return Try.success(memberClass)
                .andThen(ensureNotExists)
                .map(memberClassRepository::save)
                .get();
    }

    /**
     * Update member class
     * @throws NotFoundException if the member class does not exist
     */
    public MemberClass update(final MemberClass memberClass) {
        return Try.success(memberClass)
                .filter(MemberClass::exists)
                .orElse(() -> memberClassRepository.findByCode(memberClass.getCode()).toTry())
                .filter(Objects::nonNull, () ->
                        new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", memberClass.getCode()))
                .andThen(persistedMemberClass -> persistedMemberClass.setDescription(memberClass.getDescription()))
                .map(memberClassRepository::save)
                .get();
    }

    /**
     * Delete member class.
     * @param code member class code
     * @throws DataIntegrityException if the member class is in use
     * @throws NotFoundException if the member class does not exist
     */
    public void delete(String code) {
        memberClassRepository.findByCode(code)
                .toTry()
                .filter(Objects::nonNull, () -> new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", code))
                .filter(Predicate.not(members::existsByMemberClass), () ->
                        new DataIntegrityException(MEMBER_CLASS_IS_IN_USE, "code", code))
                .andThen(memberClassRepository::delete);
    }
}
