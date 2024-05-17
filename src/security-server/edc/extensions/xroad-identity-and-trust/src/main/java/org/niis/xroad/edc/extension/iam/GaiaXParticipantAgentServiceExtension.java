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
