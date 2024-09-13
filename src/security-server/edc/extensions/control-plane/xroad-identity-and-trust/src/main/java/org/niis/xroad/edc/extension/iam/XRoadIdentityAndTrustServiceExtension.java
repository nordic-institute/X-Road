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

package org.niis.xroad.edc.extension.iam;

import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.iam.identitytrust.service.DidCredentialServiceUrlResolver;
import org.eclipse.edc.iam.identitytrust.service.IdentityAndTrustService;
import org.eclipse.edc.iam.identitytrust.spi.ClaimTokenCreatorFunction;
import org.eclipse.edc.iam.identitytrust.spi.CredentialServiceClient;
import org.eclipse.edc.iam.identitytrust.spi.SecureTokenService;
import org.eclipse.edc.iam.identitytrust.spi.validation.TokenValidationAction;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.PresentationVerifier;
import org.eclipse.edc.iam.verifiablecredentials.spi.validation.TrustedIssuerRegistry;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.spi.TokenValidationRulesRegistry;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;

import static org.eclipse.edc.iam.identitytrust.core.IdentityAndTrustExtension.CONNECTOR_DID_PROPERTY;
import static org.eclipse.edc.iam.identitytrust.core.IdentityAndTrustExtension.DCP_SELF_ISSUED_TOKEN_CONTEXT;
import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

@Provides(IdentityService.class)
@Extension("X-Road Identity And Trust Extension")
public class XRoadIdentityAndTrustServiceExtension implements ServiceExtension {

    @Inject
    private CredentialServiceClient credentialServiceClient;

    @Inject
    private SecureTokenService secureTokenService;

    @Inject
    private RevocationServiceRegistry revocationServiceRegistry;

    @Inject
    private TrustedIssuerRegistry trustedIssuerRegistry;

    @Inject
    private PresentationVerifier presentationVerifier;

    @Inject
    private DidResolverRegistry didResolverRegistry;

    @Inject
    private TokenValidationService tokenValidationService;

    @Inject
    private TokenValidationRulesRegistry rulesRegistry;

    @Inject
    private DidPublicKeyResolver didPublicKeyResolver;

    @Inject
    private TypeManager typeManager;

    @Inject
    private Clock clock;

    @Inject
    private ClaimTokenCreatorFunction claimTokenFunction;

    @Inject
    private Monitor monitor;

    @Override
    public void initialize(ServiceExtensionContext context) {
        context.registerService(IdentityService.class, createIdentityService(context));
    }

    private IdentityService createIdentityService(ServiceExtensionContext context) {
        var credentialServiceUrlResolver = new DidCredentialServiceUrlResolver(didResolverRegistry);
        var validationAction = tokenValidationAction();

        var credentialValidationService = new VerifiableCredentialValidationServiceImpl(presentationVerifier,
                trustedIssuerRegistry, revocationServiceRegistry, clock, typeManager.getMapper(JSON_LD), monitor);

        return new IdentityAndTrustService(secureTokenService, getOwnDid(context),
                credentialServiceClient, validationAction, credentialServiceUrlResolver, claimTokenFunction,
                credentialValidationService);
    }

    @NotNull
    private TokenValidationAction tokenValidationAction() {
        return (tokenRepresentation) -> {
            var rules = rulesRegistry.getRules(DCP_SELF_ISSUED_TOKEN_CONTEXT);
            return tokenValidationService.validate(tokenRepresentation, didPublicKeyResolver, rules);
        };
    }

    private String getOwnDid(ServiceExtensionContext context) {
        var ownDid = context.getConfig().getString(CONNECTOR_DID_PROPERTY, null);
        if (ownDid == null) {
            context.getMonitor().severe("Mandatory config value missing: '%s'. This runtime will not be fully operational!".formatted(CONNECTOR_DID_PROPERTY));
        }
        return ownDid;
    }

}
