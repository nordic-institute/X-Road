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
package org.niis.xroad.proxy.edc;

import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CacheInputStream;
import ee.ria.xroad.proxy.messagelog.MessageLog;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.http.Header;
import org.niis.xroad.edc.sig.XrdSignatureService;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.message.RestRequest.Verb.DELETE;
import static ee.ria.xroad.common.message.RestRequest.Verb.OPTIONS;
import static ee.ria.xroad.common.message.RestRequest.Verb.PATCH;
import static ee.ria.xroad.common.message.RestRequest.Verb.POST;
import static ee.ria.xroad.common.message.RestRequest.Verb.PUT;
import static ee.ria.xroad.common.util.HeaderValueUtils.HEADER_CONTENT_TYPE;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;

@Slf4j
public class XrdDataSpaceClient {
    private final XrdSignatureService xrdSignatureService = new XrdSignatureService();

    public RestResponse processRestReqeust(RestRequest restRequest,
                                           AuthorizedAssetRegistry.GrantedAssetInfo assetInfo) throws Exception {

        CacheInputStream body = restRequest.getBody().getCachedContents();

        var path = assetInfo.endpoint();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (restRequest.getServicePath() != null) {
            path += restRequest.getServicePath();
        }
        if (StringUtils.isNotBlank(restRequest.getQuery())) {
            path += "?" + restRequest.getQuery();
        }
        final var dsRequest = ClassicRequestBuilder.create(restRequest.getVerb().name()).setUri(new URI(path));

        Map<String, String> headersToSign = restRequest.getHeaders().stream()
                .collect(Collectors.toMap(Header::getName, Header::getValue));

        headersToSign.put(assetInfo.authKey(), assetInfo.authCode());

        // todo: sign using streams, not body.readAllBytes()
        var signatureResponse = xrdSignatureService.sign(restRequest.getClientId(), body.readAllBytes(), headersToSign);
        for (Map.Entry<String, String> entry : headersToSign.entrySet()) {
            dsRequest.addHeader(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : signatureResponse.getSignatureHeaders().entrySet()) {
            dsRequest.addHeader(entry.getKey(), entry.getValue());
        }

        //handle body
        var method = restRequest.getVerb();
        if (POST.equals(method) || PUT.equals(method) || PATCH.equals(method)
                || DELETE.equals(method) || OPTIONS.equals(method)) {
            // Attach body to the request
            var contentType = signatureResponse.getSignatureHeaders().get(HEADER_CONTENT_TYPE);
            if (StringUtils.isBlank(contentType)) {
                contentType = "application/json";
            }

            dsRequest.setEntity(new InputStreamEntity(body, ContentType.parse(contentType)));
        }

        var signatureData = new SignatureData(signatureResponse.getSignatureDecoded(), null, null);

        MessageLog.log(restRequest, signatureData, body, true, restRequest.getXRequestId());
        var response = EdcDataPlaneHttpClient.sendRestRequest(dsRequest.build(), restRequest);
        MessageLog.log(restRequest, response, getSignatureFromHeaders(response.getHeaders()),
                response.getBody().getCachedContents(), true,
                restRequest.getXRequestId());

        return response;
    }

    public EdcDataPlaneHttpClient.EdcSoapWrapper processSoapRequest(SoapMessageImpl soapRequest, String xRequestId,
                                                                    AuthorizedAssetRegistry.GrantedAssetInfo assetInfo) throws Exception {
        var path = assetInfo.endpoint();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        final var dsRequest = ClassicRequestBuilder.post(new URI(path));

        Map<String, String> headersToSign = new HashMap<>();
        headersToSign.put(assetInfo.authKey(), assetInfo.authCode());

        var signatureResponse = xrdSignatureService.sign(soapRequest.getClient(), soapRequest.getBytes(), headersToSign);

        for (Map.Entry<String, String> entry : headersToSign.entrySet()) {
            dsRequest.addHeader(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : signatureResponse.getSignatureHeaders().entrySet()) {
            dsRequest.addHeader(entry.getKey(), entry.getValue());
        }

        dsRequest.setEntity(soapRequest.getBytes(), ContentType.parse(soapRequest.getContentType()));

        var signatureData = new SignatureData(signatureResponse.getSignatureDecoded(), null, null);

        MessageLog.log(soapRequest, signatureData, true, xRequestId);
        var response = EdcDataPlaneHttpClient.sendSoapRequest(dsRequest.build());
        MessageLog.log((SoapMessageImpl) response.soapMessage(), getSignatureFromHeaders(response.headers()), true, xRequestId);

        return response;
    }

    private SignatureData getSignatureFromHeaders(List<Header> headers) {
        for (Header header : headers) {
            if (header.getName().equals(HEADER_XRD_SIG)) {
                return new SignatureData(new String(Base64.getDecoder().decode(header.getValue())), null, null);
            }
        }
        return null;
    }

    private SignatureData getSignatureFromHeaders(Map<String, String> headers) {
        String encodedSignature = headers.get(HEADER_XRD_SIG);
        return new SignatureData(new String(Base64.getDecoder().decode(encodedSignature)),
                null, null);
    }

}
