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
package ee.ria.xroad.common.conf.globalconf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.confclient.proto.ConfClientRpcClient;
import org.niis.xroad.confclient.proto.GetGlobalConfResp;
import org.niis.xroad.confclient.proto.GlobalConfInstance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteGlobalConfSourceTest extends BaseRemoteGlobalConfTest {
    @Mock
    ConfClientRpcClient confClientRpcClient;
    @Spy
    RemoteGlobalConfDataLoader dataLoader = new RemoteGlobalConfDataLoader();

    @InjectMocks
    private RemoteGlobalConfSource remoteGlobalConfSource;

    @Test
    void shouldLoadConfDataOnce() throws Exception {
        when(confClientRpcClient.getGlobalConf()).thenReturn(loadGlobalConf(INSTANCE_IDENTIFIER, PATH_GOOD_GLOBALCONF,
                1000L));

        remoteGlobalConfSource.reload();
        remoteGlobalConfSource.reload();
        remoteGlobalConfSource.reload();

        assertThat(remoteGlobalConfSource.getReadinessState()).isEqualTo(GlobalConfInitState.INITIALIZED);
        assertThat(remoteGlobalConfSource.getShared()).isNotEqualTo(emptyList());
        assertThat(remoteGlobalConfSource.getSharedParametersCaches()).isNotEqualTo(emptyList());
        assertThat(remoteGlobalConfSource.findPrivate(INSTANCE_IDENTIFIER)).isPresent();
        assertThat(remoteGlobalConfSource.findShared(INSTANCE_IDENTIFIER)).isPresent();
        assertThat(remoteGlobalConfSource.findSharedParametersCache(INSTANCE_IDENTIFIER)).isPresent();

        verify(confClientRpcClient, times(3)).getGlobalConf();
        verify(dataLoader, times(1)).load(any(), any(), any());
    }

    @Test
    void shouldReloadOnChange() throws Exception {
        when(confClientRpcClient.getGlobalConf()).thenReturn(loadGlobalConf(INSTANCE_IDENTIFIER, PATH_GOOD_GLOBALCONF,
                1000L));

        remoteGlobalConfSource.reload();
        remoteGlobalConfSource.reload();

        when(confClientRpcClient.getGlobalConf()).thenReturn(loadGlobalConf(INSTANCE_IDENTIFIER, PATH_GOOD_GLOBALCONF,
                2000L));

        remoteGlobalConfSource.reload();

        assertThat(remoteGlobalConfSource.getReadinessState()).isEqualTo(GlobalConfInitState.INITIALIZED);
        assertThat(remoteGlobalConfSource.getShared()).isNotEqualTo(emptyList());

        verify(confClientRpcClient, times(3)).getGlobalConf();
        verify(dataLoader, times(2)).load(any(), any(), any());
    }

    @Test
    void shouldReturnVersion() throws Exception {
        when(confClientRpcClient.getGlobalConf()).thenReturn(GetGlobalConfResp.newBuilder()
                .setDateRefreshed(1000L).setInstanceIdentifier(INSTANCE_IDENTIFIER)
                .addInstances(GlobalConfInstance.newBuilder()
                        .setVersion(1)
                        .setInstanceIdentifier(INSTANCE_IDENTIFIER)
                        .build())
                .build());

        Integer version = remoteGlobalConfSource.getVersion();

        assertThat(version).isEqualTo(1);
    }

    @Test
    void shouldReturnNotExpired() throws Exception {
        when(confClientRpcClient.getGlobalConf()).thenReturn(loadGlobalConf(INSTANCE_IDENTIFIER, PATH_GOOD_GLOBALCONF,
                1000L));

        var result = remoteGlobalConfSource.isExpired();

        assertThat(result).isFalse();
    }

    @Test
    void shouldCatchExceptionsOnLoading() throws Exception {
        remoteGlobalConfSource.afterPropertiesSet();

        verify(confClientRpcClient, times(1)).getGlobalConf();
    }

}
