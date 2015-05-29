package ee.ria.xroad.proxy.messagelog;

class TestTimestamper extends Timestamper {

    @Override
    protected Class<? extends TimestamperWorker> getWorkerImpl() {
        return TestTimestamperWorker.class;
    }
}
