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
package org.niis.xroad.serverconf.impl;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.GlobalConfInitState;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ownserver.OwnIdentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DATABASE_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;

@ExtendWith(MockitoExtension.class)
class ProxyOwnServerIdentitySourceTest {

    private static final SecurityServerId.Conf SERVER_ID = SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");

    @Mock
    private ServerConfProvider serverConfProvider;

    @InjectMocks
    private ProxyOwnServerIdentitySource source;

    @Test
    void identifierGivesKnown() {
        when(serverConfProvider.getIdentifier()).thenReturn(SERVER_ID);

        assertThat(source.read()).isEqualTo(new OwnIdentity.Known(SERVER_ID));
    }

    @Test
    void notInitializedServerConfGivesOwnerNotInitialised() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(XrdRuntimeException.systemException(MALFORMED_SERVERCONF, "Server conf is not initialized!"));

        assertThat(source.read()).isEqualTo(new OwnIdentity.OwnerNotInitialised());
    }

    @Test
    void ownerNotSetGivesOwnerNotInitialised() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(XrdRuntimeException.systemException(MALFORMED_SERVERCONF, "Owner is not set"));

        assertThat(source.read()).isEqualTo(new OwnIdentity.OwnerNotInitialised());
    }

    @Test
    void globalConfInitExceptionGivesGlobalConfUnavailableWithCause() {
        var cause = new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED);
        when(serverConfProvider.getIdentifier()).thenThrow(cause);

        assertThat(source.read()).isEqualTo(new OwnIdentity.GlobalConfUnavailable(cause));
    }

    @Test
    void databaseErrorPropagates() {
        var failure = XrdRuntimeException.systemException(DATABASE_ERROR, "database down");
        when(serverConfProvider.getIdentifier()).thenThrow(failure);

        assertThatThrownBy(source::read).isSameAs(failure);
    }
}
