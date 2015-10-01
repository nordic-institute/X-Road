package ee.cyber.xroad.mediator.client;

/**
 * Declares methods for a mediator message processor.
 */
public interface MediatorMessageProcessor {

    /**
     * Processes the given mediator request and writes a response into the given
     * mediator response instance.
     * @param request the mediator request
     * @param response the mediator response
     * @throws Exception in case of any errors
     */
    void process(final MediatorRequest request,
            final MediatorResponse response) throws Exception;
}
