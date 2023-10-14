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
package ee.ria.xroad.proxy.opmonitoring;

import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.opmonitoring.OpMonitoringDaemonEndpoints;
import ee.ria.xroad.common.opmonitoring.OpMonitoringDaemonHttpClient;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.opmonitoring.StoreOpMonitoringDataResponse;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.common.util.TimeUtils;

import com.fasterxml.jackson.databind.ObjectReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static ee.ria.xroad.common.opmonitoring.StoreOpMonitoringDataResponse.STATUS_ERROR;
import static ee.ria.xroad.common.opmonitoring.StoreOpMonitoringDataResponse.STATUS_OK;

/**
 * Actor for sending operational data to the operational monitoring daemon. This actor is used by the
 * OpMonitoringBuffer class for periodically forwarding operational data gathered in the buffer.
 */
@Slf4j
public class OpMonitoringDaemonSender implements StartStop {

    private static final ObjectReader OBJECT_READER = JsonUtils.getObjectReader();

    private static final int CONNECTION_TIMEOUT_MILLISECONDS = TimeUtils.secondsToMillis(
            OpMonitoringSystemProperties.getOpMonitorBufferConnectionTimeoutSeconds());

    private static final int SOCKET_TIMEOUT_MILLISECONDS = TimeUtils.secondsToMillis(
            OpMonitoringSystemProperties.getOpMonitorBufferSocketTimeoutSeconds());

    private final OpMonitoringDataProcessor opMonitoringDataProcessor = new OpMonitoringDataProcessor();
    private final OpMonitoringBuffer opMonitoringBuffer;
    private final CloseableHttpClient httpClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final AtomicBoolean processing = new AtomicBoolean(false);

    OpMonitoringDaemonSender(OpMonitoringBuffer opMonitoringBuffer) throws Exception {
        this.httpClient = createHttpClient();
        this.opMonitoringBuffer = opMonitoringBuffer;
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
            sender.setConnectionTimeout(CONNECTION_TIMEOUT_MILLISECONDS);
            sender.setSocketTimeout(SOCKET_TIMEOUT_MILLISECONDS);

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
        return new URI(OpMonitoringSystemProperties.getOpMonitorDaemonScheme(), null,
                OpMonitoringSystemProperties.getOpMonitorHost(), OpMonitoringSystemProperties.getOpMonitorPort(),
                OpMonitoringDaemonEndpoints.STORE_DATA_PATH, null, null);
    }

    CloseableHttpClient createHttpClient() throws Exception {
        return OpMonitoringDaemonHttpClient.createHttpClient(ServerConf.getSSLKey(),
                1, 1,
                TimeUtils.secondsToMillis(OpMonitoringSystemProperties.getOpMonitorBufferConnectionTimeoutSeconds()),
                TimeUtils.secondsToMillis(OpMonitoringSystemProperties.getOpMonitorBufferSocketTimeoutSeconds()));
    }

    @Override
    public void start() {
        //No-OP
    }

    @Override
    public void stop() {
        executorService.shutdown();

        if (httpClient != null) {
            IOUtils.closeQuietly(httpClient);
        }
    }

    @Override
    public void join() {
        //NO-OP
    }
}
