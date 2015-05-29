package ee.cyber.xroad.mediator.service.wsdlmerge.structure;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Covers classes {@link Message} and {@link MessagePart}.
 */
public class MessageBehavior {
    private static final String XRD_NS = "http://x-road.eu/xsd/x-road.xsd";

    /**
     * Tests XML representation of message element.
     *
     * @throws IOException thrown if message element cannot be turned into XML.
     */
    @Test
    public void shouldTurnMessageWithElementIntoXml() throws IOException {
        // Given
        MessagePart consumerPart = new MessagePart(
                "consumer",
                new QName(XRD_NS, "consumer"),
                null);
        MessagePart producerPart = new MessagePart(
                "producer",
                new QName(XRD_NS, "producer"),
                null);

        Message message = new Message(
                "standardheader",
                Arrays.asList(consumerPart, producerPart),
                true);

        // When
        String actualXml = message.getXml();

        // Then
        String expectedXml = FileUtils.readFileToString(new File(
                "src/test/resources/structure/message-withElement.expected"))
                .trim();

        assertEquals(expectedXml, actualXml);
    }

    /**
     * Tests that message with referenced type can be turned into XML.
     *
     * @throws IOException thrown if message element cannot be turned into XML.
     */
    @Test
    public void shouldTurnMessageWithTypeIntoXml() throws IOException {
        // Given
        MessagePart consumerPart = new MessagePart(
                "consumer",
                null,
                new QName(XRD_NS, "consumer"));
        MessagePart producerPart = new MessagePart(
                "producer",
                null,
                new QName(XRD_NS, "producer"));

        Message message = new Message(
                "standardheader",
                Arrays.asList(consumerPart, producerPart),
                true);

        // When
        String actualXml = message.getXml();

        // Then
        String expectedXml = FileUtils.readFileToString(new File(
                "src/test/resources/structure/message-withType.expected"))
                .trim();

        assertEquals(expectedXml, actualXml);
    }
}
