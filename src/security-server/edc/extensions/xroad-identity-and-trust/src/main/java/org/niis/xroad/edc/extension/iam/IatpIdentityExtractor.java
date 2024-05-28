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
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.agent.ParticipantAgentServiceExtension;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.result.Result;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.eclipse.edc.spi.agent.ParticipantAgent.PARTICIPANT_IDENTITY;

/**
 * Implementation of {@link ParticipantAgentServiceExtension} which extracts the identity of a participant
 * from the MembershipCredential
 */
public class IatpIdentityExtractor implements IatpParticipantAgentServiceExtension {

    private static final String VC_CLAIM = "vc";
    //private static final String IDENTITY_CREDENTIAL = "MembershipCredential";
    private static final String IDENTITY_CREDENTIAL = "VerifiableCredential";
    private static final String IDENTITY_PROPERTY = "holderIdentifier";

    //Credentials can target a specific type.
    //public static final String CREDENTIAL_NS = "https://w3id.org/catenax/credentials/";
    public static final String CREDENTIAL_NS = "https://www.w3.org/2018/credentials";
    private final CredentialTypePredicate typePredicate = new CredentialTypePredicate(CREDENTIAL_NS, IDENTITY_CREDENTIAL);

    @Override
    public @NotNull Map<String, String> attributesFor(ClaimToken claimToken) {
        var credentials = getCredentialList(claimToken)
                .orElseThrow(failure -> new EdcException("Failed to fetch credentials from the claim token: %s".formatted(failure.getFailureDetail())));

        String issuerDid = credentials.stream()
                .filter(typePredicate)
                .filter(vc -> vc.getId().endsWith("participant.json"))
                .findFirst()
                .map(vc -> vc.getIssuer().id())
                .orElseThrow();

        Map<String, String> attributes = new HashMap<>();
        attributes.put(PARTICIPANT_IDENTITY, issuerDid);

        credentials.stream()
                .filter(typePredicate)
                .forEach(vc -> attributes.putAll(resolveAttributes(vc)));

        return attributes;
    }

    private Map<String, String> resolveAttributes(VerifiableCredential vc) {
        Map<String, String> attributes = new HashMap<>();
        vc.getCredentialSubject().stream()
                .flatMap(credentialSubject -> credentialSubject.getClaims().entrySet().stream())
                .forEach(entry -> attributes.put(entry.getKey(), entry.getValue().toString()));
        return attributes;
    }

    private Optional<String> getIdentifier(VerifiableCredential verifiableCredential) {
        return verifiableCredential.getCredentialSubject().stream()
                .flatMap(credentialSubject -> credentialSubject.getClaims().entrySet().stream())
                .filter(entry -> entry.getKey().endsWith(IDENTITY_PROPERTY))
                .map(Map.Entry::getValue)
                .map(String.class::cast)
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private Result<List<VerifiableCredential>> getCredentialList(ClaimToken claimToken) {
        var vcListClaim = claimToken.getClaims().get(VC_CLAIM);

        if (vcListClaim == null) {
            return Result.failure("ClaimToken did not contain a '%s' claim.".formatted(VC_CLAIM));
        }
        if (!(vcListClaim instanceof List)) {
            return Result.failure("ClaimToken contains a '%s' claim, but the type is incorrect. Expected %s, got %s.".formatted(VC_CLAIM, List.class.getName(), vcListClaim.getClass().getName()));
        }
        var vcList = (List<VerifiableCredential>) vcListClaim;
        if (vcList.isEmpty()) {
            return Result.failure("ClaimToken contains a '%s' claim but it did not contain any VerifiableCredentials.".formatted(VC_CLAIM));
        }
        return Result.success(vcList);
    }

    private record CredentialTypePredicate(String credentialNamespace, String credentialType) implements Predicate<VerifiableCredential> {

        @Override
        public boolean test(VerifiableCredential credential) {
            return credential.getType().contains(credentialType) || credential.getType().contains(credentialNamespace + "#" + credentialType);
        }
    }

}