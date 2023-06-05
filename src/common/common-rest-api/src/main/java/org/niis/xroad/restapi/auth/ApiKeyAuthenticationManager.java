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
package org.niis.xroad.restapi.auth;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.domain.PersistentApiKeyType;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.niis.xroad.restapi.util.SecurityHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * AuthenticationManager which expects Authentication.principal to be
 * an api key (prepared by RequestHeaderAuthenticationFilter)
 */

@Component
@Slf4j
public class ApiKeyAuthenticationManager implements AuthenticationManager {

    private final ApiKeyAuthenticationHelper apiKeyAuthenticationHelper;
    private final AuthenticationHeaderDecoder authenticationHeaderDecoder;
    private final GrantedAuthorityMapper permissionMapper;
    private final AuthenticationIpWhitelist authenticationIpWhitelist;
    private final AuditEventLoggingFacade auditEventLoggingFacade;
    private final SecurityHelper securityHelper;

    @Autowired
    public ApiKeyAuthenticationManager(ApiKeyAuthenticationHelper apiKeyAuthenticationHelper,
            AuthenticationHeaderDecoder authenticationHeaderDecoder,
            GrantedAuthorityMapper permissionMapper,
            @Qualifier(AuthenticationIpWhitelist.REGULAR_API_WHITELIST)
                    AuthenticationIpWhitelist authenticationIpWhitelist,
            AuditEventLoggingFacade auditEventLoggingFacade, SecurityHelper securityHelper) {
        this.apiKeyAuthenticationHelper = apiKeyAuthenticationHelper;
        this.authenticationHeaderDecoder = authenticationHeaderDecoder;
        this.permissionMapper = permissionMapper;
        this.authenticationIpWhitelist = authenticationIpWhitelist;
        this.auditEventLoggingFacade = auditEventLoggingFacade;
        this.securityHelper = securityHelper;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            authenticationIpWhitelist.validateIpAddress(authentication);
            String encodedAuthenticationHeader = (String) authentication.getPrincipal();
            String apiKeyValue = authenticationHeaderDecoder.decodeApiKey(encodedAuthenticationHeader);
            PersistentApiKeyType key;

            try {
                key = apiKeyAuthenticationHelper.getForPlaintextKey(apiKeyValue);
            } catch (ApiKeyService.ApiKeyNotFoundException notFound) {
                throw new BadCredentialsException("The API key was not found or not the expected value.");
            } catch (Exception e) {
                throw new BadCredentialsException("Unknown problem when getting API key", e);
            }
            Set<Role> roles = key.getRoles();
            PreAuthenticatedAuthenticationToken authenticationWithGrants =
                    new PreAuthenticatedAuthenticationToken(createPrincipal(key),
                            authentication.getCredentials(),
                            permissionMapper.getAuthorities(roles));
            return authenticationWithGrants;
        } catch (Exception e) {
            auditEventLoggingFacade.auditLogFail(RestApiAuditEvent.API_KEY_AUTHENTICATION, e);
            throw e;
        }
    }

    /**
     * Encode api key ID into the principal, so that we can use it with auditing to history table
     * @param persistentApiKey
     * @return
     */
    private String createPrincipal(PersistentApiKeyType persistentApiKey) {
        return "api-key-" + persistentApiKey.getId();
    }
}
