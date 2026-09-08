/*
 * The MIT License
 *
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
package org.niis.xroad.opmonitor.test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.test.container.OpMonitorContainerSetup;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.ServiceIdConverter;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * 0100 - Op monitoring data in intervals: queries op-monitor over gRPC for operational-data intervals,
 * filtered by security server type, member, and/or service, against the traffic data seeded by
 * {@code test-data/baseline-intTest.xml}.
 */
@ExtendWith(ApiStackExtension.class)
@SuppressWarnings("checkstyle:magicnumber")
class OpMonitorIntTest {

    private static final String CLIENT_SERVER_TYPE = "Client";
    private static final String MEMBER_SYSTEM1 = "DEV:COM:1234:System1";
    private static final String SERVICE_GET_TOP_SECRET = "DEV:COM:1234:Service9:getTopSecret.v2";
    private static final String SERVICE_XROAD_GET_RANDOM = "DEV:COM:4321:Service1:xroadGetRandom.v1";

    private OpMonitorClient opMonitorClient;

    @BeforeEach
    void opMonitorClientInitialized(OpMonitorContainerSetup setup) {
        given("op-monitor client is initialized", () -> opMonitorClient = setup.opMonitorClient());
    }

    @Test
    @DisplayName("Query with no optional filters")
    void queryWithNoOptionalFilters() {
        var intervals = when("user asks for traffic data of last 1 hour in 30 minute intervals",
                () -> trafficData(1, 30, null, null, null));
        then("the query returns 4 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 4, 1));
    }

    @Test
    @DisplayName("Query with security server type")
    void queryWithSecurityServerType() {
        var intervals = when("user asks for traffic data of last hour in 45 minute intervals where security server was "
                        + "\"Client\"",
                () -> trafficData(1, 45, CLIENT_SERVER_TYPE, null, null));
        then("the query returns 2 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 2, 1));
    }

    @Test
    @DisplayName("Query with member")
    void queryWithMember() {
        var intervals = when("user asks for traffic data of last hour in 60 minute intervals where one of the participants "
                        + "was \"DEV:COM:1234:System1\"",
                () -> trafficData(1, 60, null, MEMBER_SYSTEM1, null));
        then("the query returns 3 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 3, 1));
    }

    @Test
    @DisplayName("Query with service")
    void queryWithService() {
        var intervals = when("user asks for traffic data of last hour in 30 minute intervals where requested service was "
                        + "\"DEV:COM:1234:Service9:getTopSecret.v2\"",
                () -> trafficData(1, 30, null, null, SERVICE_GET_TOP_SECRET));
        then("the query returns 1 successful requests and 0 failed requests",
                () -> assertTotals(intervals, 1, 0));
    }

    @Test
    @DisplayName("Query with security server type and member")
    void queryWithSecurityServerTypeAndMember() {
        var intervals = when("user asks for traffic data of last two hour in 30 minute intervals where \"Client\" was "
                        + "\"DEV:COM:1234:System1\"",
                () -> trafficData(2, 30, CLIENT_SERVER_TYPE, MEMBER_SYSTEM1, null));
        then("the query returns 2 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 2, 1));
    }

    @Test
    @DisplayName("Query with member and service")
    void queryWithMemberAndService() {
        var intervals = when("user asks for traffic data of last two hour in 30 minute intervals where one of the "
                        + "participants was \"DEV:COM:1234:System1\" and requested service was "
                        + "\"DEV:COM:4321:Service1:xroadGetRandom.v1\"",
                () -> trafficData(2, 30, null, MEMBER_SYSTEM1, SERVICE_XROAD_GET_RANDOM));
        then("the query returns 2 successful requests and 1 failed requests",
                () -> assertTotals(intervals, 2, 1));
    }

    @Test
    @DisplayName("Results in different time buckets")
    void resultsInDifferentTimeBuckets() {
        var intervals = when("user asks for traffic data of last 2 hour in 30 minute intervals",
                () -> trafficData(2, 30, null, null, null));
        then("the query returns intervals with correct success and failure counts",
                () -> assertIntervalBuckets(intervals));
    }

    private List<OperationalDataInterval> trafficData(int windowInHours, int intervalMinutes,
            String securityServerType, String memberId, String serviceId) {
        var now = Instant.now();
        var from = now.minus(windowInHours, ChronoUnit.HOURS).toEpochMilli();
        var to = now.toEpochMilli();
        return opMonitorClient.getOperationalDataIntervals(from, to, intervalMinutes, securityServerType,
                memberId == null ? null : new ClientIdConverter().convertId(memberId),
                serviceId == null ? null : new ServiceIdConverter().convertId(serviceId));
    }

    private void assertTotals(List<OperationalDataInterval> intervals, long successful, long failed) {
        var successTotal = intervals.stream().map(OperationalDataInterval::getSuccessCount).reduce(0L, Long::sum);
        var failureTotal = intervals.stream().map(OperationalDataInterval::getFailureCount).reduce(0L, Long::sum);
        assertThat(successTotal).isEqualTo(successful);
        assertThat(failureTotal).isEqualTo(failed);
    }

    private void assertIntervalBuckets(List<OperationalDataInterval> intervals) {
        var zeroSuccessZeroFailure = intervals.stream()
                .filter(interval -> interval.getSuccessCount() == 0L && interval.getFailureCount() == 0L)
                .count();
        var oneSuccessOneFailure = intervals.stream()
                .filter(interval -> interval.getSuccessCount() == 1L && interval.getFailureCount() == 1L)
                .count();
        var twoSuccessZeroFailure = intervals.stream()
                .filter(interval -> interval.getSuccessCount() == 2L && interval.getFailureCount() == 0L)
                .count();
        var twoSuccessOneFailure = intervals.stream()
                .filter(interval -> interval.getSuccessCount() == 2L && interval.getFailureCount() == 1L)
                .count();
        assertThat(oneSuccessOneFailure).isEqualTo(1);
        assertThat(twoSuccessZeroFailure).isEqualTo(1);
        assertThat(twoSuccessOneFailure).isEqualTo(1);
        assertThat(zeroSuccessZeroFailure).isGreaterThanOrEqualTo(1);
    }
}
