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
package org.niis.xroad.common.rpc.mapper;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.niis.xroad.rpc.common.ClientIdProto;
import org.niis.xroad.rpc.common.XRoadObjectType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientIdMapper {

    public static ClientId.Conf fromDto(ClientIdProto clientIdProto) {
        if (clientIdProto.hasSubsystemCode()) {
            return ClientId.Conf.create(clientIdProto.getXroadInstance(),
                    clientIdProto.getMemberClass(),
                    clientIdProto.getMemberCode(),
                    clientIdProto.getSubsystemCode());
        } else {
            return ClientId.Conf.create(clientIdProto.getXroadInstance(),
                    clientIdProto.getMemberClass(),
                    clientIdProto.getMemberCode());
        }
    }

    public static ClientIdProto toDto(ClientId input) {
        var builder = ClientIdProto.newBuilder()
                .setMemberClass(input.getMemberClass())
                .setMemberCode(input.getMemberCode())
                .setXroadInstance(input.getXRoadInstance())
                .setObjectType(XRoadObjectType.valueOf(input.getObjectType().name()));

        if (input.getSubsystemCode() != null) {
            builder.setSubsystemCode(input.getSubsystemCode());
        }
        return builder.build();
    }
}