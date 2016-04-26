/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.messagelog;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.signature.SignatureData;

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
        String message;
        try (InputStream in =
                new FileInputStream("message.xml")) {
            message = IOUtils.toString(in);
        }

        return (SoapMessageImpl) new SoapParserImpl().parse(
                new ByteArrayInputStream(
                        message.getBytes(StandardCharsets.UTF_8)));
    }

    static SignatureData createTestSignature() throws Exception {
        String signature;
        try (InputStream in = new FileInputStream("signatures.xml")) {
            signature = IOUtils.toString(in);
        }

        String hc;
        try (InputStream in = new FileInputStream("hashchain.xml")) {
            hc = IOUtils.toString(in);
        }

        String hcr;
        try (InputStream in = new FileInputStream("hashchainresult.xml")) {
            hcr = IOUtils.toString(in);
        }

        return new SignatureData(signature, hcr, hc);
    }
}
