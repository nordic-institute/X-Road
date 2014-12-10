package ee.cyber.sdsb.common.signer;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.protocol.message.SignResponse;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;

@Slf4j
public class SignerHelper {

    /**
     * Returns a base64 encoded signature.
     */
    public static String sign(String keyId, String signatureAlgorithmId,
            String data) throws Exception {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);
        String digestAlgorithmId = getDigestAlgorithmId(signatureAlgorithmId);

        byte[] tbsData = data.getBytes(StandardCharsets.UTF_8);
        byte[] digest = calculateDigest(digestAlgorithmId, tbsData);

        SignResponse response =
                SignerClient.execute(
                        new Sign(keyId, digestAlgorithmId, digest));

        return encodeBase64(response.getSignature());
    }

    /**
     * Returns a base64 encoded signature.
     */
    public static String sign(String keyId, String signatureAlgorithmId,
            byte[] data) throws Exception {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);
        String digestAlgorithmId = getDigestAlgorithmId(signatureAlgorithmId);

        byte[] digest = calculateDigest(digestAlgorithmId, data);

        SignResponse response =
                SignerClient.execute(
                        new Sign(keyId, digestAlgorithmId, digest));

        return encodeBase64(response.getSignature());
    }
}
