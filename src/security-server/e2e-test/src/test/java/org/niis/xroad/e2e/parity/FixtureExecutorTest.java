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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixtureExecutorTest {

    private static final FixtureSpec TWO_SERVICES = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
            new RestService(List.of(ScenarioCatalogue.CONSUMER)),
            new RestService(List.of())));

    private static final class RecordingGateway implements AdminGateway {
        final List<String> calls = new ArrayList<>();
        Map<String, Integer> deleteStatus = Map.of();
        String failOnAddOf;
        String failOnGrantOf;

        @Override
        public String addRestDescription(String providerClientId, String backendUrl, String serviceCode) {
            if (serviceCode.equals(failOnAddOf)) {
                throw new IllegalStateException("add refused");
            }
            calls.add("add " + serviceCode);
            return "id-" + serviceCode;
        }

        @Override
        public String addWsdlDescription(String providerClientId, String wsdlUrl) {
            calls.add("add wsdl " + wsdlUrl);
            return "id-wsdl";
        }

        @Override
        public void enableDescription(String descriptionId) {
            calls.add("enable " + descriptionId);
        }

        @Override
        public void grantAccess(String providerClientId, String consumerClientId, String serviceCode) {
            if (serviceCode.equals(failOnGrantOf)) {
                throw new AssertionError("grant answered 500");
            }
            calls.add("grant " + serviceCode + " to " + consumerClientId);
        }

        @Override
        public int deleteDescription(String descriptionId) {
            calls.add("delete " + descriptionId);
            return deleteStatus.getOrDefault(descriptionId, 204);
        }
    }

    private final FixturePlan plan = new FixturePlanner("t1").plan("V09", TWO_SERVICES);

    @Test
    void setupRunsInPlanOrderAndCloseDeletesInReverse() {
        var gateway = new RecordingGateway();

        try (var ignored = new FixtureExecutor(gateway).open(plan)) {
            assertThat(gateway.calls).containsExactly(
                    "add pt1-v09",
                    "enable id-pt1-v09",
                    "grant pt1-v09 to " + ScenarioCatalogue.CONSUMER,
                    "add pt1-v09-2",
                    "enable id-pt1-v09-2");
        }

        assertThat(gateway.calls.subList(5, 7)).containsExactly("delete id-pt1-v09-2", "delete id-pt1-v09");
    }

    @Test
    void cleanupToleratesADescriptionThatIsAlreadyGone() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("id-pt1-v09-2", 404);

        var fixture = new FixtureExecutor(gateway).open(plan);
        fixture.close();

        assertThat(gateway.calls).contains("delete id-pt1-v09-2", "delete id-pt1-v09");
    }

    @Test
    void cleanupKeepsGoingAfterAFailedStepThenReportsIt() {
        var gateway = new RecordingGateway();
        gateway.deleteStatus = Map.of("id-pt1-v09-2", 500);
        var fixture = new FixtureExecutor(gateway).open(plan);

        assertThatThrownBy(fixture::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pt1-v09-2")
                .hasMessageContaining("500");
        assertThat(gateway.calls).contains("delete id-pt1-v09");
    }

    @Test
    void failedSetupRemovesWhatItCreatedAndRethrows() {
        var gateway = new RecordingGateway();
        gateway.failOnAddOf = "pt1-v09-2";

        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(plan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("add refused");
        assertThat(gateway.calls).endsWith("delete id-pt1-v09");
        assertThat(gateway.calls).doesNotContain("delete id-pt1-v09-2");
    }

    @Test
    void failedAdminAssertionDuringSetupRemovesWhatItCreatedAndRethrows() {
        var gateway = new RecordingGateway();
        gateway.failOnGrantOf = "pt1-v09";

        assertThatThrownBy(() -> new FixtureExecutor(gateway).open(plan))
                .isInstanceOf(AssertionError.class)
                .hasMessage("grant answered 500");
        assertThat(gateway.calls).endsWith("delete id-pt1-v09");
    }

    @Test
    void wsdlDescriptionIsAddedThroughItsFirstCodeAndDeletedOnClose() {
        var gateway = new RecordingGateway();
        var wsdlPlan = new FixturePlanner("t1").plan("V04", new FixtureSpec(ScenarioCatalogue.PROVIDER,
                List.of(new WsdlService(WsdlShape.PAIR, "", List.of(List.of(ScenarioCatalogue.CONSUMER), List.of())))));

        try (var ignored = new FixtureExecutor(gateway).open(wsdlPlan)) {
            assertThat(gateway.calls).containsExactly(
                    "add wsdl http://isrest:8080/acl-parity/wsdl/pair.wsdl?code1=pt1-v04&code2=pt1-v04-b",
                    "enable id-wsdl",
                    "grant pt1-v04 to " + ScenarioCatalogue.CONSUMER);
        }

        assertThat(gateway.calls).endsWith("delete id-wsdl");
        assertThat(gateway.calls).filteredOn(call -> call.startsWith("delete")).hasSize(1);
    }

    @Test
    void closeTwiceDeletesOnce() {
        var gateway = new RecordingGateway();
        var fixture = new FixtureExecutor(gateway).open(plan);

        fixture.close();
        fixture.close();

        assertThat(gateway.calls).filteredOn(call -> call.startsWith("delete")).hasSize(2);
    }
}
