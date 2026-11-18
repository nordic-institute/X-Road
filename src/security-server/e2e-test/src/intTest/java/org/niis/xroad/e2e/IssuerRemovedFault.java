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
package org.niis.xroad.e2e;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import lombok.experimental.UtilityClass;
import org.niis.xroad.e2e.parity.acl.Verdict;
import org.niis.xroad.e2e.parity.acl.VerdictClassifier;
import org.niis.xroad.e2e.parity.acl.VerdictPoller;
import org.niis.xroad.e2e.parity.acl.VerdictPoller.Observation;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The verdict a call ends in once its credential's issuer has been removed from the Central Server and the
 * consumer Security Server has picked that up through a globalconf refresh: the issuer is no longer in the
 * trusted set, so the consumer's data space control plane can no longer establish the connection the call
 * needs, which surfaces to the caller as {@link Verdict#CONSUMER_FAILED}.
 *
 * <p>Globalconf distributes on its own cycle, so the wait shares its bound with {@code SsSystemValuesPublishedTest}
 * rather than the shorter serverconf-cache waits in {@link DisabledServiceFault}.
 */
@UtilityClass
class IssuerRemovedFault {

    private static final Duration GLOBALCONF_REFRESH_TIMEOUT = Duration.ofMinutes(8);
    private static final Duration GLOBALCONF_REFRESH_POLL_INTERVAL = Duration.ofSeconds(5);

    /** Polls {@code call} until it fails because the consumer no longer trusts the credential's issuer. */
    static void awaitCallFailsAsConsumerFailed(Supplier<ValidatableResponse> call) {
        var result = new VerdictPoller().await(() -> observe(call.get().extract().response()),
                Verdict.CONSUMER_FAILED, GLOBALCONF_REFRESH_TIMEOUT, GLOBALCONF_REFRESH_POLL_INTERVAL);
        assertThat(result.matched()).as(() -> result.failureMessage("service call", Verdict.CONSUMER_FAILED))
                .isTrue();
    }

    private static Observation observe(Response response) {
        var reply = Replies.toReply(response);
        return new Observation(VerdictClassifier.classify(reply), VerdictClassifier.describe(reply));
    }
}
