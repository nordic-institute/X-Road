package ee.ria.xroad.common.util;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.SoapFault;

/**
 * Convenience base class for proxy HTTP handlers.
 */
@Slf4j
public abstract class HandlerBase extends AbstractHandler {

    /**
     * Sends SOAP fault message to the other party.
     * @param response HTTP servlet response for sending the SOAP fault
     * @param ex exception that should be converted to a SOAP fault
     * @throws IOException if an I/O error occurred
     */
    public static void sendErrorResponse(HttpServletResponse response,
            CodedException ex) throws IOException {
        String faultXml;
        if (ex instanceof CodedException.Fault) {
            faultXml = ((CodedException.Fault) ex).getFaultXml();
        } else {
            log.debug("Error detail code: " + ex.getFaultDetail());
            faultXml = SoapFault.createFaultXml(ex);
        }

        String encoding = MimeUtils.UTF8;
        byte[] messageBytes = faultXml.getBytes(encoding);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MimeTypes.TEXT_XML);
        response.setContentLength(messageBytes.length);
        response.setHeader("SOAPAction", "");
        response.setCharacterEncoding(encoding);
        response.getOutputStream().write(messageBytes);
    }

    /**
     * Sends plain text fault message to the other party.
     * @param response HTTP servlet response for sending the plain fault
     * @param status HTTP status code
     * @param message fault message
     * @throws IOException if an I/O error occurred
     */
    public static void sendPlainTextErrorResponse(HttpServletResponse response,
            int status, String message) throws IOException {
        byte[] messageBytes = message.getBytes("UTF-8");
        response.setStatus(status);
        response.setContentType(MimeTypes.TEXT_PLAIN_UTF_8);
        response.setContentLength(messageBytes.length);
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
