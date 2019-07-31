/**
 * The MIT License
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
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Handler for REST metadata services
 */
@Slf4j
public class RestMetadataHandler extends AbstractClientProxyHandler {

    /**
     * Constructor
     */
    public RestMetadataHandler(HttpClient client) {
        super(client, false);
    }

    @Override
    MessageProcessorBase createRequestProcessor(String target,
                                                HttpServletRequest request, HttpServletResponse response,
                                                OpMonitoringData opMonitoringData) throws Exception {
        log.trace("createRequestProcessor({})", target);

        if (target != null && target.startsWith("/r" + RestMessage.PROTOCOL_VERSION + "/")) {
            verifyCanProcess();
            RestMetadataClientRequestProcessor restProcessor
                    = new RestMetadataClientRequestProcessor(request, response);
            if (restProcessor.canProcess()) {
                log.trace("Processing with RestMetadataClientRequestProcessor");
                return restProcessor;
            }
        }
        return null;
    }

    private void verifyCanProcess() {
        GlobalConf.verifyValidity();

        if (!SystemProperties.isSslEnabled()) {
            return;
        }

        AuthKey authKey = KeyConf.getAuthKey();
        if (authKey.getCertChain() == null) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Security server has no valid authentication certificate");
        }
    }
}
