package ee.cyber.sdsb.common;

import java.io.InputStream;
import java.net.URI;

import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.apache.http.HttpHeaders;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.util.AsyncHttpSender;

public class SingleRequestSender {

    private static final Logger LOG =
            LoggerFactory.getLogger(SingleRequestSender.class);

    private static final int CLIENT_TIMEOUT = 30; // seconds

    private static MessageFactory messageFactory = null;

    static {
        try {
            messageFactory = MessageFactory.newInstance();
        } catch (SOAPException e) {
            throw new RuntimeException(e);
        }
    }

    private CloseableHttpAsyncClient client;

    public SingleRequestSender(CloseableHttpAsyncClient client) {
        this.client = client;
    }

    public SOAPMessage sendRequestAndReceiveResponse(String address,
            String contentType, InputStream content) throws Exception {
        try (AsyncHttpSender sender = new AsyncHttpSender(client)) {
            sender.doPost(new URI(address), content, contentType);

            sender.waitForResponse(CLIENT_TIMEOUT);

            String responseContentType = sender.getResponseContentType();
            MimeHeaders mimeHeaders = getMimeHeaders(responseContentType);

            LOG.debug("Received response with content type {}",
                    responseContentType);

            return messageFactory.createMessage(mimeHeaders,
                    sender.getResponseContent());
        }
    }

    private static MimeHeaders getMimeHeaders(String contentType) {
        MimeHeaders mimeHeaders = new MimeHeaders();
        mimeHeaders.addHeader(HttpHeaders.CONTENT_TYPE, contentType);
        return mimeHeaders;
    }

}
