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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.niis.xroad.edc.sig.XrdSignatureService;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.util.HeaderValueUtils.HEADER_CONTENT_TYPE;

@Slf4j
public class XrdDataSpaceClient {
    private final XrdSignatureService xrdSignatureService = new XrdSignatureService();

    public EdcHttpResponse processRequest(XrdClientRequest xrdClientRequest,
                                          AuthorizedAssetRegistry.GrantedAssetInfo assetInfo) throws Exception {
        var path = assetInfo.endpoint();
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (xrdClientRequest.source() == XrdClientSource.REST) {
            if (xrdClientRequest.providerServicePath() != null) {
                path += xrdClientRequest.providerServicePath();
            }
            if (StringUtils.isNotBlank(xrdClientRequest.query())) {
                path += "?" + xrdClientRequest.query();
            }
        }
        final var dsRequest = ClassicRequestBuilder.create(xrdClientRequest.method()).setUri(new URI(path));

        Map<String, String> headersToSign = new HashMap<>(xrdClientRequest.headers());
        headersToSign.put(assetInfo.authKey(), assetInfo.authCode());

        var additionalHeaders = xrdSignatureService.sign(xrdClientRequest.clientId(), xrdClientRequest.body(), headersToSign);
        for (Map.Entry<String, String> entry : headersToSign.entrySet()) {
            dsRequest.addHeader(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : additionalHeaders.entrySet()) {
            dsRequest.addHeader(entry.getKey(), entry.getValue());
        }

        //handle body
        var method = xrdClientRequest.method();
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)
                || "DELETE".equals(method) || "OPTIONS".equals(method)) {
            // Attach body to the request
            var contentType = additionalHeaders.get(HEADER_CONTENT_TYPE);
            if (StringUtils.isBlank(contentType)) {
                contentType = switch (xrdClientRequest.source()) {
                    case REST -> "application/json";
                    case SOAP -> "text/xml";
                };
            }

            dsRequest.setEntity(xrdClientRequest.body(), ContentType.parse(contentType));
        }


        return EdcDataPlaneHttpClient.sendRequest(dsRequest.build());
    }

    public record XrdClientRequest(
            XrdClientSource source,
            String method,
            ClientId clientId,
            String query,
            Map<String, String> headers,
            byte[] body,
            ServiceId providerServiceId,
            String providerServicePath) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            XrdClientRequest that = (XrdClientRequest) o;

            return new EqualsBuilder()
                    .append(source, that.source)
                    .append(method, that.method)
                    .append(clientId, that.clientId)
                    .append(query, that.query)
                    .append(headers, that.headers)
                    .append(body, that.body)
                    .append(providerServiceId, that.providerServiceId)
                    .append(providerServicePath, that.providerServicePath)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .append(source)
                    .append(method)
                    .append(clientId)
                    .append(query)
                    .append(headers)
                    .append(body)
                    .append(providerServiceId)
                    .append(providerServicePath)
                    .toHashCode();
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("source", source)
                    .append("method", method)
                    .append("clientId", clientId)
                    .append("query", query)
                    .append("headers", headers)
                    .append("body", body)
                    .append("providerServiceId", providerServiceId)
                    .append("providerServicePath", providerServicePath)
                    .toString();
        }
    }


    public enum XrdClientSource {
        REST,
        SOAP
    }
}
