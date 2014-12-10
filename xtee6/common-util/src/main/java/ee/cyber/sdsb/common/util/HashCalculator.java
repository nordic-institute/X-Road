package ee.cyber.sdsb.common.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static ee.cyber.sdsb.common.util.CryptoUtils.*;

@Slf4j
@RequiredArgsConstructor
public class HashCalculator {

    @Getter
    private final String algoURI;

    /**
     * Calculates hash value in base64 format.
     */
    public String calculateFromString(String data) throws Exception {
        log.trace("Calculating digest with algorithm URI '{}' for data:\n{}",
                algoURI, data);

        return calculateFromBytes(data.getBytes(StandardCharsets.UTF_8));
    }

    public String calculateFromBytes(byte[] data) throws Exception {
        String algoId = getAlgorithmId(algoURI);
        byte[] hashBytes = calculateDigest(algoId, data);
        return encodeBase64(hashBytes);
    }

    public String calculateFromStream(InputStream data) throws Exception {
        String algoId = getAlgorithmId(algoURI);
        byte[] hashBytes = calculateDigest(algoId, data);
        return encodeBase64(hashBytes);
    }

}
