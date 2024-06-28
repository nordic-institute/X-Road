/*
 * The MIT License
 *
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

package org.niis.xroad.edc.extension.signer.legacy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.conf.SigningCtxProvider;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import lombok.Getter;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.operator.DigestCalculator;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;

import java.io.OutputStream;
import java.util.Arrays;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_ACCESS_DENIED;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SERVICE_TYPE;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_REST;
import static ee.ria.xroad.common.ErrorCodes.X_MISSING_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_DISABLED;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_FAILED_X;
import static ee.ria.xroad.common.ErrorCodes.X_SERVICE_MISSING_URL;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_HASH_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_ORIGINAL_CONTENT_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.MimeUtils.randomBoundary;
import static java.util.Optional.ofNullable;

public class RestMessageProcessor extends MessageProcessorBase {

//    private final X509Certificate[] clientSslCerts;

    private ProxyMessage requestMessage;
    private ServiceId requestServiceId;

    private ProxyMessageDecoder decoder;

    private SigningCtx responseSigningCtx;

    private RestResponse restResponse;
    private CachingStream restResponseBody;

    private String xRequestId;
    private XRoadMessageLog xRoadMessageLog;

    public RestMessageProcessor(ContainerRequestContext request,
                                HttpClient httpClient,
                                XRoadMessageLog messageLog, Monitor monitor) {
        super(request, httpClient, monitor);

//        this.clientSslCerts = clientSslCerts;
        this.xRoadMessageLog = messageLog;
    }

    @Override
    public Response process() {
        monitor.debug("process(%s)".formatted(requestContext.getMediaType().toString()));

        xRequestId = requestContext.getHeaderString(HEADER_REQUEST_ID);

        String multipartBoundary = randomBoundary();

        StreamingOutput streamingOut = output -> {
            ProxyMessageEncoder encoder = new ProxyMessageEncoder(output, CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, multipartBoundary);
            try {
                readMessage();
                handleRequest(encoder);
                sign(encoder);
                logResponseMessage(encoder);
                writeSignature(encoder);
                close(encoder);
            } catch (Exception ex) {
                try {
                    handleException(ex, encoder);
                } catch (Exception e) {
                    throw new RuntimeException("Exception handling failed", e);
                }
            } finally {
                ofNullable(requestMessage).ifPresent(ProxyMessage::consume);
                ofNullable(restResponseBody).ifPresent(CachingStream::consume);
            }
        };

        return Response.ok()
                .type(MimeUtils.mpMixedContentType(multipartBoundary))
                .header(HEADER_HASH_ALGO_ID, SoapUtils.getHashAlgoId())
                .entity(streamingOut)
                .build();
    }

    private void handleRequest(ProxyMessageEncoder encoder) throws Exception {
        verifyAccess();
        verifySignature();
        logRequestMessage();

        DefaultRestServiceHandlerImpl handler = new DefaultRestServiceHandlerImpl();

        try {
            handler.startHandling(requestContext, requestMessage, decoder, encoder,
                    httpClient);
        } finally {
            restResponse = handler.getRestResponse();
            restResponseBody = handler.getRestResponseBody();
        }
    }

    private void readMessage() throws Exception {
        monitor.debug("readMessage()");

        requestMessage = new ProxyMessage(requestContext.getHeaderString(HEADER_ORIGINAL_CONTENT_TYPE)) {
            @Override
            public void rest(RestRequest message) throws Exception {
                super.rest(message);
                requestServiceId = message.getServiceId();
                verifyClientStatus();
                responseSigningCtx = SigningCtxProvider.getSigningCtx(requestServiceId.getClientId());
//                if (SystemProperties.isSslEnabled()) {
//                    verifySslClientCert();
//                }
            }
        };

        decoder = new ProxyMessageDecoder(requestMessage, requestContext.getMediaType().toString(), false,
                getHashAlgoId(requestContext));
        try {
            decoder.parse(requestContext.getEntityStream());
        } catch (CodedException e) {
            throw e.withPrefix(X_SERVICE_FAILED_X);
        }

        // Check if the input contained all the required bits.
        checkRequest();
    }

    private void checkRequest() {
        final RestRequest rest = requestMessage.getRest();
        if (rest == null) {
            throw new CodedException(X_MISSING_REST, "Request does not have REST message");
        }
        if (requestMessage.getSignature() == null) {
            throw new CodedException(X_MISSING_SIGNATURE, "Request does not have signature");
        }

        checkIdentifier(rest.getClientId());
        checkIdentifier(rest.getServiceId());
        checkIdentifier(rest.getTargetSecurityServer());
    }

    private void verifyClientStatus() {
        ClientId client = requestServiceId.getClientId();

        String status = ServerConf.getMemberStatus(client);

        if (!ClientType.STATUS_REGISTERED.equals(status)) {
            throw new CodedException(X_UNKNOWN_MEMBER, "Client '%s' not found", client);
        }
    }

//    private void verifySslClientCert() throws Exception {
//        if (requestMessage.getOcspResponses().isEmpty()) {
//            throw new CodedException(X_SSL_AUTH_FAILED,
//                    "Cannot verify TLS certificate, corresponding OCSP response is missing");
//        }
//
//        String instanceIdentifier = requestMessage.getRest().getClientId().getXRoadInstance();
//        X509Certificate trustAnchor = GlobalConf.getCaCert(instanceIdentifier,
//                clientSslCerts[clientSslCerts.length - 1]);
//
//        if (trustAnchor == null) {
//            throw new Exception("Unable to find trust anchor");
//        }
//
//        try {
//            CertChain chain = CertChain.create(instanceIdentifier, ArrayUtils.add(clientSslCerts,
//                    trustAnchor));
//            CertHelper.verifyAuthCert(chain, requestMessage.getOcspResponses(), requestMessage.getRest().getClientId());
//        } catch (Exception e) {
//            throw new CodedException(X_SSL_AUTH_FAILED, e);
//        }
//    }

    private void verifyAccess() {
        monitor.debug("verifyAccess()");

        if (!ServerConf.serviceExists(requestServiceId)) {
            throw new CodedException(X_UNKNOWN_SERVICE, "Unknown service: %s", requestServiceId);
        }

        DescriptionType descriptionType = ServerConf.getDescriptionType(requestServiceId);
        if (descriptionType != null && descriptionType != DescriptionType.REST
                && descriptionType != DescriptionType.OPENAPI3) {
            throw new CodedException(X_INVALID_SERVICE_TYPE,
                    "Service is a SOAP service and cannot be called using REST interface");
        }

        if (!ServerConf.isQueryAllowed(
                requestMessage.getRest().getClientId(),
                requestServiceId,
                requestMessage.getRest().getVerb().name(),
                requestMessage.getRest().getServicePath())) {
            throw new CodedException(X_ACCESS_DENIED, "Request is not allowed: %s", requestServiceId);
        }

        String disabledNotice = ServerConf.getDisabledNotice(requestServiceId);

        if (disabledNotice != null) {
            throw new CodedException(X_SERVICE_DISABLED, "Service %s is disabled: %s", requestServiceId,
                    disabledNotice);
        }
    }

    private void verifySignature() throws Exception {
        monitor.debug("verifySignature()");

        decoder.verify(requestMessage.getRest().getClientId(), requestMessage.getSignature());
    }

    private void logRequestMessage() {
        monitor.debug("logRequestMessage()");
        RestLogMessage logMessage = new RestLogMessage(requestMessage.getRest().getQueryId(),
                requestMessage.getRest().getClientId(),
                requestMessage.getRest().getServiceId(),
                requestMessage.getRest(),
                requestMessage.getSignature(),
                requestMessage.getRestBody(),
                false,
                xRequestId);

        xRoadMessageLog.log(logMessage);
    }

    private void logResponseMessage(ProxyMessageEncoder encoder) {
        monitor.debug("log response message");
        RestLogMessage logMessage = new RestLogMessage(requestMessage.getRest().getQueryId(),
                requestMessage.getRest().getClientId(),
                requestMessage.getRest().getServiceId(),
                restResponse,
                encoder.getSignature(),
                restResponseBody == null ? null : restResponseBody.getCachedContents(),
                false,
                xRequestId);

        xRoadMessageLog.log(logMessage);
    }

    private void sign(ProxyMessageEncoder encoder) throws Exception {
        monitor.debug("sign(%s)".formatted(requestServiceId.getClientId()));
        encoder.sign(responseSigningCtx);
    }

    private void writeSignature(ProxyMessageEncoder encoder) throws Exception {
        monitor.debug("writeSignature()");
        encoder.writeSignature();
    }

    private void close(ProxyMessageEncoder encoder) throws Exception {
        monitor.debug("close()");
        encoder.close();
    }

    private void handleException(Exception ex, ProxyMessageEncoder encoder) throws Exception {
        monitor.debug("Request failed", ex);

        if (encoder != null) {
            CodedException exception;
            if (ex instanceof CodedException.Fault) {
                exception = (CodedException.Fault) ex;
            } else {
                exception = translateWithPrefix(SERVER_SERVERPROXY_X, ex);
            }
            encoder.fault(SoapFault.createFaultXml(exception));
            encoder.close();
        } else {
            throw ex;
        }
    }

//    private X509Certificate getClientAuthCert() {
//        return clientSslCerts != null ? clientSslCerts[0] : null;
//    }

    @Getter
    private static final class DefaultRestServiceHandlerImpl {

        private RestResponse restResponse;
        private CachingStream restResponseBody;

        private String concatPath(String address, String path) {
            if (path == null || path.isEmpty()) return address;
            if (address.endsWith("/") && path.startsWith("/")) {
                return address.concat(path.substring(1));
            }
            return address.concat(path);
        }

        public void startHandling(ContainerRequestContext request, ProxyMessage requestProxyMessage,
                                  ProxyMessageDecoder messageDecoder, ProxyMessageEncoder messageEncoder,
                                  HttpClient restClient) throws Exception {
            String address = ServerConf.getServiceAddress(requestProxyMessage.getRest().getServiceId());
            if (address == null || address.isEmpty()) {
                throw new CodedException(X_SERVICE_MISSING_URL, "Service address not specified for '%s'",
                        requestProxyMessage.getRest().getServiceId());
            }

            address = concatPath(address, requestProxyMessage.getRest().getServicePath());
            final String query = requestProxyMessage.getRest().getQuery();
            if (query != null) {
                address += "?" + query;
            }

            HttpRequestBase req = switch (requestProxyMessage.getRest().getVerb()) {
                case GET -> new HttpGet(address);
                case POST -> new HttpPost(address);
                case PUT -> new HttpPut(address);
                case DELETE -> new HttpDelete(address);
                case PATCH -> new HttpPatch(address);
                case OPTIONS -> new HttpOptions(address);
                case HEAD -> new HttpHead(address);
                case TRACE -> new HttpTrace(address);
                default -> throw new CodedException(X_INVALID_REQUEST, "Unsupported REST verb");
            };

            int timeout = TimeUtils.secondsToMillis(ServerConf
                    .getServiceTimeout(requestProxyMessage.getRest().getServiceId()));
            req.setConfig(RequestConfig
                    .custom()
                    .setSocketTimeout(timeout)
                    .build());

            for (Header header : requestProxyMessage.getRest().getHeaders()) {
                req.addHeader(header);
            }

            if (req instanceof HttpEntityEnclosingRequest && requestProxyMessage.hasRestBody()) {
                ((HttpEntityEnclosingRequest) req).setEntity(new InputStreamEntity(requestProxyMessage.getRestBody(),
                        requestProxyMessage.getRestBody().size()));
            }

            final HttpContext ctx = new BasicHttpContext();
            ctx.setAttribute(ServiceId.class.getName(), requestProxyMessage.getRest().getServiceId());
            final HttpResponse response = restClient.execute(req, ctx);
            final StatusLine statusLine = response.getStatusLine();

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
                    statusLine.getStatusCode(),
                    statusLine.getReasonPhrase(),
                    Arrays.asList(response.getAllHeaders()),
                    request.getHeaderString(HEADER_REQUEST_ID)

            );
            messageEncoder.restResponse(restResponse);

            if (response.getEntity() != null) {
                restResponseBody = new CachingStream();
                TeeInputStream tee = new TeeInputStream(response.getEntity().getContent(), restResponseBody);
                messageEncoder.restBody(tee);
                EntityUtils.consume(response.getEntity());
            }
        }

    }

}
