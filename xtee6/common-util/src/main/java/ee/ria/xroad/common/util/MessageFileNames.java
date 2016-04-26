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

/**
 * Constants and methods that codify how to generate file names
 * for storing messages, attachments and hash chains.
 */
public final class MessageFileNames {

    private MessageFileNames() {
    }

    /** Name of the file containing hash chain. */
    public static final String SIG_HASH_CHAIN = "/sig-hashchain.xml";

    /** Name of the file containing hash chain result. */
    public static final String SIG_HASH_CHAIN_RESULT =
            "/sig-hashchainresult.xml";

    /** Name of the file containing hash chain. */
    public static final String TS_HASH_CHAIN = "/ts-hashchain.xml";

    /** Name of the file containing hash chain result. */
    public static final String TS_HASH_CHAIN_RESULT =
            "/ts-hashchainresult.xml";

    /** Name of the file containing SOAP message. */
    public static final String MESSAGE = "/message.xml";

    /** Name of the file containing SOAP message. */
    public static final String SIGNATURE = "/META-INF/signatures.xml";

    /**
     *  Name of the file containing idx-th attachment.
     *  The attachments are numbered starting from 1.
     *  @param idx index of attachment
     *  @return String
     */
    public static String attachment(int idx) {
        return "/attachment" + idx;
    }
}
