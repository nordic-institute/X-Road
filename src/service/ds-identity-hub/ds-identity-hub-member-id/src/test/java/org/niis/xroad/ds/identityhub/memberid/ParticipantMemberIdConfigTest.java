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

import org.eclipse.edc.encryption.EncryptionAlgorithmRegistryImpl;
import org.eclipse.edc.participantcontext.config.defaults.store.InMemoryParticipantContextConfigStore;
import org.eclipse.edc.participantcontext.config.service.ParticipantContextConfigServiceImpl;
import org.eclipse.edc.participantcontext.config.validation.ParticipantContextConfigValidatorRegistryImpl;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParticipantMemberIdConfigTest {

    private static final String CONTEXT_ID = "member-1";
    private static final String MEMBER_ID = "DEV/COM/222";
    private static final String OTHER_MEMBER_ID = "DEV/COM/333";

    private ParticipantContextConfigService configService;
    private ParticipantMemberIdConfig memberIdConfig;

    @BeforeEach
    void setUp() {
        configService = new ParticipantContextConfigServiceImpl(new EncryptionAlgorithmRegistryImpl(), null,
                new InMemoryParticipantContextConfigStore(), new NoopTransactionContext(), Clock.systemUTC(),
                new ParticipantContextConfigValidatorRegistryImpl(false));
        memberIdConfig = new ParticipantMemberIdConfig(configService);
    }

    @Test
    void savedMemberIdIsFound() {
        assertThat(memberIdConfig.save(CONTEXT_ID, MEMBER_ID).succeeded()).isTrue();

        assertThat(memberIdConfig.find(CONTEXT_ID)).contains(MEMBER_ID);
    }

    @Test
    void savingAgainOverwritesTheMemberId() {
        memberIdConfig.save(CONTEXT_ID, MEMBER_ID);

        assertThat(memberIdConfig.save(CONTEXT_ID, OTHER_MEMBER_ID).succeeded()).isTrue();

        assertThat(memberIdConfig.find(CONTEXT_ID)).contains(OTHER_MEMBER_ID);
    }

    @Test
    void unknownContextHasNoMemberId() {
        assertThat(memberIdConfig.find("unknown")).isEmpty();
    }

    @Test
    void contextWithoutMemberIdEntryHasNoMemberId() {
        configService.save(ParticipantContextConfiguration.Builder.newInstance()
                .participantContextId(CONTEXT_ID).privateEntry("other", "value").build());

        assertThat(memberIdConfig.find(CONTEXT_ID)).isEmpty();
    }

    @Test
    void unexpectedConfigurationReadFailureIsAnErrorNotAMissingMemberId() {
        var failingService = mock(ParticipantContextConfigService.class);
        when(failingService.get(CONTEXT_ID)).thenReturn(ServiceResult.unexpected("store unavailable"));

        assertThatThrownBy(() -> new ParticipantMemberIdConfig(failingService).find(CONTEXT_ID))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("store unavailable");
    }

    @Test
    void removedMemberIdIsNoLongerFoundAndOtherEntriesStay() {
        configService.save(ParticipantContextConfiguration.Builder.newInstance()
                .participantContextId(CONTEXT_ID).privateEntry("other", "value").build());
        memberIdConfig.save(CONTEXT_ID, MEMBER_ID);

        assertThat(memberIdConfig.remove(CONTEXT_ID).succeeded()).isTrue();

        assertThat(memberIdConfig.find(CONTEXT_ID)).isEmpty();
        assertThat(configService.get(CONTEXT_ID).getContent().getPrivateEntries())
                .doesNotContainKey(ParticipantMemberIdConfig.MEMBER_ID_KEY)
                .containsEntry("other", "value");
    }

    @Test
    void removingFromUnknownContextSucceedsWithoutCreatingConfiguration() {
        assertThat(memberIdConfig.remove("unknown").succeeded()).isTrue();

        assertThat(configService.get("unknown").failed()).isTrue();
    }
}
