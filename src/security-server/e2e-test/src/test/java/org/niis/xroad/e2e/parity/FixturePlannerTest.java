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
import org.niis.xroad.e2e.parity.AdminCall.AddEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.AddRestDescription;
import org.niis.xroad.e2e.parity.AdminCall.AddWsdlDescription;
import org.niis.xroad.e2e.parity.AdminCall.DeleteDescription;
import org.niis.xroad.e2e.parity.AdminCall.DeleteEndpoint;
import org.niis.xroad.e2e.parity.AdminCall.EnableDescription;
import org.niis.xroad.e2e.parity.AdminCall.GrantAccess;
import org.niis.xroad.e2e.parity.AdminCall.GrantEndpointAccess;
import org.niis.xroad.e2e.parity.Scenario.EndpointSpec;
import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.RestService;
import org.niis.xroad.e2e.parity.Scenario.WsdlService;
import org.niis.xroad.e2e.parity.Scenario.WsdlShape;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixturePlannerTest {

    private static final FixtureSpec TWO_SERVICES = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
            new RestService(List.of(ScenarioCatalogue.CONSUMER, ScenarioCatalogue.OTHER_CONSUMER)),
            new RestService(List.of())));

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
    void catalogueScenariosPlanDistinctCodesAndTheRequestedServiceExists() {
        var planner = new FixturePlanner("ab12");

        for (var scenario : ScenarioCatalogue.all()) {
            var codes = planner.plan(scenario.id(), scenario.fixture()).serviceCodes();

            assertThat(codes).as(scenario.id()).isNotEmpty().doesNotHaveDuplicates();
            assertThat(scenario.checks()).as(scenario.id()).isNotEmpty()
                    .allSatisfy(check -> assertThat(check.request().serviceIndex()).isLessThan(codes.size()));
        }
    }

    @Test
    void singleWsdlAppendsTheCodeTailAndGrantsTheCode() {
        var spec = wsdlSpec(WsdlShape.SINGLE, ".x", List.of(ScenarioCatalogue.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V129", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v129.x");
        assertThat(plan.setup()).containsExactly(
                new AddWsdlDescription(ScenarioCatalogue.PROVIDER,
                        "http://isrest:8080/acl-parity/wsdl/single.wsdl?code=pab12-v129.x", "pab12-v129.x"),
                new EnableDescription("pab12-v129.x"),
                new GrantAccess(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.CONSUMER, "pab12-v129.x"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v129.x"));
    }

    @Test
    void pairWsdlOffersTwoCodesAndGrantsEachOnItsOwn() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new WsdlService(WsdlShape.PAIR, "",
                List.of(List.of(ScenarioCatalogue.CONSUMER), List.of()))));

        var plan = new FixturePlanner("ab12").plan("V04", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v04", "pab12-v04-b");
        assertThat(plan.setup()).containsExactly(
                new AddWsdlDescription(ScenarioCatalogue.PROVIDER,
                        "http://isrest:8080/acl-parity/wsdl/pair.wsdl?code1=pab12-v04&code2=pab12-v04-b", "pab12-v04"),
                new EnableDescription("pab12-v04"),
                new GrantAccess(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.CONSUMER, "pab12-v04"));
        assertThat(plan.cleanup()).containsExactly(new DeleteDescription("pab12-v04"));
    }

    @Test
    void versionsWsdlOffersOneCodeFromTheVersionsStub() {
        var spec = wsdlSpec(WsdlShape.VERSIONS, "", List.of(ScenarioCatalogue.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V128", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v128");
        assertThat(plan.setup().get(0)).isEqualTo(new AddWsdlDescription(ScenarioCatalogue.PROVIDER,
                "http://isrest:8080/acl-parity/wsdl/versions.wsdl?code=pab12-v128", "pab12-v128"));
    }

    @Test
    void unversionedWsdlOffersOneCodeFromTheUnversionedStub() {
        var spec = wsdlSpec(WsdlShape.SINGLE_UNVERSIONED, "", List.of(ScenarioCatalogue.CONSUMER));

        var plan = new FixturePlanner("ab12").plan("V132a", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v132a");
        assertThat(plan.setup().get(0)).isEqualTo(new AddWsdlDescription(ScenarioCatalogue.PROVIDER,
                "http://isrest:8080/acl-parity/wsdl/single_unversioned.wsdl?code=pab12-v132a", "pab12-v132a"));
    }

    @Test
    void restAndWsdlMixKeepsCodeOrderAndCleansUpEachDescriptionOnceInReverse() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(
                new RestService(List.of()),
                new WsdlService(WsdlShape.PAIR, "", List.of(List.of(), List.of()))));

        var plan = new FixturePlanner("ab12").plan("V09", spec);

        assertThat(plan.serviceCodes()).containsExactly("pab12-v09", "pab12-v09-2", "pab12-v09-2-b");
        assertThat(plan.cleanup()).containsExactly(
                new DeleteDescription("pab12-v09-2"),
                new DeleteDescription("pab12-v09"));
    }

    @Test
    void endpointsAreCreatedAndGrantedAfterTheBaseGrantsAndDeletedInReverseBeforeTheDescription() {
        var spec = new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new RestService(List.of(),
                List.of(new EndpointSpec("GET", "/foo", List.of(ScenarioCatalogue.CONSUMER)),
                        new EndpointSpec("*", "/**", List.of())))));

        var plan = new FixturePlanner("ab12").plan("V22", spec);

        assertThat(plan.setup()).containsExactly(
                new AddRestDescription(ScenarioCatalogue.PROVIDER, "http://isrest:8080/acl-parity/pab12-v22", "pab12-v22"),
                new EnableDescription("pab12-v22"),
                new AddEndpoint("pab12-v22", "GET", "/foo"),
                new GrantEndpointAccess("pab12-v22", "GET", "/foo", ScenarioCatalogue.CONSUMER),
                new AddEndpoint("pab12-v22", "*", "/**"));
        assertThat(plan.cleanup()).containsExactly(
                new DeleteEndpoint("pab12-v22", "*", "/**"),
                new DeleteEndpoint("pab12-v22", "GET", "/foo"),
                new DeleteDescription("pab12-v22"));
    }

    @Test
    void onlyEndpointsTheScenarioCreatedAreDeleted() {
        for (var scenario : ScenarioCatalogue.all()) {
            var plan = new FixturePlanner("ab12").plan(scenario.id(), scenario.fixture());
            var created = plan.setup().stream().filter(AddEndpoint.class::isInstance).count();

            assertThat(plan.cleanup().stream().filter(DeleteEndpoint.class::isInstance).count())
                    .as(scenario.id()).isEqualTo(created);
        }
    }

    @Test
    void wsdlServiceNeedsOneGrantListPerShapeCode() {
        assertThatThrownBy(() -> new WsdlService(WsdlShape.PAIR, "", List.of(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PAIR");
    }

    private static FixtureSpec wsdlSpec(WsdlShape shape, String codeTail, List<String> granted) {
        return new FixtureSpec(ScenarioCatalogue.PROVIDER, List.of(new WsdlService(shape, codeTail, List.of(granted))));
    }
}
