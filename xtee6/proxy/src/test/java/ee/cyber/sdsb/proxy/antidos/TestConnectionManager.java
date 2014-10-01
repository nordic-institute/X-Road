package ee.cyber.sdsb.proxy.antidos;

import static org.junit.Assert.assertEquals;

class TestConnectionManager
        extends AntiDosConnectionManager<TestSocketChannel> {

    private final TestSystemMetrics systemMetrics;

    TestConnectionManager(TestConfiguration configuration,
            TestSystemMetrics systemMetrics) {
        super(configuration);
        this.systemMetrics = systemMetrics;
    }

    @Override
    protected TestSocketChannel getNextConnection()
            throws InterruptedException {
        systemMetrics.next();

        return super.getNextConnection();
    }

    @Override
    protected long getFreeFileDescriptorCount() {
        return systemMetrics.get().getMinFreeFileHandles();
    }

    @Override
    protected double getCpuLoad() {
        return systemMetrics.get().getMaxCpuLoad();
    }

    int numActivePartners() {
        return activePartners.size();
    }

    void accept(TestSocketChannel... connections) {
        for (TestSocketChannel connection : connections) {
            accept(connection);
        }
    }

    void assertNextConnection(TestSocketChannel conn) throws Exception {
        assertEquals(conn, getNextConnection());
    }

    void assertConnections(TestSocketChannel... connections)
            throws Exception {
        for (TestSocketChannel connection : connections) {
            assertNextConnection(connection);
        }
    }

    void assertEmpty() {
        assertEquals(0, numActivePartners());
    }
}
