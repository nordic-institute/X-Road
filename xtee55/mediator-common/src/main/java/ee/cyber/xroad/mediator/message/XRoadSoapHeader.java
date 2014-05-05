package ee.cyber.xroad.mediator.message;

/**
 * Base interface for all X-Road 5.0 SOAP Headers. Specifies all fields
 * relevant to the mediators.
 */
public interface XRoadSoapHeader {

    String getConsumer();

    String getProducer();

    String getService();

    String getUserId();

    String getQueryId();

    boolean isAsync();
}
