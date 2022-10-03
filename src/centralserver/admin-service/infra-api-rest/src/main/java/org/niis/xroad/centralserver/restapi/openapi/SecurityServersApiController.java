/**
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
package org.niis.xroad.centralserver.restapi.openapi;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.centralserver.openapi.SecurityServersApi;
import org.niis.xroad.centralserver.openapi.model.CertificateDetailsDto;
import org.niis.xroad.centralserver.openapi.model.PagedSecurityServersDto;
import org.niis.xroad.centralserver.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.centralserver.openapi.model.SecurityServerAddressDto;
import org.niis.xroad.centralserver.openapi.model.SecurityServerDto;
import org.niis.xroad.centralserver.restapi.converter.PageRequestConverter;
import org.niis.xroad.centralserver.restapi.converter.SecurityServerConverter;
import org.niis.xroad.centralserver.restapi.converter.db.SecurityServerDtoConverter;
import org.niis.xroad.centralserver.restapi.service.SecurityServerService;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.transaction.Transactional;

import java.util.Set;

import static java.util.Map.entry;

@RestController
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class SecurityServersApiController implements SecurityServersApi {

    private final SecurityServerConverter serverConverter;
    private final SecurityServerDtoConverter securityServerDtoConverter;
    private final PageRequestConverter pageRequestConverter;

    private final SecurityServerService securityServerService;
    private final AuditDataHelper auditData;

    private final PageRequestConverter.MappableSortParameterConverter findSortParameterConverter =
            new PageRequestConverter.MappableSortParameterConverter(
                    entry("owner_name", "owner.name"),
                    entry("xroad_id.member_class", "owner.memberClass.code"),
                    entry("xroad_id.member_code", "owner.memberCode"),
                    entry("xroad_id.server_code", "serverCode")
            );

    @Override
    @PreAuthorize("hasAuthority('DELETE_SECURITY_SERVER')")
    @AuditEventMethod(event = RestApiAuditEvent.DELETE_SECURITY_SERVER)
    public ResponseEntity<Void> deleteSecurityServer(String id) {
        throw new NotImplementedException("deleteSecurityServer not implemented yet");
    }

    @Override
    public ResponseEntity<Void> deleteSecurityServerAuthCert(String id, String hash) {
        throw new NotImplementedException("deleteSecurityServerAuthCert not implemented yet");
    }

    @Override
    @Validated
    @PreAuthorize("hasAuthority('VIEW_SECURITY_SERVERS')")
    @Transactional
    public ResponseEntity<PagedSecurityServersDto> findSecurityServers(String query,
                                                                       PagingSortingParametersDto pagingSorting) {
        PageRequest pageRequest = pageRequestConverter.convert(
                pagingSorting, findSortParameterConverter);


        Page<SecurityServerDto> servers = securityServerService.findSecurityServers(query, pageRequest)
                .map(securityServerDtoConverter::toDto);

        return ResponseEntity.ok(serverConverter.convert(servers));
    }

    @Override
    public ResponseEntity<SecurityServerDto> getSecurityServer(String id) {
        return null;
    }

    @Override
    public ResponseEntity<CertificateDetailsDto> getSecurityServerAuthCert(String id, String hash) {
        throw new NotImplementedException("getSecurityServerAuthCert not implemented yet");
    }

    @Override
    public ResponseEntity<Set<CertificateDetailsDto>> getSecurityServerAuthCerts(String id) {
        throw new NotImplementedException("getSecurityServerAuthCerts not implemented yet");
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_SECURITY_SERVER_ADDRESS')")
    @AuditEventMethod(event = RestApiAuditEvent.UPDATE_SECURITY_SERVER_ADDRESS)
    public ResponseEntity<SecurityServerDto> updateSecurityServerAddress(
            String id,
            SecurityServerAddressDto securityServerAddress) {
        throw new NotImplementedException("updateSecurityServerAddress not implemented yet");
    }
}
