package ee.ria.xroad.proxy.protocol;

import java.io.InputStream;
import java.util.Map;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;

/**
 * Describes the proxy message parts that are sent between client
 * and server proxy. The proxy message consists of a SOAP XML message and
 * optional attachments and the signature. The OCSP response is only used
 * in SSL mode to send the OCSP response of the client proxy SSL certificate.
 */
public interface ProxyMessageConsumer {

    /**
     * Called when SOAP message is parsed.
     * @param message the SOAP message
     * @throws Exception if an error occurs
     */
    void soap(SoapMessageImpl message) throws Exception;

    /**
     * Called when an attachment is received.
     * @param contentType the content type of the attachment
     * @param content the input stream holding the attachment data
     * @param additionalHeaders any additional headers for the attachment
     * @throws Exception if an error occurs
     */
    void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception;

    /***
     * Called when an OCSP response arrives.
     * @param resp the response
     * @throws Exception if an error occurs
     */
    void ocspResponse(OCSPResp resp) throws Exception;

    /***
     * Called when a signature arrives.
     * @param signature the signature
     * @throws Exception if an error occurs
     */
    void signature(SignatureData signature) throws Exception;

    /**
     * Called when a fault is encountered.
     * @param fault the fault message
     * @throws Exception if an error occurs
     */
    void fault(SoapFault fault) throws Exception;
}
