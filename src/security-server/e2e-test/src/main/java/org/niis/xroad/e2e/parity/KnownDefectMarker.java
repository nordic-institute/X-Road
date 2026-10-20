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

import org.niis.xroad.e2e.parity.ScenarioVerdicts.Outcome;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the polled outcomes of a scenario into the result the test reports. A row without a known defect passes
 * or fails by its outcomes. A row with one asserts the safe verdict: an allowed request where a denial or a
 * disabled-service answer was expected is an expected failure, meeting every expectation fails the row so the
 * marker gets removed, and any other miss fails the row as it would without a marker.
 */
public final class KnownDefectMarker {

    /** How a scenario is reported. */
    public enum Status {
        PASSED,
        FAILED,
        EXPECTED_FAILURE
    }

    /**
     * @param status  how the scenario is reported
     * @param message what to report, empty when the scenario passed
     */
    public record Judgement(Status status, String message) {
    }

    private static final Set<Verdict> SAFE_VERDICTS = EnumSet.of(Verdict.ACCESS_DENIED, Verdict.SERVICE_DISABLED);

    private KnownDefectMarker() {
    }

    public static Judgement judge(Scenario scenario, List<Outcome> outcomes) {
        var failure = ScenarioVerdicts.failureMessage(scenario.id(), outcomes);
        var finding = scenario.knownDefect();
        if (finding == null) {
            return failure.map(message -> new Judgement(Status.FAILED, message))
                    .orElseGet(() -> new Judgement(Status.PASSED, ""));
        }
        if (failure.isEmpty()) {
            return new Judgement(Status.FAILED,
                    "%s: defect fixed, remove marker %s".formatted(scenario.id(), finding));
        }
        if (outcomes.stream().allMatch(KnownDefectMarker::matchedOrAllowedWhereASafeVerdictWasExpected)) {
            return new Judgement(Status.EXPECTED_FAILURE,
                    "%s: expected failure, known defect %s".formatted(scenario.id(), finding));
        }
        return new Judgement(Status.FAILED, failure.get());
    }

    private static boolean matchedOrAllowedWhereASafeVerdictWasExpected(Outcome outcome) {
        return outcome.result().matched()
                || SAFE_VERDICTS.contains(outcome.check().expected()) && outcome.result().last() == Verdict.ALLOWED;
    }
}
