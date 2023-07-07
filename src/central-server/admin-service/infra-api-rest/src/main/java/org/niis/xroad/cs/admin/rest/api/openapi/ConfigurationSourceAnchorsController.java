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
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.service.ConfigurationAnchorService;
import org.niis.xroad.cs.admin.rest.api.converter.ConfigurationAnchorDtoConverter;
import org.niis.xroad.cs.openapi.ConfigurationSourceAnchorsApi;
import org.niis.xroad.cs.openapi.model.ConfigurationAnchorContainerDto;
import org.niis.xroad.cs.openapi.model.ConfigurationAnchorDto;
import org.niis.xroad.cs.openapi.model.ConfigurationTypeDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CONFIGURATION_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.RE_CREATE_ANCHOR;
import static org.niis.xroad.restapi.openapi.ControllerUtil.createAttachmentResourceResponse;
import static org.springframework.http.ResponseEntity.ok;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ConfigurationSourceAnchorsController implements ConfigurationSourceAnchorsApi {
    private final ConfigurationAnchorService configurationAnchorService;
    private final ConfigurationAnchorDtoConverter configurationAnchorDtoConverter;

    @Override
    @PreAuthorize("hasAuthority('DOWNLOAD_SOURCE_ANCHOR')")
    public ResponseEntity<Resource> downloadAnchor(ConfigurationTypeDto configurationType) {
        final var sourceType = ConfigurationSourceType.valueOf(configurationType.getValue());
        final var configurationAnchor = configurationAnchorService.getConfigurationAnchorWithFile(sourceType)
                .orElseThrow(() -> new NotFoundException(CONFIGURATION_NOT_FOUND));

        return createAttachmentResourceResponse(
                configurationAnchor.getAnchorFile(),
                configurationAnchor.getAnchorFileName());
    }

    @Override
    @PreAuthorize("(hasAuthority('VIEW_INTERNAL_CONFIGURATION_SOURCE') and #configurationType.value == 'INTERNAL') "
            + "or (hasAuthority('VIEW_EXTERNAL_CONFIGURATION_SOURCE') and #configurationType.value == 'EXTERNAL')")
    public ResponseEntity<ConfigurationAnchorContainerDto> getAnchor(ConfigurationTypeDto configurationType) {
        final var sourceType = ConfigurationSourceType.valueOf(configurationType.getValue());
        return ok(configurationAnchorService.getConfigurationAnchor(sourceType)
                .map(configurationAnchorDtoConverter::convert)
                .map(this::wrapAnchor)
                .orElseGet(ConfigurationAnchorContainerDto::new));
    }

    @Override
    @PreAuthorize("(hasAuthority('GENERATE_SOURCE_ANCHOR'))")
    @AuditEventMethod(event = RE_CREATE_ANCHOR)
    public ResponseEntity<ConfigurationAnchorDto> reCreateAnchor(ConfigurationTypeDto configurationType) {
        return ok(configurationAnchorDtoConverter.convert(
                configurationAnchorService.recreateAnchor(ConfigurationSourceType.valueOf(configurationType.getValue()), true)
        ));
    }

    private ConfigurationAnchorContainerDto wrapAnchor(ConfigurationAnchorDto anchorDto) {
        final var wrapper = new ConfigurationAnchorContainerDto();
        wrapper.setAnchor(anchorDto);
        return wrapper;
    }
}
