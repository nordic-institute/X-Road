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
package org.niis.xroad.centralserver.restapi.openapi;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.centralserver.openapi.ClientsApi;
import org.niis.xroad.centralserver.openapi.model.Client;
import org.niis.xroad.centralserver.openapi.model.ClientType;
import org.niis.xroad.centralserver.openapi.model.MemberName;
import org.niis.xroad.centralserver.openapi.model.PagedClients;
import org.niis.xroad.centralserver.openapi.model.PagingSortingParameters;
import org.niis.xroad.centralserver.restapi.converter.ClientTypeMapping;
import org.niis.xroad.centralserver.restapi.converter.PageRequestConverter;
import org.niis.xroad.centralserver.restapi.converter.PagedClientsConverter;
import org.niis.xroad.centralserver.restapi.dto.FlattenedSecurityServerClientDto;
import org.niis.xroad.centralserver.restapi.repository.FlattenedSecurityServerClientRepository;
import org.niis.xroad.centralserver.restapi.service.ClientSearchService;
import org.niis.xroad.centralserver.restapi.service.SecurityServerService;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ClientsApiController implements ClientsApi {

    private final ClientSearchService clientSearchService;
    private final PagedClientsConverter pagedClientsConverter;
    private final PageRequestConverter pageRequestConverter;
    private final SecurityServerIdConverter securityServerIdConverter;
    private final SecurityServerService securityServerService;

    @Override
    public ResponseEntity<Client> addClient(Client client) {
        return null;
    }

    @Override
    public ResponseEntity<Void> deleteClient(String id) {
        return null;
    }

    @Override
    @PreAuthorize("permitAll")
    public ResponseEntity<PagedClients> findClients(String q,
            PagingSortingParameters pagingSorting, String name,
            String instance, String memberClass,
            String memberCode, String subsystemCode,
            ClientType clientType, String encodedSecurityServerId) {
        PageRequest pageRequest = pageRequestConverter.convert(pagingSorting, new MemberSortParameterConverter());
        var params = new FlattenedSecurityServerClientRepository.SearchParameters()
                .setMultifieldSearch(q)
                .setInstanceSearch(instance)
                .setMemberNameSearch(name)
                .setMemberClassSearch(memberClass)
                .setMemberCodeSearch(memberCode)
                .setSubsystemCodeSearch(subsystemCode)
                .setClientType(ClientTypeMapping.map(clientType).orElse(null));
        if (!StringUtils.isEmpty(encodedSecurityServerId)) {
            SecurityServerId id = securityServerIdConverter.convertId(encodedSecurityServerId);
            var securityServer = securityServerService.find(id);
            // TO DO: NotFoundException if not found
            params.setSecurityServerId(securityServer.get().getId());
        }
        Page<FlattenedSecurityServerClientDto> page = clientSearchService.find(params, pageRequest);
        PagedClients pagedResults = pagedClientsConverter.convert(page, pagingSorting);
        return ResponseEntity.ok(pagedResults);
    }

    private class MemberSortParameterConverter implements PageRequestConverter.SortParameterConverter {
        Map<String, String> conversions = new HashMap<>();
        {
            conversions.put("id", "id");
            conversions.put("member_name", "memberName");
            conversions.put("xroad_id.instance_id", "memberName");
            conversions.put("xroad_id.member_class", "memberClass");
            conversions.put("xroad_id.member_code", "memberCode");
            conversions.put("client_type", "type");
        }
        @Override
        public String convertToSortProperty(String sortParameter) throws BadRequestException {
            String sortProperty = conversions.get(sortParameter);
            if (sortProperty == null) throw new BadRequestException("Unknown sort parameter " + sortParameter);
            return sortProperty;
        }
    }

    @Override
    public ResponseEntity<Client> getClient(String id) {
        return null;
    }

    @Override
    public ResponseEntity<Client> updateMemberName(String id, MemberName memberName) {
        return null;
    }
}
