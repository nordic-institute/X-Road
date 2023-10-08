/*
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

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.paging.Page;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.rest.api.converter.PageRequestConverter;
import org.niis.xroad.cs.admin.rest.api.converter.PagedClientsConverter;
import org.niis.xroad.cs.admin.rest.api.converter.db.ClientDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.model.ClientTypeDtoConverter;
import org.niis.xroad.cs.openapi.ClientsApi;
import org.niis.xroad.cs.openapi.model.ClientDto;
import org.niis.xroad.cs.openapi.model.ClientTypeDto;
import org.niis.xroad.cs.openapi.model.PagedClientsDto;
import org.niis.xroad.cs.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
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
    private final SecurityServerService securityServerService;
    private final PagedClientsConverter pagedClientsConverter;
    private final PageRequestConverter pageRequestConverter;
    private final SecurityServerIdConverter securityServerIdConverter;
    private final ClientDtoConverter.Flattened flattenedSecurityServerClientViewDtoConverter;
    private final ClientTypeDtoConverter clientTypeDtoConverter;

    private final PageRequestConverter.MappableSortParameterConverter findSortParameterConverter =
            new PageRequestConverter.MappableSortParameterConverter(
                    entry("id", "id"),
                    entry("member_name", "memberName"),
                    entry("client_id.instance_id", "xroadInstance"),
                    entry("client_id.member_class", "memberClass"),
                    entry("client_id.member_code", "memberCode"),
                    entry("client_type", "type")
            );

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
                                                       String encodedSecurityServerId,
                                                       String excludingGroupCode) {
        var pageRequest = pageRequestConverter.convert(pagingSorting, findSortParameterConverter);
        ClientService.SearchParameters params =
                new ClientService.SearchParameters()
                        .setMultifieldSearch(query)
                        .setMemberNameSearch(name)
                        .setInstanceSearch(instance)
                        .setMemberClassSearch(memberClass)
                        .setMemberCodeSearch(memberCode)
                        .setSubsystemCodeSearch(subsystemCode)
                        .setExcludingGroupParam(excludingGroupCode)
                        .setClientType(clientTypeDtoConverter.convert(clientTypeDto));
        if (StringUtils.isNotEmpty(encodedSecurityServerId)) {
            SecurityServerId id = securityServerIdConverter.convert(encodedSecurityServerId);
            var securityServer = securityServerService.find(id)
                    .orElseThrow(() -> new SecurityServerNotFoundException(id));
            params.setSecurityServerId(securityServer.getId());
        }
        Page<ClientDto> page = clientService.find(params, pageRequest)
                .map(flattenedSecurityServerClientViewDtoConverter::toDto);
        PagedClientsDto pagedResults = pagedClientsConverter.convert(page, pagingSorting);
        return ResponseEntity.ok(pagedResults);
    }
}
