package ee.cyber.sdsb.proxy.serverproxy;

import java.io.InputStream;

import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.proxy.protocol.ProxyMessage;

public interface ServiceHandler {

    boolean shouldVerifyAccess();
    boolean shouldVerifySignature();
    boolean shouldLogSignature();

    boolean canHandle(ServiceId requestServiceId, ProxyMessage requestMessage);

    void startHandling() throws Exception;
    void finishHandling() throws Exception;

    String getResponseContentType();
    InputStream getResponseContent() throws Exception;
}
