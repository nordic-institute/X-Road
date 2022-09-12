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
import ee.ria.xroad.common.identifier.SecurityServerId;

import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.centralserver.openapi.ClientsApi;
import org.niis.xroad.centralserver.openapi.model.ClientDto;
import org.niis.xroad.centralserver.openapi.model.ClientTypeDto;
import org.niis.xroad.centralserver.openapi.model.MemberNameDto;
import org.niis.xroad.centralserver.openapi.model.PagedClientsDto;
import org.niis.xroad.centralserver.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.centralserver.restapi.converter.PageRequestConverter;
import org.niis.xroad.centralserver.restapi.converter.PagedClientsConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.db.ClientDtoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.model.ClientTypeDtoConverter;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.FlattenedSecurityServerClientRepository;
import org.niis.xroad.centralserver.restapi.service.ClientService;
import org.niis.xroad.centralserver.restapi.service.SecurityServerService;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static java.util.Map.entry;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ClientsApiController implements ClientsApi {

    private final ClientService clientService;
    private final AuditDataHelper auditData;
    private final SecurityServerService securityServerService;
    private final PagedClientsConverter pagedClientsConverter;
    private final PageRequestConverter pageRequestConverter;
    private final SecurityServerIdConverter securityServerIdConverter;
    private final ClientDtoConverter.Flattened flattenedSecurityServerClientViewDtoConverter;
    private final ClientDtoConverter clientDtoConverter;
    private final ClientTypeDtoConverter.Service clientTypeDtoConverter;
    private final ClientIdConverter clientIdConverter;

    private final PageRequestConverter.MappableSortParameterConverter findSortParameterConverter =
            new PageRequestConverter.MappableSortParameterConverter(
                    entry("id", "id"),
                    entry("member_name", "memberName"),
                    entry("xroad_id.instance_id", "xroadInstance"),
                    entry("xroad_id.member_class", "memberClass"),
                    entry("xroad_id.member_code", "memberCode"),
                    entry("client_type", "type")
            );

    @Override
    @PreAuthorize("hasAuthority('ADD_NEW_MEMBER')")
    @AuditEventMethod(event = RestApiAuditEvent.ADD_MEMBER)
    public ResponseEntity<ClientDto> addClient(ClientDto clientDto) {
        auditData.put(RestApiAuditProperty.MEMBER_NAME, clientDto.getMemberName());
        auditData.put(RestApiAuditProperty.MEMBER_CLASS, clientDto.getXroadId().getMemberClass());
        auditData.put(RestApiAuditProperty.MEMBER_CODE, clientDto.getXroadId().getMemberCode());

        return Try.success(clientDto)
                .map(clientDtoConverter::fromDto)
                .map(clientDtoConverter.expectType(XRoadMember.class))
                .map(clientService::add)
                .map(clientDtoConverter::toDto)
                .map(ResponseEntity::ok)
                .get();
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_MEMBER')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_MEMBER)
    public ResponseEntity<Void> deleteClient(String id) {
        verifyMemberId(id);
        ClientId clientId = clientIdConverter.convertId(id);
        auditData.put(RestApiAuditProperty.MEMBER_CLASS, clientId.getMemberClass());
        auditData.put(RestApiAuditProperty.MEMBER_CODE, clientId.getMemberCode());
        clientService.delete(clientId);
        return ResponseEntity.ok().build();
    }

    @Override
    @PreAuthorize("hasAuthority('SEARCH_MEMBERS') or hasAuthority('VIEW_MEMBERS')")
    public ResponseEntity<PagedClientsDto> findClients(String query,
                                                       PagingSortingParametersDto pagingSorting,
                                                       String name,
                                                       String instance,
                                                       String memberClass,
                                                       String memberCode,
                                                       String subsystemCode,
                                                       ClientTypeDto clientTypeDto,
                                                       String encodedSecurityServerId) {
        PageRequest pageRequest = pageRequestConverter.convert(pagingSorting, findSortParameterConverter);
        FlattenedSecurityServerClientRepository.SearchParameters params =
                new FlattenedSecurityServerClientRepository.SearchParameters()
                        .setMultifieldSearch(query)
                        .setMemberNameSearch(name)
                        .setInstanceSearch(instance)
                        .setMemberClassSearch(memberClass)
                        .setMemberCodeSearch(memberCode)
                        .setSubsystemCodeSearch(subsystemCode)
                        .setClientType(clientTypeDtoConverter.fromDto(clientTypeDto));
        if (!StringUtils.isEmpty(encodedSecurityServerId)) {
            SecurityServerId id = securityServerIdConverter.convert(encodedSecurityServerId);
            securityServerService.find(id)
                    .onEmpty(() -> {
                        throw new BadRequestException("Security server does not exist");
                    })
                    .map(securityServer -> params.setSecurityServerId(securityServer.getId()));
        }
        Page<ClientDto> page = clientService.find(params, pageRequest)
                .map(flattenedSecurityServerClientViewDtoConverter::toDto);
        PagedClientsDto pagedResults = pagedClientsConverter.convert(page, pagingSorting);
        return ResponseEntity.ok(pagedResults);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('VIEW_MEMBER_DETAILS')")
    public ResponseEntity<ClientDto> getClient(String id) {
        verifyMemberId(id);
        return Option.of(id)
                .map(clientIdConverter::convertId)
                .peek(clientId -> auditData.put(RestApiAuditProperty.CLIENT_IDENTIFIER, clientId))
                .flatMap(clientService::findMember)
                .map(clientDtoConverter::toDto)
                .map(ResponseEntity::ok)
                .getOrElseThrow(() -> new NotFoundException(ErrorMessage.MEMBER_NOT_FOUND));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('EDIT_MEMBER_NAME_AND_ADMIN_CONTACT')")
    public ResponseEntity<ClientDto> updateMemberName(String id, MemberNameDto memberName) {
        verifyMemberId(id);
        return Option.of(id)
                .map(clientIdConverter::convertId)
                .peek(clientId -> auditData.put(RestApiAuditProperty.CLIENT_IDENTIFIER, clientId))
                .flatMap(clientId -> clientService.updateMemberName(clientId, memberName.getMemberName()))
                .map(clientDtoConverter::toDto)
                .map(ResponseEntity::ok)
                .getOrElseThrow(() -> new NotFoundException(ErrorMessage.MEMBER_NOT_FOUND));
    }

    private void verifyMemberId(String id) {
        if (!clientIdConverter.isEncodedMemberId(id)) {
            throw new BadRequestException("Invalid member id");
        }
    }
}
