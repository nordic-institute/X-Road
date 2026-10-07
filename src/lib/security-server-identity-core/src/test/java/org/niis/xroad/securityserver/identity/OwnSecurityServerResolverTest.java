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
package org.niis.xroad.securityserver.identity;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.GlobalConfInitState;
import org.niis.xroad.serverconf.ServerConfProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;

@ExtendWith(MockitoExtension.class)
class OwnSecurityServerResolverTest {

    @Mock
    private ServerConfProvider serverConfProvider;
    @Mock
    private GlobalConfProvider globalConfProvider;

    private SecurityServerId.Conf serverId;
    private ClientId owner;

    private OwnSecurityServerResolver resolver;

    @BeforeEach
    void setUp() {
        owner = ClientId.Conf.create("DEV", "COM", "222");
        serverId = SecurityServerId.Conf.create(owner, "SS1");

        resolver = new OwnSecurityServerResolver(serverConfProvider, globalConfProvider);
    }

    @Test
    void identityShouldReturnKnownGoodIdentity() {
        when(serverConfProvider.getIdentifier()).thenReturn(serverId);

        assertThat(resolver.identity()).contains(serverId);
    }

    @Test
    void identityShouldBeEmptyWhenOwnerNotInitialised() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(XrdRuntimeException.systemException(MALFORMED_SERVERCONF, "owner not initialised"));

        assertThat(resolver.identity()).isEmpty();
    }

    @Test
    void identityShouldBeEmptyWhenGlobalConfNotInitialised() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));

        assertThat(resolver.identity()).isEmpty();
    }

    @Test
    void identityShouldPropagateUnexpectedFailures() {
        var unexpected = XrdRuntimeException.systemException(INTERNAL_ERROR, "database error");
        when(serverConfProvider.getIdentifier()).thenThrow(unexpected);

        assertThatThrownBy(() -> resolver.identity()).isSameAs(unexpected);
    }

    @Test
    void ownerShouldReturnKnownGoodOwner() {
        when(serverConfProvider.getIdentifier()).thenReturn(serverId);

        assertThat(resolver.owner()).contains(owner);
    }

    @Test
    void ownerShouldBeEmptyWhenOwnerNotInitialised() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(XrdRuntimeException.systemException(MALFORMED_SERVERCONF, "owner not initialised"));

        assertThat(resolver.owner()).isEmpty();
        verifyNoInteractions(globalConfProvider);
    }

    @Test
    void ownerShouldBeEmptyWhenGlobalConfNotInitialised() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));

        assertThat(resolver.owner()).isEmpty();
        verifyNoInteractions(globalConfProvider);
    }

    @Test
    void registeredAddressShouldReturnKnownGoodAddress() {
        when(serverConfProvider.getIdentifier()).thenReturn(serverId);
        when(globalConfProvider.getSecurityServerAddress(serverId)).thenReturn("ss1.example.org");

        assertThat(resolver.registeredAddress()).contains("ss1.example.org");
    }

    @Test
    void registeredAddressShouldBeEmptyWhenOwnerNotInitialised() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(XrdRuntimeException.systemException(MALFORMED_SERVERCONF, "owner not initialised"));

        assertThat(resolver.registeredAddress()).isEmpty();
        verifyNoInteractions(globalConfProvider);
    }

    @Test
    void registeredAddressShouldBeEmptyWhenGlobalConfNotInitialised() {
        when(serverConfProvider.getIdentifier())
                .thenThrow(new GlobalConfInitException(GlobalConfInitState.UNINITIALIZED));

        assertThat(resolver.registeredAddress()).isEmpty();
        verifyNoInteractions(globalConfProvider);
    }

    @Test
    void registeredAddressShouldBeEmptyWhenGlobalConfNotReadable() {
        when(serverConfProvider.getIdentifier()).thenReturn(serverId);
        when(globalConfProvider.getSecurityServerAddress(serverId))
                .thenThrow(XrdRuntimeException.systemException(INTERNAL_ERROR, "globalconf not downloaded yet"));

        assertThat(resolver.registeredAddress()).isEmpty();
    }

    @Test
    void registeredAddressShouldBeEmptyWhenNotRegisteredWithNullAddress() {
        when(serverConfProvider.getIdentifier()).thenReturn(serverId);
        when(globalConfProvider.getSecurityServerAddress(serverId)).thenReturn(null);

        assertThat(resolver.registeredAddress()).isEmpty();
    }

    @Test
    void registeredAddressShouldBeEmptyWhenNotRegisteredWithBlankAddress() {
        when(serverConfProvider.getIdentifier()).thenReturn(serverId);
        when(globalConfProvider.getSecurityServerAddress(serverId)).thenReturn("  ");

        assertThat(resolver.registeredAddress()).isEmpty();
    }

    @Test
    void registeredAddressWithIdShouldReturnKnownGoodAddressWithoutResolvingIdentity() {
        when(globalConfProvider.getSecurityServerAddress(serverId)).thenReturn("ss1.example.org");

        assertThat(resolver.registeredAddress(serverId)).contains("ss1.example.org");
        verifyNoInteractions(serverConfProvider);
    }

    @Test
    void registeredAddressWithIdShouldBeEmptyWhenGlobalConfNotReadable() {
        when(globalConfProvider.getSecurityServerAddress(serverId))
                .thenThrow(XrdRuntimeException.systemException(INTERNAL_ERROR, "globalconf not downloaded yet"));

        assertThat(resolver.registeredAddress(serverId)).isEmpty();
        verifyNoInteractions(serverConfProvider);
    }

    @Test
    void registeredAddressWithIdShouldBeEmptyWhenAddressIsBlank() {
        when(globalConfProvider.getSecurityServerAddress(serverId)).thenReturn("  ");

        assertThat(resolver.registeredAddress(serverId)).isEmpty();
        verifyNoInteractions(serverConfProvider);
    }
}
