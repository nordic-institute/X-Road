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
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.service.InitializationService;
import org.niis.xroad.cs.admin.rest.api.converter.model.InitialServerConfDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.model.InitializationStatusDtoConverter;
import org.niis.xroad.cs.openapi.InitializationApi;
import org.niis.xroad.cs.openapi.model.InitialServerConfDto;
import org.niis.xroad.cs.openapi.model.InitializationStatusDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class InitializationApiController implements InitializationApi {

    private final InitializationService initializationService;
    private final InitializationStatusDtoConverter initializationStatusDtoConverter;
    private final InitialServerConfDtoConverter initialServerConfDtoConverter;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InitializationStatusDto> getInitializationStatus() {
        var initializationStatusDto = initializationService.getInitializationStatus();
        return ResponseEntity.ok(initializationStatusDtoConverter.fromDto(initializationStatusDto));
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    @AuditEventMethod(event = RestApiAuditEvent.INIT_CENTRAL_SERVER)
    public ResponseEntity<Void> initCentralServer(@Validated InitialServerConfDto config) {
        var configDto = initialServerConfDtoConverter.toDto(config);
        initializationService.initialize(configDto);

        return ResponseEntity.ok().build();
    }
}
