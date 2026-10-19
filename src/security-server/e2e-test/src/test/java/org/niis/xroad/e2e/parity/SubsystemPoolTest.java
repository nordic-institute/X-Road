/*
 * The MIT License
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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.parity.SubsystemPool.Entry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubsystemPoolTest {

    @Test
    void poolHoldsTheSiblingTheSecondConsumerAndTheUnregisteredProvider() {
        assertThat(SubsystemPool.entries()).containsExactly(
                new Entry(SubsystemPool.SIBLING_OF_CONSUMER, SubsystemPool.SS1, true),
                new Entry(SubsystemPool.SECOND_CONSUMER, SubsystemPool.SS1, true),
                new Entry(SubsystemPool.UNREGISTERED_PROVIDER, SubsystemPool.SS0, false));
    }

    @Test
    void siblingBelongsToTheConsumersMemberAndTheSecondConsumerToTheProvidersMember() {
        assertThat(SubsystemPool.SIBLING_OF_CONSUMER).startsWith(memberOf(ScenarioCatalogue.CONSUMER));
        assertThat(SubsystemPool.SECOND_CONSUMER).startsWith(memberOf(ScenarioCatalogue.PROVIDER));
        assertThat(SubsystemPool.UNREGISTERED_PROVIDER).startsWith(memberOf(ScenarioCatalogue.PROVIDER));
    }

    @Test
    void poolClientIdsAreUniqueAndNeverCollideWithTheSeededSubsystems() {
        var ids = SubsystemPool.entries().stream().map(Entry::clientId).toList();

        assertThat(ids).doesNotHaveDuplicates()
                .doesNotContain(ScenarioCatalogue.PROVIDER, ScenarioCatalogue.CONSUMER, ScenarioCatalogue.OTHER_CONSUMER);
    }

    @Test
    void byHostGroupsEntriesKeepingTheDeclaredOrder() {
        var grouped = SubsystemPool.byHost(SubsystemPool.entries());

        assertThat(grouped.keySet()).containsExactly(SubsystemPool.SS1, SubsystemPool.SS0);
        assertThat(grouped.get(SubsystemPool.SS1)).extracting(Entry::clientId)
                .containsExactly(SubsystemPool.SIBLING_OF_CONSUMER, SubsystemPool.SECOND_CONSUMER);
    }

    @Test
    void onlyRegisterEntriesAreSelectedForRegistration() {
        assertThat(SubsystemPool.clientIdsToRegister(SubsystemPool.entries()))
                .containsExactly(SubsystemPool.SIBLING_OF_CONSUMER, SubsystemPool.SECOND_CONSUMER)
                .doesNotContain(SubsystemPool.UNREGISTERED_PROVIDER);
    }

    @Test
    void addedPoolEntryNeedsNoPoolLogicChange() {
        var extra = new Entry("DEV:COM:4321:Extra", SubsystemPool.SS0, true);

        assertThat(SubsystemPool.byHost(List.of(extra)).get(SubsystemPool.SS0)).containsExactly(extra);
        assertThat(SubsystemPool.clientIdsToRegister(List.of(extra))).containsExactly("DEV:COM:4321:Extra");
    }

    @Test
    void entryRejectsAMemberIdAndABlankHost() {
        assertThatThrownBy(() -> new Entry("DEV:COM:4321", SubsystemPool.SS1, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Entry("DEV:COM:4321:Sub", " ", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String memberOf(String clientId) {
        return clientId.substring(0, clientId.lastIndexOf(':') + 1);
    }
}
