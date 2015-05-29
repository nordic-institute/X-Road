package ee.cyber.xroad.mediator.message;

/**
 * Abstract class for all X-Road 5.0 SOAP Headers. Specifies protected methods
 * for setting the field values.
 */
abstract class AbstractV5XRoadSoapHeader implements V5XRoadSoapHeader {

    protected abstract void setConsumer(String consumer);

    protected abstract void setProducer(String producer);

    protected abstract void setService(String service);

    protected abstract void setQueryId(String queryId);

    protected abstract void setUserId(String userId);

    protected abstract void setAsync(boolean isAsync);
}
