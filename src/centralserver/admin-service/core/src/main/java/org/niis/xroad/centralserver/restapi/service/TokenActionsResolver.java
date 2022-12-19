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

import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.PossibleAction;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

import static java.util.EnumSet.noneOf;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.TOKEN_ACTION_NOT_POSSIBLE;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.CHANGE_PIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.EDIT_FRIENDLY_NAME;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.GENERATE_KEY;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.LOGIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleAction.LOGOUT;

@Component
public class TokenActionsResolver {

    public EnumSet<PossibleAction> resolveActions(TokenInfo tokenInfo) {
        EnumSet<PossibleAction> actions = noneOf(PossibleAction.class);

        if (tokenInfo.isActive()) {
            actions.add(GENERATE_KEY);
        }

        if (tokenInfo.isActive()) {
            actions.add(LOGOUT);
            actions.add(CHANGE_PIN);
        } else {
            if (tokenInfo.isAvailable()) {
                actions.add(LOGIN);
            }
        }

        if (tokenInfo.isSavedToConfiguration()) {
            actions.add(EDIT_FRIENDLY_NAME);
        }

        return actions;
    }

    public void requireAction(PossibleAction action, TokenInfo tokenInfo) {
        final EnumSet<PossibleAction> possibleActions = resolveActions(tokenInfo);
        if (!possibleActions.contains(action))
            throw new ValidationFailureException(TOKEN_ACTION_NOT_POSSIBLE);
    }
}
