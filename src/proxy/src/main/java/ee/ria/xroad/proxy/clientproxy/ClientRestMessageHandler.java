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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.JsonUtils;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.XmlUtils;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Handles client messages. This handler must be the last handler in the
 * handler collection, since it will not pass handling of the request to
 * the next handler (i.e. throws exception instead), if it cannot process
 * the request itself.
 */
@Slf4j
class ClientRestMessageHandler extends AbstractClientProxyHandler {

    private static final String TEXT_XML = "text/xml";
    private static final String APPLICATION_XML = "application/xml";
    private static final String TEXT_ANY = "text/*";
    private static final String APPLICATION_JSON = "application/json";
    private static final List<String> XML_TYPES = Arrays.asList(TEXT_XML, APPLICATION_XML, TEXT_ANY);

    ClientRestMessageHandler(HttpClient client) {
        super(client, true);
    }

    @Override
    MessageProcessorBase createRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response,
            OpMonitoringData opMonitoringData) throws Exception {
        if (target != null && target.startsWith("/r" + RestMessage.PROTOCOL_VERSION + "/")) {
            verifyCanProcess();
            return new ClientRestMessageProcessor(request, response, client,
                    getIsAuthenticationData(request), opMonitoringData);
        }
        return null;
    }

    private void verifyCanProcess() {
        GlobalConf.verifyValidity();

        if (!SystemProperties.isSslEnabled()) {
            return;
        }

        AuthKey authKey = KeyConf.getAuthKey();
        if (authKey.getCertChain() == null) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Security server has no valid authentication certificate");
        }
    }

    @Override
    public void sendErrorResponse(HttpServletRequest request,
            HttpServletResponse response,
            CodedException ex) throws IOException {
        if (ex.getFaultCode().startsWith("Server.")) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
        } else {
            response.setStatus(HttpStatus.BAD_REQUEST_400);
        }
        response.setCharacterEncoding(MimeUtils.UTF8);
        response.setHeader("X-Road-Error", ex.getFaultCode());

        final String responseContentType = decideErrorResponseContentType(request.getHeaders("Accept"));
        response.setContentType(responseContentType);
        if (XML_TYPES.contains(responseContentType)) {
            try {
                Document doc = XmlUtils.newDocumentBuilder(false).newDocument();
                Element errorRootElement = doc.createElement("error");
                doc.appendChild(errorRootElement);
                Element typeElement = doc.createElement("type");
                typeElement.appendChild(doc.createTextNode(ex.getFaultCode()));
                errorRootElement.appendChild(typeElement);
                Element messageElement = doc.createElement("message");
                messageElement.appendChild(doc.createTextNode(ex.getFaultString()));
                errorRootElement.appendChild(messageElement);
                Element detailElement = doc.createElement("detail");
                detailElement.appendChild(doc.createTextNode(ex.getFaultDetail()));
                errorRootElement.appendChild(detailElement);
                response.getOutputStream().write(XmlUtils.prettyPrintXml(doc, "UTF-8", 0).getBytes());
            } catch (Exception e) {
                log.error("Unable to generate XML document");
            }
        } else {
            try (JsonGenerator jsonGenerator = JsonUtils.getObjectWriter()
                    .getFactory().createGenerator(new PrintWriter(response.getOutputStream()))) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("type", ex.getFaultCode());
                jsonGenerator.writeStringField("message", ex.getFaultString());
                jsonGenerator.writeStringField("detail", ex.getFaultDetail());
                jsonGenerator.writeEndObject();
            }
        }
    }

    private static String decideErrorResponseContentType(Enumeration<String> acceptHeaderValue) {
        return Collections.list(acceptHeaderValue).stream()
                .flatMap(h -> Arrays.stream(h.split(",")))
                .map(s -> s.split(";", 2)[0].trim().toLowerCase())
                .filter(XML_TYPES::contains)
                .findAny()
                .map(orig -> mapTextToXml(orig))
                .orElse(APPLICATION_JSON);
    }

    private static String mapTextToXml(String orig) {
        return TEXT_ANY.equals(orig) ? TEXT_XML : orig;
    }
}
