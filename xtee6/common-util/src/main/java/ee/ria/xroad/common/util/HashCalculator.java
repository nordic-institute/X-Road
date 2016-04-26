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
