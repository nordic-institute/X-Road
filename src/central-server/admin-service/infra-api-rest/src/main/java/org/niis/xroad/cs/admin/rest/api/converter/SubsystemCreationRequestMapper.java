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
package org.niis.xroad.cs.admin.rest.api.converter;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.converter.GenericUniDirectionalMapper;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.api.dto.SubsystemCreationRequest;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.openapi.model.NewSubsystemIdDto;
import org.niis.xroad.cs.openapi.model.SubsystemAddDto;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static java.util.function.Predicate.not;

@Component
@RequiredArgsConstructor
public class SubsystemCreationRequestMapper implements GenericUniDirectionalMapper<SubsystemAddDto, SubsystemCreationRequest> {

    private final SystemParameterService systemParameterService;

    @Override
    public SubsystemCreationRequest toTarget(SubsystemAddDto source) {
        var subsystemId = createSubsystemId(source.getSubsystemId());
        var name = Optional.ofNullable(source.getSubsystemName())
                .filter(not(String::isEmpty))
                .orElse(null);

        return new SubsystemCreationRequest(
                subsystemId.getMemberId(),
                subsystemId,
                name);
    }

    private SubsystemId createSubsystemId(NewSubsystemIdDto source) {

        return SubsystemId.create(
                systemParameterService.getInstanceIdentifier(),
                source.getMemberClass(),
                source.getMemberCode(),
                source.getSubsystemCode());
    }
}
