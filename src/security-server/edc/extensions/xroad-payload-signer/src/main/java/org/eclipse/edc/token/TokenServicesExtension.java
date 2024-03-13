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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.token.spi.TokenDecoratorRegistry;
import org.eclipse.edc.token.spi.TokenValidationRulesRegistry;
import org.eclipse.edc.token.spi.TokenValidationService;

import static org.eclipse.edc.token.TokenServicesExtension.NAME;

/**
 * This extension registers the {@link TokenValidationService} and the {@link TokenValidationRulesRegistry}
 * which can then be used by downstream modules.
 */
@Extension(value = NAME, categories = { "token", "security", "auth" })
public class TokenServicesExtension implements ServiceExtension {
    public static final String NAME = "Token Services Extension";

    @Provider
    public TokenValidationRulesRegistry tokenValidationRulesRegistry() {
        return new TokenValidationRulesRegistryImpl();
    }

    @Provider
    public TokenValidationService validationService() {
        return new XrdTokenValidationService();
    }

    @Provider
    public TokenDecoratorRegistry tokenDecoratorRegistry() {
        return new TokenDecoratorRegistryImpl();
    }
}
