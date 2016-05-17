package ee.ria.xroad.proxy.testsuite.testcases;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.util.Arrays;

import ee.ria.xroad.common.message.RequestHash;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

/**
 * Client sends message with attachment. Server responds with attachment. Both
 * messages and contents of the SOAP message part have the UTF-8 BOM bytes.
 * Result: all OK.
 */
@Slf4j
public class Utf8BomAttachment2 extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public Utf8BomAttachment2() {
        requestFileName = "attachm2-utf8bom-inside.query";
        requestContentType = "multipart/related; "
                + "boundary=jetty771207119h3h10dty";

        responseFile = "attachm2-utf8bom-inside.answer";
        responseContentType = "multipart/related; charset=UTF-8; "
                + "boundary=jetty771207119h3h10dty";
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        RequestHash requestHashFromResponse = ((SoapMessageImpl)
                receivedResponse.getSoap()).getHeader().getRequestHash();

        byte[] requestFileBytes = IOUtils.toByteArray(
                getRequestInput(false).getRight());
        byte[] requestSoapBytes = Arrays.copyOfRange(
                requestFileBytes, 64, 1156
                        + getQueryId().getBytes("UTF-8").length);

        byte[] requestHash = CryptoUtils.calculateDigest(
                CryptoUtils.getAlgorithmId(requestHashFromResponse
                        .getAlgorithmId()), requestSoapBytes);

        if (!Arrays.areEqual(requestHash, CryptoUtils.decodeBase64(
                requestHashFromResponse.getHash()))) {
            throw new RuntimeException(
                    "Request message hash does not match request message");
        }
    }
}
