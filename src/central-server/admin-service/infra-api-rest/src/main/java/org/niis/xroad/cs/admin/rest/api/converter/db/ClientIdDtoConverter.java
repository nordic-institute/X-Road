/*
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
package org.niis.xroad.cs.admin.rest.api.converter.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.rest.api.converter.model.XRoadObjectTypeDtoConverter;
import org.niis.xroad.cs.openapi.model.ClientIdDto;
import org.niis.xroad.cs.openapi.model.XRoadIdDto;
import org.niis.xroad.restapi.converter.DtoConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static ee.ria.xroad.common.util.Fn.self;

@Slf4j
@Service
@Qualifier("clientIdDtoToModelMapper")
@RequiredArgsConstructor
public class ClientIdDtoConverter extends DtoConverter<ClientId, ClientIdDto> {

    private final XRoadObjectTypeDtoConverter xRoadObjectTypeConverter;

    @Override
    public ClientIdDto toDto(ClientId source) {
        return self(new ClientIdDto(), self -> {
            self.setInstanceId(source.getXRoadInstance());
            self.setMemberClass(source.getMemberClass());
            self.setMemberCode(source.getMemberCode());
            self.setSubsystemCode(source.getSubsystemCode());
            self.setEncodedId(source.asEncodedId());
            self.setType(xRoadObjectTypeConverter.convert(source.getObjectType()));
        });
    }

    @Override
    public ClientId fromDto(ClientIdDto source) {
        XRoadIdDto.TypeEnum type = source.getType();

        if (type != null) {
            switch (type) {
                case MEMBER:
                    return MemberId.create(
                            source.getInstanceId(),
                            source.getMemberClass(),
                            source.getMemberCode());

                case SUBSYSTEM:
                    return SubsystemId.create(
                            source.getInstanceId(),
                            source.getMemberClass(),
                            source.getMemberCode(),
                            source.getSubsystemCode());

                default://Ignore other cases
            }
        }

        throw new IllegalArgumentException("illegal ClientId type: " + type);
    }
}
