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
package org.niis.xroad.test.globalconf;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestGlobalConfWrapperTest {
    private static final ClientId MEMBER = ClientId.Conf.create("DEV", "COM", "222");
    private static final ClientId SUBSYSTEM = ClientId.Conf.create("DEV", "COM", "222", "sub");
    private static final SecurityServerId SERVER = SecurityServerId.Conf.create(MEMBER, "ss0");

    private final GlobalConfProvider wrapped = mock(GlobalConfProvider.class);
    private final TestGlobalConfWrapper wrapper = new TestGlobalConfWrapper(wrapped);

    @Test
    void shouldDelegateServerSystemValuesLookup() {
        var values = new ServerSystemValues("did:web:ss0.example.org:system", "https://ss0.example.org/api/dsp");
        when(wrapped.getServerSystemValues(SERVER)).thenReturn(Optional.of(values));

        assertThat(wrapper.getServerSystemValues(SERVER)).contains(values);
    }

    @Test
    void shouldDelegateMemberDidLookup() {
        when(wrapped.getMemberDid(SUBSYSTEM, SERVER)).thenReturn(Optional.of("did:web:ss0.example.org:DEV:COM:222"));

        assertThat(wrapper.getMemberDid(SUBSYSTEM, SERVER)).contains("did:web:ss0.example.org:DEV:COM:222");
    }
}
