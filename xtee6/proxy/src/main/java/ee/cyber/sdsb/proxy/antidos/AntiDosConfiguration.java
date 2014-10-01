package ee.cyber.sdsb.proxy.antidos;

// TODO: #2575 read configuration parameters from file
class AntiDosConfiguration {

    private static final int MAX_PARALLEL_CONNECTIONS = 5000;

    private static final int MIN_FREE_FILE_HANDLES = 100;

    // Set to > 1.0 to disable CPU load checking.
    private static final double MAX_CPU_LOAD = 1.1;

    /**
     * @return the number of allowed parallel connections
     */
    int getMaxParallelConnections() {
        return MAX_PARALLEL_CONNECTIONS;
    }

    /**
     * @return the minimum number of free file handles required to process
     * an incoming connection after it has been accepted
     */
    int getMinFreeFileHandles() {
        return MIN_FREE_FILE_HANDLES;
    }

    /**
     * @return the maximum allowed CPU load. If the CPU load is more than this
     * value, incoming connection is not processed.
     */
    double getMaxCpuLoad() {
        return MAX_CPU_LOAD;
    }
}
