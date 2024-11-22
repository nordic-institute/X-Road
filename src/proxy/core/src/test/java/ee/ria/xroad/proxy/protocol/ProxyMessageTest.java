package ee.ria.xroad.proxy.protocol;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapMessageTestUtil;

import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ProxyMessageTest {

    public static final byte[] MOCK_SOAP_MESSAGE_BODY = "<mock-soap-message-body-xml/>".getBytes(UTF_8);

    @Test
    public void soapContentTypeMimeEncodedSoap() throws Exception {
        var originalContentType = "multipart/related";

        ProxyMessage message = new ProxyMessage(originalContentType);

        assertThat(message.getSoapContentType()).isEqualTo(originalContentType);
    }

    @Test
    public void soapContentTypeAttachment() throws Exception {
        var originalContentType = "original-content-type";

        ProxyMessage message = new ProxyMessage(originalContentType);
        message.attachment("application/octet-stream", new ByteArrayInputStream("attachment".getBytes(UTF_8)), Map.of());

        assertThat(message.getSoapContentType()).isEqualTo(originalContentType);
    }

    @Test
    public void soapContentTypeTextXml() throws Exception {
        var originalContentType = "application/xml";

        ProxyMessage message = new ProxyMessage(originalContentType);

        assertThat(message.getSoapContentType()).isEqualTo("text/xml; charset=UTF-8");
    }


    @Test
    public void soapContent() throws Exception {
        ProxyMessage message = new ProxyMessage("text/xml; charset=UTF-8");
        message.soap(getMockSoapMessage(), Map.of());

        assertThat(message.getSoapContent().readAllBytes())
                .withRepresentation(bytes -> new String((byte[]) bytes))
                .isEqualTo(MOCK_SOAP_MESSAGE_BODY);
    }

    @Test
    public void soapContentMime() throws Exception {
        var expectedSoapContent = """
                --BOUNDARY\r
                content-type: text/xml; charset=UTF-8\r
                \r
                <mock-soap-message-body-xml/>\r
                --BOUNDARY--\r
                """;

        ProxyMessage message = new ProxyMessage("multipart/related; boundary=BOUNDARY");
        message.soap(getMockSoapMessage(), Map.of());

        var soapContent = message.getSoapContent().readAllBytes();

        assertThat(soapContent)
                .withRepresentation(bytes -> new String((byte[]) bytes))
                .isEqualTo(expectedSoapContent.getBytes(UTF_8));
    }

    @Test
    public void soapContentWithAttachment() throws Exception {
        var expectedSoapContent = """
                --BOUNDARY\r
                content-type:text/xml; charset=UTF-8\r
                \r
                <mock-soap-message-body-xml/>\r
                --BOUNDARY\r
                content-type:text/plain\r
                \r
                attachment\r
                --BOUNDARY--\r
                """;

        ProxyMessage message = new ProxyMessage("multipart/related; boundary=BOUNDARY");
        message.soap(getMockSoapMessage(), Map.of());
        message.attachment("text/plain", new ByteArrayInputStream("attachment".getBytes(UTF_8)), Map.of());

        var soapConent = message.getSoapContent().readAllBytes();

        assertThat(soapConent)
                .withRepresentation(bytes -> new String((byte[]) bytes))
                .isEqualTo(expectedSoapContent.getBytes(UTF_8));
    }

    private SoapMessageImpl getMockSoapMessage() {
        var mockSoapMessage = Mockito.mock(SoapMessageImpl.class);
        when(mockSoapMessage.getContentType()).thenReturn("text/xml; charset=UTF-8");
        when(mockSoapMessage.getBytes()).thenReturn(MOCK_SOAP_MESSAGE_BODY);
        return mockSoapMessage;
    }
}
