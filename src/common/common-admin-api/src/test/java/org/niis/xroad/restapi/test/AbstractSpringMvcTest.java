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
package org.niis.xroad.restapi.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.restapi.auth.ApiKeyAuthenticationManager;
import org.niis.xroad.restapi.config.ApiCachingConfiguration;
import org.niis.xroad.restapi.config.LimitRequestSizesFilter;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.niis.xroad.restapi.controller.CommonModuleEndpointPaths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.unit.DataSize;

import javax.transaction.Transactional;

/**
 * Base class for {@link  MockMvc} based test cases.
 */
@ExtendWith(SpringExtension.class)
@AutoConfigureTestDatabase
@WithMockUser
@Transactional
@AutoConfigureMockMvc
@EnableAutoConfiguration
@SpringBootTest(classes = AbstractSpringMvcTest.CommonRestApiTestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class AbstractSpringMvcTest {

    @MockBean
    AuditEventLoggingFacade auditEventLoggingFacade;
    @MockBean
    AuditDataHelper auditDataHelper;
    @MockBean
    AuditEventHelper auditEventHelper;
    @MockBean
    ApiKeyAuthenticationManager apiKeyAuthenticationManager;
    @Autowired
    protected CommonModuleEndpointPaths commonModuleEndpointPaths;

    @Autowired
    protected MockMvc mockMvc;

    @EnableCaching
    @Configuration
    @ComponentScan("org.niis.xroad.restapi")
    public static class CommonRestApiTestConfiguration {

        @Bean
        public ApiCachingConfiguration.Config cacheConfig() {
            return new ApiCachingConfiguration.Config() {
                @Override
                public int getCacheDefaultTtl() {
                    return 5;
                }

                @Override
                public int getCacheApiKeyTtl() {
                    return 5;
                }
            };
        }

        @Bean
        public LimitRequestSizesFilter.Config requestSizeConfig() {
            return new LimitRequestSizesFilter.Config() {
                @Override
                public DataSize getRequestSizeLimitRegular() {
                    return DataSize.ofMegabytes(1);
                }

                @Override
                public DataSize getRequestSizeLimitBinaryUpload() {
                    return DataSize.ofMegabytes(5);
                }
            };
        }
    }
}
