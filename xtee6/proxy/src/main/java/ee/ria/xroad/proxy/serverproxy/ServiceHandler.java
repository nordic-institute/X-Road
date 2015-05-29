package ee.ria.xroad.proxy.serverproxy;

import java.io.InputStream;

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.protocol.ProxyMessage;

interface ServiceHandler {

    boolean shouldVerifyAccess();
    boolean shouldVerifySignature();
    boolean shouldLogSignature();

    boolean canHandle(ServiceId requestServiceId, ProxyMessage requestMessage);

    void startHandling() throws Exception;
    void finishHandling() throws Exception;

    String getResponseContentType();
    InputStream getResponseContent() throws Exception;
}
