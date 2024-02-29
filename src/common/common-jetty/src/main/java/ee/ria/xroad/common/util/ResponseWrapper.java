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

import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Response;

import java.io.OutputStream;

public interface ResponseWrapper {
    OutputStream getOutputStream();

    void setStatus(int status);
    void setContentLength(int length);
    void setContentType(String contentType);
    void setContentType(MimeTypes.Type contentType);
    void setContentType(String contentType, String charset);

    void putHeader(String headerName, String headerValue);
    void addHeader(String headerName, String headerValue);

    static ResponseWrapper of(Response response) {
        var out = Content.Sink.asOutputStream(response);

        return new ResponseWrapper() {
            @Override
            public OutputStream getOutputStream() {
                return out;
            }

            @Override
            public void setStatus(int status) {
                response.setStatus(status);
            }

            @Override
            public void setContentLength(int length) {
                JettyUtils.setContentLength(response, length);
            }

            @Override
            public void setContentType(String contentType) {
                JettyUtils.setContentType(response, contentType);
            }

            @Override
            public void setContentType(MimeTypes.Type contentType) {
                JettyUtils.setContentType(response, contentType);
            }

            @Override
            public void setContentType(String contentType, String charset) {
                JettyUtils.setContentType(response, contentType, charset);
            }

            @Override
            public void putHeader(String headerName, String headerValue) {
                response.getHeaders().put(headerName, headerValue);
            }

            @Override
            public void addHeader(String headerName, String headerValue) {
                response.getHeaders().add(headerName, headerValue);
            }
        };
    }
}
