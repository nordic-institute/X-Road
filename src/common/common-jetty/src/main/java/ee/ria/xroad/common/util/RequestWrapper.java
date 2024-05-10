/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ProxyInputStream;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Request;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;

public interface RequestWrapper {
    InputStream getInputStream();

    HttpURI getHttpURI();

    String getMethod();

    String getContentType();

    HttpFields getHeaders();

    Object getAttribute(String attributeName);

    String getParameter(String name) throws Exception;

    Map<String, String[]> getParametersMap() throws Exception;

    Optional<X509Certificate[]> getPeerCertificates();

    static RequestWrapper of(Request request) {
        var in = new ProxyInputStream(Request.asInputStream(request)) {
            @Override
            public void close() throws IOException {
                // Consume the input stream to the end before closing it.
                IOUtils.consume(in);
                super.close();
            }
        };

        return new RequestWrapper() {
            @Override
            public InputStream getInputStream() {
                return in;
            }

            @Override
            public HttpURI getHttpURI() {
                return request.getHttpURI();
            }

            @Override
            public String getMethod() {
                return request.getMethod();
            }

            @Override
            public String getContentType() {
                return JettyUtils.getContentType(request);
            }

            @Override
            public HttpFields getHeaders() {
                return request.getHeaders();
            }

            @Override
            public Object getAttribute(String attributeName) {
                return request.getAttribute(attributeName);
            }

            @Override
            public String getParameter(String name) throws Exception {
                return Request.getParameters(request).getValue(name);
            }

            @Override
            public Map<String, String[]> getParametersMap() throws Exception {
                return Request.getParameters(request).toStringArrayMap();
            }

            @Override
            public Optional<X509Certificate[]> getPeerCertificates() {
                var ssd = (EndPoint.SslSessionData) request.getAttribute(EndPoint.SslSessionData.ATTRIBUTE);
                return Optional.ofNullable(ssd)
                        .map(EndPoint.SslSessionData::peerCertificates);
            }
        };
    }
}
