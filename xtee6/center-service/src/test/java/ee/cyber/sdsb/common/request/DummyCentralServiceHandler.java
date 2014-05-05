package ee.cyber.sdsb.common.request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapUtils;

public class DummyCentralServiceHandler extends AbstractHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(DummyCentralServiceHandler.class);

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        LOG.info("Received request from {}", request.getRemoteAddr());
        try {
            SoapMessageImpl requestMessage = ManagementRequestHandler.readRequest(
                    request.getContentType(), request.getInputStream());

            LOG.info("Got request message: {}", requestMessage.getXml());

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
            sendErrorResponse(response, ErrorCodes.translateException(e));
        } finally {
            baseRequest.setHandled(true);
        }
    }

    private static void handleClientDeletionRequest(SoapMessageImpl soap,
            HttpServletResponse response) throws Exception {
        ClientRequestType req =
                ManagementRequestParser.parseClientDeletionRequest(soap);

        LOG.info("Got ClientDeletionRequest: {}", toString(req));

        sendResponse(SoapUtils.toResponse(soap), response);
    }

    private static void handleClientRegRequest(SoapMessageImpl soap,
            HttpServletResponse response) throws Exception {
        ClientRequestType req =
                ManagementRequestParser.parseClientRegRequest(soap);

        LOG.info("Got ClientRegRequest: {}", toString(req));

        sendResponse(SoapUtils.toResponse(soap), response);
    }

    private static void handleAuthCertDeletionRequest(SoapMessageImpl soap,
            HttpServletResponse response) throws Exception {
        AuthCertDeletionRequestType req =
                ManagementRequestParser.parseAuthCertDeletionRequest(soap);

        LOG.info("Got AuthCertDeletionRequest: {}", toString(req));

        sendResponse(SoapUtils.toResponse(soap), response);
    }

    private static void handleAuthCertRegRequest(SoapMessageImpl soap,
            HttpServletResponse response) throws Exception {
        AuthCertRegRequestType req =
                ManagementRequestParser.parseAuthCertRegRequest(soap);

        LOG.info("Got AuthCertRegRequest: {}", toString(req));

        sendResponse(SoapUtils.toResponse(soap), response);
    }

    private static void sendResponse(SoapMessageImpl responseSoap,
            HttpServletResponse response) throws Exception {
        LOG.info("Sending response: {}", responseSoap.getXml());
        try {
            response.setContentType(MimeTypes.TEXT_XML);
            response.getWriter().write(responseSoap.getXml());
        } finally {
            response.getWriter().close();
        }
    }

    private static void sendErrorResponse(HttpServletResponse response,
            CodedException ex) throws IOException {
        LOG.debug("sendErrorResponse()");
        sendErrorResponse(response, ex.getFaultCode(), ex.getFaultString(),
                ex.getFaultActor(), ex.getFaultDetail());
    }

    private static void sendErrorResponse(HttpServletResponse response,
            String faultCode, String faultString, String faultActor,
            String faultDetail) throws IOException {
        // TODO: handle the case where CodedException has a cause.
        String soapMessageXml = SoapFault.createFaultXml(
                faultCode, faultString, faultActor,
                faultDetail);

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
