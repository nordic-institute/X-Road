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
package org.niis.xroad.common.rpc.client;

import io.grpc.InsecureChannelCredentials;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.credentials.ChannelTrust;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RpcChannelFactoryTest {
    private final RpcCredentialsConfigurer configurer = mock(RpcCredentialsConfigurer.class);
    private final RpcChannelFactory factory = new RpcChannelFactory(configurer);

    @Test
    void channelWithTrustGetsTheCredentialsOfThatTrust() {
        var trust = ChannelTrust.shared();
        when(configurer.createClientCredentials(trust)).thenReturn(InsecureChannelCredentials.create());

        var channel = factory.createChannel(properties(10_000), trust);

        channel.shutdownNow();
        verify(configurer).createClientCredentials(trust);
        verify(configurer, never()).createClientCredentials();
    }

    @Test
    void channelWithoutTrustGetsTheSharedCredentials() {
        when(configurer.createClientCredentials()).thenReturn(InsecureChannelCredentials.create());

        var channel = factory.createChannel(properties(10_000));

        channel.shutdownNow();
        verify(configurer).createClientCredentials();
    }

    @Test
    void deadlineThatIsNotPositiveIsRejectedBeforeCredentialsAreBuilt() {
        assertThatThrownBy(() -> factory.createChannel(properties(0), ChannelTrust.shared()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(configurer);
    }

    private static RpcChannelProperties properties(int deadlineAfter) {
        return new RpcChannelProperties() {
            @Override
            public String host() {
                return "127.0.0.1";
            }

            @Override
            public int port() {
                return 1;
            }

            @Override
            public int deadlineAfter() {
                return deadlineAfter;
            }
        };
    }
}
