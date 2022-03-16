/**
 * The MIT License
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
package org.niis.xroad.centralserver.restapi.converter;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.model.Client;
import org.niis.xroad.centralserver.openapi.model.ClientId;
import org.niis.xroad.centralserver.openapi.model.ClientType;
import org.niis.xroad.centralserver.restapi.dto.FlattenedSecurityServerClientDto;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientConverter {
    public Client convert(FlattenedSecurityServerClientDto flattened) {
        Client client = new Client();
        switch (flattened.getType()) {
            case SUBSYSTEM:
                client.setClientType(ClientType.SUBSYSTEM);
                break;
            case MEMBER:
                client.setClientType(ClientType.MEMBER);
                break;
            default:
                throw new IllegalStateException("unknown type " + flattened.getType());
        }
        client.setId(String.valueOf(flattened.getId()));
        client.setMemberName(flattened.getMemberName());
        client.setCreatedAt(null); // TO DO
        client.setUpdatedAt(null); // TO DO
        ClientId clientId = new ClientId();
        clientId.setInstanceId(flattened.getXroadInstance());
        clientId.setMemberClass(flattened.getMemberClassCode());
        clientId.setMemberCode(flattened.getMemberCode());
        clientId.setSubsystemCode(flattened.getSubsystemCode());
        client.setXroadId(clientId);
        return client;
    }
}
