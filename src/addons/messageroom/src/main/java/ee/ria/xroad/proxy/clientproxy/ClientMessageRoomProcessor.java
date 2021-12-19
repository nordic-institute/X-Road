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
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.common.MessageRoomUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.eclipse.jetty.server.Request;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_BAD_REQUEST;

@Slf4j
public class ClientMessageRoomProcessor extends AbstractClientMessageProcessor {

    private ClientId senderId;
    private RestRequest restRequest;

    static final ObjectMapper MAPPER;

    static {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        MAPPER = mapper;
    }

    ClientMessageRoomProcessor(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                               HttpClient httpClient, IsAuthenticationData clientCert,
                               OpMonitoringData opMonitoringData)
            throws Exception {
        super(servletRequest, servletResponse, httpClient, clientCert, opMonitoringData);
    }

    @Override
    public MessageInfo createRequestMessageInfo() {
        if (restRequest == null) {
            return null;
        }

        return new MessageInfo(MessageInfo.Origin.CLIENT_PROXY,
                restRequest.getClientId(),
                null,
                null,
                null);
    }

    @Override
    public void process() throws Exception {
        try {
            restRequest = new RestRequest(
                    servletRequest.getMethod(),
                    "/r1/instanceIdentifier/memberClass/memberCode/subsystemCode/serviceCode/",
                    null,
                    headers(servletRequest),
                    null
            );

            // Check that incoming identifiers do not contain illegal characters
            checkRequestIdentifiers();

            senderId = restRequest.getClientId();

            if (!MessageRoomUtil.isValidPublisher(senderId)) {
                throw new CodedException(X_BAD_REQUEST,
                        "Message Room messages are disabled for this client");
            }

            verifyClientStatus(senderId);
            verifyClientAuthentication(senderId);

            MessageRoomQueue.getInstance().add(new MessageRoomRequest(senderId,
                    IOUtils.toByteArray(servletRequest.getInputStream()), restRequest, servletRequest, httpClient));

            //writeResponseJson(null);
        } catch (Exception e) {
            throw e;
        }
    }

    private void checkRequestIdentifiers() {
        checkIdentifier(restRequest.getClientId());
    }

    private void writeResponseJson(Object object) throws Exception {
        servletResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        servletResponse.setContentType(
                MimeUtils.contentTypeWithCharset(MimeTypes.JSON, StandardCharsets.UTF_8.name().toLowerCase()));
        MAPPER.writeValue(servletResponse.getOutputStream(), object);
    }

    private List<Header> headers(HttpServletRequest req) {
        //Use jetty request to keep the original order
        Request jrq = (Request) req;
        return jrq.getHttpFields().stream()
                .map(f -> new BasicHeader(f.getName(), f.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
