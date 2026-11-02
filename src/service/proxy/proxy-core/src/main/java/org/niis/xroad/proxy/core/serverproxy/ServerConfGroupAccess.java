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

package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.agreementtoken.AgreementTokenGroup;
import org.niis.xroad.common.agreementtoken.AgreementTokenGroupAccess;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

/**
 * Answers the group-token lookups from the same sources as the legacy ACL check: local-group members from the
 * (cached) server configuration, global-group members from the global configuration, and the endpoint check
 * through {@link ServerConfProvider#isQueryAllowed}. Members are compared exactly, subsystem included.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class ServerConfGroupAccess implements AgreementTokenGroupAccess {

    private final ServerConfProvider serverConfProvider;
    private final GlobalConfProvider globalConfProvider;

    @Override
    public boolean isMember(AgreementTokenGroup group, ClientId client) {
        return switch (group) {
            case AgreementTokenGroup.Local(var owner, var groupId) ->
                    serverConfProvider.isExactLocalGroupMember(owner, groupId, client);
            case AgreementTokenGroup.Global(var groupId) ->
                    globalConfProvider.isSubjectInGlobalGroup(client, groupId);
        };
    }

    @Override
    public boolean isEndpointAllowed(ClientId client, ServiceId service, String requestMethod, String requestPath) {
        return requestMethod == null
                ? serverConfProvider.isQueryAllowed(client, service)
                : serverConfProvider.isQueryAllowed(client, service, requestMethod, requestPath);
    }
}
