/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.token;

import org.eclipse.edc.token.spi.TokenValidationRule;
import org.eclipse.edc.token.spi.TokenValidationRulesRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory implementation of {@link TokenValidationRulesRegistry}
 */
public class TokenValidationRulesRegistryImpl implements TokenValidationRulesRegistry {

    private final Map<String, List<TokenValidationRule>> rules = new HashMap<>();

    @Override
    public void addRule(String context, TokenValidationRule rule) {
        rules.computeIfAbsent(context, s -> new ArrayList<>())
                .add(rule);
    }

    @Override
    public List<TokenValidationRule> getRules(String context) {
        return Collections.unmodifiableList(rules.get(context));
    }
}
