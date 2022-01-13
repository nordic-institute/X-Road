/**
 * The MIT License
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
import org.niis.xroad.restapi.util.SecurityHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;

import static org.niis.xroad.restapi.auth.AuthenticationIpWhitelist.KEY_MANAGEMENT_API_WHITELIST;

/**
 * PAM authentication provider configuration.
 * Configures PAM authentication beans for key management API and for form login,
 * with different configurations.
 */
@Slf4j
@Configuration
@Profile("!devtools-test-auth")
public class PamAuthenticationProviderConfig {

    public static final String KEY_MANAGEMENT_PAM_AUTHENTICATION = "keyManagementPam";
    public static final String FORM_LOGIN_PAM_AUTHENTICATION = "formLoginPam";

    // allow all ipv4 and ipv6
    private static final Iterable<String> FORM_LOGIN_IP_WHITELIST =
            Arrays.asList("::/0", "0.0.0.0/0");

    private final GrantedAuthorityMapper grantedAuthorityMapper;
    private final AuditEventLoggingFacade auditEventLoggingFacade;
    private final SecurityHelper securityHelper;

    /**
     * constructor
     */
    public PamAuthenticationProviderConfig(GrantedAuthorityMapper grantedAuthorityMapper,
            AuditEventLoggingFacade auditEventLoggingFacade, SecurityHelper securityHelper) {
        this.grantedAuthorityMapper = grantedAuthorityMapper;
        this.auditEventLoggingFacade = auditEventLoggingFacade;
        this.securityHelper = securityHelper;
    }

    /**
     * PAM authentication for form login, with corresponding IP whitelist
     * @return
     */
    @Bean(FORM_LOGIN_PAM_AUTHENTICATION)
    public PamAuthenticationProvider formLoginPamAuthentication() {
        AuthenticationIpWhitelist formLoginWhitelist = new AuthenticationIpWhitelist();
        formLoginWhitelist.setWhitelistEntries(FORM_LOGIN_IP_WHITELIST);
        return new PamAuthenticationProvider(formLoginWhitelist, grantedAuthorityMapper, RestApiAuditEvent.FORM_LOGIN,
                auditEventLoggingFacade, securityHelper);
    }

    /**
     * PAM authentication for key management API, with corresponding IP whitelist
     * @return
     */
    @Bean(KEY_MANAGEMENT_PAM_AUTHENTICATION)
    public PamAuthenticationProvider keyManagementWhitelist(
            @Qualifier(KEY_MANAGEMENT_API_WHITELIST) AuthenticationIpWhitelist keyManagementWhitelist) {
        return new PamAuthenticationProvider(keyManagementWhitelist, grantedAuthorityMapper,
                RestApiAuditEvent.KEY_MANAGEMENT_PAM_LOGIN, auditEventLoggingFacade, securityHelper);
    }
}

