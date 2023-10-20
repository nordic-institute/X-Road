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

import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Operational monitoring buffer. This buffer is used for gathering
 * operational data and for periodically sending the data to the operational
 * monitoring daemon (using OpMonitoringDaemonSender).
 */
@Slf4j
public class OpMonitoringBuffer extends AbstractOpMonitoringBuffer {
    private final int maxBufferSize = OpMonitoringSystemProperties.getOpMonitorBufferSize();
    private final int maxRecordsInMessage = OpMonitoringSystemProperties.getOpMonitorBufferMaxRecordsInMessage();

    private final ExecutorService executorService;
    private final ScheduledExecutorService taskScheduler;
    private final OpMonitoringDataProcessor opMonitoringDataProcessor;
    private final OpMonitoringDaemonSender sender;

    final BlockingDeque<OpMonitoringData> buffer = new LinkedBlockingDeque<>();

    /**
     * Constructor.
     *
     * @throws Exception if an error occurs
     */
    public OpMonitoringBuffer() throws Exception {
        if (ignoreOpMonitoringData()) {
            log.info("Operational monitoring buffer is switched off, no operational monitoring data is stored");

            sender = null;
            executorService = null;
            taskScheduler = null;
            opMonitoringDataProcessor = null;
        } else {
            sender = createSender();
            executorService = Executors.newSingleThreadExecutor();
            taskScheduler = Executors.newSingleThreadScheduledExecutor();
            opMonitoringDataProcessor = createDataProcessor();
        }
    }

    OpMonitoringDataProcessor createDataProcessor() {
        return new OpMonitoringDataProcessor();
    }

    OpMonitoringDaemonSender createSender() throws Exception {
        return new OpMonitoringDaemonSender(this);
    }

    @Override
    public void store(final OpMonitoringData data) {
        if (ignoreOpMonitoringData()) {
            return;
        }
        executorService.execute(() -> {
            try {
                data.setSecurityServerInternalIp(opMonitoringDataProcessor.getIpAddress());

                buffer.addLast(data);
                if (buffer.size() > maxBufferSize) {
                    synchronized (buffer) {
                        if (buffer.size() > maxBufferSize) {
                            buffer.removeFirst();
                            log.warn("Operational monitoring buffer overflow (limit: {}), removing oldest record. Current size: {}",
                                    maxBufferSize, buffer.size());
                        }
                    }
                }
                sendInternal();
            } catch (Exception e) {
                log.error("Failed to process OpMonitoringData..", e);
            }
        });
    }

    private void send() {
        executorService.execute(() -> {
            try {
                this.sendInternal();
            } catch (Exception e) {
                log.error("Failed to send message", e);
            }
        });
    }

    private void sendInternal() {
        if (!canSend()) {
            return;
        }

        final List<OpMonitoringData> dataToProcess = new ArrayList<>();

        buffer.drainTo(dataToProcess, maxRecordsInMessage);
        if (log.isDebugEnabled()) {
            log.debug("Op monitoring remaining buffer records count {}", buffer.size());
        }

        sender.sendMessage(dataToProcess);
    }

    private boolean canSend() {
        return !buffer.isEmpty() && sender.isReady();
    }

    void sendingSuccess(int count) {
        log.trace("Sent {} messages from buffer", count);

        if (canSend()) {
            send();
        }
    }

    void sendingFailure(List<OpMonitoringData> failedData) {
        failedData.forEach(buffer::addFirst);
        // Do not worry, scheduled sending retries.
    }

    @Override
    public void start() {
        if (ignoreOpMonitoringData()) {
            return;
        }

        var sendingIntervalSeconds = OpMonitoringSystemProperties.getOpMonitorBufferSendingIntervalSeconds();
        taskScheduler.scheduleWithFixedDelay(this::send, sendingIntervalSeconds, sendingIntervalSeconds, TimeUnit.SECONDS);

    }

    @Override
    public void stop() {
        if (executorService != null) {
            executorService.shutdown();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }

        if (sender != null) {
            sender.stop();
        }
    }

    private boolean ignoreOpMonitoringData() {
        return maxBufferSize < 1;
    }

    int getCurrentBufferSize() {
        return buffer.size();
    }

}
