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
package org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.extension.GlobalConfExtensions;
import org.niis.xroad.opmonitor.api.AccessScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessScopeDeciderTest {
    private static final SecurityServerId.Conf SERVER = SecurityServerId.Conf.create("DEV", "COM", "1234", "SS0");
    private static final ClientId.Conf OWNER = ClientId.Conf.create("DEV", "COM", "1234");
    private static final ClientId.Conf MONITORING_CLIENT = ClientId.Conf.create("DEV", "COM", "5678");
    private static final ClientId.Conf OTHER_CLIENT = ClientId.Conf.create("DEV", "COM", "9999");

    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);

    @BeforeEach
    void setUp() {
        var extensions = mock(GlobalConfExtensions.class);
        when(extensions.getMonitoringClient()).thenReturn(MONITORING_CLIENT);
        when(globalConfProvider.getGlobalConfExtensions()).thenReturn(extensions);
        when(globalConfProvider.getServerOwner(SERVER)).thenReturn(OWNER);
    }

    @Test
    void monitoringClientSeesAllRecords() {
        assertThat(AccessScopeDecider.decide(MONITORING_CLIENT, null, globalConfProvider)).isEqualTo(AccessScope.FULL_VIEW);
    }

    @Test
    void ownerSeesAllRecordsWhenTheHeaderNamesItsServer() {
        assertThat(AccessScopeDecider.decide(OWNER, SERVER, globalConfProvider)).isEqualTo(AccessScope.FULL_VIEW);
    }

    @Test
    void ownerSeesOwnRecordsOnlyWithoutTheHeader() {
        when(globalConfProvider.getServerOwner(null)).thenReturn(OWNER);

        assertThat(AccessScopeDecider.decide(OWNER, null, globalConfProvider)).isEqualTo(AccessScope.CALLER_ONLY);
    }

    @Test
    void otherClientSeesOwnRecordsOnly() {
        assertThat(AccessScopeDecider.decide(OTHER_CLIENT, SERVER, globalConfProvider)).isEqualTo(AccessScope.CALLER_ONLY);
    }
}
