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

package org.niis.xroad.opmonitor.test.glue;

import io.cucumber.java.en.Step;
import io.cucumber.java.en.When;
import org.assertj.core.api.AssertionsForClassTypes;
import org.niis.xroad.common.test.glue.BaseStepDefs;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.test.container.OpMonitorClientHolder;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.ServiceIdConverter;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;


public class OpMonitorStepDefs extends BaseStepDefs {

    @Autowired
    protected OpMonitorClientHolder clientHolder;

    protected OpMonitorClient opMonitorClient;


    List<OperationalDataInterval> operationalDataIntervals;

    @Step("op-monitor client is initialized")
    public void opMonitorClientInitialized() {
        if (opMonitorClient == null) {
            opMonitorClient = clientHolder.initializeOpMonitorClient();
        }
    }

    @Step("user asks for traffic data of last {int} hour(s) in {int} minute intervals")
    public void getTrafficDataLastHour(int windowsInHours, int interval) {
        Instant now = Instant.now();
        Long from = now.minus(windowsInHours, ChronoUnit.HOURS).toEpochMilli();
        Long to = now.toEpochMilli();
        operationalDataIntervals = opMonitorClient.getOperationalDataIntervals(from, to, interval, null, null, null);
    }

    @Step("user asks for traffic data of last hour in {int} minute intervals where security server was {string}")
    public void getTrafficDataLastHourWithSecurityServerType(int interval, String securityServerType) {
        Instant now = Instant.now();
        Long from = now.minus(1, ChronoUnit.HOURS).toEpochMilli();
        Long to = now.toEpochMilli();
        operationalDataIntervals = opMonitorClient.getOperationalDataIntervals(from, to, interval, securityServerType, null, null);
    }

    @Step("user asks for traffic data of last hour in {int} minute intervals where one of the participants was {string}")
    public void getTrafficDataLastHourByClient(int interval, String memberId) {
        Instant now = Instant.now();
        Long from = now.minus(1, ChronoUnit.HOURS).toEpochMilli();
        Long to = now.toEpochMilli();
        operationalDataIntervals = opMonitorClient.getOperationalDataIntervals(from, to, interval, null,
                new ClientIdConverter().convertId(memberId), null);
    }

    @Step("user asks for traffic data of last hour in {int} minute intervals where requested service was {string}")
    public void getTrafficDataLastHourByService(int interval, String serviceId) {
        Instant now = Instant.now();
        Long from = now.minus(1, ChronoUnit.HOURS).toEpochMilli();
        Long to = now.toEpochMilli();
        operationalDataIntervals = opMonitorClient.getOperationalDataIntervals(from, to, interval, null, null,
                new ServiceIdConverter().convertId(serviceId));
    }

    @When("user asks for traffic data of last two hour in {int} minute intervals where {string} was {string}")
    public void getTrafficDataLastTwoHourBySecurityServerTypeAndMember(int interval, String securityServerType, String memberId) {
        Instant now = Instant.now();
        Long from = now.minus(2, ChronoUnit.HOURS).toEpochMilli();
        Long to = now.toEpochMilli();
        operationalDataIntervals = opMonitorClient.getOperationalDataIntervals(from,
                to,
                interval,
                securityServerType,
                new ClientIdConverter().convertId(memberId),
                null);
    }

    @When("user asks for traffic data of last two hour in {int} minute intervals where one of the participants was {string} and requested"
            + " service was {string}")
    public void getTrafficDataLastTwoHoursByMemberAndService(int interval, String memberId, String serviceId) {
        Instant now = Instant.now();
        Long from = now.minus(2, ChronoUnit.HOURS).toEpochMilli();
        Long to = now.toEpochMilli();
        operationalDataIntervals = opMonitorClient.getOperationalDataIntervals(from,
                to,
                interval,
                null,
                new ClientIdConverter().convertId(memberId),
                new ServiceIdConverter().convertId(serviceId));
    }

    @Step("the query returns {int} successful requests and {int} failed requests")
    public void validateResultsHavingCorrectTotalCounts(int successful, int failed) {
        Long successTotal = operationalDataIntervals.stream().map(OperationalDataInterval::getSuccessCount).reduce(0L, Long::sum);
        Long failureTotal = operationalDataIntervals.stream().map(OperationalDataInterval::getFailureCount).reduce(0L, Long::sum);
        assertThat(successTotal).isEqualTo(successful);
        assertThat(failureTotal).isEqualTo(failed);
    }

    @Step("the query returns intervals with correct success and failure counts")
    public void validateResultsHavingIntervalsWithCorrectCounts() {
        Long intervalWithZeroSuccessAndFailure = operationalDataIntervals.stream()
                .filter(interval -> interval.getSuccessCount() == 0L && interval.getFailureCount() == 0L)
                .count();
        Long intervalWithOneSuccessAndOneFailure = operationalDataIntervals.stream()
                .filter(interval -> interval.getSuccessCount() == 1L && interval.getFailureCount() == 1L)
                .count();
        Long intervalWithTwoSuccessAndZeroFailure = operationalDataIntervals.stream()
                .filter(interval -> interval.getSuccessCount() == 2L && interval.getFailureCount() == 0L)
                .count();
        Long intervalWithTwoSuccessAndOneFailure = operationalDataIntervals.stream()
                .filter(interval -> interval.getSuccessCount() == 2L && interval.getFailureCount() == 1L)
                .count();
        AssertionsForClassTypes.assertThat(intervalWithOneSuccessAndOneFailure).isEqualTo(1);
        AssertionsForClassTypes.assertThat(intervalWithTwoSuccessAndZeroFailure).isEqualTo(1);
        AssertionsForClassTypes.assertThat(intervalWithTwoSuccessAndOneFailure).isEqualTo(1);
        AssertionsForClassTypes.assertThat(intervalWithZeroSuccessAndFailure).isGreaterThanOrEqualTo(1);
    }

}
