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

import org.niis.xroad.e2e.parity.AdminCall.AddRestDescription;
import org.niis.xroad.e2e.parity.AdminCall.AddWsdlDescription;
import org.niis.xroad.e2e.parity.AdminCall.DeleteDescription;
import org.niis.xroad.e2e.parity.AdminCall.EnableDescription;
import org.niis.xroad.e2e.parity.AdminCall.GrantAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs a {@link FixturePlan} against the provider's admin API. A setup that fails part way removes what it
 * created before the failure is rethrown. Cleanup treats a missing service description (HTTP 404) as already
 * removed and keeps going when a step fails.
 */
public final class FixtureExecutor {

    private static final int HTTP_OK = 200;
    private static final int HTTP_LAST_SUCCESS = 299;
    private static final int HTTP_NOT_FOUND = 404;

    private final AdminGateway gateway;

    public FixtureExecutor(AdminGateway gateway) {
        this.gateway = gateway;
    }

    /** Runs the plan's setup calls and returns the handle that removes the fixtures on {@link Fixture#close()}. */
    public Fixture open(FixturePlan plan) {
        var fixture = new Fixture(plan);
        try {
            for (var call : plan.setup()) {
                fixture.apply(call);
            }
        } catch (RuntimeException | AssertionError e) {
            try {
                fixture.close();
            } catch (IllegalStateException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
        return fixture;
    }

    /** Created fixtures of one scenario. */
    public final class Fixture implements AutoCloseable {

        private final FixturePlan plan;
        private final Map<String, String> descriptionIds = new HashMap<>();

        private Fixture(FixturePlan plan) {
            this.plan = plan;
        }

        private void apply(AdminCall call) {
            switch (call) {
                case AddRestDescription add -> descriptionIds.put(add.serviceCode(),
                        gateway.addRestDescription(add.providerClientId(), add.backendUrl(), add.serviceCode()));
                case AddWsdlDescription add -> descriptionIds.put(add.serviceCode(),
                        gateway.addWsdlDescription(add.providerClientId(), add.wsdlUrl()));
                case EnableDescription enable -> gateway.enableDescription(descriptionIds.get(enable.serviceCode()));
                case GrantAccess grant -> gateway.grantAccess(
                        grant.providerClientId(), grant.consumerClientId(), grant.serviceCode());
                case DeleteDescription delete -> throw new IllegalArgumentException("not a setup call: " + delete);
            }
        }

        /** Runs the plan's cleanup calls for the descriptions that were created. */
        @Override
        public void close() {
            var failures = new ArrayList<String>();
            for (var call : plan.cleanup()) {
                var id = descriptionIds.remove(call.serviceCode());
                if (id == null) {
                    continue;
                }
                try {
                    var status = gateway.deleteDescription(id);
                    var removed = status >= HTTP_OK && status <= HTTP_LAST_SUCCESS || status == HTTP_NOT_FOUND;
                    if (!removed) {
                        failures.add("delete of %s answered %d".formatted(call.serviceCode(), status));
                    }
                } catch (RuntimeException e) {
                    failures.add("delete of %s failed: %s".formatted(call.serviceCode(), e.getMessage()));
                }
            }
            if (!failures.isEmpty()) {
                throw new IllegalStateException("fixture cleanup incomplete: " + String.join("; ", failures));
            }
        }
    }
}
