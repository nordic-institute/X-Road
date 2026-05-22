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

import org.eclipse.edc.identityhub.spi.authentication.ParticipantSecureTokenService;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import static org.niis.xroad.ds.identityhub.claim.XRoadClaimSignerExtension.NAME;

/**
 * Installs the {@link XRoadClaimAwareSecureTokenService} wrap only when running inside the
 * identity-hub runtime. In the issuer-service runtime (which does not include
 * {@code identity-hub-core}) the extension is a no-op and the unwrapped
 * {@code EmbeddedSecureTokenService} is left in place for issuer-side PSTS consumers
 * (offer service, credential storage client, STS client token generator).
 *
 * <p>Wrap mechanism: {@code @Provides(ParticipantSecureTokenService.class)} adds this
 * extension to the dependency graph as a provider of PSTS, so consumers that
 * {@code @Inject} it (notably {@code CoreServicesExtension} which builds
 * {@code CredentialRequestManagerImpl}) are topologically ordered after us. The
 * {@code @Inject ParticipantSecureTokenService delegate} field resolves to the upstream
 * {@code EmbeddedSecureTokenService} (the self-edge is filtered by the dependency graph).
 * We then {@code registerService} the wrapper during {@code initialize()}, before
 * downstream consumers' inject phase runs.
 *
 * <p>Scope: in the identity-hub runtime the only {@code @Inject}-driven PSTS consumer is
 * {@code CoreServicesExtension} (for CRM). {@code StsClientTokenGeneratorServiceImpl} uses
 * the embedded service via a direct method call inside {@code EmbeddedStsServiceExtension},
 * not via the service registry, so the registry swap does not affect it. Net effect: only
 * the holder-side credential-request flow is wrapped.
 *
 * <p>Runtime detection: a static {@code Class.forName} probe for
 * {@code CredentialRequestManagerImpl} discriminates the identity-hub runtime from the
 * issuer-service runtime. Using {@code @Inject(required = false)} for that role would
 * introduce a topological cycle ({@code us → CoreServicesExtension → us} via the PSTS
 * provides-edge). The static class probe creates no edge.
 *
 * <p>Signer activation is controlled by {@code xroad.identityhub.sign-claim}.
 * Default {@code true} (production): a real {@link MemberClaimSigner} bean must be
 * available (typically a signer-service gRPC client — follow-up layer).
 * Set to {@code false} for system-test: a stub claim is produced, paired with the
 * issuer-side bypass verifier for end-to-end testability.
 */
@Extension(NAME)
@Provides(ParticipantSecureTokenService.class)
public class XRoadClaimSignerExtension implements ServiceExtension {

    static final String NAME = "X-Road Claim Signer Extension";

    private static final String CRM_IMPL_CLASS =
            "org.eclipse.edc.identityhub.core.services.verifiablecredential.CredentialRequestManagerImpl";

    private static final boolean IDENTITY_HUB_RUNTIME = isClassPresent(CRM_IMPL_CLASS);

    @Setting(key = "xroad.identityhub.sign.claim",
            description = "Whether to sign the X-Road MemberId claim with a real X-Road sign key. "
                    + "Must be true in production. Set false only for system-test fixtures.",
            defaultValue = "true")
    private boolean signClaim;

    @Inject
    private ParticipantSecureTokenService delegate;

    @Inject
    private IdentityHubParticipantContextService participantContextService;

    @Inject(required = false)
    private MemberClaimSigner injectedSigner;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        if (!IDENTITY_HUB_RUNTIME) {
            context.getMonitor().debug("X-Road claim signer skipped — " + CRM_IMPL_CLASS
                    + " not on classpath (not an identity-hub runtime)");
            return;
        }
        MemberClaimSigner signer = resolveSigner(context);
        var wrapper = new XRoadClaimAwareSecureTokenService(
                delegate, participantContextService, signer, context.getMonitor());
        context.registerService(ParticipantSecureTokenService.class, wrapper);
        context.getMonitor().info("X-Road MemberId claim signer installed (sign-claim=" + signClaim + ")");
    }

    private static boolean isClassPresent(String fqcn) {
        try {
            Class.forName(fqcn, false, XRoadClaimSignerExtension.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private MemberClaimSigner resolveSigner(ServiceExtensionContext context) {
        if (!signClaim) {
            context.getMonitor().warning(
                    "X-Road MemberId claim signing is using the STUB signer. "
                            + "This is acceptable only in system-test environments. "
                            + "Set xroad.identityhub.sign-claim=true in production.");
            return new StubMemberClaimSigner();
        }
        if (injectedSigner == null) {
            throw new IllegalStateException(
                    "xroad.identityhub.sign-claim=true but no MemberClaimSigner bean is available. "
                            + "Wire a signer-service-backed signer or set xroad.identityhub.sign-claim=false "
                            + "in test environments.");
        }
        return injectedSigner;
    }
}
