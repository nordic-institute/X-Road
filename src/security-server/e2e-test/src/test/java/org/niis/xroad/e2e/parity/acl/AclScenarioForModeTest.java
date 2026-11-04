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

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.CONSUMER_ONLY;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.allowed;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.check;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.denied;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phase;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.phased;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restRequest;
import static org.niis.xroad.e2e.parity.acl.AclScenarioBuilders.restService;

class AclScenarioForModeTest {

    private static final AclScenario ROW = phased("V127", restService(CONSUMER_ONLY),
            List.of(check(restRequest("GET", "/a"), Verdict.UNKNOWN_SERVICE).inDsp(Verdict.ACCESS_DENIED),
                    allowed("GET", "/b")),
            phase(new AclScenario.Revoke(0, AclParticipants.CONSUMER),
                    check(restRequest("GET", "/c"), Verdict.UNKNOWN_SERVICE).inDsp(Verdict.ACCESS_DENIED),
                    denied("GET", "/d")))
            .as("row with expectations of its own for the data space protocol");

    private static List<Verdict> expectedVerdicts(AclScenario scenario) {
        return Stream.concat(scenario.checks().stream(),
                        scenario.phases().stream().flatMap(phase -> phase.checks().stream()))
                .map(AclScenario.Check::expected).toList();
    }

    @Test
    void legacyModeKeepsEveryLegacyVerdict() {
        assertThat(expectedVerdicts(ROW.forMode(AclMode.LEGACY))).containsExactly(
                Verdict.UNKNOWN_SERVICE, Verdict.ALLOWED, Verdict.UNKNOWN_SERVICE, Verdict.ACCESS_DENIED);
    }

    @Test
    void modesThatNegotiateTakeTheDspVerdictOfTheChecksThatHaveOne() {
        var expected = List.of(
                Verdict.ACCESS_DENIED, Verdict.ALLOWED, Verdict.ACCESS_DENIED, Verdict.ACCESS_DENIED);

        assertThat(expectedVerdicts(ROW.forMode(AclMode.DSP))).isEqualTo(expected);
        assertThat(expectedVerdicts(ROW.forMode(AclMode.DSP_REFUSAL))).isEqualTo(expected);
    }

    @Test
    void theRowKeepsEverythingButItsExpectations() {
        var forDsp = ROW.forMode(AclMode.DSP);

        assertThat(forDsp.id()).isEqualTo(ROW.id());
        assertThat(forDsp.description()).isEqualTo(ROW.description());
        assertThat(forDsp.fixture()).isEqualTo(ROW.fixture());
        assertThat(forDsp.waitClass()).isEqualTo(ROW.waitClass());
        assertThat(forDsp.phases()).hasSize(1);
        assertThat(forDsp.phases().get(0).changes()).isEqualTo(ROW.phases().get(0).changes());
    }
}
