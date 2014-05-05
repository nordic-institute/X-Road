package ee.cyber.xroad.mediator.common;

public interface MediatorMessageProcessor {

    void process(final MediatorRequest request,
            final MediatorResponse response) throws Exception;
}
