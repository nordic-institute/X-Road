package ee.cyber.sdsb.common.request;

import java.io.InputStream;

import ee.cyber.sdsb.common.message.SoapMessageImpl;

public interface ManagementRequest {

    SoapMessageImpl getRequestMessage();

    InputStream getRequestContent() throws Exception;

    String getRequestContentType();

    String getResponseContentType();

}
