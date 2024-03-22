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

package org.eclipse.edc.jwt;

import org.assertj.core.api.Assertions;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.token.spi.TokenValidationRule;
import org.eclipse.edc.tokendisabled.TokenValidationRulesRegistryImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenValidationRulesRegistryImplTest {

    private final TokenValidationRulesRegistryImpl registry = new TokenValidationRulesRegistryImpl();

    @Test
    void addRule() {
        registry.addRule("foo", (toVerify, additional) -> Result.success());
        assertThat(registry.getRules("foo")).hasSize(1);
    }

    @Test
    void addRuleWhenExistsShouldAdd() {
        registry.addRule("foo", (toVerify, additional) -> Result.success());
        registry.addRule("foo", (toVerify, additional) -> Result.success());
        assertThat(registry.getRules("foo")).hasSize(2);
    }

    @Test
    void addRuleWhenExistsSameInstanceShouldAdd() {
        TokenValidationRule rule = (toVerify, additional) -> Result.success();
        registry.addRule("foo", rule);
        registry.addRule("foo", rule);
        assertThat(registry.getRules("foo")).hasSize(2);
    }

    @Test
    void getRules() {
        registry.addRule("rules", (toVerify, additional) -> Result.success());
        var rules = registry.getRules("rules");
        assertThat(rules).hasSize(1);

        Assertions.assertThatThrownBy(() -> rules.add((toVerify, additional) -> Result.success()));
    }
}
