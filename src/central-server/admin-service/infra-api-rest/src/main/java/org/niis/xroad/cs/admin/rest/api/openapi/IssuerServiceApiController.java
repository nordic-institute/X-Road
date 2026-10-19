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
package org.niis.xroad.cs.admin.rest.api.openapi;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.dto.IssuerHostState;
import org.niis.xroad.cs.admin.api.service.IssuerHostService;
import org.niis.xroad.cs.openapi.IssuerServiceApi;
import org.niis.xroad.cs.openapi.model.IssuerServiceHostDto;
import org.niis.xroad.cs.openapi.model.IssuerServiceStateDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class IssuerServiceApiController implements IssuerServiceApi {

    private final IssuerHostService issuerHostService;
    private final AuditDataHelper auditDataHelper;

    @Override
    @PreAuthorize("hasAuthority('VIEW_SYSTEM_SETTINGS')")
    public ResponseEntity<IssuerServiceStateDto> getIssuerServiceState() {
        return ResponseEntity.ok(toDto(issuerHostService.getState()));
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ISSUER_SERVICE_HOST')")
    @AuditEventMethod(event = RestApiAuditEvent.EDIT_ISSUER_SERVICE_HOST)
    public ResponseEntity<IssuerServiceStateDto> setIssuerServiceHost(IssuerServiceHostDto issuerServiceHost) {
        auditDataHelper.put(RestApiAuditProperty.ISSUER_SERVICE_HOST, issuerServiceHost.getHost());
        return ResponseEntity.ok(toDto(issuerHostService.setHost(issuerServiceHost.getHost())));
    }

    private static IssuerServiceStateDto toDto(IssuerHostState state) {
        return new IssuerServiceStateDto()
                .host(state.host())
                .bound(state.bound());
    }
}
