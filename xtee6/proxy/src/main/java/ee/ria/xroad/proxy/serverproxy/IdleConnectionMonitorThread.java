/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.serverproxy;

import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.HttpClientConnectionManager;

/**
 * Thread that periodically closes expired and idle connections.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public class IdleConnectionMonitorThread extends Thread {

    private static final int DEFAULT_IDLE_TIMEOUT = 1000;
    private static final int DEFAULT_MONITORING_INTERVAL = 5000;

    private final HttpClientConnectionManager connectionManager;

    private volatile boolean shutdown;

    @Setter
    private int intervalMilliseconds = DEFAULT_MONITORING_INTERVAL;
    @Setter
    private int connectionIdleTimeMilliseconds = DEFAULT_IDLE_TIMEOUT;


    void closeNow() {
        connectionManager.closeExpiredConnections();
        connectionManager.closeIdleConnections(connectionIdleTimeMilliseconds,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        while (!shutdown && !isInterrupted()) {
            try {
                sleep(intervalMilliseconds);
                closeNow();
            } catch (InterruptedException ex) {
                log.warn("InterruptedException occurred: {}", ex);
            }
        }
    }

    public void shutdown() {
        shutdown = true;
        interrupt();
    }

}
