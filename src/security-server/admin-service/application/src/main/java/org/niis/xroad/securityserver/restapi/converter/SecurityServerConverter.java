/*
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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.identifier.SecurityServerId;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServer;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converter for security server related data between openapi
 * and service domain classes
 */
@Component
@RequiredArgsConstructor
public class SecurityServerConverter {

    private final GlobalConfFacade globalConfFacade;

    private SecurityServerIdConverter securityServerIdConverter = new SecurityServerIdConverter();

    /**
     * Convert SecurityServerId into SecurityServer
     * @param securityServerId
     * @return
     */
    public SecurityServer convert(SecurityServerId securityServerId) {
        SecurityServer securityServer = new SecurityServer();
        securityServer.setId(securityServerIdConverter.convert(securityServerId));
        securityServer.setInstanceId(securityServerId.getXRoadInstance());
        securityServer.setMemberClass(securityServerId.getMemberClass());
        securityServer.setMemberCode(securityServerId.getMemberCode());
        securityServer.setServerCode(securityServerId.getServerCode());
        String securityServerAddress = globalConfFacade.getSecurityServerAddress(securityServerId);
        securityServer.setServerAddress(securityServerAddress);
        return securityServer;
    }

    /**
     * Convert a group of {@link SecurityServerId SecurityServerIds} into {@link SecurityServer SecurityServers}
     * @param securityServerIds
     * @return
     */
    public Set<SecurityServer> convert(Iterable<? extends SecurityServerId> securityServerIds) {
        return Streams.stream(securityServerIds)
                      .map(this::convert)
                      .collect(Collectors.toSet());
    }


}
