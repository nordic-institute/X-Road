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
package org.niis.xroad.proxy.core.addon.opmonitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.StoreOpMonitoringDataResponse;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests operational monitoring buffer.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class OpMonitoringBufferImplTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CloseableHttpClient httpClient;

    @SuppressWarnings("checkstyle:FinalClass")
    private class TestOpMonitoringBufferImpl extends OpMonitoringBufferImpl {
        TestOpMonitoringBufferImpl(
                ProxyProperties.ProxyAddonProperties.ProxyAddonOpMonitorProperties opMonitorProperties) throws Exception {
            super(mock(ServerConfProvider.class), opMonitorProperties, mock(VaultClient.class), false);
        }

        @Override
        OpMonitoringDaemonSender createSender(ServerConfProvider serverConfProvider,
                                              ProxyProperties.ProxyAddonProperties.ProxyAddonOpMonitorProperties opMonitorProperties,
                                              VaultClient vaultClient, boolean isEnabledPooledConnectionReuse)
                throws UnrecoverableKeyException, CertificateException, KeyStoreException, IOException,
                NoSuchAlgorithmException, KeyManagementException, InvalidKeySpecException {
            return new OpMonitoringDaemonSender(serverConfProvider, this, opMonitorProperties,
                    vaultClient, isEnabledPooledConnectionReuse) {
                @Override
                CloseableHttpClient createHttpClient() {
                    return httpClient;
                }
            };
        }

        @Override
        OpMonitoringDataProcessor createDataProcessor() {
            return new TestOpMonitoringDataProcessor();
        }
    }

    private static final class TestOpMonitoringDataProcessor extends OpMonitoringDataProcessor {
        @Override
        String getIpAddress() {
            return "127.0.0.1";
        }
    }

    @Test
    void bufferSaturatesUnderLoad() throws Exception {

        when(httpClient.execute(any(HttpRequestBase.class), any(HttpContext.class))).thenAnswer(invocation -> {
            doSleep(20, 80);

            CloseableHttpResponse response = mock(CloseableHttpResponse.class, RETURNS_DEEP_STUBS);
            when(response.getStatusLine().getStatusCode()).thenReturn(200);
            when(response.getAllHeaders()).thenReturn(new Header[0]);

            when(response.getEntity().getContent())
                    .thenReturn(IOUtils.toInputStream(objectMapper.writeValueAsString(new StoreOpMonitoringDataResponse()), UTF_8));
            return response;
        });

        ProxyProperties.ProxyAddonProperties.ProxyAddonOpMonitorProperties opMonitorProperties =
                ConfigUtils.initConfiguration(ProxyProperties.ProxyAddonProperties.class, Map.of(
                        "xroad.proxy.addon.op-monitor.buffer.size", "10000"
                )).opMonitor();

        final TestOpMonitoringBufferImpl opMonitoringBuffer = new TestOpMonitoringBufferImpl(opMonitorProperties);
        int requestCount = 30_000;
        AtomicInteger processedCounter = new AtomicInteger();
        try (ExecutorService executorService = Executors.newFixedThreadPool(80)) {
            IntStream.range(0, requestCount).forEach(index -> executorService.execute(() -> {
                doSleep(0, 50);
                OpMonitoringData opMonitoringData = new OpMonitoringData(
                        OpMonitoringData.SecurityServerType.CLIENT, RandomUtils.secure().randomLong());

                try {
                    opMonitoringBuffer.store(opMonitoringData);
                    processedCounter.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                if (index % 10000 == 0) {
                    log.info("Current execution {}+", index);
                }
            }));

            Awaitility.await()
                    .atMost(Duration.ofSeconds(120))
                    .pollDelay(Duration.ofSeconds(1))
                    .untilAsserted(() -> {
                        assertEquals(requestCount, processedCounter.get());
                        assertEquals(0, opMonitoringBuffer.getCurrentBufferSize());
                    });
        }
    }

    @Test
    void bufferOverflow() throws Exception {
        ProxyProperties.ProxyAddonProperties.ProxyAddonOpMonitorProperties opMonitorProperties =
                ConfigUtils.initConfiguration(ProxyProperties.ProxyAddonProperties.class, Map.of(
                        "xroad.proxy.addon.op-monitor.buffer.size", "2"
                )).opMonitor();

        final TestOpMonitoringBufferImpl opMonitoringBuffer = new TestOpMonitoringBufferImpl(opMonitorProperties) {
            @Override
            OpMonitoringDaemonSender createSender(ServerConfProvider serverConfProvider,
                                                  ProxyProperties.ProxyAddonProperties.ProxyAddonOpMonitorProperties opMonitorProperties,
                                                  VaultClient vaultTlsCredentialsProvider,
                                                  boolean isEnabledPooledConnectionReuse) {
                var mockedSender = mock(OpMonitoringDaemonSender.class);
                when(mockedSender.isReady()).thenReturn(false);
                return mockedSender;
            }
        };
        OpMonitoringData opMonitoringData1 = new OpMonitoringData(
                OpMonitoringData.SecurityServerType.CLIENT, 100);
        OpMonitoringData opMonitoringData2 = new OpMonitoringData(
                OpMonitoringData.SecurityServerType.CLIENT, 200);
        OpMonitoringData opMonitoringData3 = new OpMonitoringData(
                OpMonitoringData.SecurityServerType.CLIENT, 300);

        opMonitoringBuffer.store(opMonitoringData1);
        opMonitoringBuffer.store(opMonitoringData2);
        opMonitoringBuffer.store(opMonitoringData3);

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertEquals(2, opMonitoringBuffer.buffer.size());
                    assertFalse(opMonitoringBuffer.buffer.contains(opMonitoringData1));
                    assertTrue(opMonitoringBuffer.buffer.contains(opMonitoringData2));
                    assertTrue(opMonitoringBuffer.buffer.contains(opMonitoringData3));
                });
    }

    @Test
    void noOpMonitoringDataIsStored() throws Exception {
        var serverConfProvider = mock(ServerConfProvider.class);
        var vaultTlsCredentialsProvider = mock(VaultClient.class);
        new OpMonitoringBufferImpl(serverConfProvider,
                ConfigUtils.initConfiguration(ProxyProperties.ProxyAddonProperties.class, Map.of(
                        "xroad.proxy.addon.op-monitor.buffer.size", "0"
                )).opMonitor(),
                vaultTlsCredentialsProvider, false);
        verifyNoInteractions(serverConfProvider);
    }

    @SneakyThrows
    @SuppressWarnings("squid:S2925")
    private void doSleep(long min, long max) {
        var sleep = RandomUtils.secure().randomLong(min, max);
        Thread.sleep(sleep);
    }
}
