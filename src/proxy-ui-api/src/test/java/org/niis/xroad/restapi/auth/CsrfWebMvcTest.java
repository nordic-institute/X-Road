package org.niis.xroad.restapi.auth;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.auth.securityconfigurer.CookieAndSessionCsrfTokenRepository;
import org.niis.xroad.restapi.openapi.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.servlet.http.Cookie;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.niis.xroad.restapi.auth.PamAuthenticationProvider.FORM_LOGIN_PAM_AUTHENTICATION;
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

    private String username = "xroad-user";
    private String tokenValue = "token";
    private CsrfToken csrfToken = new DefaultCsrfToken(XSRF_HEADER, CSRF_PARAM, tokenValue);

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    @Qualifier(FORM_LOGIN_PAM_AUTHENTICATION)
    private PamAuthenticationProvider pamAuthenticationProvider;

    @SpyBean
    private ApiKeyAuthenticationManager apiKeyAuthenticationManager;

    @Before
    public void setup() {
        // setup mock auth in the SecurityContext and mock both auth providers (form login and api-key)
        Authentication mockAuth = new UsernamePasswordAuthenticationToken(username, "pass");
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(mockAuth);
        doReturn(mockAuth).when(pamAuthenticationProvider).authenticate(any());
        doReturn(mockAuth).when(apiKeyAuthenticationManager).authenticate(any());
    }

    /**
     * Test login with mocked authentication. Should return 200 with a valid CSRF token in a cookie
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
     * @throws Exception
     */
    @Test
    public void getUser() throws Exception {
        User expectedUser = new User().username(username);
        String expectedUserJsonString = new Gson().toJson(expectedUser);
        MockHttpServletRequestBuilder mockRequest = MockMvcRequestBuilders.get("/api/user")
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
     * @throws Exception
     */
    @Test
    public void getUserCsrfHeaderFail() throws Exception {
        MockHttpServletRequestBuilder mockRequest = MockMvcRequestBuilders.get("/api/user")
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
     * @throws Exception
     */
    @Test
    public void getUserCsrfCookieFail() throws Exception {
        MockHttpServletRequestBuilder mockRequest = MockMvcRequestBuilders.get("/api/user")
                .session(getMockSession())
                .header(XSRF_HEADER, tokenValue)
                .cookie(new Cookie(XSRF_COOKIE, "wrong-value"));
        mockMvc.perform(mockRequest)
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    private MockHttpSession getMockSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CookieAndSessionCsrfTokenRepository.SESSION_CSRF_TOKEN_ATTR_NAME, csrfToken);
        return session;
    }
}
