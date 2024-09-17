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
package org.niis.xroad.edc.extension.signer;

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.signature.BatchSigner;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.serverproxy.HttpClientCreator;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.apache.http.client.HttpClient;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.response.TransferErrorResponse;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.edc.extension.signer.legacy.MessageProcessorBase;
import org.niis.xroad.edc.extension.signer.legacy.RestMessageProcessor;
import org.niis.xroad.edc.extension.signer.legacy.SoapMessageProcessor;
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;

import javax.net.ssl.SSLSession;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_MESSAGE_TYPE;
import static ee.ria.xroad.common.util.MimeUtils.VALUE_MESSAGE_TYPE_REST;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static jakarta.ws.rs.core.Response.status;

@Path("/proxy")
@Produces(WILDCARD)
public class XrdDataPlaneProxyApiController {

    private final XRoadMessageLog xRoadMessageLog;
    private final Monitor monitor;

    private final DataPlaneAuthorizationService authorizationService;

    private final HttpClient httpClient;
    private final boolean needClientAuth;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfProvider serverConfProvider;
    private final KeyConfProvider keyConfProvider;
    private final CertChainFactory certChainFactory;

    public XrdDataPlaneProxyApiController(Monitor monitor,
                                          XRoadMessageLog xRoadMessageLog,
                                          DataPlaneAuthorizationService authorizationService,
                                          boolean needClientAuth,
                                          GlobalConfProvider globalConfProvider,
                                          KeyConfProvider keyConfProvider,
                                          ServerConfProvider serverConfProvider,
                                          CertChainFactory certChainFactory) {
        this.monitor = monitor;
        this.xRoadMessageLog = xRoadMessageLog;
        this.authorizationService = authorizationService;
        this.needClientAuth = needClientAuth;
        this.globalConfProvider = globalConfProvider;
        this.keyConfProvider = keyConfProvider;
        this.serverConfProvider = serverConfProvider;
        this.certChainFactory = certChainFactory;

        try {
            HttpClientCreator creator = new HttpClientCreator(serverConfProvider);
            this.httpClient = creator.getHttpClient();
            BatchSigner.init();
        } catch (Exception e) {
            throw new EdcException("Failed to create http client");
        }
    }

    @POST
    public Response post(@Context ContainerRequestContext requestContext) {
        return handle(requestContext);
    }

    private Response handle(ContainerRequestContext requestContext) {
        var token = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (token == null) {
            return error(UNAUTHORIZED, "Missing Authorization Header");
        }

        var sourceDataAddress = authorizationService.authorize(token, buildRequestData(requestContext));
        if (sourceDataAddress.failed()) {
            return error(FORBIDDEN, sourceDataAddress.getFailureDetail());
        }

        try {
            return getMessageProcessor(requestContext).process();
        } catch (Exception e) {
            throw new EdcException("Request processing failed", e);
        }
    }

    private MessageProcessorBase getMessageProcessor(ContainerRequestContext requestContext) {
        if (VALUE_MESSAGE_TYPE_REST.equals(requestContext.getHeaderString(HEADER_MESSAGE_TYPE))) {
            return new RestMessageProcessor(requestContext, httpClient, getClientSslCerts(requestContext),
                    needClientAuth, xRoadMessageLog, globalConfProvider, serverConfProvider, keyConfProvider, certChainFactory, monitor);
        } else {
            return new SoapMessageProcessor(requestContext, httpClient, getClientSslCerts(requestContext),
                    needClientAuth, xRoadMessageLog, globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory, monitor);
        }
    }

    private X509Certificate[] getClientSslCerts(ContainerRequestContext requestContext) {
        if (needClientAuth) {
            try {
                return (X509Certificate[]) ((SSLSession) requestContext.getProperty("org.eclipse.jetty.servlet.request.ssl_session"))
                        .getPeerCertificates();
            } catch (Exception e) {
                monitor.severe("Failed to get client SSL certificates", e);
            }
        }
        return null;
    }

    private Map<String, Object> buildRequestData(ContainerRequestContext requestContext) {
        var requestData = new HashMap<String, Object>();
        requestData.put("headers", requestContext.getHeaders());

        var uriInfo = requestContext.getUriInfo();
        requestData.put("path", uriInfo);

        var path = uriInfo.getPath();
        requestData.put("resolvedPath", path.startsWith("/") ? path.substring(1) : path);
        requestData.put("method", requestContext.getMethod());
        requestData.put("content-type", requestContext.getMediaType());
        return requestData;
    }

    private static Response error(Response.Status status, String error) {
        return status(status).type(APPLICATION_JSON).entity(new TransferErrorResponse(List.of(error))).build();
    }

}
