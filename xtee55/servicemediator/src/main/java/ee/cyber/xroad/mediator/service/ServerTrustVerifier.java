package ee.cyber.xroad.mediator.service;

import javax.net.ssl.SSLSession;

import org.apache.http.protocol.HttpContext;

interface ServerTrustVerifier {

    void checkServerTrusted(HttpContext httpContext, SSLSession session)
            throws Exception;

}
