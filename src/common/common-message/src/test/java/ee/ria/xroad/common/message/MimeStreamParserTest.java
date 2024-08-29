package ee.ria.xroad.common.message;

import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class MimeStreamParserTest {

    /**
     * Passes with mime4j 0.8.7, fails with 0.8.8+
     * Mime4j 0.8.8+ has a bug, that causes it to not detect multipart boundary correctly.
     * With specific lengths of the part, the boundary is not detected correctly and '\r' that is part
     * of the '\r\n' before the boundary marker is included in the extracted part. Bug only occurs when
     * '\r\n' is used as a line separator.
     */
    @Test
    void shouldExtractPart() throws MimeException, IOException {
        for (int i = 4000; i < 4100; i++) {
            var part = "a".repeat(i);
            var mimeMessage = createMimeMultipart(part);
            var extracted = extractPart(mimeMessage, "testBoundary");
            assertEquals(part, extracted, () ->
                    String.format("Expected part with length %d but got %d", part.length(), extracted.length()));
        }
    }

    private String createMimeMultipart(String part) {
        return "--testBoundary\r\n"
                + "Content-Type: text/plain\r\n"
                + "\r\n"
                + part
                + "\r\n"
                + "--testBoundary--\r\n";
    }

    private String extractPart(String mimeMessage, String boundary) throws MimeException, IOException {
        var resultWrapper = new Object() { String result; };

        var config = new MimeConfig.Builder()
                .setHeadlessParsing("multipart/mixed; charset=UTF-8; boundary=" + boundary)
                .build();
        var parser = new MimeStreamParser(config);
        parser.setContentHandler(new AbstractContentHandler() {
            @Override
            public void body(BodyDescriptor bd, InputStream is) throws MimeException, IOException {
                resultWrapper.result = new String(is.readAllBytes());
            }
        });
        parser.parse(new ByteArrayInputStream(mimeMessage.getBytes()));
        return resultWrapper.result;
    }
}
