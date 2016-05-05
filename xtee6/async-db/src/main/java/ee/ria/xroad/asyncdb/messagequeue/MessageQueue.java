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
package ee.ria.xroad.asyncdb.messagequeue;

import java.util.List;

import ee.ria.xroad.asyncdb.SendingCtx;
import ee.ria.xroad.asyncdb.WritingCtx;

/**
 * Represents branch related to one particular provider in asynchronous requests
 * database.
 */
public interface MessageQueue {
    String METADATA_FILE_NAME = "metadata";
    String LOCK_FILE_NAME = ".lockfile";

    String MESSAGE_FILE_NAME = "message";
    String CONTENT_TYPE_FILE_NAME = "contenttype";

    /**
     * Initiates process of adding request to the queue and returns everything
     * needed to perform adding.
     *
     * @return - context necessary for writing into DB.
     * @throws Exception - thrown when start of writing fails.
     */
    WritingCtx startWriting() throws Exception;

    /**
     * Initiates sending process of the request and returns everything needed to
     * perform sending.
     *
     * @return - context necessary for sending request.
     * @throws Exception - thrown when start of sending fails.
     */
    SendingCtx startSending() throws Exception;

    /**
     * Marks request with particular ID as removed.
     *
     * @param requestId - id of request to be marked as removed.
     * @throws Exception - thrown if marking fails.
     */
    void markAsRemoved(String requestId) throws Exception;

    /**
     * Inverse operation to 'markAsRemoved().
     *
     * @param requestId - id of request to be restored.
     * @throws Exception - thrown if restore fails.
     */
    void restore(String requestId) throws Exception;

    /**
     * Resets request sending count.
     *
     * @throws Exception - thrown if resetting count fails.
     */
    void resetCount() throws Exception;

    /**
     * Returns brief info about the queue.
     *
     * @return - queue info briefly.
     * @throws Exception - if getting queue info fails.
     */
    QueueInfo getQueueInfo() throws Exception;

    /**
     * Returns list with requests ordered by orderNo of request.
     *
     * @return - list of requests under the queue.
     * @throws Exception - thrown if getting requests fails.
     */
    List<RequestInfo> getRequests() throws Exception;
}
