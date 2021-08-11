package org.niis.xroad.centralserver.restapi.util;

import java.util.Collections;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class TestUtils {

    public static final String API_KEY_HEADER_PREFIX = "X-Road-apikey token=";
    public static final String API_KEY_TOKEN_WITH_ALL_ROLES = "d56e1ca7-4134-4ed4-8030-5f330bdb602a";

    public static void mockServletRequestAttributes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    /**
     * Add Authentication header for API key with all roles
     *
     * @param testRestTemplate
     */
    public static void addApiKeyAuthorizationHeader(TestRestTemplate testRestTemplate) {
        addApiKeyAuthorizationHeader(testRestTemplate, API_KEY_TOKEN_WITH_ALL_ROLES);
    }

    /**
     * Add Authentication header for specific API key
     *
     * @param testRestTemplate
     * @param apiKeyToken API key token
     */
    public static void addApiKeyAuthorizationHeader(TestRestTemplate testRestTemplate,
                                                    String apiKeyToken) {
        testRestTemplate.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add("Authorization",
                                    API_KEY_HEADER_PREFIX + apiKeyToken);
                    return execution.execute(request, body);
                }));
    }
}
