package ee.cyber.xroad.mediator.service;

import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;

/**
 * Thread that periodically closes expired and idle connections.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class IdleConnectionMonitorThread extends Thread {

    private static final int DEFAULT_CONNECTION_IDLE_TIME = 1000;
    private static final int DEFAULT_INTERVAL = 5000;

    private final PoolingNHttpClientConnectionManager connectionManager;

    private volatile boolean shutdown;

    @Setter
    private int intervalMilliseconds = DEFAULT_INTERVAL;
    @Setter
    private int connectionIdleTimeMilliseconds = DEFAULT_CONNECTION_IDLE_TIME;

    public void closeNow() {
        connectionManager.closeExpiredConnections();
        connectionManager.closeIdleConnections(connectionIdleTimeMilliseconds,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        try {
            while (!shutdown) {
                synchronized (this) {
                    wait(intervalMilliseconds);
                    closeNow();
                }
            }
        } catch (InterruptedException ex) {
            log.trace("Idle connection monitor thread terminated");
        }
    }

    public void shutdown() {
        shutdown = true;

        synchronized (this) {
            notifyAll();
        }
    }

}
