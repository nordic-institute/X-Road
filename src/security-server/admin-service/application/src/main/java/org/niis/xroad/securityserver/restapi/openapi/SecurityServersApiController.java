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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.securityserver.restapi.converter.SecurityServerConverter;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServer;
import org.niis.xroad.securityserver.restapi.service.GlobalConfService;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * security servers listing controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class SecurityServersApiController implements SecurityServersApi {

    private final GlobalConfService globalConfService;
    private final GlobalConfFacade globalConfFacade;
    private final SecurityServerConverter securityServerConverter;
    private final ServerConfService serverConfService;
    private final SecurityServerIdConverter securityServerIdConverter;

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    public ResponseEntity<SecurityServer> getSecurityServer(String encodedSecurityServerId) {
        SecurityServerId securityServerId = securityServerIdConverter.convertId(encodedSecurityServerId);
        if (!globalConfService.securityServerExists(securityServerId)) {
            throw new ResourceNotFoundException("Security server " + encodedSecurityServerId + " not found");
        }
        SecurityServer securityServer = securityServerConverter.convert(securityServerId);
        return new ResponseEntity<>(securityServer, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_SECURITY_SERVERS')")
    public ResponseEntity<Set<SecurityServer>> getSecurityServers(Boolean currentServer) {
        boolean shouldGetOnlyCurrentServer = Boolean.TRUE.equals(currentServer);
        Set<SecurityServer> securityServers = null;
        if (shouldGetOnlyCurrentServer) {
            SecurityServerId currentSecurityServerId = serverConfService.getSecurityServerId();
            SecurityServer currentSecurityServer = securityServerConverter.convert(currentSecurityServerId);
            securityServers = Collections.singleton(currentSecurityServer);
        } else {
            List<SecurityServerId.Conf> securityServerIds = globalConfFacade.getSecurityServers();
            securityServers = securityServerConverter.convert(securityServerIds);
        }
        return new ResponseEntity<>(securityServers, HttpStatus.OK);
    }
}
