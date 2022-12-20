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

package org.niis.xroad.centralserver.restapi.openapi;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.centralserver.openapi.SigningKeysApi;
import org.niis.xroad.centralserver.openapi.model.ConfigurationSigningKeyAddDto;
import org.niis.xroad.centralserver.openapi.model.ConfigurationSigningKeyDto;
import org.niis.xroad.centralserver.openapi.model.ConfigurationTypeDto;
import org.niis.xroad.centralserver.restapi.converter.ConfigurationSigningKeysDtoConverter;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeyService;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.http.ResponseEntity.ok;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class SigningKeysController implements SigningKeysApi {

    private final ConfigurationSigningKeyService signingKeyService;
    private final ConfigurationSigningKeysDtoConverter configurationSigningKeysDtoConverter;

    @Override
    public ResponseEntity<Void> activateKey(String id) {
        throw new NotImplementedException("activateKey not implemented yet");
    }

    @Override
    @PreAuthorize("hasAuthority('GENERATE_SIGNING_KEY')")
    @AuditEventMethod(event = RestApiAuditEvent.GENERATE_KEY)
    public ResponseEntity<ConfigurationSigningKeyDto> addKey(ConfigurationTypeDto configurationType,
                                                             ConfigurationSigningKeyAddDto configurationSigningKeyAddDto) {
        return ok(configurationSigningKeysDtoConverter.convert(
                signingKeyService.addKey(configurationType.getValue(),
                        configurationSigningKeyAddDto.getTokenId(),
                        configurationSigningKeyAddDto.getKeyLabel())
        ));
    }

    @Override
    public ResponseEntity<Void> deleteKey(String id, Boolean ignoreWarnings) {
        throw new NotImplementedException("deleteKey not implemented yet");
    }
}
