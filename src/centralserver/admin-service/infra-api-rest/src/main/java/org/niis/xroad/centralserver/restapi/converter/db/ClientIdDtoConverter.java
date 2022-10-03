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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.ClientIdDto;
import org.niis.xroad.centralserver.openapi.model.XRoadIdDto;
import org.niis.xroad.centralserver.restapi.converter.model.XRoadObjectTypeDtoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.DtoConverter;
import org.niis.xroad.centralserver.restapi.entity.ClientId;
import org.niis.xroad.centralserver.restapi.entity.MemberId;
import org.niis.xroad.centralserver.restapi.entity.SubsystemId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static ee.ria.xroad.common.util.Fn.self;

@Slf4j
@Service
@Qualifier("clientIdDtoToModelMapper")
@RequiredArgsConstructor
public class ClientIdDtoConverter extends DtoConverter<ClientId, ClientIdDto> {

    private final XRoadObjectTypeDtoConverter.Service xRoadObjectTypeConverter;

    @Override
    public ClientIdDto toDto(ClientId source) {
        return self(new ClientIdDto(), self -> {
            self.setInstanceId(source.getXRoadInstance());
            self.setMemberClass(source.getMemberClass());
            self.setMemberCode(source.getMemberCode());
            self.setSubsystemCode(source.getSubsystemCode());
            self.setType(xRoadObjectTypeConverter.toDto(source.getObjectType()));
        });
    }

    @Override
    @SuppressWarnings("checkstyle:MissingSwitchDefault")
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
            }
        }

        throw new IllegalArgumentException("illegal ClientId type: " + type);
    }
}
