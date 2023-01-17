/**
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.auth;

import ee.ria.xroad.common.util.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.auth.ApiKeyAuthenticationManager;
import org.niis.xroad.restapi.auth.GrantedAuthorityMapper;
import org.niis.xroad.restapi.auth.PamAuthenticationProvider;
import org.niis.xroad.restapi.auth.securityconfigurer.CookieAndSessionCsrfTokenRepository;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.restapi.openapi.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.servlet.http.Cookie;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Slf4j
public class CsrfWebMvcTest {
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";
    public static final String XSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_PARAM = "_csrf";

    private final String username = "xroad-user";
    private final String tokenValue = "token";
    private final CsrfToken csrfToken = new DefaultCsrfToken(XSRF_HEADER, CSRF_PARAM, tokenValue);
    private Set<String> userPermissions;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GrantedAuthorityMapper grantedAuthorityMapper;

    @SpyBean
    @Qualifier(PamAuthenticationProvider.FORM_LOGIN_PAM_AUTHENTICATION)
    private PamAuthenticationProvider pamAuthenticationProvider;

    @SpyBean
    private ApiKeyAuthenticationManager apiKeyAuthenticationManager;

    @Before
    // setup mock auth in the SecurityContext and mock both auth providers (form login and api-key)
    public void setup() {
        Set<GrantedAuthority> authorities = grantedAuthorityMapper
                .getAuthorities(Collections.singletonList(Role.XROAD_SECURITYSERVER_OBSERVER));
        userPermissions = authorities.stream()
                .filter(grantedAuthority -> !grantedAuthority.getAuthority()
                        .equals(Role.XROAD_SECURITYSERVER_OBSERVER.getGrantedAuthorityName()))
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        Authentication mockAuth = new UsernamePasswordAuthenticationToken(username, "pass", authorities);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(mockAuth);
        doReturn(mockAuth).when(pamAuthenticationProvider).authenticate(any());
        doReturn(mockAuth).when(apiKeyAuthenticationManager).authenticate(any());
    }

    /**
     * Test login with mocked authentication. Should return 200 with a valid CSRF token in a cookie
     *
     * @throws Exception
     */
    @Test
    public void login() throws Exception {
        MockHttpServletRequestBuilder mockRequest = MockMvcRequestBuilders.post("/login");
        mockMvc.perform(mockRequest)
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(cookie().exists(XSRF_COOKIE))
                .andReturn()
                .getResponse();
    }

    /**
     * Test getting user data for the currently logged in user. Uses mocked authentication in a mock session.
     *
     * @throws Exception
     */
    @Test
    public void getUser() throws Exception {
        User expectedUser = new User()
                .username(username)
                .roles(Collections.singleton(Role.XROAD_SECURITYSERVER_OBSERVER.getGrantedAuthorityName()))
                .permissions(userPermissions);
        String expectedUserJsonString = JsonUtils.getObjectWriter().writeValueAsString(expectedUser);
        MockHttpServletRequestBuilder mockRequest = MockMvcRequestBuilders.get("/api/v1/user")
                .session(getMockSession())
                .header(XSRF_HEADER, tokenValue)
                .cookie(new Cookie(XSRF_COOKIE, tokenValue));
        mockMvc.perform(mockRequest)
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().json(expectedUserJsonString));
    }

    /**
     * Test getting user data for the currently logged in user. Uses mocked authentication in a mock session.
     * XSRF header value should not match
     *
     * @throws Exception
     */
    @Test
    public void getUserCsrfHeaderFail() throws Exception {
        MockHttpServletRequestBuilder mockRequest = MockMvcRequestBuilders.get("/api/v1/user")
                .session(getMockSession())
                .header(XSRF_HEADER, "wrong-value")
                .cookie(new Cookie(XSRF_COOKIE, tokenValue));
        mockMvc.perform(mockRequest)
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    /**
     * Test getting user data for the currently logged in user. Uses mocked authentication in a mock session.
     * XSRF cookie value should not match
     *
     * @throws Exception
     */
    @Test
    public void getUserCsrfCookieFail() throws Exception {
        MockHttpServletRequestBuilder mockRequest = MockMvcRequestBuilders.get("/api/v1/user")
                .session(getMockSession())
                .header(XSRF_HEADER, tokenValue)
                .cookie(new Cookie(XSRF_COOKIE, "wrong-value"));
        mockMvc.perform(mockRequest)
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    /**
     * Test getting user data for the currently logged in user. This mimics an api user request so no cookies
     * should be returned
     *
     * @throws Exception
     */
    @Test
    public void getUserNoSession() throws Exception {
        User expectedUser = new User()
                .username(username)
                .roles(Collections.singleton(Role.XROAD_SECURITYSERVER_OBSERVER.getGrantedAuthorityName()))
                .permissions(userPermissions);
        String expectedUserJsonString = JsonUtils.getObjectWriter().writeValueAsString(expectedUser);
        MockHttpServletRequestBuilder mockRequest = MockMvcRequestBuilders.get("/api/v1/user");
        mockMvc.perform(mockRequest)
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().json(expectedUserJsonString))
                .andExpect(cookie().doesNotExist(XSRF_COOKIE));
    }

    private MockHttpSession getMockSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CookieAndSessionCsrfTokenRepository.SESSION_CSRF_TOKEN_ATTR_NAME, csrfToken);
        return session;
    }
}
