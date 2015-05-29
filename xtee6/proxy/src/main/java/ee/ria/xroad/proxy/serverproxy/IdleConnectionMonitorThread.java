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
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class IdleConnectionMonitorThread extends Thread {

    private static final int DEFAULT_IDLE_TIMEOUT = 1000;
    private static final int DEFAULT_MONITORING_INTERVAL = 5000;

    private final HttpClientConnectionManager connectionManager;

    private volatile boolean shutdown;

    @Setter
    private int intervalMilliseconds = DEFAULT_MONITORING_INTERVAL;
    @Setter
    private int connectionIdleTimeMilliseconds = DEFAULT_IDLE_TIMEOUT;

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
            // terminate
            log.warn("Idle connection monitor thread was interrupted");
        }
    }

    public void shutdown() {
        shutdown = true;

        synchronized (this) {
            notifyAll();
        }
    }

}
