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
package ee.ria.xroad.asyncdb;

import java.io.InputStream;

/**
 * Manages process of sending asyncronous request: provides necessary data for
 * sending and manages post-sending activities.
 */
public interface SendingCtx {

    /**
     * Returns input stream of the request to be sent. Input stream is closed by
     * either 'success()' or 'failure()' method.
     *
     * @return - input stream of the request
     */
    InputStream getInputStream();

    /**
     * Returns content type of the request to be sent.
     *
     * @return - content type of the request
     */
    String getContentType();

    /**
     * After request is sent successfully starts modifying underlying queue
     * accordingly. At first, closes the input stream provided by method
     * 'getInputStream()'.
     *
     * @param lastSendResult - result of last sent message.
     * @throws Exception - thrown when cannot handle sending success.
     */
    void success(String lastSendResult) throws Exception;

    /**
     * After request sending has been failed, modifies underlying queue
     * accordingly. At first, closes the input stream provided by method
     * 'getInputStream()'.
     *
     * @param fault - fault string.
     * @param lastSendResult - result of last sent message.
     *
     * @throws Exception - thrown when cannot handle failure.
     */
    void failure(String fault, String lastSendResult) throws Exception;
}
