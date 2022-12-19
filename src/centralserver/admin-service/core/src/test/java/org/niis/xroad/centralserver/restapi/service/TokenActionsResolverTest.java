/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.PossibleAction;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.CHANGE_PIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.GENERATE_KEY;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.LOGIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.LOGOUT;

class TokenActionsResolverTest {

    private final TokenActionsResolver tokenActionsResolver = new TokenActionsResolver();

    @Test
    void resolve() {
        assertThat(tokenActionsResolver.resolveActions(createTokenInfo(true, true)))
                .containsExactlyInAnyOrder(GENERATE_KEY, LOGOUT, CHANGE_PIN);

        assertThat(tokenActionsResolver.resolveActions(createTokenInfo(true, false)))
                .containsExactlyInAnyOrder(GENERATE_KEY, LOGOUT, CHANGE_PIN);

        assertThat(tokenActionsResolver.resolveActions(createTokenInfo(false, true)))
                .containsExactlyInAnyOrder(LOGIN);

        assertThat(tokenActionsResolver.resolveActions(createTokenInfo(false, false)))
                .isEmpty();
    }

    @Test
    void requireAction() {
        final TokenInfo tokenInfo = createTokenInfo(true, true);
        final EnumSet<PossibleAction> possibleActions = tokenActionsResolver.resolveActions(tokenInfo);
        possibleActions.forEach(
                action -> tokenActionsResolver.requireAction(action, tokenInfo)
        );

        EnumSet<PossibleAction> otherActions = EnumSet.complementOf(possibleActions);
        otherActions.forEach(
                action -> assertThatThrownBy(() -> tokenActionsResolver.requireAction(action, tokenInfo))
                        .isInstanceOf(ValidationFailureException.class)
        );
    }

    private TokenInfo createTokenInfo(boolean active, boolean available) {
        return new TokenInfo(
                "type", "TOKEN_FRIENDLY_NAME", "TOKEN_ID", false, available,
                active, "TOKEN_SERIAL_NUMBER", "label", 13, OK, List.of(), Map.of()
        );
    }

}
