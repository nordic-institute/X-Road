package ee.ria.xroad.proxy.testsuite.testcases;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.Arrays;

import ee.ria.xroad.common.message.RequestHash;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * The simplest case -- normal message and normal response. Both messages
 * have the UTF-8 BOM bytes.
 * Result: client receives message.
 */
public class Utf8BomNormalSubsystem extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public Utf8BomNormalSubsystem() {
        requestFileName = "getstate-subsystem.query";
        responseFile = "getstate-subsystem.answer";

        addUtf8BomToRequestFile = true;
        addUtf8BomToResponseFile = true;
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {

        RequestHash requestHashFromResponse = ((SoapMessageImpl)
                receivedResponse.getSoap()).getHeader().getRequestHash();

        byte[] requestHash = CryptoUtils.calculateDigest(
                CryptoUtils.getAlgorithmId(
                        requestHashFromResponse.getAlgorithmId()),
                IOUtils.toByteArray(getRequestInput(
                        addUtf8BomToRequestFile).getRight()));

        if (!Arrays.areEqual(requestHash, CryptoUtils.decodeBase64(
                requestHashFromResponse.getHash()))) {
            throw new RuntimeException(
                    "Request message hash does not match request message");
        }
    }
}
