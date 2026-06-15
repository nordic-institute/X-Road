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
import org.eclipse.edc.participant.spi.ParticipantAgentPolicyContext;
import org.niis.xroad.restapi.converter.ClientIdConverter;

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

    public static Optional<ClientId> findMemberIdFromContext(ParticipantAgentPolicyContext context) {
        var participantAgent = context.participantAgent();
        if (participantAgent == null) {
            log.debug("findMemberIdFromContext: participantAgent is null, returning empty");
            return Optional.empty();
        }
        var attributes = participantAgent.getAttributes();
        var xroadInstance = attributes.get(XRD_INSTANCE_ATTRIBUTE);
        var memberClass = attributes.get(XRD_MEMBER_CLASS_ATTRIBUTE);
        var memberCode = attributes.get(XRD_MEMBER_CODE_ATTRIBUTE);
        log.debug("findMemberIdFromContext: identity={} attributes={} instance={} class={} code={}",
                participantAgent.getIdentity(), attributes.keySet(), xroadInstance, memberClass, memberCode);
        if (isBlank(xroadInstance) || isBlank(memberClass) || isBlank(memberCode)) {
            throw new IllegalStateException("Invalid member identifier: instance=" + xroadInstance
                    + ", memberClass=" + memberClass + ", memberCode=" + memberCode);
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
