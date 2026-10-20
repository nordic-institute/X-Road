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

import org.niis.xroad.e2e.parity.Reply;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sends client registrations from a Security Server to the management service. A registration needs a client in
 * the saved state, so the probe adds a fresh subsystem under the sender for each accepted registration and reuses
 * the current one while the management service refuses it. {@link #close()} unregisters and deletes every
 * subsystem the probe added.
 */
final class ManagementProbe implements AutoCloseable {

    private static final int SUCCESS_CLASS = 2;
    private static final int STATUS_CLASS_DIVISOR = 100;

    private final String baseUrl;
    private final String runToken;
    private final String scenarioId;
    private final List<String> created = new ArrayList<>();
    private AdminApi.AdminSession session;
    private String current;
    private int counter;

    ManagementProbe(String baseUrl, String runToken, String scenarioId) {
        this.baseUrl = baseUrl;
        this.runToken = runToken;
        this.scenarioId = scenarioId;
    }

    /** Registers a subsystem under the owner and returns the Security Server's answer. */
    Reply register(String ownerClientId) {
        if (session == null) {
            session = AdminApi.login(baseUrl);
        }
        if (current == null) {
            current = "%s:p%s%sn%d".formatted(ownerClientId, runToken, scenarioId.toLowerCase(Locale.ROOT), counter++);
            AdminApi.addLocalClient(baseUrl, session, current);
            created.add(current);
        }
        var response = AdminApi.authed(session).put(baseUrl + "/api/v1/clients/" + current + "/register");
        if (response.getStatusCode() / STATUS_CLASS_DIVISOR == SUCCESS_CLASS) {
            current = null;
        }
        return Replies.toReply(response);
    }

    @Override
    public void close() {
        var failures = new ArrayList<String>();
        for (var clientId : created) {
            try {
                AdminApi.unregisterClient(baseUrl, session, clientId);
            } catch (RuntimeException | AssertionError e) {
                failures.add("unregister of %s failed: %s".formatted(clientId, e.getMessage()));
            }
            try {
                AdminApi.deleteClient(baseUrl, session, clientId);
            } catch (RuntimeException | AssertionError e) {
                failures.add("delete of %s failed: %s".formatted(clientId, e.getMessage()));
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("management probe cleanup incomplete: " + String.join("; ", failures));
        }
    }
}
