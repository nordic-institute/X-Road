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
package org.niis.xroad.common.acme.spring.provider;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.AcmeProfileIdContext;
import org.niis.xroad.common.acme.provider.AcmeXroadHttpConnector;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.connector.DefaultConnection;
import org.shredzone.acme4j.connector.HttpConnector;

import java.io.IOException;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;

/**
 * Sends the {@code profile_id} header carried in the {@link AcmeProfileIdContext} for whichever ACME call is
 * currently in flight on this thread. Holds no CSR- or CA-list-derived state of its own: the caller ({@link
 * org.niis.xroad.common.acme.AcmeService}) resolves the profile id up front and scopes it via {@link
 * AcmeProfileIdContext#runWithProfileId}.
 */
@Slf4j
public class AcmeProfileIdConnection extends DefaultConnection {

    private static final String PROFILE_ID_HEADER_KEY = "profile_id";

    public AcmeProfileIdConnection(HttpConnector httpConnector) {
        super(httpConnector);
    }

    @Override
    protected void sendRequest(Session session, URL url, Consumer<HttpRequest.Builder> body) throws IOException {
        var builder = httpConnector.createRequestBuilder(url)
                .header("Accept-Charset", "utf-8")
                .header("Accept-Language", session.getLanguageHeader());

        if (session.networkSettings().isCompressionEnabled()) {
            builder.header("Accept-Encoding", "gzip");
        }

        AcmeProfileIdContext.current().ifPresent(profileId -> builder.setHeader("User-Agent",
                PROFILE_ID_HEADER_KEY + "=" + profileId + " " + AcmeXroadHttpConnector.XROAD_ACME_USER_AGENT));

        body.accept(builder);
        try {
            lastResponse = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            throw new IOException("Request was interrupted", ex);
        }
    }
}
