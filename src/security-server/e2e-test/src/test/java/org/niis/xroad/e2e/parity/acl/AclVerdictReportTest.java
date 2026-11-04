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
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.e2e.parity.acl.AclScenario.Check;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestRequest;
import org.niis.xroad.e2e.parity.acl.AclScenarioVerdicts.Outcome;
import org.niis.xroad.e2e.parity.acl.VerdictPoller.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AclVerdictReportTest {

    private static final RestRequest REQUEST = new RestRequest(AclParticipants.CONSUMER, 0, "GET", "/a");

    private static Outcome outcome(Verdict expected, Verdict observed) {
        return new Outcome(new Check(REQUEST, expected),
                new Result(observed, "reply", observed == expected, 1, Duration.ZERO));
    }

    @Test
    void aRowWhoseRequestsAllReachedTheirVerdictsMatches() {
        var line = AclVerdictReport.line("V26",
                List.of(outcome(Verdict.ALLOWED, Verdict.ALLOWED), outcome(Verdict.ACCESS_DENIED, Verdict.ACCESS_DENIED)));

        assertThat(line).isEqualTo("V26\tMATCH\tALLOWED,ACCESS_DENIED\tALLOWED,ACCESS_DENIED");
    }

    @Test
    void aRowWithARequestThatMissedItsVerdictMismatchesAndNamesWhatItGot() {
        var line = AclVerdictReport.line("V66",
                List.of(outcome(Verdict.ALLOWED, Verdict.ALLOWED), outcome(Verdict.ALLOWED, Verdict.AGREEMENT_TOKEN_REJECTED)));

        assertThat(line).isEqualTo("V66\tMISMATCH\tALLOWED,ALLOWED\tALLOWED,AGREEMENT_TOKEN_REJECTED");
    }

    @Test
    void aRowWhoseReplyReachedItsVerdictWithoutItsNoticeMismatches() {
        var outcome = new Outcome(new Check(REQUEST, Verdict.SERVICE_DISABLED, "back at noon"),
                new Result(Verdict.SERVICE_DISABLED, "service is disabled", true, 1, Duration.ZERO));

        assertThat(AclVerdictReport.line("V120", List.of(outcome)))
                .isEqualTo("V120\tMISMATCH\tSERVICE_DISABLED\tSERVICE_DISABLED");
    }

    @Test
    void aRowWhoseReplyCarriesItsNoticeMatches() {
        var outcome = new Outcome(new Check(REQUEST, Verdict.SERVICE_DISABLED, "back at noon"),
                new Result(Verdict.SERVICE_DISABLED, "service is disabled: back at noon", true, 1, Duration.ZERO));

        assertThat(AclVerdictReport.line("V120", List.of(outcome)))
                .isEqualTo("V120\tMATCH\tSERVICE_DISABLED\tSERVICE_DISABLED");
    }

    @Test
    void aRowWithNoRequestsPolledMismatches() {
        assertThat(AclVerdictReport.line("V99", List.of())).isEqualTo("V99\tMISMATCH\t\t");
    }

    @Test
    void anAdminGrantRowMatchesWhenTheAnswerIsTheExpectedOne() {
        assertThat(AclVerdictReport.adminGrantLine("V12", "409 duplicate_accessright", "409 duplicate_accessright"))
                .isEqualTo("V12\tMATCH\t409 duplicate_accessright\t409 duplicate_accessright");
        assertThat(AclVerdictReport.adminGrantLine("V13", "400 invalid_service_client_id", "200 null"))
                .startsWith("V13\tMISMATCH\t");
    }

    @Test
    void theFileHasTheHeaderAndOneLinePerAppendedRow(@TempDir Path dir) throws IOException {
        var file = dir.resolve("nested/verdicts.tsv");

        AclVerdictReport.start(file);
        AclVerdictReport.append(file, "V01\tMATCH\tALLOWED\tALLOWED");
        AclVerdictReport.append(file, "V02\tMISMATCH\tACCESS_DENIED\tUNKNOWN_MEMBER");

        assertThat(Files.readAllLines(file)).containsExactly(AclVerdictReport.HEADER,
                "V01\tMATCH\tALLOWED\tALLOWED", "V02\tMISMATCH\tACCESS_DENIED\tUNKNOWN_MEMBER");
    }

    @Test
    void startingAgainEmptiesTheFileOfAnEarlierRun(@TempDir Path dir) throws IOException {
        var file = dir.resolve("verdicts.tsv");
        AclVerdictReport.start(file);
        AclVerdictReport.append(file, "V01\tMATCH\tALLOWED\tALLOWED");

        AclVerdictReport.start(file);

        assertThat(Files.readAllLines(file)).containsExactly(AclVerdictReport.HEADER);
    }
}
