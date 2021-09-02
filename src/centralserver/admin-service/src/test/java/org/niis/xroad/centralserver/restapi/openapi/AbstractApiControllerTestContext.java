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
package org.niis.xroad.centralserver.restapi.openapi;

import org.junit.After;
import org.junit.Before;
import org.niis.xroad.centralserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.centralserver.restapi.repository.SystemParameterRepository;
import org.niis.xroad.centralserver.restapi.service.InitializationService;
import org.niis.xroad.centralserver.restapi.service.TokenPinValidator;
import org.niis.xroad.centralserver.restapi.util.TestUtils;
import org.niis.xroad.restapi.converter.PublicApiKeyDataConverter;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Base for all api controller tests that need mocked beans in the application context. All api controller
 * test classes inheriting this will share the same mock bean configuration, and have a common
 * Spring Application Context therefore drastically reducing the execution time of the tests.
 *
 * Service layer mocking strategy varies
 * - real implementations are used for services not defined as @MockBean or @SpyBean here
 * - mocks are always used for services defined as @MockBeans
 * - mocking depends on a case by case basis when @SpyBean is used. Some tests use 100% real implementation, others
 * mock some parts
 *
 * Mocks the usual untestable facades (such as SignerProxyFacade) via {@link AbstractFacadeMockingTestContext}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractApiControllerTestContext extends AbstractFacadeMockingTestContext {

    @MockBean
    public ApiKeyService apiKeyService;

    @MockBean
    public PublicApiKeyDataConverter publicApiKeyDataConverter;

    @SpyBean
    public InitializationService initializationService;

    @SpyBean
    public SystemParameterRepository systemParameterRepository;

    @SpyBean
    public TokenPinValidator tokenPinValidator;

    /**
     * Add mock servlet request attributes to the RequestContextHolder. This is because testing a controller method
     * by directly calling it is not actually considered a real request. Some tests will need a 'real' request
     * (e.g. request scoped beans will not work without an existing request)
     */
    @Before
    public void mockServlet() {
        TestUtils.mockServletRequestAttributes();
    }

    @After
    public void cleanUpServlet() {
        RequestContextHolder.resetRequestAttributes();
    }
}
