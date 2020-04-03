/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.AccessRightConverter;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.openapi.model.AccessRight;
import org.niis.xroad.restapi.service.AccessRightService;
import org.niis.xroad.restapi.service.ClientNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class ServiceClientApiController implements ServiceClientsApi {

    private final ClientConverter clientConverter;
    private final AccessRightService accessRightService;
    private AccessRightConverter accessRightConverter;

    public ServiceClientApiController(ClientConverter clientConverter, AccessRightService accessRightService,
            AccessRightConverter accessRightConverter) {
        this.clientConverter = clientConverter;
        this.accessRightService = accessRightService;
        this.accessRightConverter = accessRightConverter;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_ACL_SUBJECT_OPEN_SERVICES')")
    public ResponseEntity<List<AccessRight>> getServiceClientAccessRights(String id, String clientId) {
        ClientId clientIdentifier = clientConverter.convertId(clientId);
            List<AccessRight> accessRights = null;
        try {
            accessRights = accessRightConverter.convert(
                    accessRightService.getServiceClientAccessRights(id, clientIdentifier));
        } catch (ClientNotFoundException e) {
            throw new BadRequestException(e);
        }
        return new ResponseEntity<>(accessRights, HttpStatus.OK);
    }
}
