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
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.cs.admin.api.service.ConfigurationService;
import org.niis.xroad.cs.admin.rest.api.converter.ConfigurationPartsDtoConverter;
import org.niis.xroad.cs.openapi.ConfigurationPartsApi;
import org.niis.xroad.cs.openapi.model.ConfigurationPartDto;
import org.niis.xroad.cs.openapi.model.ConfigurationTypeDto;
import org.niis.xroad.cs.openapi.model.TrustedAnchorDto;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.ResponseEntity.ok;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ConfigurationPartsController implements ConfigurationPartsApi {

    private final ConfigurationService configurationService;
    private final ConfigurationPartsDtoConverter configurationPartsDtoConverter;

    @Override
    public ResponseEntity<Resource> downloadConfigurationParts(ConfigurationTypeDto configurationType,
                                                               String contentIdentifier, Integer version) {
        throw new NotImplementedException("downloadConfigurationParts not implemented yet");
    }

    @Override
    @PreAuthorize("(hasAuthority('VIEW_INTERNAL_CONFIGURATION_SOURCE') and #configurationType.value == 'INTERNAL') "
            + "or (hasAuthority('VIEW_EXTERNAL_CONFIGURATION_SOURCE') and #configurationType.value == 'EXTERNAL')")
    public ResponseEntity<Set<ConfigurationPartDto>> getConfigurationParts(ConfigurationTypeDto configurationType) {
        return ok(configurationService.getConfigurationParts(configurationType.getValue()).stream()
                .map(configurationPartsDtoConverter::convert)
                .collect(Collectors.toSet()));
    }

    @Override
    public ResponseEntity<TrustedAnchorDto> uploadConfigurationParts(ConfigurationTypeDto configurationType,
                                                                     String contentIdentifier, MultipartFile file) {
        throw new NotImplementedException("uploadConfigurationParts not implemented yet");
    }
}
