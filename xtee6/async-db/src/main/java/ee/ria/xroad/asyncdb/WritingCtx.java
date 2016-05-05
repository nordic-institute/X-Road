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

import ee.ria.xroad.common.message.SoapMessageConsumer;

/**
 * Provides necessary context for writing asynchronous request (with attachment)
 * to the database.
 */
public interface WritingCtx {
    /**
     * Returns object used for writing SOAP message and attachment into
     * temporary database branch.
     *
     * @return - SOAP message callback
     */
    SoapMessageConsumer getConsumer();

    /**
     * Moves saved request into correct branch in the database.
     *
     * @throws Exception - if moving request into correct branch fails.
     */
    void commit() throws Exception;

    /**
     * Handles situation when writing request to database fails, recovering its
     * previous state.
     *
     * @throws Exception - if rollback fails
     */
    void rollback() throws Exception;
}
