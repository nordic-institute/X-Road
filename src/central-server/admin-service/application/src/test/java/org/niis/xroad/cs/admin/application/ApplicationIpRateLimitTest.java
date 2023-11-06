/*
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
package org.niis.xroad.cs.admin.application;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.concurrent.TimeUnit;

import static org.niis.xroad.restapi.openapi.ControllerUtil.API_V1_PREFIX;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SuppressWarnings("java:S2925")
@SpringBootTest(
        classes = {ApplicationIpRateLimitTest.TestIpRateLimitController.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"xroad.admin-service.rate-limit-requests-per-minute=10", "xroad.admin-service.rate-limit-requests-per-second=5"}
)
@ComponentScan({"org.niis.xroad.cs.admin.core.config"})
@ActiveProfiles({"test"})
@AutoConfigureMockMvc
class ApplicationIpRateLimitTest {
    private static final int RUNS_PER_MINUTE = 11;
    private static final int RUNS_PER_SECOND = 6;

    @Autowired
    private MockMvc mvc;

    @PostConstruct
    void setGlobalSecurityContext() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    @Nested
    @DirtiesContext
    class PerMinuteTests {
        @Test
        @WithMockUser(authorities = {"VIEW_VERSION"})
        void shouldTriggerRateLimitPerMin() throws Exception {
            for (int i = 1; i <= RUNS_PER_MINUTE; i++) {
                var expectedStatus = i == RUNS_PER_MINUTE
                        ? MockMvcResultMatchers.status().is(TOO_MANY_REQUESTS.value()) : MockMvcResultMatchers.status().is2xxSuccessful();
                mvc.perform(get(API_V1_PREFIX + "/test")).andExpect(expectedStatus).andReturn();
                TimeUnit.MILLISECONDS.sleep(200);
            }
        }
    }

    @Nested
    @DirtiesContext
    class PerSecondTests {
        @Test
        @WithMockUser(authorities = {"VIEW_VERSION"})
        void shouldTriggerRateLimitPerSec() throws Exception {
            for (int i = 1; i <= RUNS_PER_SECOND; i++) {
                var expectedStatus = i == RUNS_PER_SECOND
                        ? MockMvcResultMatchers.status().is(TOO_MANY_REQUESTS.value()) : MockMvcResultMatchers.status().is2xxSuccessful();
                mvc.perform(get(API_V1_PREFIX + "/test")).andExpect(expectedStatus).andReturn();
            }
        }
    }

    @Controller
    @PreAuthorize("denyAll")
    @RequestMapping(API_V1_PREFIX)
    static class TestIpRateLimitController {
        @GetMapping("/test")
        @PreAuthorize("hasAuthority('VIEW_VERSION')")
        ResponseEntity<Void> test() {
            return ResponseEntity.ok().build();
        }
    }
}

