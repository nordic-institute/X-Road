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
package org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.AccessScope;

/**
 * Decides which operational data a caller may see. The monitoring client of the global configuration, and the owner
 * of the Security Server named in the query header, see all clients' data. Everyone else sees only their own.
 * Clients are compared by exact identifier: a member does not match its subsystems, nor the other way round.
 */
final class AccessScopeDecider {

    private AccessScopeDecider() {
    }

    static AccessScope decide(ClientId caller, SecurityServerId headerServer, GlobalConfProvider globalConfProvider) {
        if (caller == null) {
            return AccessScope.CALLER_ONLY;
        }
        return isMonitoringClient(caller, globalConfProvider) || isServerOwner(caller, headerServer, globalConfProvider)
                ? AccessScope.FULL_VIEW
                : AccessScope.CALLER_ONLY;
    }

    private static boolean isMonitoringClient(ClientId caller, GlobalConfProvider globalConfProvider) {
        return caller.equals(globalConfProvider.getGlobalConfExtensions().getMonitoringClient());
    }

    private static boolean isServerOwner(ClientId caller, SecurityServerId headerServer, GlobalConfProvider globalConfProvider) {
        return headerServer != null && caller.equals(globalConfProvider.getServerOwner(headerServer));
    }
}
