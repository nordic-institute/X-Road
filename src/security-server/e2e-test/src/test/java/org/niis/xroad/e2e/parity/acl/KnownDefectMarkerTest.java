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
import org.niis.xroad.e2e.parity.acl.AclScenario.Check;
import org.niis.xroad.e2e.parity.acl.AclScenario.FixtureSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.WaitClass;
import org.niis.xroad.e2e.parity.acl.AclScenarioVerdicts.Outcome;
import org.niis.xroad.e2e.parity.acl.KnownDefectMarker.Status;
import org.niis.xroad.e2e.parity.acl.VerdictPoller.Result;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnownDefectMarkerTest {

    private static final Check CHECK = new Check(
            new RestRequest(AclParticipants.CONSUMER, 0, "GET", "/x"), Verdict.ACCESS_DENIED);

    private static final Check CONTROL = new Check(
            new RestRequest(AclParticipants.CONSUMER, 0, "GET", "/y"), Verdict.ALLOWED);

    private static final Check FAULT_CHECK = new Check(
            new RestRequest(AclParticipants.CONSUMER, 0, "GET", "/z"), Verdict.INTERNAL_ERROR);

    private static final Check DISABLED_CHECK = new Check(
            new RestRequest(AclParticipants.CONSUMER, 0, "GET", "/w"), Verdict.SERVICE_DISABLED);

    private static AclScenario row(String knownDefect, Check... checks) {
        return new AclScenario("T1", "test row", new FixtureSpec(AclParticipants.PROVIDER, List.of()),
                List.of(checks), WaitClass.ACL_CACHE, knownDefect);
    }

    private static AclScenario row(String knownDefect) {
        return row(knownDefect, CHECK);
    }

    private static Outcome outcome(Check check, Verdict got) {
        return new Outcome(check, new Result(got, null, got == check.expected(), 1, Duration.ZERO));
    }

    private static List<Outcome> outcomes(Verdict got) {
        return List.of(outcome(CHECK, got));
    }

    @Test
    void markedRowThatIsAllowedWhereDenialIsSafeIsAnExpectedFailureNamingTheFinding() {
        var judgement = KnownDefectMarker.judge(row("D7"), outcomes(Verdict.ALLOWED));

        assertThat(judgement.status()).isEqualTo(Status.EXPECTED_FAILURE);
        assertThat(judgement.message()).contains("T1").contains("expected failure").contains("D7");
    }

    @Test
    void markedRowWithAnErrorVerdictFailsWithTheUsualMessage() {
        var judgement = KnownDefectMarker.judge(row("D7"), outcomes(Verdict.ERROR));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("expected ACCESS_DENIED but got ERROR")
                .doesNotContain("defect fixed");
    }

    @Test
    void markedRowRejectedByTheConsumerFailsWithTheUsualMessage() {
        var judgement = KnownDefectMarker.judge(row("D7"), outcomes(Verdict.CONSUMER_REJECTED));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("expected ACCESS_DENIED but got CONSUMER_REJECTED")
                .doesNotContain("defect fixed");
    }

    @Test
    void markedRowFailingACheckThatIsNotTheSafeVerdictFailsEvenWhenTheSafeCheckIsAllowed() {
        var judgement = KnownDefectMarker.judge(row("D7", CHECK, CONTROL),
                List.of(outcome(CHECK, Verdict.ALLOWED), outcome(CONTROL, Verdict.ERROR)));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("expected ALLOWED but got ERROR");
    }

    @Test
    void markedRowWithAPassingControlAndAnAllowedSafeCheckIsAnExpectedFailure() {
        var judgement = KnownDefectMarker.judge(row("D7", CHECK, CONTROL),
                List.of(outcome(CHECK, Verdict.ALLOWED), outcome(CONTROL, Verdict.ALLOWED)));

        assertThat(judgement.status()).isEqualTo(Status.EXPECTED_FAILURE);
    }

    @Test
    void markedRowAllowedWhereAnotherVerdictWasExpectedFailsWithTheUsualMessage() {
        var judgement = KnownDefectMarker.judge(row("D7", FAULT_CHECK),
                List.of(outcome(FAULT_CHECK, Verdict.ALLOWED)));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("expected INTERNAL_ERROR but got ALLOWED")
                .doesNotContain("expected failure");
    }

    @Test
    void markedRowWithAnInternalErrorOnTheSafeCheckFailsWithTheUsualMessage() {
        var judgement = KnownDefectMarker.judge(row("D7"), outcomes(Verdict.INTERNAL_ERROR));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("expected ACCESS_DENIED but got INTERNAL_ERROR")
                .doesNotContain("defect fixed");
    }

    @Test
    void markedRowThatIsAllowedWhereAServiceDisabledAnswerIsSafeIsAnExpectedFailureNamingTheFinding() {
        var judgement = KnownDefectMarker.judge(row("D7", DISABLED_CHECK),
                List.of(outcome(DISABLED_CHECK, Verdict.ALLOWED)));

        assertThat(judgement.status()).isEqualTo(Status.EXPECTED_FAILURE);
        assertThat(judgement.message()).contains("T1").contains("expected failure").contains("D7");
    }

    @Test
    void markedRowThatMeetsTheServiceDisabledAnswerFailsAskingForTheMarkerToGo() {
        var judgement = KnownDefectMarker.judge(row("D7", DISABLED_CHECK),
                List.of(outcome(DISABLED_CHECK, Verdict.SERVICE_DISABLED)));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("T1").contains("defect fixed, remove marker").contains("D7");
    }

    @Test
    void markedRowWithAnErrorWhereAServiceDisabledAnswerWasExpectedFailsWithTheUsualMessage() {
        var judgement = KnownDefectMarker.judge(row("D7", DISABLED_CHECK),
                List.of(outcome(DISABLED_CHECK, Verdict.ERROR)));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("expected SERVICE_DISABLED but got ERROR")
                .doesNotContain("defect fixed").doesNotContain("expected failure");
    }

    @Test
    void markedRowThatMeetsTheSafeVerdictFailsAskingForTheMarkerToGo() {
        var judgement = KnownDefectMarker.judge(row("D7"), outcomes(Verdict.ACCESS_DENIED));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("T1").contains("defect fixed, remove marker").contains("D7");
    }

    @Test
    void unmarkedRowPassesWhenEveryRequestMatched() {
        var judgement = KnownDefectMarker.judge(row(null), outcomes(Verdict.ACCESS_DENIED));

        assertThat(judgement.status()).isEqualTo(Status.PASSED);
        assertThat(judgement.message()).isEmpty();
    }

    @Test
    void unmarkedRowFailsWithTheVerdictMessageWhenARequestMissed() {
        var judgement = KnownDefectMarker.judge(row(null), outcomes(Verdict.ALLOWED));

        assertThat(judgement.status()).isEqualTo(Status.FAILED);
        assertThat(judgement.message()).contains("expected ACCESS_DENIED but got ALLOWED");
    }
}
