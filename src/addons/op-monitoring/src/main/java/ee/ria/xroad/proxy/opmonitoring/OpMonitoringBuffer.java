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
import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.common.opmonitoring.OpMonitoringDaemonHttpClient;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.opmonitoring.StoreOpMonitoringDataRequest;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.TimeUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import java.net.NetworkInterface;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.net.NetworkInterface.getNetworkInterfaces;
import static java.util.Collections.list;

/**
 * Operational monitoring buffer. This buffer is used for gathering
 * operational data and for periodically sending the data to the operational
 * monitoring daemon (using OpMonitoringDaemonSender).
 */
@Slf4j
public class OpMonitoringBuffer extends AbstractOpMonitoringBuffer {
    private static final String NO_ADDRESS_FOUND = "No suitable IP address is bound to the network interface ";
    private static final String NO_INTERFACE_FOUND = "No non-loopback network interface found";

    private static final long MAX_BUFFER_SIZE = OpMonitoringSystemProperties.getOpMonitorBufferSize();

    private static final int MAX_RECORDS_IN_MESSAGE =
            OpMonitoringSystemProperties.getOpMonitorBufferMaxRecordsInMessage();
    private static final long SENDING_INTERVAL_SECONDS =
            OpMonitoringSystemProperties.getOpMonitorBufferSendingIntervalSeconds();

    private static final int CLIENT_CONNECTION_TIMEOUT_MILLISECONDS = TimeUtils.secondsToMillis(
            OpMonitoringSystemProperties.getOpMonitorBufferConnectionTimeoutSeconds());

    private static final int CLIENT_SOCKET_TIMEOUT_MILLISECONDS = TimeUtils.secondsToMillis(
            OpMonitoringSystemProperties.getOpMonitorBufferSocketTimeoutSeconds());

    private static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter();

    private final ExecutorService executorService;
    private final ScheduledExecutorService taskScheduler;

    final Map<Long, OpMonitoringData> buffer =
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry eldest) {
                    boolean overflow = size() > MAX_BUFFER_SIZE;

                    if (overflow) {
                        log.warn("Operational monitoring buffer overflow, removing eldest record: {}", eldest.getKey());
                    }

                    return overflow;
                }
            };

    private long bufferIndex = 0;

    private final Set<Long> processedBufferIndices = new HashSet<>();

    private final CloseableHttpClient httpClient;

    private final OpMonitoringDaemonSender sender;

    private static String ipAddress;

    /**
     * Constructor.
     *
     * @throws Exception if an error occurs
     */
    public OpMonitoringBuffer() throws Exception {

        if (ignoreOpMonitoringData()) {
            log.info("Operational monitoring buffer is switched off, no operational monitoring data is stored");

            httpClient = null;
            sender = null;
            executorService = null;
            taskScheduler = null;
        } else {
            httpClient = createHttpClient();
            sender = createSender();

            executorService = Executors.newFixedThreadPool(1);
            taskScheduler = Executors.newScheduledThreadPool(1);
        }
    }

    CloseableHttpClient createHttpClient() throws Exception {
        return OpMonitoringDaemonHttpClient.createHttpClient(ServerConf.getSSLKey(), 1, 1,
                CLIENT_CONNECTION_TIMEOUT_MILLISECONDS, CLIENT_SOCKET_TIMEOUT_MILLISECONDS);
    }

    OpMonitoringDaemonSender createSender() {
        return new OpMonitoringDaemonSender(httpClient);
    }

    @Override
    public void store(OpMonitoringData data) throws Exception {
        if (ignoreOpMonitoringData()) {
            return;
        }

        executorService.execute(() -> {
            try {
                process(data);
            } catch (Exception e) {
                log.error("Failed to process OpMonitoringData..", e);
            }
        });
    }

    protected synchronized void process(OpMonitoringData data) throws Exception {
        if (ignoreOpMonitoringData()) {
            return;
        }

        data.setSecurityServerInternalIp(getIpAddress());

        buffer.put(getNextBufferIndex(), data);

        send();
    }

    protected synchronized void send() throws Exception {
        if (!canSend()) {
            return;
        }

        String json = prepareMonitoringMessage();

        if (sender.sendMessage(json)) {
            sendingSuccess();
        } else {
            sendingFailure();
        }
    }

    private boolean canSend() {
        return !buffer.isEmpty() && processedBufferIndices.isEmpty();
    }

    private String prepareMonitoringMessage() throws JsonProcessingException {
        StoreOpMonitoringDataRequest request = new StoreOpMonitoringDataRequest();

        for (Map.Entry<Long, OpMonitoringData> entry : buffer.entrySet()) {
            processedBufferIndices.add(entry.getKey());
            request.addRecord(entry.getValue().getData());

            if (request.getRecords().size() == MAX_RECORDS_IN_MESSAGE) {
                break;
            }
        }

        log.debug("Op monitoring buffer records count: {}", buffer.size());

        return OBJECT_WRITER.writeValueAsString(request);
    }

    private void sendingSuccess() throws Exception {
        processedBufferIndices.forEach(buffer::remove);
        processedBufferIndices.clear();

        if (canSend()) {
            send();
        }
    }

    protected void sendingFailure() {
        processedBufferIndices.clear();
        // Do not worry, scheduled sending retries..
    }

    long getNextBufferIndex() {
        bufferIndex = bufferIndex == Long.MAX_VALUE ? 0 : bufferIndex + 1;

        return bufferIndex;
    }

    private void scheduleSendMonitoringData() {
        taskScheduler.scheduleWithFixedDelay(() -> {
            try {
                this.send();
            } catch (Exception e) {
                log.error("Failed to send scheduled message", e);
            }
        }, SENDING_INTERVAL_SECONDS, SENDING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void start() {
        if (ignoreOpMonitoringData()) {
            return;
        }

        scheduleSendMonitoringData();
    }

    @Override
    public void stop() {
        if (httpClient != null) {
            IOUtils.closeQuietly(httpClient);
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    private boolean ignoreOpMonitoringData() {
        return MAX_BUFFER_SIZE < 1;
    }

    private static String getIpAddress() {
        try {
            if (ipAddress == null) {
                NetworkInterface ni = list(getNetworkInterfaces()).stream()
                        .filter(OpMonitoringBuffer::isNonLoopback)
                        .findFirst()
                        .orElseThrow(() -> new Exception(NO_INTERFACE_FOUND));

                Exception addressNotFound = new Exception(NO_ADDRESS_FOUND + ni.getDisplayName());

                ipAddress = list(ni.getInetAddresses()).stream()
                        .filter(addr -> !addr.isLinkLocalAddress())
                        .findFirst()
                        .orElseThrow(() -> addressNotFound)
                        .getHostAddress();

                if (ipAddress == null) {
                    throw addressNotFound;
                }
            }

            return ipAddress;
        } catch (Exception e) {
            log.error("Cannot get IP address of a non-loopback network interface", e);

            return "0.0.0.0";
        }
    }

    @SneakyThrows
    private static boolean isNonLoopback(NetworkInterface ni) {
        return !ni.isLoopback() && ni.isUp();
    }

}
