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
package org.niis.xroad.restapi.auth.securityconfigurer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

/**
 * Security configuration follows https://spring.io/guides/gs/securing-web/
 * adapted to multiple security configs:
 * https://docs.spring.io/spring-security/site/docs/current/reference/htmlsingle/#multiple-httpsecurity
 *
 * Users are either PAM users (linux username and login, see PamAuthenticationProvider)
 * or dummy in-memory users (user/password etc), depending on which AuthenticationProvider is
 * used.
 *
 * Uses form login and session cookie based auth for rest apis with Vue frontend
 *
 * Uses http basic authentication for manage api-keys api.
 *
 * Uses authentication tokens for rest apis, when session cookies are not available.
 *
 * Static resources such as images and javascripts are open without authentication.
 *
 * Security is configured with from 4 different WebSecurityConfigurerAdapters.
 * Authentication configurations are used in the following order:
 * - ManageApiKeysWebSecurityConfigurerAdapter, @Order(1), matches /api/v1/api-keys/**
 * - ApiWebSecurityConfigurerAdapter, @Order(2), matches /api/**
 * - StaticAssetsWebSecurityConfig, @Order(3), matches static asset paths such as /js/**
 * - FormLoginWebSecurityConfigurerAdapter, @Order(100), matches any URL (denies /api/**)
 * - and finally, this configurer defines global constants for configuration order and
 * sets up shared configuration such as web security debugging
 */
@EnableWebSecurity
@EnableGlobalMethodSecurity(proxyTargetClass = true, prePostEnabled = true)
@Slf4j
@Order(MultiAuthWebSecurityConfig.GLOBAL_CONFIGURATION_SECURITY_ORDER)
public class MultiAuthWebSecurityConfig extends WebSecurityConfigurerAdapter {
    public static final int API_KEY_MANAGEMENT_SECURITY_ORDER = 1;
    public static final int API_SECURITY_ORDER = 2;
    public static final int STATIC_ASSETS_SECURITY_ORDER = 3;
    public static final int FORM_LOGIN_SECURITY_ORDER = 100;
    public static final int GLOBAL_CONFIGURATION_SECURITY_ORDER = 200;

    @Value("${web.security.debug:false}")
    private Boolean isWebSecurityDebugEnabled;

    /**
     * Toggle debugging based on property value
     * @param web
     * @throws Exception
     */
    public void configure(WebSecurity web) throws Exception {
        web.debug(isWebSecurityDebugEnabled);
    }
}
