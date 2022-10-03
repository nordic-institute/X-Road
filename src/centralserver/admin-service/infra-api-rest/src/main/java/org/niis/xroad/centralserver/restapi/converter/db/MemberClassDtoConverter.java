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
package org.niis.xroad.centralserver.restapi.converter.db;

import io.vavr.control.Option;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.MemberClassDto;
import org.niis.xroad.centralserver.restapi.dto.converter.DtoConverter;
import org.niis.xroad.centralserver.restapi.entity.MemberClass;
import org.niis.xroad.centralserver.restapi.repository.MemberClassRepository;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.Objects;
import java.util.function.Supplier;

import static ee.ria.xroad.common.util.Fn.self;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberClassDtoConverter extends DtoConverter<MemberClass, MemberClassDto> {

    private final ZoneOffset dtoZoneOffset;
    private final MemberClassRepository memberClasses;

    @Override
    public MemberClassDto toDto(MemberClass source) {
        return self(new MemberClassDto(), self -> {
            self.setCode(source.getCode());
            self.setDescription(source.getDescription());
            self.setCreatedAt(Option.of(source.getCreatedAt())
                    .map(instant -> instant.atOffset(dtoZoneOffset))
                    .getOrNull());
            self.setUpdatedAt(Option.of(source.getUpdatedAt())
                    .map(instant -> instant.atOffset(dtoZoneOffset))
                    .getOrNull());
        });
    }

    @Override
    public MemberClass fromDto(MemberClassDto source) {
        String memberClassCode = source.getCode();
        Supplier<org.niis.xroad.centralserver.restapi.entity.MemberClass> newMemberClass = () -> {
            return new MemberClass(
                    memberClassCode,
                    source.getDescription()
            );
        };

        return memberClasses
                .findByCode(memberClassCode)
                .filter(Objects::nonNull)
                .getOrElse(newMemberClass);
    }
}
