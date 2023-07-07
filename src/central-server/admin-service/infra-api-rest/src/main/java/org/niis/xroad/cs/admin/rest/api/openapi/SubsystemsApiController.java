/**
 * The MIT License
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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.rest.api.converter.SubsystemCreationRequestMapper;
import org.niis.xroad.cs.admin.rest.api.converter.db.ClientDtoConverter;
import org.niis.xroad.cs.openapi.SubsystemsApi;
import org.niis.xroad.cs.openapi.model.ClientDto;
import org.niis.xroad.cs.openapi.model.SubsystemAddDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_SUBSYSTEM_ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_SUBSYSTEM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_SUBSYSTEM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.UNREGISTER_SUBSYSTEM;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.noContent;

@Controller
@PreAuthorize("denyAll")
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class SubsystemsApiController implements SubsystemsApi {

    private final SubsystemService subsystemService;
    private final ClientDtoConverter clientDtoConverter;
    private final ClientIdConverter clientIdConverter;
    private final SecurityServerIdConverter securityServerIdConverter;
    private final SubsystemCreationRequestMapper subsystemCreationRequestMapper;

    @Override
    @PreAuthorize("hasAuthority('ADD_MEMBER_SUBSYSTEM')")
    @AuditEventMethod(event = ADD_SUBSYSTEM)
    public ResponseEntity<ClientDto> addSubsystem(SubsystemAddDto subsystemAddDto) {
        return Try.success(subsystemAddDto)
                .map(subsystemCreationRequestMapper::toTarget)
                .map(subsystemService::add)
                .map(clientDtoConverter::toDto)
                .map(ResponseEntity.status(CREATED)::body)
                .get();
    }

    @Override
    @PreAuthorize("hasAuthority('UNREGISTER_SUBSYSTEM')")
    @AuditEventMethod(event = UNREGISTER_SUBSYSTEM)
    public ResponseEntity<Void> unregisterSubsystem(String subsystemId, String serverId) {
        verifySubsystemId(subsystemId);
        ClientId clientId = clientIdConverter.convertId(subsystemId);
        SecurityServerId securityServerId = securityServerIdConverter.convertId(serverId);

        subsystemService.unregisterSubsystem(clientId, securityServerId);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('REMOVE_MEMBER_SUBSYSTEM')")
    @AuditEventMethod(event = DELETE_SUBSYSTEM)
    public ResponseEntity<Void> deleteSubsystem(String id) {
        verifySubsystemId(id);
        ClientId clientId = clientIdConverter.convertId(id);

        subsystemService.deleteSubsystem(clientId);
        return noContent().build();
    }

    private void verifySubsystemId(String clientId) {
        if (!clientIdConverter.isEncodedSubsystemId(clientId)) {
            throw new ValidationFailureException(INVALID_SUBSYSTEM_ID, clientId);
        }
    }
}
