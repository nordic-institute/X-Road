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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.MessageRoomSubscriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.common.MessageRoomUtil;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.operator.DigestCalculator;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;

/**
 * Handler for Message Room subscribe and unsubscribe services
 */
@Slf4j
public class MessageRoomServiceHandlerImpl implements RestServiceHandler {

    private static final String SUBSCRIBE_REQUEST = "subscribe";
    private static final String UNSUBSCRIBE_REQUEST = "unsubscribe";

    static final ObjectMapper MAPPER;

    static {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        MAPPER = mapper;
    }

    private RestResponse restResponse;
    private CachingStream restResponseBody;

    @Override
    public boolean shouldVerifyAccess() {
        return false;
    }

    @Override
    public boolean shouldVerifySignature() {
        return true;
    }

    @Override
    public boolean shouldLogSignature() {
        return true;
    }

    @Override
    public boolean canHandle(ServiceId requestServiceId, ProxyMessage requestProxyMessage) {
        if (requestProxyMessage.getRest().getVerb() != RestRequest.Verb.POST) {
            return false;
        }
        return Arrays.asList(SUBSCRIBE_REQUEST, UNSUBSCRIBE_REQUEST).contains(requestServiceId.getServiceCode());
    }

    @Override
    public void startHandling(HttpServletRequest servletRequest, ProxyMessage requestProxyMessage,
                              ProxyMessageDecoder messageDecoder, ProxyMessageEncoder messageEncoder,
                              HttpClient restClient, HttpClient opMonitorClient,
                              OpMonitoringData opMonitoringData) throws Exception {
        //calculate request hash
        byte[] requestDigest;
        if (messageDecoder.getRestBodyDigest() != null) {
            final DigestCalculator dc = CryptoUtils.createDigestCalculator(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);
            try (OutputStream out = dc.getOutputStream()) {
                out.write(requestProxyMessage.getRest().getHash());
                out.write(messageDecoder.getRestBodyDigest());
            }
            requestDigest = dc.getDigest();
        } else {
            requestDigest = requestProxyMessage.getRest().getHash();
        }

        restResponse = new RestResponse(requestProxyMessage.getRest().getClientId(),
                requestProxyMessage.getRest().getQueryId(),
                requestDigest,
                requestProxyMessage.getRest().getServiceId(),
                HttpStatus.SC_OK,
                "OK",
                requestProxyMessage.getRest().getHeaders(),
                servletRequest.getHeader(HEADER_REQUEST_ID)
        );

        if (!MessageRoomUtil.isValidPublisher(requestProxyMessage.getRest().getServiceId().getClientId())) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Message Room messages are disabled for this subsystem");
        }

        JsonNode jsonMap = MAPPER.readTree(requestProxyMessage.getRestBody());
        JsonNode xRoadService = jsonMap.get("x-road-service");
        if (xRoadService == null || xRoadService.asText().isEmpty()) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Missing x-road-service in message body");
        }

        ServiceId xRoadServiceId = MessageRoomUtil.decodeServiceId(xRoadService.asText());
        if (!xRoadServiceId.getClientId().equals(requestProxyMessage.getRest().getClientId())) {
            throw new CodedException(X_INVALID_REQUEST,
                    "x-road-service is not owned by x-road-client");
        }

        restResponseBody = new CachingStream();
        if (requestProxyMessage.getRest().getServiceId().getServiceCode().equals(SUBSCRIBE_REQUEST)) {
            handleSubscribe(requestProxyMessage, xRoadServiceId);
        } else if (requestProxyMessage.getRest().getServiceId().getServiceCode().equals(UNSUBSCRIBE_REQUEST)) {
            handleUnsubscribe(requestProxyMessage, xRoadServiceId);
        }

        messageEncoder.restResponse(restResponse);
        messageEncoder.restBody(restResponseBody.getCachedContents());

        // It's required that in case of metadata service (where message is
        // not forwarded) the requestOutTs must be equal with the requestInTs
        // and the responseInTs must be equal with the responseOutTs.
        opMonitoringData.setRequestOutTs(opMonitoringData.getRequestInTs());
        opMonitoringData.setAssignResponseOutTsToResponseInTs(true);
        opMonitoringData.setServiceType(DescriptionType.REST.name());
    }

    private void handleSubscribe(ProxyMessage requestProxyMessage, ServiceId xRoadServiceId) throws IOException {
        restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE, MimeTypes.JSON));
        ClientId messageRoomId = requestProxyMessage.getRest().getServiceId().getClientId();
        List<MessageRoomSubscriptionType> subscriptions = ServerConf.getMessageRoomSubscriptions(messageRoomId);

        Optional<MessageRoomSubscriptionType> match = MessageRoomUtil.findSubscription(subscriptions, xRoadServiceId);
        if (match.isPresent()) {
            throw new CodedException(X_INVALID_REQUEST, "Subscription already exists");
        }

        try {
            ServerConf.saveMessageRoomSubscription(messageRoomId, xRoadServiceId);
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, "An internal error occurred");
        }
    }

    private void handleUnsubscribe(ProxyMessage requestProxyMessage, ServiceId xRoadServiceId) throws IOException {
        restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE, MimeTypes.JSON));
        ClientId messageRoomId = requestProxyMessage.getRest().getServiceId().getClientId();
        List<MessageRoomSubscriptionType> subscriptions = ServerConf.getMessageRoomSubscriptions(messageRoomId);

        Optional<MessageRoomSubscriptionType> match = MessageRoomUtil.findSubscription(subscriptions, xRoadServiceId);
        if (!match.isPresent()) {
            throw new CodedException(X_INVALID_REQUEST, "Subscription doesn't exist");
        }

        try {
            ServerConf.deleteMessageRoomSubscription(match.get());
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, "An internal error occurred");
        }
    }

    @Override
    public RestResponse getRestResponse() {
        return restResponse;
    }

    @Override
    public CachingStream getRestResponseBody() {
        return restResponseBody;
    }

    @Override
    public void finishHandling() {
        // NOP
    }
}
