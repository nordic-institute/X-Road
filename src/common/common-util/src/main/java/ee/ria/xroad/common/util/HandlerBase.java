/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.util.JettyUtils.setContentLength;
import static ee.ria.xroad.common.util.JettyUtils.setContentType;
import static org.eclipse.jetty.http.MimeTypes.Type.TEXT_XML_UTF_8;

/**
 * Convenience base class for proxy HTTP handlers.
 */
public abstract class HandlerBase extends Handler.Abstract {

    /**
     * Sends SOAP fault message to the other party.
     *
     * @param response HTTP servlet response for sending the SOAP fault
     * @param callback
     * @param ex       exception that should be converted to a SOAP fault
     * @throws IOException if an I/O error occurred
     */
    public void sendErrorResponse(Request request,
                                  Response response,
                                  Callback callback,
                                  CodedException ex) throws IOException {
        String faultXml = ex instanceof CodedException.Fault
                ? ((CodedException.Fault) ex).getFaultXml() : SoapFault.createFaultXml(ex);
        String encoding = MimeUtils.UTF8;
        byte[] messageBytes = faultXml.getBytes(encoding);

        response.setStatus(HttpStatus.OK_200);
        setContentType(response, TEXT_XML_UTF_8);
        setContentLength(response, messageBytes.length);
        response.getHeaders().put("SOAPAction", "");
        response.write(true, ByteBuffer.wrap(messageBytes), callback);
    }

    /**
     * Sends plain text fault message to the other party.
     *
     * @param response HTTP servlet response for sending the plain fault
     * @param status   HTTP status code
     * @param message  fault message
     * @throws IOException if an I/O error occurred
     */
    public void sendPlainTextErrorResponse(Response response, Callback callback, int status, String message)
            throws IOException {
        byte[] messageBytes = message.getBytes("UTF-8");
        response.setStatus(status);
        setContentType(response, MimeTypes.TEXT_PLAIN_UTF8);
        setContentLength(response, messageBytes.length);
        response.write(true, ByteBuffer.wrap(messageBytes), callback);
    }

    /**
     * Returns the client certificate from the SSL context.
     */
    protected List<X509Certificate> getClientCertificates(HttpServletRequest request) {
        Object attribute = request.getAttribute(EndPoint.SslSessionData.ATTRIBUTE);

        if (attribute != null) {
            return List.of(((EndPoint.SslSessionData) attribute).peerCertificates());
        } else {
            return new ArrayList<>();
        }
    }

    protected void failure(Request request, Response response, Callback callback, CodedException ex)
            throws IOException {
    }
}
