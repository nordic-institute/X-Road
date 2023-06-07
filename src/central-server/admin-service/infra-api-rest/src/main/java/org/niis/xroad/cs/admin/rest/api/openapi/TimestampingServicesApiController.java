/*
 * The MIT License
 *
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
import org.niis.xroad.cs.admin.api.dto.TimestampServiceRequest;
import org.niis.xroad.cs.admin.api.service.TimestampingServicesService;
import org.niis.xroad.cs.admin.rest.api.mapper.TimestampingServiceMapper;
import org.niis.xroad.cs.openapi.TimestampingServicesApi;
import org.niis.xroad.cs.openapi.model.TimestampingServiceDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.FileVerifier;
import org.niis.xroad.restapi.util.MultipartFileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_TSP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_TSP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_TIMESTAMP_SERVICE;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class TimestampingServicesApiController implements TimestampingServicesApi {

    private final TimestampingServicesService timestampingServicesService;

    private final TimestampingServiceMapper timestampingServiceMapper;
    private final FileVerifier fileVerifier;

    @Override
    @AuditEventMethod(event = ADD_TSP)
    @PreAuthorize("hasAuthority('ADD_APPROVED_TSA')")
    public ResponseEntity<TimestampingServiceDto> addTimestampingService(String url, MultipartFile certificate) {
        byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
        fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
        return status(HttpStatus.CREATED).body(timestampingServiceMapper.toTarget(
                timestampingServicesService.add(url, fileBytes)));
    }

    @Override
    @AuditEventMethod(event = DELETE_TSP)
    @PreAuthorize("hasAuthority('DELETE_APPROVED_TSA')")
    public ResponseEntity<Void> deleteTimestampingService(Integer id) {
        timestampingServicesService.delete(id);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_TSAS')")
    public ResponseEntity<TimestampingServiceDto> getTimestampingService(Integer id) {
        return ok(timestampingServiceMapper.toTarget(timestampingServicesService.get(id)));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_APPROVED_TSAS')")
    public ResponseEntity<List<TimestampingServiceDto>> getTimestampingServices() {
        return ok(timestampingServicesService.getTimestampingServices().stream()
                .map(timestampingServiceMapper::toTarget)
                .collect(toList()));
    }

    @Override
    @AuditEventMethod(event = EDIT_TIMESTAMP_SERVICE)
    @PreAuthorize("hasAuthority('EDIT_APPROVED_TSA')")
    public ResponseEntity<TimestampingServiceDto> updateTimestampingService(Integer id, String url, MultipartFile certificate) {
        final TimestampServiceRequest updateRequest = new TimestampServiceRequest()
                .setId(id)
                .setUrl(url);
        if (certificate != null) {
            byte[] fileBytes = MultipartFileUtils.readBytes(certificate);
            fileVerifier.validateCertificate(certificate.getOriginalFilename(), fileBytes);
            updateRequest.setCertificate(fileBytes);
        }
        return ok(timestampingServiceMapper.toTarget(timestampingServicesService.update(updateRequest)));
    }
}
