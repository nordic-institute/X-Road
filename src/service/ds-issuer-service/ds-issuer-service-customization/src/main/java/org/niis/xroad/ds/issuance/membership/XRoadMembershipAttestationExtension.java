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

package org.niis.xroad.ds.issuance.membership;

import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationSourceFactoryRegistry;
import org.eclipse.edc.jwt.validation.jti.JtiValidationStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;

import java.time.Clock;

import static org.niis.xroad.ds.issuance.membership.XRoadMembershipAttestationExtension.NAME;

/**
 * Registers the X-Road MemberId-claim-verifying attestation source under the {@code holder}
 * type, overriding EDC's built-in DID-control attestation.
 *
 * <p>Cred-defs continue to reference {@code attestationType: holder} unchanged. Behavior
 * becomes: holder must present a signed X-Road MemberId claim in the DCP JWT, verified
 * against X-Road global conf via {@link GlobalConfMemberIdClaimVerifier}. The X-Road-side
 * {@link GlobalConfProvider} is resolved through EDC {@code @Inject} (which delegates to
 * Quarkus CDI for beans not provided by an EDC extension).
 */
@Extension(NAME)
public class XRoadMembershipAttestationExtension implements ServiceExtension {

    static final String NAME = "X-Road Membership Attestation Extension";

    static final String ATTESTATION_TYPE = "holder";

    @Setting(key = "xroad.issuer.claim.lifetime-seconds",
            description = "Maximum age in seconds of the X-Road MemberId claim JWS (iat to exp).",
            defaultValue = "300")
    private long claimLifetimeSeconds;

    @Setting(key = "xroad.issuer.claim.leeway-seconds",
            description = "Allowed clock skew in seconds when checking the claim JWS iat/exp.",
            defaultValue = "30")
    private long claimLeewaySeconds;

    @Inject
    private AttestationSourceFactoryRegistry registry;

    @Inject
    private IdentityHubParticipantContextService participantContextService;

    @Inject
    private GlobalConfProvider globalConf;

    @Inject
    private TokenValidationService tokenValidationService;

    @Inject
    private JtiValidationStore jtiValidationStore;

    @Inject
    private Clock clock;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var verifier = buildVerifier(context);
        registry.registerFactory(ATTESTATION_TYPE,
                new XRoadMembershipAttestationSourceFactory(verifier, participantContextService));
        context.getMonitor().info("X-Road MembershipCredential attestation registered for type '"
                + ATTESTATION_TYPE + "'");
    }

    private GlobalConfMemberIdClaimVerifier buildVerifier(ServiceExtensionContext context) {
        var properties = new MemberClaimVerifierProperties(claimLifetimeSeconds, claimLeewaySeconds);
        var xroadOcspVerifierFactory = new OcspVerifierFactory();
        var certChainValidator = new CertChainValidator(globalConf, xroadOcspVerifierFactory, clock);
        var ocspVerifier = new OcspVerifier(globalConf, xroadOcspVerifierFactory, clock);
        return new GlobalConfMemberIdClaimVerifier(globalConf,
                certChainValidator,
                ocspVerifier,
                tokenValidationService,
                jtiValidationStore,
                properties,
                context.getMonitor(),
                clock);
    }
}
