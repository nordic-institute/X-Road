package ee.cyber.xroad.mediator.message;

/**
 * Base interface for all X-Road 5.0 SOAP Headers. Specifies all fields
 * relevant to the mediators.
 */
public interface V5XRoadSoapHeader {

    /**
     * @return the consumer short name
     */
    String getConsumer();

    /**
     * @return the producer short name
     */
    String getProducer();

    /**
     * @return the service ID
     */
    String getService();

    /**
     * @return the user ID
     */
    String getUserId();

    /**
     * @return the query ID
     */
    String getQueryId();

    /**
     * @return true if the message is asynchronous
     */
    boolean isAsync();
}
