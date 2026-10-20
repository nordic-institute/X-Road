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

import org.niis.xroad.e2e.parity.Scenario.Check;
import org.niis.xroad.e2e.parity.Scenario.RequestSpec;
import org.niis.xroad.e2e.parity.VerdictPoller.Observation;
import org.niis.xroad.e2e.parity.VerdictPoller.Result;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Polls every request of a scenario and reports each request that missed its expected verdict. */
public final class ScenarioVerdicts {

    /**
     * @param check  the request and the verdict it was expected to reach
     * @param result what polling the request observed
     */
    public record Outcome(Check check, Result result) {
    }

    private ScenarioVerdicts() {
    }

    /** Polls each request of the scenario in turn; a request that misses its verdict does not stop the next. */
    public static List<Outcome> poll(VerdictPoller poller, Scenario scenario,
                                     Function<RequestSpec, Observation> probe, Duration timeout, Duration interval) {
        var outcomes = new ArrayList<Outcome>();
        for (var check : scenario.checks()) {
            var result = poller.await(() -> probe.apply(check.request()), check.expected(), timeout, interval);
            outcomes.add(new Outcome(check, result));
        }
        return outcomes;
    }

    /**
     * One line per request that missed its verdict or its notice, naming the request; empty when every request
     * matched.
     */
    public static Optional<String> failureMessage(String scenarioId, List<Outcome> outcomes) {
        var lines = new ArrayList<String>();
        for (var index = 0; index < outcomes.size(); index++) {
            var outcome = outcomes.get(index);
            var subject = "%s request %d of %d (%s)".formatted(scenarioId, index + 1, outcomes.size(),
                    outcome.check().request().describe());
            if (!outcome.result().matched()) {
                lines.add(outcome.result().failureMessage(subject, outcome.check().expected()));
            } else if (!carriesNotice(outcome)) {
                lines.add("%s: reply does not carry the notice \"%s\"; last reply: %s"
                        .formatted(subject, outcome.check().notice(), outcome.result().detail()));
            }
        }
        return lines.isEmpty() ? Optional.empty() : Optional.of(String.join("\n", lines));
    }

    private static boolean carriesNotice(Outcome outcome) {
        var notice = outcome.check().notice();
        return notice == null || outcome.result().detail() != null && outcome.result().detail().contains(notice);
    }
}
