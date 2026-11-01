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
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.agreementtoken.AgreementTokenGroup;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerConfGroupAccessTest {

    private static final ClientId OWNER = ClientId.Conf.create("DEV", "COM", "333", "PROVIDER");
    private static final ClientId CALLER = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId SERVICE = ServiceId.Conf.create(OWNER, "getData", "v1");
    private static final LocalGroupId LOCAL_GROUP_ID = LocalGroupId.Conf.create("readers");
    private static final GlobalGroupId GLOBAL_GROUP_ID = GlobalGroupId.Conf.create("DEV", "consumers");

    private final ServerConfProvider serverConf = mock(ServerConfProvider.class);
    private final GlobalConfProvider globalConf = mock(GlobalConfProvider.class);
    private final ServerConfGroupAccess access = new ServerConfGroupAccess(serverConf, globalConf);

    @Test
    void localGroupMembershipIsTheExactOwnerScopedLookup() {
        when(serverConf.isExactLocalGroupMember(OWNER, LOCAL_GROUP_ID, CALLER)).thenReturn(true);

        assertThat(access.isMember(new AgreementTokenGroup.Local(OWNER, LOCAL_GROUP_ID), CALLER)).isTrue();
        assertThat(access.isMember(new AgreementTokenGroup.Local(CALLER, LOCAL_GROUP_ID), CALLER)).isFalse();
    }

    @Test
    void globalGroupMembershipIsTheGlobalConfLookup() {
        when(globalConf.isSubjectInGlobalGroup(CALLER, GLOBAL_GROUP_ID)).thenReturn(true);

        assertThat(access.isMember(new AgreementTokenGroup.Global(GLOBAL_GROUP_ID), CALLER)).isTrue();
        assertThat(access.isMember(new AgreementTokenGroup.Global(GlobalGroupId.Conf.create("DEV", "other")), CALLER)).isFalse();
    }

    @Test
    void restEndpointCheckIsTheLegacyAclLookupWithMethodAndPath() {
        when(serverConf.isQueryAllowed(CALLER, SERVICE, "GET", "/foo")).thenReturn(true);

        assertThat(access.isEndpointAllowed(CALLER, SERVICE, "GET", "/foo")).isTrue();
        assertThat(access.isEndpointAllowed(CALLER, SERVICE, "POST", "/foo")).isFalse();
    }

    @Test
    void soapEndpointCheckIsTheLegacyServiceLevelAclLookup() {
        when(serverConf.isQueryAllowed(CALLER, SERVICE)).thenReturn(true);

        assertThat(access.isEndpointAllowed(CALLER, SERVICE, null, null)).isTrue();
        verify(serverConf).isQueryAllowed(CALLER, SERVICE);
    }
}
