package ee.cyber.sdsb.proxy.clientproxy;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.HttpClient;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.globalconf.AuthKey;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.util.MessageProcessorBase;

import static ee.cyber.sdsb.common.ErrorCodes.X_INVALID_HTTP_METHOD;
import static ee.cyber.sdsb.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Handles client messages. This handler must be the last handler in the
 * handler collection, since it will not pass handling of the request to
 * the next handler (i.e. throws exception instead), if it cannot process
 * the request itself.
 */
class ClientMessageHandler extends AbstractClientProxyHandler {

    ClientMessageHandler(HttpClient client) {
        super(client);
    }

    @Override
    MessageProcessorBase createRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response)
                    throws Exception {
        verifyCanProcess(request);

        return new ClientMessageProcessor(request, response, client,
                getClientCert(request));
    }

    private void verifyCanProcess(HttpServletRequest request) {
        if (!isPostRequest(request)) {
            throw new ClientException(X_INVALID_HTTP_METHOD,
                    "Must use POST request method instead of %s",
                    request.getMethod());
        }

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
