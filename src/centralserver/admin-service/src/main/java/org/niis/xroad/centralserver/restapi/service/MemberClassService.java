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
import org.niis.xroad.centralserver.restapi.service.exception.ConflictException;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Optional;
import java.util.Set;

import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_DELETE_FAILED;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.MEMBER_CLASS_NOT_FOUND;

/**
 * MemberClass Service
 */
@Service
@Transactional
public class MemberClassService {

    private final MemberClassRepository repository;

    public MemberClassService(MemberClassRepository repository) {
        this.repository = repository;
    }

    /**
     * List all member classes
     */
    public Set<MemberClassDto> list() {
        return repository.findAllAsDtoBy();
    }

    /**
     * Get a member class corresponding to the code
     * @param code member class code
     */
    public Optional<MemberClassDto> get(String code) {
        return repository.findByCodeIgnoreCase(code).map(m -> new MemberClassDto(m.getCode(), m.getDescription()));
    }

    /**
     * Add a new member class
     * @param memberClassDto
     * @return
     */
    public MemberClassDto add(final MemberClassDto memberClassDto) {
        return toDto(repository.findByCodeIgnoreCase(memberClassDto.getCode())
                .orElseGet(() -> repository.save(
                        new MemberClass(memberClassDto.getCode(), memberClassDto.getDescription()))));
    }

    /**
     * Update member class
     * @throws NotFoundException if the member class does not exist
     */
    public MemberClassDto update(final MemberClassDto memberClassDto) {
        return toDto(repository.findByCodeIgnoreCase(memberClassDto.getCode()).map(m -> {
            m.setDescription(memberClassDto.getDescription());
            return repository.save(m);
        }).orElseThrow(() -> new NotFoundException(MEMBER_CLASS_NOT_FOUND, memberClassDto.getCode())));
    }

    /**
     * Delete member class.
     * @param code member class code
     * @throws ConflictException if the member class is in use
     * @throws NotFoundException if the member class does not exist
     */
    public void delete(String code) {
        repository.findByCodeIgnoreCase(code).map(m -> {
            if (!repository.isInUse(m)) {
                repository.delete(m);
                return true;
            } else {
                throw new ConflictException(MEMBER_CLASS_DELETE_FAILED, code);
            }
        }).orElseThrow(() -> new NotFoundException(MEMBER_CLASS_NOT_FOUND, code));
    }

    private static MemberClassDto toDto(MemberClass memberClass) {
        return new MemberClassDto(memberClass.getCode(), memberClass.getDescription());
    }
}
