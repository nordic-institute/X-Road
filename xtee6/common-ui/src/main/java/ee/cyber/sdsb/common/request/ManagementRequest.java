package ee.cyber.sdsb.common.request;

import java.io.InputStream;

import ee.cyber.sdsb.common.message.SoapMessageImpl;

/**
 * Describes the management request to be sent.
 */
public interface ManagementRequest {

    /**
     * @return the request SOAP message
     */
    SoapMessageImpl getRequestMessage();

    /**
     * @return the request content
     * @throws Exception if an error occurs
     */
    InputStream getRequestContent() throws Exception;

    /**
     * @return the request content type (such as text/xml)
     */
    String getRequestContentType();

    /**
     * @return the response content type
     */
    String getResponseContentType();

}
