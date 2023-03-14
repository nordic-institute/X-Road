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
import org.niis.xroad.cs.admin.api.dto.TokenInfo;
import org.niis.xroad.cs.admin.api.dto.TokenLoginRequest;
import org.niis.xroad.cs.admin.api.service.TokensService;
import org.niis.xroad.cs.admin.rest.api.mapper.TokenDtoMapper;
import org.niis.xroad.cs.openapi.TokensApi;
import org.niis.xroad.cs.openapi.model.TokenDto;
import org.niis.xroad.cs.openapi.model.TokenPasswordDto;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.LOGIN_TOKEN;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.LOGOUT_TOKEN;
import static org.springframework.http.ResponseEntity.ok;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class TokensApiController implements TokensApi {

    private final TokensService tokensService;
    private final TokenDtoMapper tokenDtoMapper;

    @Override
    @PreAuthorize(
            "hasAuthority('VIEW_INTERNAL_CONFIGURATION_SOURCE') or hasAuthority('VIEW_EXTERNAL_CONFIGURATION_SOURCE')"
    )
    public ResponseEntity<List<TokenDto>> getTokens() {
        Set<TokenInfo> tokens = tokensService.getTokens();
        return ok(tokens.stream()
                .map(tokenDtoMapper::toTarget)
                .collect(toList()));
    }

    @Override
    @AuditEventMethod(event = LOGIN_TOKEN)
    @PreAuthorize("hasAuthority('ACTIVATE_TOKEN')")
    public ResponseEntity<TokenDto> loginToken(String id, TokenPasswordDto tokenPasswordDto) {
        final TokenInfo token = tokensService.login(new TokenLoginRequest(id, tokenPasswordDto.getPassword()));
        return ok(tokenDtoMapper.toTarget(token));
    }

    @Override
    @AuditEventMethod(event = LOGOUT_TOKEN)
    @PreAuthorize("hasAuthority('DEACTIVATE_TOKEN')")
    public ResponseEntity<TokenDto> logoutToken(String id) {
        return ok(tokenDtoMapper.toTarget(tokensService.logout(id)));
    }
}
