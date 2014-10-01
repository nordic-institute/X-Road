package ee.cyber.sdsb.proxy.serverproxy;

import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.apache.http.conn.HttpClientConnectionManager;

/**
 * Thread that periodically closes expired and idle connections.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class IdleConnectionMonitorThread extends Thread {

    private final HttpClientConnectionManager connectionManager;

    private volatile boolean shutdown;

    @Setter private int intervalMilliseconds = 5000;
    @Setter private int connectionIdleTimeMilliseconds = 1000;

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
        }
    }

    public void shutdown() {
        shutdown = true;

        synchronized (this) {
            notifyAll();
        }
    }

}
