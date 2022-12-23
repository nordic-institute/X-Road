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
import org.niis.xroad.cs.admin.rest.api.converter.ConfigurationAnchorDtoConverter;
import org.niis.xroad.cs.openapi.ConfigurationSourceAnchorsApi;
import org.niis.xroad.cs.openapi.model.ConfigurationAnchorDto;
import org.niis.xroad.cs.openapi.model.ConfigurationTypeDto;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.http.ResponseEntity.ok;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ConfigurationSourceAnchorsController implements ConfigurationSourceAnchorsApi {

    private final ConfigurationService configurationService;
    private final ConfigurationAnchorDtoConverter configurationAnchorDtoConverter;

    @Override
    public ResponseEntity<Resource> downloadAnchor(ConfigurationTypeDto configurationType) {
        throw new NotImplementedException("downloadAnchor not implemented yet");
    }

    @Override
    @PreAuthorize("(hasAuthority('VIEW_INTERNAL_CONFIGURATION_SOURCE') and #configurationType.value == 'INTERNAL') "
            + "or (hasAuthority('VIEW_EXTERNAL_CONFIGURATION_SOURCE') and #configurationType.value == 'EXTERNAL')")
    public ResponseEntity<ConfigurationAnchorDto> getAnchor(ConfigurationTypeDto configurationType) {
        return ok(configurationAnchorDtoConverter.convert(
                configurationService.getConfigurationAnchor(configurationType.getValue())
        ));
    }

    @Override
    public ResponseEntity<ConfigurationAnchorDto> reCreateAnchor(ConfigurationTypeDto configurationType) {
        throw new NotImplementedException("reCreateAnchor not implemented yet");
    }
}
