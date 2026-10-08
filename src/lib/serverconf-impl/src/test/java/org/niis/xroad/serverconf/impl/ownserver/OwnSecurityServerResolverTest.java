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
package org.niis.xroad.serverconf.impl.ownserver;

import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DATABASE_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

@ExtendWith(MockitoExtension.class)
class OwnSecurityServerResolverTest {

    private static final SecurityServerId SERVER_ID = SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");
    private static final String ADDRESS = "ss1.example.org";

    @Mock
    private GlobalConfProvider globalConfProvider;

    private final AtomicReference<OwnIdentity> identity = new AtomicReference<>(new OwnIdentity.Known(SERVER_ID));
    private OwnSecurityServerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OwnSecurityServerResolver(identity::get, globalConfProvider);
    }

    @Test
    void identityReturnsKnownId() {
        assertThat(resolver.identity()).isEqualTo(new OwnIdentity.Known(SERVER_ID));
    }

    @Test
    void identityReturnsOwnerNotInitialised() {
        identity.set(new OwnIdentity.OwnerNotInitialised());

        assertThat(resolver.identity()).isEqualTo(new OwnIdentity.OwnerNotInitialised());
    }

    @Test
    void identityReturnsGlobalConfUnavailableWithCause() {
        var cause = new IllegalStateException("not downloaded");
        identity.set(new OwnIdentity.GlobalConfUnavailable(cause));

        assertThat(resolver.identity()).isEqualTo(new OwnIdentity.GlobalConfUnavailable(cause));
    }

    @Test
    void addressReturnsRegisteredWithIdAndAddress() {
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(ADDRESS);

        assertThat(resolver.address()).isEqualTo(new OwnAddress.Registered(SERVER_ID, ADDRESS));
    }

    @Test
    void addressForGivenIdSkipsIdentityLookup() {
        identity.set(new OwnIdentity.OwnerNotInitialised());
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(ADDRESS);

        assertThat(resolver.address(SERVER_ID)).isEqualTo(new OwnAddress.Registered(SERVER_ID, ADDRESS));
    }

    @Test
    void addressReturnsNotRegisteredForNullAddress() {
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(null);

        assertThat(resolver.address()).isEqualTo(new OwnAddress.NotRegistered(SERVER_ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void addressReturnsNotRegisteredForBlankAddress(String blank) {
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(blank);

        assertThat(resolver.address()).isEqualTo(new OwnAddress.NotRegistered(SERVER_ID));
    }

    @Test
    void addressKeepsKnownIdWhenGlobalConfFails() {
        var cause = XrdRuntimeException.systemException(INTERNAL_ERROR, "global conf not readable");
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenThrow(cause);

        assertThat(resolver.address()).isEqualTo(new OwnAddress.GlobalConfUnavailable(SERVER_ID, cause));
    }

    @Test
    void addressHasNoIdWhenIdentityReportedGlobalConfUnavailable() {
        var cause = new IllegalStateException("not downloaded");
        identity.set(new OwnIdentity.GlobalConfUnavailable(cause));

        assertThat(resolver.address()).isEqualTo(new OwnAddress.GlobalConfUnavailable(null, cause));
    }

    @Test
    void addressReturnsOwnerNotInitialised() {
        identity.set(new OwnIdentity.OwnerNotInitialised());

        assertThat(resolver.address()).isEqualTo(new OwnAddress.OwnerNotInitialised());
    }

    @Test
    void databaseFailureFromIdentitySourcePropagatesFromIdentity() {
        var failing = new OwnSecurityServerResolver(() -> {
            throw XrdRuntimeException.systemException(DATABASE_ERROR, "db down");
        }, globalConfProvider);

        assertThatThrownBy(failing::identity).isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void databaseFailureFromIdentitySourcePropagatesFromAddress() {
        var failing = new OwnSecurityServerResolver(() -> {
            throw XrdRuntimeException.systemException(DATABASE_ERROR, "db down");
        }, globalConfProvider);

        assertThatThrownBy(failing::address).isInstanceOf(XrdRuntimeException.class);
    }
}
