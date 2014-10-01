package ee.cyber.xroad.mediator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;

import ee.cyber.sdsb.common.util.AsyncHttpSender;

public class MockSender extends AsyncHttpSender {

    public MockSender() {
        super(null);
    }

    @Override
    public void doPost(URI address, final InputStream content,
            long contentLength, String contentType) throws Exception {
        // Simply consume the content
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    IOUtils.copy(content, new NullOutputStream());
                } catch (IOException ignored) {
                }
            }
        }).start();
    }

    @Override
    public void waitForResponse(int timeoutSeconds) throws Exception {
    }

    @Override
    public void close() {
    }

    public static MockSender create(final String responseContentType,
            final InputStream responseInputStream) {
        return new MockSender() {
            @Override
            public InputStream getResponseContent() {
                return responseInputStream;
            }

            @Override
            public String getResponseContentType() {
                return responseContentType;
            }
        };
    }
}
