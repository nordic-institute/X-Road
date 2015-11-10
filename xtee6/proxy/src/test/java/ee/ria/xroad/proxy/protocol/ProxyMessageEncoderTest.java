package ee.ria.xroad.proxy.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.jetty.http.MimeTypes;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CryptoUtils;

import static org.junit.Assert.*;

/**
 * Tests to verify correct proxy message encoder behavior.
 */
@Ignore(value = "Test data must be updated -- protocolVersion header field is required")
public class ProxyMessageEncoderTest {

    ByteArrayOutputStream out;
    ProxyMessageEncoder encoder;

    /**
     * Initialize.
     * @throws Exception in case of any unexpected errors
     */
    @Before
    public void initialize() throws Exception {
        out = new ByteArrayOutputStream();
        encoder = new ProxyMessageEncoder(out, getHashAlgoId());
    }

    /**
     * Test to ensure a normal message is encoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalMessage() throws Exception {
        SoapMessageImpl message = createMessage(getQuery("getstate.query"));
        SignatureData signature =
                new SignatureData(IOUtils.toString(getQuery("signature.xml")),
                        null, null);

        encoder.soap(message, new HashMap<String, String>());
        encoder.signature(signature);
        encoder.close();

        ProxyMessage proxyMessage = decode();

        // assert the required parts are there
        assertNotNull(proxyMessage.getSoap());
        assertNotNull(proxyMessage.getSignature());
        assertNull(proxyMessage.getFault());
        assertTrue(proxyMessage.getOcspResponses().isEmpty());
    }

    /**
     * Test to ensure a normal message with OCSP responses is encoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalMessageWithOcsp() throws Exception {
        SoapMessageImpl message = createMessage(getQuery("getstate.query"));
        SignatureData signature =
                new SignatureData(IOUtils.toString(getQuery("signature.xml")),
                        null, null);
        OCSPResp ocsp = getOcsp("src/test/queries/test.ocsp");

        encoder.ocspResponse(ocsp);
        encoder.soap(message, new HashMap<String, String>());
        encoder.signature(signature);
        encoder.close();

        ProxyMessage proxyMessage = decode();

        // assert the required parts are there
        assertFalse(proxyMessage.getOcspResponses().isEmpty());
        assertNotNull(proxyMessage.getSoap());
        assertNotNull(proxyMessage.getSignature());
        assertNull(proxyMessage.getFault());
    }

    /**
     * Test to ensure a normal message with an attachment is encoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalMessageWithAttachment() throws Exception {
        SoapMessageImpl message = createMessage(getQuery("getstate.query"));
        SignatureData signature =
                new SignatureData(IOUtils.toString(getQuery("signature.xml")),
                        null, null);

        InputStream attachment = new ByteArrayInputStream(
                "Hello, world!".getBytes(StandardCharsets.UTF_8));

        encoder.soap(message, new HashMap<String, String>());
        encoder.attachment(MimeTypes.TEXT_PLAIN, attachment, null);
        encoder.signature(signature);
        encoder.close();

        ProxyMessage proxyMessage = decode();

        // assert the required parts are there
        assertNotNull(proxyMessage.getSoap());
        assertNotNull(proxyMessage.getSignature());
    }

    /**
     * Test to ensure a SOAP fault message is encoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void faultMessage() throws Exception {
        SoapFault fault = createFault(getQuery("fault.query"));

        encoder.fault(fault);
        encoder.close();

        ProxyMessage proxyMessage = decode();

        // assert the required parts are there
        assertNotNull(proxyMessage.getFault());
    }

    /**
     * Test to ensure a normal message with fault instead of signature is encoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalMessageWithFaultInsteadOfSignature() throws Exception {
        SoapMessageImpl message = createMessage(getQuery("getstate.query"));
        SoapFault fault = createFault(getQuery("fault.query"));

        encoder.soap(message, new HashMap<String, String>());
        encoder.fault(fault);
        encoder.close();

        ProxyMessage proxyMessage = decode();

        // assert the required parts are there
        assertNotNull(proxyMessage.getSoap());
        assertNotNull(proxyMessage.getFault());
    }

    private ProxyMessage decode() throws Exception {
        ProxyMessage proxyMessage = new ProxyMessage("text/xml");
        ProxyMessageDecoder decoder =
                new ProxyMessageDecoder(proxyMessage, encoder.getContentType(),
                        getHashAlgoId());
        decoder.parse(new ByteArrayInputStream(out.toByteArray()));

        return proxyMessage;
    }

    private static SoapMessageImpl createMessage(InputStream is) throws Exception {
        Soap soap = new SoapParserImpl().parse(MimeTypes.TEXT_XML_UTF_8, is);
        if (soap instanceof SoapMessageImpl) {
            return (SoapMessageImpl) soap;
        }

        throw new RuntimeException(
                "Unexpected SOAP from parser: " + soap.getClass());
    }

    private static SoapFault createFault(InputStream is) throws Exception {
        Soap soap = new SoapParserImpl().parse(MimeTypes.TEXT_XML_UTF_8, is);
        if (soap instanceof SoapFault) {
            return (SoapFault) soap;
        }

        throw new RuntimeException(
                "Unexpected SOAP from parser: " + soap.getClass());
    }

    private static InputStream getQuery(String fileName) throws Exception {
        return new FileInputStream("src/test/queries/" + fileName);
    }

    private static OCSPResp getOcsp(String fileName) throws Exception {
        try (InputStream is = new FileInputStream(fileName)) {
            return new OCSPResp(IOUtils.toByteArray(is));
        }
    }

    private String getHashAlgoId() {
        return CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
    }
}
