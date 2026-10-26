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

package org.niis.xroad.ds.identityhub.memberid;

import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;

import java.util.Optional;

/**
 * Answers which X-Road member a participant context belongs to. The canonical {@code INSTANCE/CLASS/CODE}
 * member id is kept as a private entry of the participant context configuration.
 */
public class ParticipantMemberIdConfig {

    static final String MEMBER_ID_KEY = "xroadMemberId";

    private final ParticipantContextConfigService configService;

    public ParticipantMemberIdConfig(ParticipantContextConfigService configService) {
        this.configService = configService;
    }

    /**
     * Returns the stored member id, or empty when the context has none or its configuration cannot be read.
     */
    public Optional<String> find(String participantContextId) {
        var config = configService.get(participantContextId);
        if (config.failed()) {
            return Optional.empty();
        }
        return Optional.ofNullable(config.getContent().getPrivateEntries().get(MEMBER_ID_KEY));
    }

    /**
     * Stores the member id, replacing any previous value and leaving other configuration entries untouched.
     */
    public ServiceResult<Void> save(String participantContextId, String memberId) {
        return configService.merge(patch(participantContextId, memberId));
    }

    /**
     * Removes the stored member id. Succeeds without writing when the context has no configuration.
     */
    public ServiceResult<Void> remove(String participantContextId) {
        var current = configService.get(participantContextId);
        if (current.failed()) {
            return current.reason() == ServiceFailure.Reason.NOT_FOUND
                    ? ServiceResult.success()
                    : ServiceResult.unexpected(current.getFailureDetail());
        }
        return configService.merge(patch(participantContextId, null));
    }

    private static ParticipantContextConfiguration patch(String participantContextId, String memberId) {
        return ParticipantContextConfiguration.Builder.newInstance()
                .participantContextId(participantContextId)
                .privateEntry(MEMBER_ID_KEY, memberId)
                .build();
    }
}
