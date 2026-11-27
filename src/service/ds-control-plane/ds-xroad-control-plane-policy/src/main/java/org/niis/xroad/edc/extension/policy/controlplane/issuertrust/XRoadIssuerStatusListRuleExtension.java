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

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.token.spi.TokenValidationRulesRegistry;
import org.niis.xroad.edc.issuerdata.IssuerStatusListAvailabilityRule;

import static org.eclipse.edc.verifiablecredentials.jwt.Constants.JWT_VC_TOKEN_CONTEXT;
import static org.niis.xroad.edc.extension.policy.controlplane.issuertrust.XRoadIssuerStatusListRuleExtension.EXTENSION_NAME;

/**
 * Adds the status list availability rule to the checks of every presented credential, so that a peer whose
 * credential cannot be checked because the issuer's status list is unavailable gets a retryable answer instead of
 * a final refusal. The rule belongs to the control plane, which verifies the credentials of other participants; the
 * Identity Hub does not register it.
 */
@Extension(value = EXTENSION_NAME)
public class XRoadIssuerStatusListRuleExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road Issuer Status List Rule";

    @Inject
    private TokenValidationRulesRegistry rulesRegistry;

    @Inject
    private IssuerStatusListAvailabilityRule availabilityRule;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        rulesRegistry.addRule(JWT_VC_TOKEN_CONTEXT, availabilityRule);
    }
}
