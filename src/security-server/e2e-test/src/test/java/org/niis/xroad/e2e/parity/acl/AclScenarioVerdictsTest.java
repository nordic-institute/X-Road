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

package org.niis.xroad.e2e.parity.acl;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestRequest;
import org.niis.xroad.e2e.parity.acl.VerdictPoller.Observation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.allowed;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.denied;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.endpointOnly;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phase;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phased;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restService;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.row;

class AclScenarioVerdictsTest {

    private static final Duration TIMEOUT = Duration.ZERO;
    private static final Duration INTERVAL = Duration.ofSeconds(1);

    private static final AclScenario ROW = row("V26", endpointOnly("GET", "/foo/*"),
            allowed("GET", "/foo/x"), allowed("GET", "/foo/"), denied("GET", "/foo/x/y"), denied("GET", "/foo"))
            .as("endpoint row");

    private static Observation observe(Verdict verdict) {
        return new Observation(verdict, "reply for " + verdict);
    }

    @Test
    void everyRequestOfTheRowIsPolledEvenAfterAnEarlierOneMissed() {
        var polled = new ArrayList<String>();

        var outcomes = AclScenarioVerdicts.poll(new VerdictPoller(), ROW, request -> {
            polled.add(request.describe());
            return observe(Verdict.ALLOWED);
        }, TIMEOUT, INTERVAL);

        assertThat(polled).containsExactly("GET /foo/x", "GET /foo/", "GET /foo/x/y", "GET /foo");
        assertThat(outcomes).hasSize(4);
    }

    @Test
    void messageNamesTheFailingRequestAndTheVerdictItGot() {
        var outcomes = AclScenarioVerdicts.poll(new VerdictPoller(), ROW,
                request -> observe(((RestRequest) request).path().equals("/foo/x/y")
                        ? Verdict.ALLOWED : expectedFor(request)),
                TIMEOUT, INTERVAL);

        var message = AclScenarioVerdicts.failureMessage(ROW.id(), outcomes);

        assertThat(message).hasValueSatisfying(text -> assertThat(text)
                .contains("V26 request 3 of 4 (GET /foo/x/y)")
                .contains("expected ACCESS_DENIED but got ALLOWED")
                .doesNotContain("request 1 of 4")
                .doesNotContain("request 2 of 4")
                .doesNotContain("request 4 of 4"));
    }

    @Test
    void messageListsEveryFailingRequest() {
        var outcomes = AclScenarioVerdicts.poll(new VerdictPoller(), ROW,
                request -> observe(Verdict.ERROR), TIMEOUT, INTERVAL);

        var message = AclScenarioVerdicts.failureMessage(ROW.id(), outcomes);

        assertThat(message).hasValueSatisfying(text -> assertThat(text.lines().toList()).hasSize(4));
    }

    @Test
    void noMessageWhenEveryRequestMatched() {
        var outcomes = AclScenarioVerdicts.poll(new VerdictPoller(), ROW,
                AclScenarioVerdictsTest::expectedObservation, TIMEOUT, INTERVAL);

        assertThat(AclScenarioVerdicts.failureMessage(ROW.id(), outcomes)).isEmpty();
    }

    @Test
    void matchedVerdictWithoutTheNoticeInTheReplyIsReported() {
        var request = new RestRequest(AclParticipants.CONSUMER, 0, "GET", "/a");
        var check = new AclScenario.Check(request, Verdict.SERVICE_DISABLED, "back at noon");
        var outcome = new AclScenarioVerdicts.Outcome(check,
                new VerdictPoller.Result(Verdict.SERVICE_DISABLED, "body without it", true, 1, Duration.ZERO));

        var message = AclScenarioVerdicts.failureMessage("V120", List.of(outcome));

        assertThat(message).hasValueSatisfying(text -> assertThat(text).contains("back at noon"));
    }

    @Test
    void matchedVerdictWithTheNoticeInTheReplyIsNotReported() {
        var request = new RestRequest(AclParticipants.CONSUMER, 0, "GET", "/a");
        var check = new AclScenario.Check(request, Verdict.SERVICE_DISABLED, "back at noon");
        var outcome = new AclScenarioVerdicts.Outcome(check,
                new VerdictPoller.Result(Verdict.SERVICE_DISABLED, "body back at noon", true, 1, Duration.ZERO));

        assertThat(AclScenarioVerdicts.failureMessage("V120", List.of(outcome))).isEmpty();
    }

    @Test
    void phasedRowPollsItsChecksThenAppliesTheChangeThenPollsTheChecksOfThePhase() {
        var row = grantRow();
        var events = new ArrayList<String>();
        var granted = new boolean[1];

        var outcomes = AclScenarioVerdicts.pollPhased(new VerdictPoller(), row, index -> {
            events.add("apply " + index);
            granted[0] = true;
        }, request -> {
            events.add("probe");
            return observe(granted[0] ? Verdict.ALLOWED : Verdict.ACCESS_DENIED);
        }, TIMEOUT, INTERVAL);

        assertThat(events).containsExactly("probe", "apply 0", "probe");
        assertThat(outcomes).extracting(outcome -> outcome.result().matched()).containsExactly(true, true);
        assertThat(outcomes).extracting(outcome -> outcome.check().expected())
                .containsExactly(Verdict.ACCESS_DENIED, Verdict.ALLOWED);
    }

    @Test
    void phasedRowThatMissesItsVerdictBeforeTheChangeAppliesNoChange() {
        var row = grantRow();
        var applied = new ArrayList<Integer>();

        var outcomes = AclScenarioVerdicts.pollPhased(new VerdictPoller(), row, applied::add,
                request -> observe(Verdict.ALLOWED), TIMEOUT, INTERVAL);

        assertThat(applied).isEmpty();
        assertThat(outcomes).extracting(outcome -> outcome.result().matched()).containsExactly(false);
    }

    @Test
    void phasedRowThatMissesItsVerdictInAPhaseAppliesNoLaterPhase() {
        var base = grantRow();
        var twoPhases = new AclScenario("VX", "test row", base.fixture(), base.checks(),
                List.of(base.phases().get(0), base.phases().get(0)), base.waitClass());
        var applied = new ArrayList<Integer>();

        var outcomes = AclScenarioVerdicts.pollPhased(new VerdictPoller(), twoPhases, applied::add,
                request -> observe(Verdict.ACCESS_DENIED), TIMEOUT, INTERVAL);

        assertThat(applied).containsExactly(0);
        assertThat(outcomes).extracting(outcome -> outcome.result().matched()).containsExactly(true, false);
    }

    @Test
    void eachPhaseIsAppliedByItsOwnIndexBeforeItsOwnChecksArePolled() {
        var row = phased("V11", restService(List.of()), List.of(denied("GET", "/a")),
                phase(new AclScenario.Grant(0, AclParticipants.CONSUMER), allowed("GET", "/b")),
                phase(new AclScenario.Revoke(0, AclParticipants.CONSUMER), denied("GET", "/c"))).as("two phases");
        var events = new ArrayList<String>();
        var appliedPhase = new int[] {-1};

        var outcomes = AclScenarioVerdicts.pollPhased(new VerdictPoller(), row, index -> {
            events.add("apply " + index);
            appliedPhase[0] = index;
        }, request -> {
            events.add("probe " + request.describe());
            return observe(appliedPhase[0] == 0 ? Verdict.ALLOWED : Verdict.ACCESS_DENIED);
        }, TIMEOUT, INTERVAL);

        assertThat(events).containsExactly("probe GET /a", "apply 0", "probe GET /b", "apply 1", "probe GET /c");
        assertThat(outcomes).extracting(outcome -> outcome.result().matched()).containsExactly(true, true, true);
        assertThat(outcomes).extracting(outcome -> outcome.check().request().describe())
                .containsExactly("GET /a", "GET /b", "GET /c");
    }

    @Test
    void aScenarioWithNoOutcomesFails() {
        assertThat(AclScenarioVerdicts.failureMessage("V00", List.of()))
                .hasValueSatisfying(text -> assertThat(text).contains("V00").contains("no request was checked"));
    }

    private static AclScenario grantRow() {
        return phased("V10", restService(List.of()), List.of(denied("GET", "/a")),
                phase(new AclScenario.Grant(0, AclParticipants.CONSUMER), allowed("GET", "/a"))).as("grant row");
    }

    private static Observation expectedObservation(AclScenario.RequestSpec request) {
        return observe(expectedFor(request));
    }

    private static Verdict expectedFor(AclScenario.RequestSpec request) {
        List<AclScenario.Check> checks = ROW.checks();
        return checks.stream().filter(check -> check.request().equals(request)).findFirst().orElseThrow().expected();
    }
}
