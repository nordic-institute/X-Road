/*
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
package org.niis.xroad.securityserver.restapi;

import ee.ria.xroad.common.db.DatabaseCtx;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.api.throttle.test.ParallelMockMvcExecutor;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.niis.xroad.serverconf.impl.ServerConfDatabaseConfig.SERVER_CONF_DB_CTX;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * application test
 */
@SuppressWarnings("java:S2925")
@SpringBootTest(properties = {
        "xroad.proxy-ui-api.rate-limit-requests-per-minute=10",
        "xroad.proxy-ui-api.rate-limit-requests-per-second=5"})
@ActiveProfiles({"nontest", "test"})
@AutoConfigureTestDatabase
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class ApplicationIpRateLimitTest {
    private static final int RUNS_PER_MINUTE = 11;
    private static final int RUNS_PER_SECOND = 6;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    MonitorRpcClient monitorClient;
    @MockitoBean
    ConfClientRpcClient confClientRpcClient;
    @MockitoBean
    ProxyRpcClient proxyRpcClient;

    @MockitoBean(name = SERVER_CONF_DB_CTX)
    DatabaseCtx databaseCtx;

    @MockitoBean
    GlobalConfProvider globalConfProvider;

    @PostConstruct
    void setGlobalSecurityContext() {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    @Nested
    @DirtiesContext
    class PerMinuteTests {
        @RepeatedTest(RUNS_PER_MINUTE)
        @WithMockUser(authorities = "VIEW_VERSION")
        void shouldTriggerRateLimitPerMin(RepetitionInfo repetitionInfo) throws Exception {
            var expectedStatus = repetitionInfo.getCurrentRepetition() == RUNS_PER_MINUTE
                    ? MockMvcResultMatchers.status().is(TOO_MANY_REQUESTS.value()) : MockMvcResultMatchers.status().is2xxSuccessful();
            mvc.perform(get("http://localhost:4000/api/v1/system/version"))
                    .andExpect(expectedStatus);

            TimeUnit.MILLISECONDS.sleep(500);
        }
    }

    @Test
    @DirtiesContext
    @WithMockUser(authorities = "VIEW_VERSION")
    void shouldTriggerRateLimitPerSec() throws Exception {
        try (var executor = new ParallelMockMvcExecutor(mvc)) {
            executor.run(() -> (get("http://localhost:4000/api/v1/system/version")), RUNS_PER_SECOND);

            List<Integer> result = executor.getExecuted().stream()
                    .map(MvcResult::getResponse)
                    .map(MockHttpServletResponse::getStatus)
                    .collect(Collectors.toList());

            assertThat(result).asInstanceOf(LIST).containsOnlyOnce(TOO_MANY_REQUESTS.value());
        }
    }
}
