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
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.rest.api.converter.ConfigurationPartsDtoConverter;
import org.niis.xroad.cs.openapi.ConfigurationPartsApi;
import org.niis.xroad.cs.openapi.model.ConfigurationPartDto;
import org.niis.xroad.cs.openapi.model.ConfigurationTypeDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.service.FileVerifier;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.UPLOAD_CONFIGURATION_PART;
import static org.niis.xroad.restapi.openapi.ControllerUtil.createAttachmentResourceResponse;
import static org.niis.xroad.restapi.util.MultipartFileUtils.readBytes;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ConfigurationPartsController implements ConfigurationPartsApi {

    private final ConfigurationService configurationService;
    private final ConfigurationPartsDtoConverter configurationPartsDtoConverter;
    private final FileVerifier fileVerifier;

    @Override
    @PreAuthorize("hasAuthority('DOWNLOAD_CONFIGURATION_PART')")
    public ResponseEntity<Resource> downloadConfigurationParts(ConfigurationTypeDto configurationType,
                                                               String contentIdentifier, Integer version) {

        var file = configurationService.getConfigurationPartFile(contentIdentifier, version);

        return createAttachmentResourceResponse(file.getData(), file.getFilename());
    }

    @Override
    @PreAuthorize("(hasAuthority('VIEW_INTERNAL_CONFIGURATION_SOURCE') and #configurationType.value == 'INTERNAL') "
            + "or (hasAuthority('VIEW_EXTERNAL_CONFIGURATION_SOURCE') and #configurationType.value == 'EXTERNAL')")
    public ResponseEntity<List<ConfigurationPartDto>> getConfigurationParts(ConfigurationTypeDto configurationType) {
        final var sourceType = ConfigurationSourceType.valueOf(configurationType.getValue());
        return ok(
                configurationService.getConfigurationParts(sourceType)
                        .stream()
                        .map(configurationPartsDtoConverter::convert)
                        .collect(toList()));
    }

    @Override
    @AuditEventMethod(event = UPLOAD_CONFIGURATION_PART)
    @PreAuthorize("hasAuthority('UPLOAD_CONFIGURATION_PART')")
    public ResponseEntity<Void> uploadConfigurationParts(ConfigurationTypeDto configurationType,
                                                         String contentIdentifier, MultipartFile file) {
        byte[] fileBytes = readBytes(file);
        fileVerifier.validateXml(file.getOriginalFilename(), fileBytes);
        final var sourceType = ConfigurationSourceType.valueOf(configurationType.getValue());
        configurationService.uploadConfigurationPart(sourceType,
                contentIdentifier,
                file.getOriginalFilename(), fileBytes);
        return noContent().build();
    }
}
