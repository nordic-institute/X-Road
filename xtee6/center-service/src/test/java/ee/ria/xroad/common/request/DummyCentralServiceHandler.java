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
package ee.ria.xroad.common.request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapUtils;

import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * HTTP request handler for the dummy central service.
 */
@Slf4j
public class DummyCentralServiceHandler extends AbstractHandler {

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        log.info("Received request from {}", request.getRemoteAddr());
        try {
            SoapMessageImpl requestMessage =
                    ManagementRequestHandler.readRequest(
                            request.getContentType(),
                            request.getInputStream());

            log.info("Got request message: {}", requestMessage.getXml());

            String service = requestMessage.getService().getServiceCode();
            switch (service) {
                case "authCertRegRequest":
                    handleAuthCertRegRequest(requestMessage, response);
                    break;
                case "authCertDeletionRequest":
                    handleAuthCertDeletionRequest(requestMessage, response);
                    break;
                case "clientRegRequest":
                    handleClientRegRequest(requestMessage, response);
                    break;
                case "clientDeletionRequest":
                    handleClientDeletionRequest(requestMessage, response);
                    break;
                default:
                    throw new RuntimeException("Unknown service " + service);
            }
        } catch (Exception e) {
            sendErrorResponse(response, translateException(e));
        } finally {
            baseRequest.setHandled(true);
        }
    }

    private static void handleClientDeletionRequest(SoapMessageImpl soap,
            HttpServletResponse response) throws Exception {
        ClientRequestType req =
                ManagementRequestParser.parseClientDeletionRequest(soap);

        log.info("Got ClientDeletionRequest: {}", toString(req));

        sendResponse(SoapUtils.toResponse(soap), response);
    }

    private static void handleClientRegRequest(SoapMessageImpl soap,
            HttpServletResponse response) throws Exception {
        ClientRequestType req =
                ManagementRequestParser.parseClientRegRequest(soap);

        log.info("Got ClientRegRequest: {}", toString(req));

        sendResponse(SoapUtils.toResponse(soap), response);
    }

    private static void handleAuthCertDeletionRequest(SoapMessageImpl soap,
            HttpServletResponse response) throws Exception {
        AuthCertDeletionRequestType req =
                ManagementRequestParser.parseAuthCertDeletionRequest(soap);

        log.info("Got AuthCertDeletionRequest: {}", toString(req));

        sendResponse(SoapUtils.toResponse(soap), response);
    }

    private static void handleAuthCertRegRequest(SoapMessageImpl soap,
            HttpServletResponse response) throws Exception {
        AuthCertRegRequestType req =
                ManagementRequestParser.parseAuthCertRegRequest(soap);

        log.info("Got AuthCertRegRequest: {}", toString(req));

        sendResponse(SoapUtils.toResponse(soap), response);
    }

    private static void sendResponse(SoapMessageImpl responseSoap,
            HttpServletResponse response) throws Exception {
        log.info("Sending response: {}", responseSoap.getXml());
        try {
            response.setContentType(MimeTypes.TEXT_XML);
            response.getWriter().write(responseSoap.getXml());
        } finally {
            response.getWriter().close();
        }
    }

    private static void sendErrorResponse(HttpServletResponse response,
            CodedException ex) throws IOException {
        log.debug("sendErrorResponse()");
        sendErrorResponse(response, ex.getFaultCode(), ex.getFaultString(),
                ex.getFaultActor(), ex.getFaultDetail());
    }

    private static void sendErrorResponse(HttpServletResponse response,
            String faultCode, String faultString, String faultActor,
            String faultDetail) throws IOException {
        String soapMessageXml = SoapFault.createFaultXml(
                faultCode, faultString, faultActor, faultDetail);

        String encoding = StandardCharsets.UTF_8.name();
        byte[] messageBytes = soapMessageXml.getBytes(encoding);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MimeTypes.TEXT_XML);
        response.setContentLength(messageBytes.length);
        response.setHeader("SOAPAction", "");
        response.setCharacterEncoding(encoding);
        response.getOutputStream().write(messageBytes);
    }

    private static String toString(Object o) {
        return ToStringBuilder.reflectionToString(o,
                ToStringStyle.MULTI_LINE_STYLE);
    }
}
