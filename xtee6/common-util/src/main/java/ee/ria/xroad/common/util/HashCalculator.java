package ee.ria.xroad.common.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.util.CryptoUtils.*;

/**
 * Calculates hash values according to the provided algorithm URI.
 */
@Slf4j
@RequiredArgsConstructor
public class HashCalculator {

    @Getter
    private final String algoURI;

    /**
     * Calculates hash value in base64 format.
     * @param data input data from which to calculate the hash
     * @return the calculated hash String
     * @throws Exception in case of any errors
     */
    public String calculateFromString(String data) throws Exception {
        log.trace("Calculating digest with algorithm URI '{}' for data:\n{}",
                algoURI, data);

        return calculateFromBytes(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Calculates hash value in base64 format.
     * @param data input data from which to calculate the hash
     * @return the calculated hash String
     * @throws Exception in case of any errors
     */
    public String calculateFromBytes(byte[] data) throws Exception {
        String algoId = getAlgorithmId(algoURI);
        byte[] hashBytes = calculateDigest(algoId, data);
        return encodeBase64(hashBytes);
    }

    /**
     * Calculates hash value in base64 format.
     * @param data input stream containing data from which to calculate the hash
     * @return the calculated hash String
     * @throws Exception in case of any errors
     */
    public String calculateFromStream(InputStream data) throws Exception {
        String algoId = getAlgorithmId(algoURI);
        byte[] hashBytes = calculateDigest(algoId, data);
        return encodeBase64(hashBytes);
    }

}
