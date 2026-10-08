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

package org.niis.xroad.edc.extension.policy.controlplane.util;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.contract.spi.policy.AgreementPolicyContext;
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.eclipse.edc.policy.engine.spi.PolicyContext;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.niis.xroad.edc.extension.policy.controlplane.participantagent.XRoadMemberIdAttributes;
import org.niis.xroad.restapi.converter.ClientIdConverter;

import java.util.Map;
import java.util.Optional;

@Slf4j
@UtilityClass
public class PolicyContextHelper {

    /**
     * Participant-agent attribute keys carrying the consumer's X-Road member identity, set by
     * {@code XRoadMemberIdAttributes} from the {@code XRoadMembershipCredential} VC claims. The
     * {@link ClientId} is assembled from them in {@link #findMemberIdFromContext}.
     */
    public static final String XRD_INSTANCE_ATTRIBUTE = "xrd:xroadInstance";
    public static final String XRD_MEMBER_CLASS_ATTRIBUTE = "xrd:memberClass";
    public static final String XRD_MEMBER_CODE_ATTRIBUTE = "xrd:memberCode";

    private final ClientIdConverter clientIdConverter = new ClientIdConverter();
    private final XRoadMemberIdAttributes memberIdAttributes = new XRoadMemberIdAttributes();

    /**
     * Resolves the consumer's X-Road member identity from whichever shape of {@code context} is
     * available: a live DSP request carries a {@link ParticipantAgentPolicyContext} (catalog,
     * negotiation and transfer scopes), while the policy monitor's {@code PolicyMonitorContext} only
     * carries the stored agreement ({@link AgreementPolicyContext}), replayed outside of any request.
     */
    public static Optional<ClientId> findMemberId(PolicyContext context) {
        if (context instanceof ParticipantAgentPolicyContext participantAgentContext) {
            return findMemberIdFromContext(participantAgentContext);
        }
        if (context instanceof AgreementPolicyContext agreementContext) {
            var agreement = agreementContext.contractAgreement();
            return agreement == null ? Optional.empty() : findMemberIdFromClaims(agreement.getClaims());
        }
        log.debug("findMemberId: context {} is neither participant-agent- nor agreement-based, returning empty",
                context.getClass());
        return Optional.empty();
    }

    public static Optional<ClientId> findMemberIdFromContext(ParticipantAgentPolicyContext context) {
        var participantAgent = context.participantAgent();
        if (participantAgent == null) {
            log.debug("findMemberIdFromContext: participantAgent is null, returning empty");
            return Optional.empty();
        }
        var attributes = participantAgent.getAttributes();
        log.debug("findMemberIdFromContext: identity={} attributes={}",
                participantAgent.getIdentity(), attributes.keySet());
        return fromAttributes(attributes);
    }

    /**
     * Resolves the consumer's X-Road member identity from a stored {@link AgreementPolicyContext}'s
     * claims — the same {@code XRoadMembershipCredential} claims a live request's {@link ClaimToken}
     * would carry, snapshotted onto the agreement when it was reached ({@code
     * ContractNegotiationProtocolServiceImpl}) — by replaying them through the same extraction
     * {@code XRoadMemberIdAttributes} uses for a live request.
     */
    public static Optional<ClientId> findMemberIdFromClaims(Map<String, Object> claims) {
        var token = ClaimToken.Builder.newInstance().claims(claims).build();
        var attributes = memberIdAttributes.attributesFor(token);
        log.debug("findMemberIdFromClaims: attributes={}", attributes.keySet());
        return fromAttributes(attributes);
    }

    private static Optional<ClientId> fromAttributes(Map<String, String> attributes) {
        var xroadInstance = attributes.get(XRD_INSTANCE_ATTRIBUTE);
        var memberClass = attributes.get(XRD_MEMBER_CLASS_ATTRIBUTE);
        var memberCode = attributes.get(XRD_MEMBER_CODE_ATTRIBUTE);
        if (isBlank(xroadInstance) || isBlank(memberClass) || isBlank(memberCode)) {
            log.debug("fromAttributes: no X-Road membership attributes present, returning empty");
            return Optional.empty();
        }
        return Optional.of(ClientId.Conf.create(xroadInstance, memberClass, memberCode));
    }

    public static ClientId parseClientId(String value) {
        return clientIdConverter.convertId(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
