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
package org.niis.xroad.signer.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.test.apitest.core.junit.Step;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 0050 - Signer: general. Two scenarios independent of any token state: an RPC deadline check and the
 * pin-policy-enforcement status endpoint.
 */
@Order(50)
class SignerGeneralIntTest extends AbstractSignerIntTest {

    private static final int SHORT_DEADLINE_MILLIS = 10;

    @Test
    @DisplayName("Signer client timeout works")
    void signerClientTimeoutWorks() {
        var shortDeadlineClient = Step.given("signer client initialized with timeout 10 milliseconds",
                () -> containerSetup.newSignerClientWithTimeout(SHORT_DEADLINE_MILLIS));
        Step.then("getTokens fails with timeout exception", () ->
                assertThatThrownBy(shortDeadlineClient::getTokens)
                        .isInstanceOf(XrdRuntimeException.class)
                        .hasMessageMatching("\\[.*?] signer\\.network_error: gRPC client timed out\\..*"));
    }

    @Test
    @DisplayName("Signer policy enforcement status endpoint works")
    void signerPolicyEnforcementStatusEndpointWorks() {
        Step.given("Policy enforcement status endpoint returns false",
                () -> assertThat(client().isEnforcedTokenPinPolicy()).isFalse());
    }
}
