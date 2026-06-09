/*
 * The MIT License
 *
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
package org.niis.xroad.ss.test.api.platform;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.SsApiTest;

/**
 * Placeholder tests for DS control-plane scenarios. These require a dedicated DS stack
 * (issuer service + identity hub) that is not included in the standard API test stack.
 * Kept skipped pending DS enablement (out of scope for this slice).
 */
@DisplayName("DS control-plane scenarios (skipped — DS stack not wired)")
class DsControlPlaneSkippedTest extends SsApiTest {

    // MIGRATED-FROM: 5000-ds-control-plane.feature :: "Issuer Service is provisioned"
    @Test
    @Disabled("DS stack not wired — requires issuer service + identity hub")
    @DisplayName("Issuer Service is provisioned")
    void issuerServiceIsProvisioned() {
    }

    // MIGRATED-FROM: 5000-ds-control-plane.feature :: "Identity Hub is provisioned"
    @Test
    @Disabled("DS stack not wired — requires issuer service + identity hub")
    @DisplayName("Identity Hub is provisioned")
    void identityHubIsProvisioned() {
    }

    // MIGRATED-FROM: 5000-ds-control-plane.feature :: "Catalog can be retrieved over DSP protocol"
    @Test
    @Disabled("DS stack not wired — requires DSP catalog endpoint")
    @DisplayName("Catalog can be retrieved over DSP protocol")
    void catalogCanBeRetrievedOverDsp() {
    }
}
