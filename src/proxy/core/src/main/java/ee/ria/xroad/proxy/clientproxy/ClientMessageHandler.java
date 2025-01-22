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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import org.apache.http.client.HttpClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.auth.AuthKey;
import org.niis.xroad.serverconf.ServerConfProvider;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HTTP_METHOD;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Handles client messages. This handler must be the last handler in the
 * handler collection, since it will not pass handling of the request to
 * the next handler (i.e. throws exception instead), if it cannot process
 * the request itself.
 */
class ClientMessageHandler extends AbstractClientProxyHandler {

    ClientMessageHandler(GlobalConfProvider globalConfProvider,
                         KeyConfProvider keyConfProvider,
                         ServerConfProvider serverConfProvider,
                         CertChainFactory certChainFactory, HttpClient client) {
        super(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory, client, true);
    }

    @Override
    MessageProcessorBase createRequestProcessor(
            RequestWrapper request, ResponseWrapper response,
            OpMonitoringData opMonitoringData) throws Exception {
        verifyCanProcess(request);

        return new ClientMessageProcessor(globalConfProvider, keyConfProvider, serverConfProvider, certChainFactory,
                request, response, client, getIsAuthenticationData(request), opMonitoringData);
    }

    private void verifyCanProcess(RequestWrapper request) {
        if (!isPostRequest(request)) {
            throw new ClientException(X_INVALID_HTTP_METHOD,
                    "Must use POST request method instead of %s",
                    request.getMethod());
        }

        globalConfProvider.verifyValidity();

        if (!SystemProperties.isSslEnabled()) {
            return;
        }

        AuthKey authKey = keyConfProvider.getAuthKey();
        if (authKey.certChain() == null) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Security server has no valid authentication certificate");
        }
    }
}
