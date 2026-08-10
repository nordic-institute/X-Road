/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.config;
import org.niis.xroad.common.properties.config.XRoadConfigOverrides;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

/**
 * Explicit {@code xroad.*} DSL overrides needed by the test application context. Replaces the production
 * override channel (empty) with a test-scoped one so tests run against test-appropriate values without the
 * DSL reading yaml/env. Component-scanned into every full-context test.
 */
@Configuration(proxyBeanMethods = false)
class TestXRoadConfigOverridesConfig {

    private static final String MAIL_NOTIFICATION = """
            host: mailpit
            port: 587
            username: testusername
            password: testpassword
            contacts:
              'DEV:COM:1234': member1@example.org
            """;

    @Bean
    @Primary
    XRoadConfigOverrides testXRoadConfigOverrides() {
        return new XRoadConfigOverrides(Map.of(
                "xroad.common-rpc.use-tls", "false",
                "xroad.proxy-ui-api.key-management-api-whitelist", "127.0.0.0/8, ::1",
                "xroad.proxy-ui-api.regular-api-whitelist", "0.0.0.0/0, ::/0",
                "xroad.mail-notification", MAIL_NOTIFICATION));
    }
}
