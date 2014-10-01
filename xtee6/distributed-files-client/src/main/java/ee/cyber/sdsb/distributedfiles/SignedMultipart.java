package ee.cyber.sdsb.distributedfiles;

import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;

import static ee.cyber.sdsb.common.util.CryptoUtils.decodeBase64;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_CONTENT_TYPE;
import static ee.cyber.sdsb.common.util.MimeUtils.HEADER_SIG_ALGO_ID;

@Getter
@Slf4j
class SignedMultipart extends AbstractMultipartContentHandler {

    private byte[] signedData;
    private String signedDataContentType;

    private byte[] signatureValue;
    private String signatureAlgoId;

    SignedMultipart(MimeStreamParser parser) {
        super(parser);
    }

    public void verifyParts() throws Exception {
        if (signedData == null) {
            throw new IllegalStateException("Signed data missing");
        }

        if (signatureValue == null) {
            throw new IllegalStateException("Signature value missing");
        }

        if (signatureAlgoId == null) {
            throw new IllegalStateException("Signature algorithm missing");
        }
    }

    @Override
    public void body(BodyDescriptor bd, InputStream is)
            throws MimeException, IOException {
        if (signedData == null) {
            log.trace("Reading signed data");

            signedDataContentType = getHeader(HEADER_CONTENT_TYPE);
            signedData = IOUtils.toByteArray(is);
        } else if (signatureValue == null) {
            log.trace("Reading signature value");

            signatureAlgoId = getHeader(HEADER_SIG_ALGO_ID);
            signatureValue = decodeBase64(IOUtils.toString(is));
        }
    }
}
