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

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.messagelog.MessageLog;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.http.message.BasicHeader;
import org.niis.xroad.edc.sig.XrdSignatureService;

import java.net.URI;
import java.util.Base64;

import static ee.ria.xroad.common.message.RestRequest.Verb.DELETE;
import static ee.ria.xroad.common.message.RestRequest.Verb.OPTIONS;
import static ee.ria.xroad.common.message.RestRequest.Verb.PATCH;
import static ee.ria.xroad.common.message.RestRequest.Verb.POST;
import static ee.ria.xroad.common.message.RestRequest.Verb.PUT;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;

@Slf4j
@Deprecated(forRemoval = true)
public class XrdDataSpaceClient {
    private final XrdSignatureService xrdSignatureService;
    private final GlobalConfProvider globalConfProvider;
    private final KeyConfProvider keyConfProvider;

    public XrdDataSpaceClient(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider, CertChainFactory certChainFactory) {
        this.globalConfProvider = globalConfProvider;
        this.keyConfProvider = keyConfProvider;
        this.xrdSignatureService = new XrdSignatureService(globalConfProvider, certChainFactory, null);
    }

    public RestResponse processRestRequest(RestRequest restRequest,
                                           AuthorizedAssetRegistry.GrantedAssetInfo assetInfo, byte[] requestDigest) throws Exception {

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

        var encodedDigest = requestDigest != null ? encodeBase64(requestDigest) : null;
        var signatureResponse = xrdSignatureService.sign(restRequest.getClientId(), restRequest.getMessageBytes(),
                encodedDigest);


        final var dsRequest = ClassicRequestBuilder.create(restRequest.getVerb().name()).setUri(new URI(path));
        dsRequest.addHeader(HEADER_XRD_SIG, signatureResponse.getSignature());
        dsRequest.addHeader("Authorization", assetInfo.authCode());
        restRequest.getHeaders().forEach(header -> dsRequest.addHeader(header.getName(), header.getValue()));

        MessageLog.log(restRequest, toSignatureData(signatureResponse.getSignature()),
                restRequest.getBody() != null ? restRequest.getBody().getCachedContents() : null,
                true, restRequest.getXRequestId());

        //handle body
        var method = restRequest.getVerb();
        if (POST.equals(method) || PUT.equals(method) || PATCH.equals(method)
                || DELETE.equals(method) || OPTIONS.equals(method)) {
            // Attach body to the request
            if (restRequest.getBody() != null) {
                var cachedStream = restRequest.getBody().getCachedContents();
                dsRequest.setEntity(new InputStreamEntity(cachedStream, cachedStream.size(), ContentType.APPLICATION_JSON));
            }
        }

        var response = EdcDataPlaneHttpClient.sendRestRequest(dsRequest.build(), restRequest, globalConfProvider, keyConfProvider);

        MessageLog.log(restRequest, response, toSignatureData(response.getSignature()),
                response.getBody().getCachedContents(), true,
                restRequest.getXRequestId());
        response.getHeaders().add(new BasicHeader(HEADER_XRD_SIG, response.getSignature()));
        return response;
    }

    public EdcDataPlaneHttpClient.EdcSoapWrapper processSoapRequest(SoapMessageImpl soapRequest, String xRequestId,
                                                                    AuthorizedAssetRegistry.GrantedAssetInfo assetInfo) throws Exception {
        var path = assetInfo.endpoint();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        var signatureResponse = xrdSignatureService.sign(soapRequest.getClient(), soapRequest.getBytes());

        final var dsRequest = ClassicRequestBuilder.post(new URI(path))
                .addHeader("Authorization", assetInfo.authCode())
                .addHeader(HEADER_XRD_SIG, signatureResponse.getSignature())
                .setEntity(soapRequest.getBytes(), ContentType.parse(soapRequest.getContentType()));
//      todo if needed
//      MessageLog.log(soapRequest, toSignatureData(signatureResponse.getSignature()), true, xRequestId);
        var response = EdcDataPlaneHttpClient.sendSoapRequest(dsRequest.build(), globalConfProvider, keyConfProvider);
//      MessageLog.log((SoapMessageImpl) response.soapMessage(), toSignatureData(response.headers().get(HEADER_XRD_SIG)), true, xRequestId);
        return response;
    }

    private SignatureData toSignatureData(String signature) {
        return new SignatureData(new String(Base64.getDecoder().decode(signature)));
    }

}
