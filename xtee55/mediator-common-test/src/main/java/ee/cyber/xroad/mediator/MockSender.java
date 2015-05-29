package ee.cyber.xroad.mediator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;

import ee.ria.xroad.common.util.AsyncHttpSender;

/**
 * HTTP sender implementation that does not actually send anything.
 */
@Slf4j
public class MockSender extends AsyncHttpSender {

    /**
     * Construct a new mock sender.
     */
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
                    log.trace("Error while consuming data");
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

    /**
     * Factory method for creating a mock sender with the specified response
     * content type and input stream.
     * @param responseContentType the content type
     * @param responseInputStream the input stream
     * @return the created mock sender
     */
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
