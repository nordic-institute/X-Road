package ee.cyber.xroad.mediator.message;

import lombok.RequiredArgsConstructor;

import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;

/**
 * Converts from SDSB SOAP message to X-Road 5.0 SOAP message and vice versa.
 */
@RequiredArgsConstructor
public class SoapMessageConverter {

    private final IdentifierMappingProvider mapping;

    /**
     * Converts the specified X-Road 5.0 SOAP message to SDSB SOAP message.
     */
    public SoapMessageImpl sdsbSoapMessage(XRoadSoapMessageImpl message,
            boolean includeLegacyHeaders) throws Exception {
        XRoadSoapMessageConverter converter =
                new XRoadSoapMessageConverter(mapping);
        converter.setIncludeLegacyHeaders(includeLegacyHeaders);
        return converter.convert(message);
    }

    /**
     * Converts the specified SDSB SOAP message to X-Road 5.0 SOAP message.
     */
    public XRoadSoapMessageImpl xroadSoapMessage(SoapMessageImpl message)
            throws Exception {
        return xroadSoapMessage(message, null);
    }

    /**
     * Converts the specified SDSB SOAP message to X-Road 5.0 SOAP message of
     * type specified by <code>headerClassHint</code>.
     */
    public XRoadSoapMessageImpl xroadSoapMessage(SoapMessageImpl message,
            Class<?> headerClassHint) throws Exception {
        SdsbSoapMessageConverter converter =
                new SdsbSoapMessageConverter(mapping);
        converter.setDestinationSoapHeaderClass(headerClassHint);
        return converter.convert(message);
    }

    /**
     * Removes XRoad headers from SDSB message.
     */
    public SoapMessageImpl removeXRoadHeaders(SoapMessageImpl message)
            throws Exception {
        return XRoadSoapMessageConverter.removeXRoadHeaders(message);
    }

}
