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
package ee.ria.xroad.common.hashchain;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reference resolver that is used when verifying hash chains.
 */
public interface HashChainReferenceResolver {

    /**
     * Retrieves given URI.
     * @param uri data location
     * @return input stream of the data at the given URI
     * @throws IOException if data could not be read from the given URI
     */
    InputStream resolve(String uri) throws IOException;

    /**
     * This method should return true, if the data reference with the given URI
     * should be resolved by this resolver. If this method returns false,
     * then this reference will not be resolved and hash values are not
     * verified.
     * @param uri URI of the data reference
     * @param digestValue hash of the data
     * @return true, if the data reference with the given URI
     * should be resolved by this resolver, false otherwise
     */
    boolean shouldResolve(String uri, byte[] digestValue);
}
