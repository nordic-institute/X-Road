package ee.ria.xroad.proxy.messagelog;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.MimeTypes;

import static ee.ria.xroad.proxy.messagelog.TestUtil.createMessage;
import static ee.ria.xroad.proxy.messagelog.TestUtil.createSignature;

/**
 * Messagelog integration test program.
 */
public class MessageLogIntegrationTest extends AbstractMessageLogTest {

    /**
     * Main program access point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        new MessageLogIntegrationTest().run();
    }

    void run() throws Exception {
        try {
            timestampAsynchronously();
            //timestampSynchronously();

            startArchiving();

            awaitTermination();
        } finally {
            testTearDown();
        }
    }

    void timestampAsynchronously() throws Exception {
        testSetUp(false);

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
    }

    void timestampSynchronously() throws Exception {
        testSetUp(true);

        log(createMessage(), createSignature());
        log(createMessage(), createSignature());
        //log(createTestMessage(), createTestSignature());
    }

    @Override
    protected void testSetUp(boolean timestampImmediately) throws Exception {
        TestUtil.initForTest();

        System.setProperty(MessageLogProperties.ARCHIVE_PATH, "build/slog");
        System.setProperty(MessageLogProperties.ARCHIVE_INTERVAL,
                "0 0/2 * 1/1 * ? *");

        new File("build/slog/").mkdirs();

        super.testSetUp(timestampImmediately);

        initLogManager();
    }

    static SoapMessageImpl createTestMessage() throws Exception {
        try (InputStream in = new FileInputStream("message.xml")) {
            return (SoapMessageImpl) new SoapParserImpl().parse(
                    MimeTypes.TEXT_XML_UTF_8, in);
        }
    }

    static SignatureData createTestSignature() throws Exception {
        return new SignatureData(
            FileUtils.readFileToString(new File("signatures.xml")),
            FileUtils.readFileToString(new File("hashchain.xml")),
            FileUtils.readFileToString(new File("hashchainresult.xml"))
        );
    }
}
