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
package org.niis.xroad.proxy.edc;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.ServerAddressInfo;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TargetSecurityServerLookup {

    public static TargetSecurityServers resolveTargetSecurityServers(ClientId clientId) {
        // Resolve available security servers
        var allServers = GlobalConf.getProviderSecurityServers(clientId);
        if (SystemProperties.isDataspacesEnabled()) {
            var dsEnabledServers = allServers.stream().filter(ServerAddressInfo::dsSupported).toList();
            if (dsEnabledServers.isEmpty()) {
                log.trace("Falling back to legacy protocol, there are no DataSpace compliant Security Servers for this service.");
                return new TargetSecurityServers(allServers, false);
            } else {
                return new TargetSecurityServers(dsEnabledServers, true);
            }
        } else {
            return new TargetSecurityServers(allServers, false);
        }
    }

    public record TargetSecurityServers(
            java.util.Collection<ServerAddressInfo> servers,
            boolean dsEnabledServers
    ) {
    }
}
