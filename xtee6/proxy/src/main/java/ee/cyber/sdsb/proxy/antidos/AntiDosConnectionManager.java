package ee.cyber.sdsb.proxy.antidos;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.util.SystemMetrics;

/**
 * Manages the incoming connections and prevents system resource exhaustion.
 */
class AntiDosConnectionManager {

    // Tiny wrapper class for active connections of a partner
    private static class HostData {
        final Deque<SocketChannel> connections = new ArrayDeque<>();
    }

    private static final Logger LOG =
            LoggerFactory.getLogger(AntiDosConnectionManager.class);

    // Holds the minimum number of free file handles required to process
    // an incoming connection after it has been accepted
    // TODO: needs to be some reasonable valuer
    // TODO: make configurable?
    private static final int MIN_FREE_FILE_HANDLES = 100;

    // Holds the maximum allowed CPU load. If the CPU load is more than this
    // value, incoming connection is not processed.
    // Set to > 1.0 to disable CPU load checking.
    // TODO: needs to be some reasonable value
    // TODO: make configurable?
    private static final double MAX_CPU_LOAD = 1.1;

    // The IP used for unknown members
    private static final String UNKNOWN_ORG_IP = "0.0.0.0";

    // IP to HostData mapping
    private Map<String, HostData> database = new HashMap<>();

    // Buffer of partners with waiting connections
    private LinkedBlockingQueue<HostData> activePartners =
            new LinkedBlockingQueue<>();

    // Holds a cache of previously known member IPs.
    // Used to determine if should sync the database when conf changed.
    private Set<String> previousKnownOrganizations = new HashSet<>();

    AntiDosConnectionManager() {
        previousKnownOrganizations.add(UNKNOWN_ORG_IP);
        database.put(UNKNOWN_ORG_IP, new HostData());
    }

    void init() throws Exception {
        // Populate the database based on registered members' IPs.
        syncDatabase();
    }

    /**
     * Checks if we can accept the incoming connection. Basically verify that
     * we have at least one free file handle.
     */
    boolean canAccept() {
        long freeFileHandles = SystemMetrics.getFreeFileDescriptorCount();
        LOG.debug("canAccept({})", freeFileHandles);
        return freeFileHandles > 0;
    }

    /**
     * Adds the connection into the partner's connection queue.
     */
    synchronized void accept(SocketChannel connection) {
        // We need to synchronize the database with the existing
        // members from the conf.
        syncDatabase();

        // Find the HostData for the incoming connection
        HostData currentPartner = getHostData(connection);
        // ... and register the new connection
        currentPartner.connections.addFirst(connection);

        // If the host data was previously empty, add it to the connection
        // buffer as the newest partner.
        if (currentPartner.connections.size() == 1) {
            activePartners.offer(currentPartner);
        }
    }

    /**
     * Retrieves the next connection to be processed. First, it checks that
     * there are sufficient resources available (free file handles etc.).
     * If not, then the connection is closed thus freeing some resources.
     */
    SocketChannel takeNextConnection() throws InterruptedException {
        // TODO: currently closing only one connection if low on resources
        while (true) {
            // Take the oldest partner from the buffer (blocks until available).
            HostData oldestPartner = activePartners.take();

            // Take the oldest connection.
            SocketChannel sock = oldestPartner.connections.pollLast();
            if (sock == null) {
                continue;
            }

            // If there are more connections left for this partner, add the partner
            // back to the buffer as the first partner.
            if (!oldestPartner.connections.isEmpty()) {
                activePartners.offer(oldestPartner);
            }

            // Processing a connection consumes file handles and other resources
            if (hasSufficientResources()) {
                return sock;
            }

            LOG.error("Insufficient resources, closing connection " + sock);
            try {
                closeConnection(sock);
            } catch (IOException e) {
                LOG.error("Error closing connection " + sock, e);
            }
        }
    }

    void closeConnection(SocketChannel sock) throws IOException {
        sock.close();
    }

    private HostData getHostData(SocketChannel connection) {
        String ip = connection.socket().getInetAddress().getHostAddress();
        return getHostData(ip);
    }

    private HostData getHostData(String ip) {
        return database.containsKey(ip) ?
                database.get(ip) : database.get(UNKNOWN_ORG_IP);
    }

    private void syncDatabase() {
        Set<String> knownAddresses = getAllAddresses();

        if (previousKnownOrganizations.equals(knownAddresses)) {
            // Nothing has changed, do not sync.
            return;
        }

        Map<String, HostData> newDatabase = new HashMap<>();

        // Retain existing members connections
        for (String existingAddress : database.keySet()) {
            if (knownAddresses.contains(existingAddress)) {
                newDatabase.put(existingAddress, database.get(existingAddress));
            }
        }

        // Add new members
        for (String knownAddress : knownAddresses) {
            if (!database.containsKey(knownAddress)) {
                LOG.info("Registering HostData for " + knownAddress);
                newDatabase.put(knownAddress, new HostData());
            }
        }

        previousKnownOrganizations = knownAddresses;
        database = newDatabase;
    }

    private static Set<String> getAllAddresses() {
        Set<String> ret = new HashSet<>();
        try {
            ret.addAll(GlobalConf.getKnownAddresses());
        } catch (Exception ignored) {
            // In case the conf was invalid, we do not sync. We should not
            // log this exception, since this method might be
            // called very frequently.
        }

        ret.add(UNKNOWN_ORG_IP);
        return ret;
    }

    private static boolean hasSufficientResources() {
        long freeHandles = SystemMetrics.getFreeFileDescriptorCount();
        // TODO: use an average load over last few minutes instead?
        double cpuLoad = SystemMetrics.getStats().getProcessCpuLoad();
        //double cpuLoad = SystemMetrics.getStats().getSystemLoadAverage();

        return freeHandles >= MIN_FREE_FILE_HANDLES && cpuLoad < MAX_CPU_LOAD;
    }

}
