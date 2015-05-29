package ee.ria.xroad.proxy.antidos;

import ee.ria.xroad.common.SystemProperties;

class AntiDosConfiguration {

    /**
     * @return the number of allowed parallel connections
     */
    int getMaxParallelConnections() {
        return SystemProperties.getAntiDosMaxParallelConnections();
    }

    /**
     * @return the minimum number of free file handles required to process
     * an incoming connection after it has been accepted
     */
    int getMinFreeFileHandles() {
        return SystemProperties.getAntiDosMinFreeFileHandles();
    }

    /**
     * @return the maximum allowed CPU load. If the CPU load is more than this
     * value, incoming connection is not processed.
     */
    double getMaxCpuLoad() {
        return SystemProperties.getAntiDosMaxCpuLoad();
    }
    
    /**
     * @return the maximum allowed heap usage. If the heap usage is more than
     * this value, incoming connection is not processed.
     */
    double getMaxHeapUsage() {
        return SystemProperties.getAntiDosMaxHeapUsage();
    }
}
