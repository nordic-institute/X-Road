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

package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.dto.PossibleTokenAction;
import org.niis.xroad.cs.admin.api.service.TokenActionsResolver;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;

import static java.util.EnumSet.noneOf;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.CHANGE_PIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.EDIT_FRIENDLY_NAME;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.GENERATE_EXTERNAL_KEY;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.GENERATE_INTERNAL_KEY;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.LOGIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.LOGOUT;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.TOKEN_ACTION_NOT_POSSIBLE;

@Component
public class TokenActionsResolverImpl implements TokenActionsResolver {

    private static final int MAX_KEYS_PER_SOURCE_TYPE = 2;

    @Override
    public EnumSet<PossibleTokenAction> resolveActions(TokenInfo tokenInfo, List<? extends ConfigurationSigningKey> keys) {
        EnumSet<PossibleTokenAction> actions = noneOf(PossibleTokenAction.class);

        if (tokenInfo.isActive()) {
            actions.add(LOGOUT);
            actions.add(CHANGE_PIN);

            if (isGenerateKeyAllowedFor(ConfigurationSourceType.EXTERNAL, keys)) {
                actions.add(GENERATE_EXTERNAL_KEY);
            }

            if (isGenerateKeyAllowedFor(ConfigurationSourceType.INTERNAL, keys)) {
                actions.add(GENERATE_INTERNAL_KEY);
            }
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

    @Override
    public void requireAction(PossibleTokenAction action, TokenInfo tokenInfo, List<ConfigurationSigningKey> keys) {
        requireAction(action, resolveActions(tokenInfo, keys));
    }

    public void requireAction(PossibleTokenAction action, final EnumSet<PossibleTokenAction> possibleActions) {
        if (!possibleActions.contains(action))
            throw new ValidationFailureException(TOKEN_ACTION_NOT_POSSIBLE);
    }

    private boolean isGenerateKeyAllowedFor(final ConfigurationSourceType sourceType,
                                            final List<? extends ConfigurationSigningKey> configurationSigningKeys) {
        return configurationSigningKeys.stream()
                .map(ConfigurationSigningKey::getSourceType)
                .filter(sourceType::equals)
                .count() < MAX_KEYS_PER_SOURCE_TYPE;
    }
}
