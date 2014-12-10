package ee.cyber.xroad.common.util;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.xroad.common.CodedException;
import ee.cyber.xroad.common.message.SoapFault;

/** Convenience base class for proxy HTTP handlers. */
public abstract class HandlerBase extends AbstractHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(HandlerBase.class);

    /**
     * Sends SOAP fault message to the other party.
     */
    public static void sendErrorResponse(HttpServletResponse response,
            CodedException ex) throws IOException {
        LOG.debug("sendErrorResponse()");
        sendErrorResponse(response, ex.getFaultCode(), ex.getFaultString(),
                ex.getFaultActor(), ex.getFaultDetail());
    }

    /**
    * Sends SOAP fault message to the other party.
    */
    public static void sendErrorResponse(HttpServletResponse response,
            String faultCode, String faultString, String faultActor,
            String faultDetail) throws IOException {
        String soapMessageXml = SoapFault.createFaultXml(
                faultCode, faultString, faultActor,
                faultDetail);

        String encoding = MimeUtils.UTF8;
        byte[] messageBytes = soapMessageXml.getBytes(encoding);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MimeTypes.TEXT_XML);
        response.setContentLength(messageBytes.length);
        response.setHeader("SOAPAction", "");
        response.setCharacterEncoding(encoding);
        response.getOutputStream().write(messageBytes);
    }

    /**
     * Returns the client certificate from the SSL context.
     */
    protected X509Certificate getClientCertificate(HttpServletRequest request) {
        Object attribute = request.getAttribute(
                "javax.servlet.request.X509Certificate");

        if (attribute != null) {
            X509Certificate[] certs = (X509Certificate[]) attribute;

            if (certs.length != 0 && certs[0] != null) {
                return certs[0];
            }
        }

        return null;
    }

    protected void failure(HttpServletResponse response, CodedException ex)
            throws IOException {
    }
}
