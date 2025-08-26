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

import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.TimeUtils;

import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.tls.vault.VaultTlsCredentialsProvider;
import org.niis.xroad.opmonitor.api.OpMonitorCommonProperties;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.opmonitor.api.OpMonitoringDaemonEndpoints;
import org.niis.xroad.opmonitor.api.OpMonitoringDaemonHttpClient;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.StoreOpMonitoringDataResponse;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.niis.xroad.opmonitor.api.StoreOpMonitoringDataResponse.STATUS_ERROR;
import static org.niis.xroad.opmonitor.api.StoreOpMonitoringDataResponse.STATUS_OK;

/**
 * Actor for sending operational data to the operational monitoring daemon. This actor is used by the
 * OpMonitoringBuffer class for periodically forwarding operational data gathered in the buffer.
 */
@Slf4j
public class OpMonitoringDaemonSender {

    private static final ObjectReader OBJECT_READER = JsonUtils.getObjectReader();

    private final OpMonitoringDataProcessor opMonitoringDataProcessor = new OpMonitoringDataProcessor();
    private final OpMonitorCommonProperties opMonitorCommonProperties;
    private final ServerConfProvider serverConfProvider;
    private final OpMonitoringBuffer opMonitoringBuffer;
    private final VaultTlsCredentialsProvider vaultTlsCredentialsProvider;
    private final CloseableHttpClient httpClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final AtomicBoolean processing = new AtomicBoolean(false);

    OpMonitoringDaemonSender(ServerConfProvider serverConfProvider, OpMonitoringBuffer opMonitoringBuffer,
                             OpMonitorCommonProperties opMonitorCommonProperties, VaultTlsCredentialsProvider vaultTlsCredentialsProvider) throws Exception {
        this.serverConfProvider = serverConfProvider;
        this.opMonitoringBuffer = opMonitoringBuffer;
        this.opMonitorCommonProperties = opMonitorCommonProperties;
        this.vaultTlsCredentialsProvider = vaultTlsCredentialsProvider;

        this.httpClient = createHttpClient();
    }

    void sendMessage(final List<OpMonitoringData> dataToProcess) {
        executorService.execute(() -> {
            try {
                processing.set(true);
                var json = opMonitoringDataProcessor.prepareMonitoringMessage(dataToProcess);
                log.trace("onReceive: {}", json);

                send(json);

                processing.set(false);
                opMonitoringBuffer.sendingSuccess(dataToProcess.size());
            } catch (Exception e) {
                log.error("Sending operational monitoring data failed", e);
                processing.set(false);
                opMonitoringBuffer.sendingFailure(dataToProcess);
            }
        });
    }

    public boolean isReady() {
        return Boolean.FALSE.equals(processing.get());
    }

    private void send(String json) throws Exception {
        try (HttpSender sender = new HttpSender(httpClient)) {
            sender.setConnectionTimeout(TimeUtils.secondsToMillis(opMonitorCommonProperties.buffer().connectionTimeoutSeconds()));
            sender.setSocketTimeout(TimeUtils.secondsToMillis(opMonitorCommonProperties.buffer().socketTimeoutSeconds()));

            sender.doPost(getAddress(), json, MimeTypes.JSON);

            String responseJson = IOUtils.toString(sender.getResponseContent(), MimeUtils.UTF8);
            StoreOpMonitoringDataResponse response;

            try {
                response = OBJECT_READER.readValue(responseJson, StoreOpMonitoringDataResponse.class);
            } catch (Exception e) {
                throw new Exception("Received invalid response: " + responseJson);
            }

            if (STATUS_OK.equals(response.getStatus())) {
                log.trace("Received OK response");

                return;
            }

            if (STATUS_ERROR.equals(response.getStatus())) {
                throw new Exception("Received error response" + (StringUtils.isBlank(response.getErrorMessage())
                        ? "" : ": " + response.getErrorMessage()));
            } else {
                throw new Exception("Received invalid response: " + responseJson);
            }
        }
    }

    private URI getAddress() throws URISyntaxException {
        return new URI(opMonitorCommonProperties.connection().scheme(), null,
                opMonitorCommonProperties.connection().host(), opMonitorCommonProperties.connection().port(),
                OpMonitoringDaemonEndpoints.STORE_DATA_PATH, null, null);
    }

    CloseableHttpClient createHttpClient() throws Exception {
        return OpMonitoringDaemonHttpClient.createHttpClient(
                opMonitorCommonProperties, vaultTlsCredentialsProvider, serverConfProvider.getSSLKey(),
                1, 1,
                TimeUtils.secondsToMillis(opMonitorCommonProperties.buffer().connectionTimeoutSeconds()),
                TimeUtils.secondsToMillis(opMonitorCommonProperties.buffer().socketTimeoutSeconds()));
    }

    public void destroy() {
        executorService.shutdown();

        if (httpClient != null) {
            IOUtils.closeQuietly(httpClient);
        }
    }

}
