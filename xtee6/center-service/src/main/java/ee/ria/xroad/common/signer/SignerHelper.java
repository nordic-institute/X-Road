package ee.ria.xroad.common.signer;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;

import static ee.ria.xroad.common.util.CryptoUtils.*;

/**
 * Contains utility methods for interacting with the signer.
 */
@Slf4j
public final class SignerHelper {

    private SignerHelper() {
    }

    /**
     * @return a base64 encoded signature.
     * @param keyId signing key ID
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param data the data to sign
     * @throws Exception in case of any errors
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
     * @return a base64 encoded signature.
     * @param keyId signing key ID
     * @param signatureAlgorithmId ID of the signature algorithm to use
     * @param data the data to sign
     * @throws Exception in case of any errors
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
