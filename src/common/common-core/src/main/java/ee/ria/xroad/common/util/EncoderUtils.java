/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.util;

import jakarta.xml.bind.DatatypeConverter;
import lombok.experimental.UtilityClass;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

@UtilityClass
public class EncoderUtils {

    /**
     * Creates a base 64 encoded string from the given input string.
     * @param input the value to encode
     * @return base 64 encoded string
     */
    public static String encodeBase64(String input) {
        return DatatypeConverter.printBase64Binary(
                input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a base 64 encoded string from the given input bytes.
     * @param input the value to encode
     * @return base 64 encoded string
     */
    public static String encodeBase64(byte[] input) {
        return DatatypeConverter.printBase64Binary(input);
    }

    /**
     * Decodes a base 64 encoded string into byte array.
     * @param base64Str the base64 encoded string
     * @return decoded byte array
     */
    public static byte[] decodeBase64(String base64Str) {
        return DatatypeConverter.parseBase64Binary(base64Str);
    }

    /**
     * Hex-encodes the given byte array.
     * @param data the value to encode
     * @return hex encoded String of the data
     */
    public static String encodeHex(byte[] data) {
        return new String(Hex.encode(data));
    }

}
