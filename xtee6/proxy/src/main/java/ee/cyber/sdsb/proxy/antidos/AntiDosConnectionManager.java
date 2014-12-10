package ee.cyber.sdsb.proxy.antidos;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.util.SystemMetrics;

/**
 * Manages the incoming connections and prevents system resource exhaustion.
 */
@Slf4j
class AntiDosConnectionManager<T extends SocketChannelWrapper> {

    // Tiny wrapper class for active connections of a partner
    private class HostData {
        final Deque<T> connections = new ArrayDeque<>();
    }

    // The IP used for unknown members
    private static final String UNKNOWN_ORG_IP = "0.0.0.0";

    // Holds the configuration
    protected final AntiDosConfiguration configuration;

    // IP to HostData mapping
    protected Map<String, HostData> database = new HashMap<>();

    // Buffer of partners with waiting connections
    protected LinkedBlockingQueue<HostData> activePartners =
            new LinkedBlockingQueue<>();

    // Holds a cache of previously known member IPs.
    // Used to determine if should sync the database when conf changed.
    private Set<String> previousKnownOrganizations = new HashSet<>();

    AntiDosConnectionManager(AntiDosConfiguration configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration cannot be null");
        }

        this.configuration = configuration;
        this.previousKnownOrganizations.add(UNKNOWN_ORG_IP);
        this.database.put(UNKNOWN_ORG_IP, new HostData());
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
        long freeFileHandles = getFreeFileDescriptorCount();
        log.trace("canAccept({})", freeFileHandles);
        return freeFileHandles > 0;
    }

    /**
     * Adds the connection into the partner's connection queue.
     */
    synchronized void accept(T connection) {
        // We need to synchronize the database with the existing members.
        syncDatabase();

        // Find the host data for the incoming connection and register
        // the new connection to this host data
        HostData currentPartner = getHostData(connection.getHostAddress());
        currentPartner.connections.addFirst(connection);

        // If the host data was previously empty, add it to the connection
        // buffer as the newest partner.
        if (currentPartner.connections.size() == 1) {
            activePartners.offer(currentPartner);
        }
    }

    /**
     * Returns the next connection or blocks until next connection is available.
     */
    T takeNextConnection() throws InterruptedException {
        while (true) {
            T next = getNextConnection();
            if (next != null) {
                return next;
            }
        }
    }

    void closeConnection(T sock) throws IOException {
        sock.close();
    }

    /**
     * Retrieves the next connection to be processed. First, it checks that
     * there are sufficient resources available (free file handles etc.).
     * If not, then the connection is closed thus freeing some resources.
     */
    protected T getNextConnection() throws InterruptedException {
        // Take the oldest partner from the buffer (blocks until available).
        HostData oldestPartner = activePartners.take();

        // Take the oldest connection.
        T sock = oldestPartner.connections.pollLast();
        if (sock == null) {
            return null;
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

        log.warn("Insufficient resources, closing connection " + sock);
        try {
            closeConnection(sock);
        } catch (IOException e) {
            log.error("Error closing connection " + sock, e);
        }

        return null;
    }

    protected long getFreeFileDescriptorCount() {
        return SystemMetrics.getFreeFileDescriptorCount();
    }

    protected double getCpuLoad() {
        return SystemMetrics.getStats().getSystemCpuLoad();
    }

    protected double getHeapUsage() {
        return SystemMetrics.getHeapUsage();
    }

    private HostData getHostData(String ip) {
        return database.containsKey(ip)
                ? database.get(ip) : database.get(UNKNOWN_ORG_IP);
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
                log.trace("Registering HostData for " + knownAddress);
                newDatabase.put(knownAddress, new HostData());
            }
        }

        previousKnownOrganizations = knownAddresses;
        database = newDatabase;
    }

    private boolean hasSufficientResources() {
        long freeFileDescriptorCount = getFreeFileDescriptorCount();
        int minFreeFileHandles = configuration.getMinFreeFileHandles();
        double cpuLoad = getCpuLoad();
        double maxCpuLoad = configuration.getMaxCpuLoad();
        double heapUsage = getHeapUsage();
        double maxHeapUsage = configuration.getMaxHeapUsage();

        log.trace("Resource usage when considering connection:\n"
                + "freeFileDescriptorCount: {} ( >= {})\n"
                + "cpuLoad: {} ( < {})\n"
                + "heapUsage: {} ( < {})",
                new Object[] { freeFileDescriptorCount, minFreeFileHandles,
                    cpuLoad, maxCpuLoad, heapUsage, maxHeapUsage});

        return freeFileDescriptorCount >= minFreeFileHandles
                && cpuLoad < maxCpuLoad
                && heapUsage < maxHeapUsage;
    }

    private static Set<String> getAllAddresses() {
        Set<String> addresses = new HashSet<>();
        try {
            addresses.addAll(GlobalConf.getKnownAddresses());
        } catch (Exception ignored) {
            // In case the conf was invalid, we do not sync. We should not
            // log this exception, since this method might be
            // called very frequently.
        }

        addresses.add(UNKNOWN_ORG_IP);
        return addresses;
    }

}
