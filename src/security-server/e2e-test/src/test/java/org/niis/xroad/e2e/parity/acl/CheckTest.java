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
import org.niis.xroad.e2e.parity.acl.AclScenario.Expectation;
import org.niis.xroad.e2e.parity.acl.AclScenario.ListClientsRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.MetaserviceRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.MonitoringRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.RequestSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckTest {

    private static final RequestSpec REST = new RestRequest(AclParticipants.CONSUMER, 0, "GET", "/a");

    private static MetaserviceRequest listMethods(Expectation expectation) {
        return MetaserviceRequest.rest(AclParticipants.CONSUMER, "listMethods", MetaserviceRequest.NO_SERVICE,
                expectation);
    }

    private static MonitoringRequest healthData(Expectation expectation) {
        return new MonitoringRequest(AclParticipants.CONSUMER, SoapRequests.HEALTH_DATA, null, expectation);
    }

    @Test
    void errorVerdictsCannotBeExpected() {
        assertThatThrownBy(() -> new Check(REST, Verdict.ERROR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Check(REST, Verdict.INTERNAL_ERROR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Check(REST, Verdict.ERROR, "notice"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Check(REST, Verdict.AGREEMENT_TOKEN_REJECTED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Check(REST, Verdict.CONSUMER_FAILED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namedVerdictsCanBeExpected() {
        assertThatCode(() -> {
            new Check(REST, Verdict.ALLOWED);
            new Check(REST, Verdict.ACCESS_DENIED);
            new Check(REST, Verdict.CONSUMER_REJECTED);
            new Check(REST, Verdict.SERVICE_DISABLED, "notice");
        }).doesNotThrowAnyException();
    }

    @Test
    void allowedNeedsATextTheReplyHasToCarryWhenTheRequestCarriesAnExpectation() {
        var onlyForbidden = Expectation.listing(List.of(), List.of(1));

        assertThatThrownBy(() -> new Check(listMethods(Expectation.NONE), Verdict.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Check(listMethods(onlyForbidden), Verdict.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Check(healthData(Expectation.NONE), Verdict.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Check(new ListClientsRequest(Expectation.NONE), Verdict.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowedWithARequiredTextIsAccepted() {
        assertThatCode(() -> {
            new Check(listMethods(Expectation.carrying("p-a")), Verdict.ALLOWED);
            new Check(listMethods(Expectation.listing(List.of(0), List.of())), Verdict.ALLOWED);
            new Check(healthData(Expectation.masked("shown", "hidden")), Verdict.ALLOWED);
            new Check(new ListClientsRequest(Expectation.carrying("member")), Verdict.ALLOWED);
        }).doesNotThrowAnyException();
    }

    @Test
    void aDenialNeedsNoText() {
        assertThat(new Check(listMethods(Expectation.NONE), Verdict.ACCESS_DENIED).expected())
                .isEqualTo(Verdict.ACCESS_DENIED);
        assertThat(new Check(healthData(Expectation.NONE), Verdict.UNKNOWN_SERVICE).expected())
                .isEqualTo(Verdict.UNKNOWN_SERVICE);
    }

    @Test
    void aCheckWithoutADspExpectationExpectsTheSameVerdictInEveryMode() {
        var check = new Check(REST, Verdict.UNKNOWN_SERVICE);

        assertThat(check.forMode(AclMode.LEGACY).expected()).isEqualTo(Verdict.UNKNOWN_SERVICE);
        assertThat(check.forMode(AclMode.DSP).expected()).isEqualTo(Verdict.UNKNOWN_SERVICE);
        assertThat(check.forMode(AclMode.DSP_REFUSAL).expected()).isEqualTo(Verdict.UNKNOWN_SERVICE);
    }

    @Test
    void aDspExpectationReplacesTheLegacyVerdictInEveryModeThatNegotiates() {
        var check = new Check(REST, Verdict.UNKNOWN_SERVICE).inDsp(Verdict.ACCESS_DENIED);

        assertThat(check.forMode(AclMode.LEGACY).expected()).isEqualTo(Verdict.UNKNOWN_SERVICE);
        assertThat(check.forMode(AclMode.DSP).expected()).isEqualTo(Verdict.ACCESS_DENIED);
        assertThat(check.forMode(AclMode.DSP_REFUSAL).expected()).isEqualTo(Verdict.ACCESS_DENIED);
    }

    @Test
    void aCheckForAModeKeepsTheRequestAndTheNoticeAndLosesTheModeSpecificExpectation() {
        var check = new Check(REST, Verdict.SERVICE_DISABLED, "back at noon").inDsp(Verdict.ACCESS_DENIED);

        var forDsp = check.forMode(AclMode.DSP);

        assertThat(forDsp.request()).isEqualTo(REST);
        assertThat(forDsp.notice()).isEqualTo("back at noon");
        assertThat(forDsp.dspExpected()).isNull();
    }

    @Test
    void aDspExpectationEqualToTheLegacyVerdictIsRejected() {
        assertThatThrownBy(() -> new Check(REST, Verdict.ACCESS_DENIED).inDsp(Verdict.ACCESS_DENIED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDspExpectationFollowsTheRulesOfAnExpectedVerdict() {
        assertThatThrownBy(() -> new Check(REST, Verdict.ACCESS_DENIED).inDsp(Verdict.AGREEMENT_TOKEN_REJECTED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Check(listMethods(Expectation.NONE), Verdict.ACCESS_DENIED)
                .inDsp(Verdict.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
