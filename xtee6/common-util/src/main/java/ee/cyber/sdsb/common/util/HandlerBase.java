package ee.cyber.sdsb.common.util;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.message.SoapFault;

/** Convenience base class for proxy HTTP handlers. */
public abstract class HandlerBase extends AbstractHandler {

    /**
     * Sends SOAP fault message to the other party.
     */
    public static void sendErrorResponse(HttpServletResponse response,
            CodedException ex) throws IOException {
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
    protected List<X509Certificate> getClientCertificates(
            HttpServletRequest request) {
        Object attribute = request.getAttribute(
                "javax.servlet.request.X509Certificate");
        if (attribute != null) {
            return Arrays.asList((X509Certificate[]) attribute);
        } else {
            return new ArrayList<>();
        }
    }

    protected void failure(HttpServletResponse response, CodedException ex)
            throws IOException {
    }
}
