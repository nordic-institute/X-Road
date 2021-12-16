/**
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
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import com.google.gson.stream.JsonWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HTTP_METHOD;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * MessageRoomHandler
 */
@Slf4j
public class ClientMessageRoomHandler extends AbstractClientProxyHandler {

    private static final String APPLICATION_JSON = "application/json";

    /**
     * Constructor
     */
    public ClientMessageRoomHandler(HttpClient client) {
        super(client, false);
    }

    @Override
    MessageProcessorBase createRequestProcessor(String target,
                                                HttpServletRequest request, HttpServletResponse response,
                                                OpMonitoringData opMonitoringData) throws Exception {
        if (target != null && target.equals("/publish")) {
            verifyCanProcess(request);
            return new ClientMessageRoomProcessor(request, response, client,
                    getIsAuthenticationData(request), opMonitoringData);
        }
        return null;
    }

    private void verifyCanProcess(HttpServletRequest request) {
        if (!isPostRequest(request)) {
            throw new ClientException(X_INVALID_HTTP_METHOD,
                    "Must use POST request method instead of %s",
                    request.getMethod());
        }

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

        response.setContentType(APPLICATION_JSON);
        final JsonWriter writer = new JsonWriter(new PrintWriter(response.getOutputStream()));
        writer.beginObject()
                .name("type").value(ex.getFaultCode())
                .name("message").value(ex.getFaultString())
                .name("detail").value(ex.getFaultDetail())
                .endObject()
                .close();
    }
}
