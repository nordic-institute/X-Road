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

package org.niis.xroad.ds.identityhub.claim;

import org.eclipse.edc.iam.decentralizedclaims.sts.service.StsClientTokenGeneratorServiceImpl;
import org.eclipse.edc.iam.decentralizedclaims.sts.spi.service.StsClientTokenGeneratorService;
import org.eclipse.edc.identityhub.spi.authentication.ParticipantSecureTokenService;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.ds.identityhub.memberid.ParticipantMemberIdConfig;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import static org.niis.xroad.ds.identityhub.claim.XRoadClaimSignerExtension.NAME;

/**
 * Wires the IdentityHub's token services so that only credential-request tokens carry the signed
 * X-Road MemberId claim, using the member's X-Road sign key via the local signer service over gRPC.
 *
 * <p>Two services are published:
 * <ul>
 *   <li>{@link ParticipantSecureTokenService}: the {@link XRoadClaimAwareSecureTokenService} wrapper
 *   around the upstream {@code EmbeddedSecureTokenService}. Its consumers (notably
 *   {@code CredentialRequestManagerImpl}, built by {@code CoreServicesExtension}) therefore send
 *   the claim to the Issuer Service.</li>
 *   <li>{@link StsClientTokenGeneratorService}: built over the plain, unwrapped embedded service, so
 *   the self-issued tokens that the STS endpoint mints for DSP requests stay small and carry no claim.
 *   Upstream {@code StsClientTokenGeneratorExtension} builds this service over
 *   {@link ParticipantSecureTokenService}, which would resolve to the wrapper; it is excluded through
 *   {@code xroad.edc.boot.excluded-service-extensions} and this extension takes its place.</li>
 * </ul>
 *
 * <p>Wrap mechanism: {@code @Provides(ParticipantSecureTokenService.class)} adds this extension to the
 * dependency graph as a provider of that service, so consumers that {@code @Inject} it are ordered after
 * this extension. The {@code @Inject ParticipantSecureTokenService delegate} field resolves to the upstream
 * {@code EmbeddedSecureTokenService} (the self-edge is filtered by the dependency graph). The wrapper is
 * registered during {@code initialize()}, before downstream consumers' inject phase runs.
 */
@Extension(NAME)
@Provides(ParticipantSecureTokenService.class)
public class XRoadClaimSignerExtension implements ServiceExtension {

    static final String NAME = "X-Road Claim Signer Extension";

    private static final String DEFAULT_STS_TOKEN_EXPIRATION_MIN = "5";

    @Setting(key = "xroad.identityhub.claim.lifetime-seconds",
            description = "Lifetime in seconds of the signed X-Road MemberId claim JWS (exp - iat).",
            defaultValue = "300")
    private long claimLifetimeSeconds;

    @Setting(key = "edc.iam.sts.token.expiration",
            description = "Self-issued ID Token expiration in minutes. By default is 5 minutes",
            defaultValue = DEFAULT_STS_TOKEN_EXPIRATION_MIN)
    private long stsTokenExpirationMin;

    @Inject
    private ParticipantSecureTokenService delegate;

    @Inject
    private IdentityHubParticipantContextService participantContextService;

    @Inject
    private ParticipantContextConfigService participantContextConfigService;

    @Inject
    private SignerRpcClient signerRpcClient;

    @Inject
    private SignerSignClient signerSignClient;

    @Inject
    private Clock clock;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var signer = new SignerServiceMemberClaimSigner(
                signerRpcClient, signerSignClient,
                new MemberClaimSignerProperties(claimLifetimeSeconds), clock);
        var wrapper = new XRoadClaimAwareSecureTokenService(
                delegate, participantContextService, new ParticipantMemberIdConfig(participantContextConfigService),
                signer, context.getMonitor());
        context.registerService(ParticipantSecureTokenService.class, wrapper);
        context.getMonitor().info("X-Road MemberId claim signer installed");
    }

    @Provider
    public StsClientTokenGeneratorService clientTokenService() {
        return new StsClientTokenGeneratorServiceImpl(TimeUnit.MINUTES.toSeconds(stsTokenExpirationMin), delegate);
    }

}
