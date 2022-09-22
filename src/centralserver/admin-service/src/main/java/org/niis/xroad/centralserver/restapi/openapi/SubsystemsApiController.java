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
package org.niis.xroad.centralserver.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;

import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.SubsystemsApi;
import org.niis.xroad.centralserver.openapi.model.ClientDto;
import org.niis.xroad.centralserver.openapi.model.SubsystemDto;
import org.niis.xroad.centralserver.restapi.dto.converter.db.ClientDtoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.db.SubsystemDtoConverter;
import org.niis.xroad.centralserver.restapi.entity.Subsystem;
import org.niis.xroad.centralserver.restapi.service.SubsystemService;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;

import static java.util.stream.Collectors.toSet;

@Controller
@PreAuthorize("denyAll")
@RequiredArgsConstructor
@RequestMapping(ControllerUtil.API_V1_PREFIX)
public class SubsystemsApiController implements SubsystemsApi {

    private final SubsystemService subsystemService;
    private final AuditDataHelper auditData;
    private final ClientDtoConverter clientDtoConverter;
    private final ClientIdConverter clientIdConverter;
    private final SubsystemDtoConverter subsystemDtoConverter;

    @Override
    @PreAuthorize("hasAuthority('ADD_MEMBER_SUBSYSTEM')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_SUBSYSTEM)
    public ResponseEntity<ClientDto> addSubsystem(ClientDto clientDto) {
        auditData.put(RestApiAuditProperty.MEMBER_CLASS, clientDto.getXroadId().getMemberClass());
        auditData.put(RestApiAuditProperty.MEMBER_CODE, clientDto.getXroadId().getMemberCode());
        auditData.put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, clientDto.getXroadId().getSubsystemCode());

        return Try.success(clientDto)
                .map(clientDtoConverter::fromDto)
                .map(clientDtoConverter.expectType(Subsystem.class))
                .map(subsystemService::add)
                .map(clientDtoConverter::toDto)
                .map(ResponseEntity.status(HttpStatus.CREATED)::body)
                .get();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_MEMBER_DETAILS')")
    @Transactional
    public ResponseEntity<Set<SubsystemDto>> getSubsystems(String id) {
        return ResponseEntity.ok(subsystemService.findByMemberCode(id)
                .map(subsystemDtoConverter::toDto)
                .collect(toSet()));
    }

    @PreAuthorize("hasAuthority('REMOVE_MEMBER_SUBSYSTEM')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_SUBSYSTEM)
    public ResponseEntity<Void> deleteSubsystem(String id) {
        verifySubsystemId(id);
        ClientId clientId = clientIdConverter.convertId(id);

        auditData.put(RestApiAuditProperty.MEMBER_CLASS, clientId.getMemberClass());
        auditData.put(RestApiAuditProperty.MEMBER_CODE, clientId.getMemberCode());
        auditData.put(RestApiAuditProperty.MEMBER_SUBSYSTEM_CODE, clientId.getSubsystemCode());

        subsystemService.deleteSubsystem(clientId);
        return ResponseEntity.noContent().build();
    }

    private void verifySubsystemId(String clientId) {
        if (!clientIdConverter.isEncodedSubsystemId(clientId)) {
            throw new BadRequestException("Invalid subsystem id");
        }
    }
}
