package ee.cyber.xroad.mediator.message;

import lombok.RequiredArgsConstructor;

import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.ria.xroad.common.message.SoapMessageImpl;

/**
 * Converts from X-Road 6.0 SOAP message to X-Road 5.0 SOAP message
 * and vice versa.
 */
@RequiredArgsConstructor
public class SoapMessageConverter {

    private final IdentifierMappingProvider mapping;

    /**
     * Converts the specified X-Road 5.0 SOAP message to X-Road 6.0
     * SOAP message.
     * @param message the X-Road 5.0 SOAP message
     * @param includeLegacyHeaders whether X-Road 5.0 headers should be retained
     * @return the resulting X-Road 6.0 SOAP message
     * @throws Exception in case of any errors
     */
    public SoapMessageImpl xroadSoapMessage(V5XRoadSoapMessageImpl message,
            boolean includeLegacyHeaders) throws Exception {
        V5XRoadSoapMessageConverter converter =
                new V5XRoadSoapMessageConverter(mapping);
        converter.setIncludeLegacyHeaders(includeLegacyHeaders);
        return converter.convert(message);
    }

    /**
     * Converts the specified X-Road 6.0 SOAP message to X-Road 5.0
     * SOAP message.
     * @param message the SOAP message
     * @return the resulting X-Road 5.0 SOAP message
     * @throws Exception in case of any errors
     */
    public V5XRoadSoapMessageImpl v5XroadSoapMessage(SoapMessageImpl message)
            throws Exception {
        return v5XroadSoapMessage(message, null);
    }

    /**
     * Converts the specified X-Road 6.0 SOAP message to X-Road 5.0
     * SOAP message of type specified by <code>headerClassHint</code>.
     * @param message the X-Road 6.0 SOAP message
     * @param headerClassHint X-Road 5.0 message header type
     * @return the resulting X-Road 5.0 SOAP message
     * @throws Exception in case of any errors
     */
    public V5XRoadSoapMessageImpl v5XroadSoapMessage(SoapMessageImpl message,
            Class<?> headerClassHint) throws Exception {
        XroadSoapMessageConverter converter =
                new XroadSoapMessageConverter(mapping);
        converter.setDestinationSoapHeaderClass(headerClassHint);
        return converter.convert(message);
    }

    /**
     * Removes XRoad headers from X-Road 6.0 message.
     * @param message the X-Road 6.0 SOAP message
     * @return the SOAP message without X-Road 6.0 headers
     * @throws Exception in case of any errors
     */
    public SoapMessageImpl removeXRoadHeaders(SoapMessageImpl message)
            throws Exception {
        return V5XRoadSoapMessageConverter.removeXRoadHeaders(message);
    }

}
