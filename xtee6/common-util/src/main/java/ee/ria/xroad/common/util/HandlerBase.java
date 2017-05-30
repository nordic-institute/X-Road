/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.SoapFault;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Convenience base class for proxy HTTP handlers.
 */
public abstract class HandlerBase extends AbstractHandler {

    /**
     * Sends SOAP fault message to the other party.
     * @param response HTTP servlet response for sending the SOAP fault
     * @param ex exception that should be converted to a SOAP fault
     * @throws IOException if an I/O error occurred
     */
    public static void sendErrorResponse(HttpServletResponse response,
            CodedException ex) throws IOException {
        sendErrorResponse(response, ex.getFaultCodeAsQName(), ex.getFaultString(),
                ex.getFaultActor(), ex.getFaultDetail());
    }

    /**
    * Sends SOAP fault message to the other party.
    * @param response HTTP servlet response for sending the SOAP fault
    * @param faultCode code of the SOAP fault
    * @param faultString string of the SOAP fault
    * @param faultActor actor of the SOAP fault
    * @param faultDetail detail of the SOAP fault
    * @throws IOException if an I/O error occurred
    */
    public static void sendErrorResponse(HttpServletResponse response,
                                         QName faultCode, String faultString, String faultActor,
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
