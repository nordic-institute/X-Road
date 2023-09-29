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
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.api.service.MemberClassService;
import org.niis.xroad.cs.admin.rest.api.converter.db.MemberClassDtoConverter;
import org.niis.xroad.cs.openapi.MemberClassesApi;
import org.niis.xroad.cs.openapi.model.MemberClassDescriptionDto;
import org.niis.xroad.cs.openapi.model.MemberClassDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_MEMBER_CLASS;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@RestController
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("hasAuthority('VIEW_MEMBER_CLASSES')")
@RequiredArgsConstructor
public class MemberClassesApiController implements MemberClassesApi {

    private final MemberClassService service;
    private final MemberClassDtoConverter memberClassDtoConverter;

    @Override
    @PreAuthorize("hasAuthority('ADD_MEMBER_CLASS')")
    @AuditEventMethod(event = ADD_MEMBER_CLASS)
    public ResponseEntity<MemberClassDto> addMemberClass(MemberClassDto memberClassDto) {
        final MemberClass memberClass = service.add(memberClassDtoConverter.fromDto(memberClassDto));
        return status(CREATED).body(memberClassDtoConverter.toDto(memberClass));
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_MEMBER_CLASS')")
    @AuditEventMethod(event = DELETE_MEMBER_CLASS)
    public ResponseEntity<Void> deleteMemberClass(String code) {
        service.delete(code);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_MEMBER_CLASSES')")
    public ResponseEntity<List<MemberClassDto>> getMemberClasses() {
        return ok(service.findAll().stream()
                .map(memberClassDtoConverter::toDto)
                .collect(toList()));
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_MEMBER_CLASS')")
    @AuditEventMethod(event = EDIT_MEMBER_CLASS)
    public ResponseEntity<MemberClassDto> updateMemberClass(String code, MemberClassDescriptionDto memberClassDescriptionDto) {
        return ok(memberClassDtoConverter.toDto(service.updateDescription(code, memberClassDescriptionDto.getDescription())));
    }
}
