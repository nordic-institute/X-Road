/**
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

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import scala.concurrent.duration.FiniteDuration;

import java.net.NetworkInterface;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    public static final String OP_MONITORING_DAEMON_SENDER = "OpMonitoringDaemonSender";

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

    private Cancellable tick;

    final Map<Long, OpMonitoringData> buffer =
            new LinkedHashMap<Long, OpMonitoringData>() {
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

    private final ActorRef sender;

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
        } else {
            httpClient = createHttpClient();
            sender = createSender();
        }
    }

    CloseableHttpClient createHttpClient() throws Exception {
        return OpMonitoringDaemonHttpClient.createHttpClient(ServerConf.getSSLKey(), 1, 1,
                CLIENT_CONNECTION_TIMEOUT_MILLISECONDS, CLIENT_SOCKET_TIMEOUT_MILLISECONDS);
    }

    ActorRef createSender() {
        return getContext().system().actorOf(Props.create(OpMonitoringDaemonSender.class, httpClient),
                OP_MONITORING_DAEMON_SENDER);
    }

    @Override
    protected void store(OpMonitoringData data) throws Exception {
        if (ignoreOpMonitoringData()) {
            return;
        }

        data.setSecurityServerInternalIp(getIpAddress());

        buffer.put(getNextBufferIndex(), data);

        send();
    }

    @Override
    protected void send() throws Exception {
        if (!canSend()) {
            return;
        }

        String json = prepareMonitoringMessage();

        sender.tell(json, getSelf());
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

    @Override
    protected void sendingSuccess() throws Exception {
        processedBufferIndices.forEach(buffer::remove);
        processedBufferIndices.clear();

        if (canSend()) {
            send();
        }
    }

    @Override
    protected void sendingFailure() throws Exception {
        processedBufferIndices.clear();

        // Do not worry, scheduled sending retries..
    }

    long getNextBufferIndex() {
        bufferIndex = bufferIndex == Long.MAX_VALUE ? 0 : bufferIndex + 1;

        return bufferIndex;
    }

    private void scheduleSendMonitoringData() {
        FiniteDuration interval = FiniteDuration.create(SENDING_INTERVAL_SECONDS, TimeUnit.SECONDS);

        tick = getContext().system().scheduler().schedule(interval, interval, getSelf(), SEND_MONITORING_DATA,
                getContext().dispatcher(), ActorRef.noSender());
    }

    @Override
    public void preStart() throws Exception {
        if (ignoreOpMonitoringData()) {
            return;
        }

        scheduleSendMonitoringData();
    }

    @Override
    public void postStop() throws Exception {
        if (tick != null) {
            tick.cancel();
        }

        if (httpClient != null) {
            IOUtils.closeQuietly(httpClient);
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
