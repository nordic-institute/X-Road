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
import org.niis.xroad.e2e.parity.AdminCall.AddRestDescription;
import org.niis.xroad.e2e.parity.AdminCall.DeleteDescription;
import org.niis.xroad.e2e.parity.AdminCall.EnableDescription;
import org.niis.xroad.e2e.parity.AdminCall.GrantAccess;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.ServiceSpec;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixturePlannerTest {

    private static final FixtureSpec TWO_SERVICES = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
            new ServiceSpec(List.of(ScenarioCatalogue.CONSUMER, ScenarioCatalogue.OTHER_CONSUMER)),
            new ServiceSpec(List.of())));

    @Test
    void setupAddsEnablesAndGrantsEachServiceInOrder() {
        var plan = new FixturePlanner("ab12").plan("V09", TWO_SERVICES);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v09", "pab12-v09"),
                new EnableDescription("pab12-v09"),
                new GrantAccess(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.CONSUMER, "pab12-v09"),
                new GrantAccess(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.OTHER_CONSUMER, "pab12-v09"),
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v09-2", "pab12-v09-2"),
                new EnableDescription("pab12-v09-2"));
    }

    @Test
    void cleanupDeletesInReverseCreationOrder() {
        var plan = new FixturePlanner("ab12").plan("V09", TWO_SERVICES);

        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v09-2"),
                new DeleteDescription("pab12-v09"));
    }

    @Test
    void namesCarryTheRunTokenAndScenarioIdSoRunsAndScenariosNeverCollide() {
        var firstRun = new FixturePlanner("run1").plan("V01", TWO_SERVICES).serviceCodes();
        var secondRun = new FixturePlanner("run2").plan("V01", TWO_SERVICES).serviceCodes();
        var otherScenario = new FixturePlanner("run1").plan("V02", TWO_SERVICES).serviceCodes();

        assertThat(firstRun).doesNotContainAnyElementsOf(secondRun).doesNotContainAnyElementsOf(otherScenario);
        assertThat(firstRun).allMatch(code -> code.contains("run1") && code.contains("v01"));
    }

    @Test
    void neverTouchesTheSharedDescriptions() {
        var plan = new FixturePlanner("ab12").plan("V01", TWO_SERVICES);

        assertThat(plan.serviceCodes()).noneMatch(code -> code.equals("mock1") || code.equals("restapi"));
        assertThat(plan.setup()).extracting(AdminCall::serviceCode).doesNotContain("mock1", "restapi");
        assertThat(plan.cleanup()).extracting(AdminCall::serviceCode).doesNotContain("mock1", "restapi");
    }

    @Test
    void catalogueScenariosPlanOneServiceEach() {
        var planner = new FixturePlanner("ab12");

        for (var scenario : ScenarioCatalogue.all()) {
            assertThat(planner.plan(scenario.id(), scenario.fixture()).serviceCodes()).hasSize(1);
        }
    }
}
