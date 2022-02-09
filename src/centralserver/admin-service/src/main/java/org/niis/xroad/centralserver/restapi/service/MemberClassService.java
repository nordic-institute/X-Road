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

import org.niis.xroad.centralserver.restapi.dto.MemberClassDto;
import org.niis.xroad.centralserver.restapi.entity.MemberClass;
import org.niis.xroad.centralserver.restapi.repository.MemberClassRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.DataIntegrityException;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_EXISTS;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_IS_IN_USE;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;

/**
 * MemberClass Service
 */
@Service
@Transactional
public class MemberClassService {

    private final MemberClassRepository memberClasses;
    private final XRoadMemberRepository members;

    public MemberClassService(MemberClassRepository memberClasses, XRoadMemberRepository members) {
        this.memberClasses = memberClasses;
        this.members = members;
    }

    /**
     * List all member classes
     */
    public List<MemberClassDto> findAll() {
        return memberClasses.findAllAsDtoBy(Sort.by(Sort.Order.asc("code").ignoreCase()));
    }

    /**
     * Find a member class corresponding to the code
     * @param code member class code
     */
    public Optional<MemberClassDto> find(String code) {
        return memberClasses.findByCode(code).map(m -> new MemberClassDto(m.getCode(), m.getDescription()));
    }

    /**
     * Add a new member class
     * @param memberClassDto member class to add
     * @throws DataIntegrityException if the member class already exists
     */
    public MemberClassDto add(final MemberClassDto memberClassDto) {
        final String code = memberClassDto.getCode().toUpperCase(Locale.ROOT);
        memberClasses.findByCode(code).ifPresent(m -> {
            throw new DataIntegrityException(MEMBER_CLASS_EXISTS, code);
        });
        return toDto(memberClasses.save(new MemberClass(code, memberClassDto.getDescription())));
    }

    /**
     * Update member class
     * @throws NotFoundException if the member class does not exist
     */
    public MemberClassDto update(final MemberClassDto memberClassDto) {
        return toDto(memberClasses.findByCode(memberClassDto.getCode()).map(m -> {
            m.setDescription(memberClassDto.getDescription());
            return memberClasses.save(m);
        }).orElseThrow(() -> new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", memberClassDto.getCode())));
    }

    /**
     * Delete member class.
     * @param code member class code
     * @throws DataIntegrityException if the member class is in use
     * @throws NotFoundException if the member class does not exist
     */
    public void delete(String code) {
        memberClasses.findByCode(code).ifPresentOrElse(m -> {
            if (!members.existsByMemberClass(m)) {
                memberClasses.delete(m);
            } else throw new DataIntegrityException(MEMBER_CLASS_IS_IN_USE, "code", code);
        }, () -> {
            throw new NotFoundException(MEMBER_CLASS_NOT_FOUND, "code", code);
        });
    }

    private static MemberClassDto toDto(MemberClass memberClass) {
        return new MemberClassDto(memberClass.getCode(), memberClass.getDescription());
    }
}
