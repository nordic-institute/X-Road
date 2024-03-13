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

import org.eclipse.edc.token.spi.TokenDecorator;
import org.eclipse.edc.token.spi.TokenDecoratorRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TokenDecoratorRegistryImpl implements TokenDecoratorRegistry {

    private final Map<String, List<TokenDecorator>> decorators = new HashMap<>();

    @Override
    public void register(String context, TokenDecorator decorator) {
        decorators.computeIfAbsent(context, s -> new ArrayList<>())
                .add(decorator);
    }

    @Override
    public void unregister(String context, TokenDecorator decorator) {
        if (decorators.containsKey(context)) {
            decorators.get(context).remove(decorator);
        }
    }

    @Override
    public Collection<TokenDecorator> getDecoratorsFor(String context) {
        return Optional.ofNullable(decorators.get(context))
                .map(Collections::unmodifiableList)
                .orElseGet(Collections::emptyList);
    }

}
