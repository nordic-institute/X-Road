package ee.cyber.sdsb.common.signer;

import java.nio.charset.StandardCharsets;

import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.protocol.message.SignResponse;

import static ee.cyber.sdsb.common.util.CryptoUtils.calculateDigest;

public class SignerHelper {

    /**
     * Returns a base64 encoded signature.
     */
    public static String sign(String keyId, String algorithmId, String data)
            throws Exception {
        byte[] tbsData = data.getBytes(StandardCharsets.UTF_8);
        byte[] digest = calculateDigest(algorithmId, tbsData);

        SignResponse response =
                SignerClient.execute(new Sign(keyId, algorithmId, digest));

        return CryptoUtils.encodeBase64(response.getSignature());
}
}
