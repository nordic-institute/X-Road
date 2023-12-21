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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoProto;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;

import java.util.EnumSet;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.cs.admin.api.dto.PossibleKeyAction.ACTIVATE;
import static org.niis.xroad.cs.admin.api.dto.PossibleKeyAction.DELETE;

class SigningKeyActionsResolverTest {
    private final SigningKeyActionsResolver signingKeyActionsResolver = new SigningKeyActionsResolver();

    @Test
    void resolveActions() {
        assertThat(signingKeyActionsResolver.resolveActions(createTokenInfo(true), createKey(true)))
                .isEmpty();

        assertThat(signingKeyActionsResolver.resolveActions(createTokenInfo(true), createKey(false)))
                .containsExactlyInAnyOrder(DELETE, ACTIVATE);

        assertThat(signingKeyActionsResolver.resolveActions(createTokenInfo(false), createKey(false)))
                .isEmpty();
    }

    @Test
    void requireActionForInactiveKey() {
        var inactiveKey = createKey(false);

        var allowed = signingKeyActionsResolver.resolveActions(createTokenInfo(true), inactiveKey);
        allowed.forEach(
                action -> signingKeyActionsResolver.requireAction(action, createTokenInfo(true), inactiveKey)
        );

        var otherActions = EnumSet.complementOf(allowed);
        otherActions.forEach(
                action -> assertThatThrownBy(() -> signingKeyActionsResolver.requireAction(action, createTokenInfo(true), inactiveKey))
                        .isInstanceOf(ValidationFailureException.class)
        );
    }

    @Test
    void requireActionForActiveKey() {
        var activeKey = createKey(true);

        var allowed = signingKeyActionsResolver.resolveActions(createTokenInfo(true), activeKey);
        allowed.forEach(
                action -> signingKeyActionsResolver.requireAction(action, createTokenInfo(true), activeKey)
        );

        var otherActions = EnumSet.complementOf(allowed);
        otherActions.forEach(
                action -> assertThatThrownBy(() -> signingKeyActionsResolver.requireAction(action, createTokenInfo(true), activeKey))
                        .isInstanceOf(ValidationFailureException.class)
        );
    }

    private ConfigurationSigningKey createKey(final boolean active) {
        var key = new ConfigurationSigningKey();
        key.setActiveSourceSigningKey(active);
        return key;
    }

    private TokenInfo createTokenInfo(boolean active) {
        return new TokenInfo(TokenInfoProto.newBuilder()
                .setType("type")
                .setFriendlyName("TOKEN_FRIENDLY_NAME")
                .setId("TOKEN_ID")
                .setReadOnly(false)
                .setAvailable(true)
                .setActive(active)
                .setSerialNumber("TOKEN_SERIAL_NUMBER")
                .setLabel("label")
                .setSlotIndex(13)
                .setStatus(OK)
                .build());
    }
}
