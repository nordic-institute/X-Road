package org.niis.xroad.edc.extension.iam;

import org.eclipse.edc.iam.identitytrust.spi.IatpParticipantAgentServiceExtension;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.ofNullable;
import static org.eclipse.edc.iam.identitytrust.core.IatpDefaultServicesExtension.CLAIMTOKEN_VC_KEY;
import static org.eclipse.edc.spi.agent.ParticipantAgent.PARTICIPANT_IDENTITY;
import static org.niis.xroad.edc.extension.iam.GaiaXParticipantAgentServiceExtension.NAME;

/**
 * In case of Gaia-X VCs credentialSubject.id doesn't contain the identity in DID format -> take the identity from issuer
 */
@Extension(NAME)
public class GaiaXParticipantAgentServiceExtension implements ServiceExtension {

    static final String NAME = "X-Road participant agent extension";

    @Provider
    public IatpParticipantAgentServiceExtension createGaiaXIatpParticipantAgentServiceExtension() {
        return new IatpParticipantAgentServiceExtension() {
            @Override
            public @NotNull Map<String, String> attributesFor(ClaimToken token) {
                return ofNullable(token.getListClaim(CLAIMTOKEN_VC_KEY)).orElse(emptyList())
                        .stream()
                        .filter(VerifiableCredential.class::isInstance)
                        .map(o -> (VerifiableCredential) o)
                        .map(VerifiableCredential::getIssuer)
                        .map(Issuer::id)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .map(sub -> Map.of(PARTICIPANT_IDENTITY, sub))
                        .orElse(emptyMap());
            }
        };
    }

}
