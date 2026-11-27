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

package org.niis.xroad.edc.extension.policy.controlplane.issuertrust;

import org.eclipse.edc.boot.system.injection.ObjectFactory;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.token.spi.TokenValidationRulesRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.edc.issuerdata.IssuerStatusListAvailabilityRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.verifiablecredentials.jwt.Constants.JWT_VC_TOKEN_CONTEXT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(DependencyInjectionExtension.class)
class XRoadIssuerStatusListRuleExtensionTest {

    private final TokenValidationRulesRegistry rulesRegistry = mock();
    private final IssuerStatusListAvailabilityRule rule = mock();

    @Test
    void initializeRegistersTheRuleForPresentedCredentials(ServiceExtensionContext context, ObjectFactory factory) {
        context.registerService(TokenValidationRulesRegistry.class, rulesRegistry);
        context.registerService(IssuerStatusListAvailabilityRule.class, rule);
        var extension = factory.constructInstance(XRoadIssuerStatusListRuleExtension.class);

        extension.initialize(context);

        assertThat(extension.name()).isEqualTo(XRoadIssuerStatusListRuleExtension.EXTENSION_NAME);
        verify(rulesRegistry).addRule(JWT_VC_TOKEN_CONTEXT, rule);
    }
}
