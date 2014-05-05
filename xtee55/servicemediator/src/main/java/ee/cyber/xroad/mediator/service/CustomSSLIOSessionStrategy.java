package ee.cyber.xroad.mediator.service;

import java.lang.reflect.Field;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.http.nio.protocol.HttpAsyncRequestExecutor.HTTP_HANDLER;

class CustomSSLIOSessionStrategy extends SSLIOSessionStrategy {

    private static final Logger LOG =
            LoggerFactory.getLogger(CustomSSLIOSessionStrategy.class);

    CustomSSLIOSessionStrategy(SSLContext sslcontext,
            X509HostnameVerifier hostnameVerifier) {
        super(sslcontext, hostnameVerifier);
    }

    @Override
    protected void verifySession(HttpHost host, IOSession iosession,
            SSLSession sslsession) throws SSLException {
        super.verifySession(host, iosession, sslsession);

        HttpContext ctx;
        try {
            ctx = getHttpContext(iosession);
        } catch (Exception e) {
            throw new SSLException(
                    "Unable to get HttpContext: " + e.getMessage());
        }

        try {
            Object v = ctx.getAttribute(ServerTrustVerifier.class.getName());
            if (v == null || !(v instanceof ServerTrustVerifier)) {
                throw new Exception(
                        "Unable to get ServerTrustManager from HttpContext");
            }

            ((ServerTrustVerifier) v).checkServerTrusted(ctx, sslsession);

            LOG.trace("SSL session verified successfully!");
        } catch (Exception e) {
            throw new SSLException(
                    "Unable verify session: " + e.getMessage());
        }
    }

    private static HttpContext getHttpContext(IOSession iosession)
            throws Exception {
        Object handler = iosession.getAttribute(HTTP_HANDLER);
        if (handler == null) {
            throw new Exception(HTTP_HANDLER + " not set in iosession");
        }

        // XXX: Hack to get the user HttpContext!
        // There seems to be no better way of getting the HttpContext
        // at this point other than via reflection.
        Field localContextField =
                handler.getClass().getDeclaredField("localContext");
        localContextField.setAccessible(true);

        Object ctx = localContextField.get(handler);
        if (ctx == null || !(ctx instanceof HttpContext)) {
            throw new Exception("HttpContext not available");
        }

        return (HttpContext) ctx;
    }
}
