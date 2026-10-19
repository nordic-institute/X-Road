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

import org.niis.xroad.e2e.parity.Scenario.FixtureSpec;
import org.niis.xroad.e2e.parity.Scenario.RequestSpec;
import org.niis.xroad.e2e.parity.Scenario.ServiceSpec;
import org.niis.xroad.e2e.parity.Scenario.WaitClass;

import java.util.List;

/**
 * The scenarios the parity suite runs. Every scenario calls from the consumer Security Server (ss1) to the
 * provider (ss0).
 */
public final class ScenarioCatalogue {

    public static final String PROVIDER = "DEV:COM:1234:TestService";
    public static final String CONSUMER = "DEV:COM:4321:TestClient";
    public static final String OTHER_CONSUMER = "DEV:COM:1234:test-consumer";

    private static final List<Scenario> ALL = List.of(
            new Scenario("V01", "REST service, consumer subsystem granted the base endpoint",
                    new FixtureSpec(PROVIDER, List.of(new ServiceSpec(List.of(CONSUMER)))),
                    new RequestSpec(CONSUMER, "GET", "/a/b"),
                    Verdict.ALLOWED, WaitClass.ACL_CACHE),
            new Scenario("V02", "REST service, another subsystem with no grant",
                    new FixtureSpec(PROVIDER, List.of(new ServiceSpec(List.of()))),
                    new RequestSpec(OTHER_CONSUMER, "GET", "/a"),
                    Verdict.ACCESS_DENIED, WaitClass.ACL_CACHE));

    private ScenarioCatalogue() {
    }

    public static List<Scenario> all() {
        return ALL;
    }
}
