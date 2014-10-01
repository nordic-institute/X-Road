package ee.cyber.sdsb.asyncdb.messagequeue;

import java.util.List;

import ee.cyber.sdsb.asyncdb.SendingCtx;
import ee.cyber.sdsb.asyncdb.WritingCtx;

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
     * @return
     * @throws Exception
     */
    WritingCtx startWriting() throws Exception;

    /**
     * Initiates sending process of the request and returns everything needed to
     * perform sending.
     *
     * @return
     * @throws Exception
     */
    SendingCtx startSending() throws Exception;

    void markAsRemoved(String requestId) throws Exception;

    /**
     * Inverse operation to 'markAsRemoved().
     *
     * @param requestId
     * @throws Exception
     */
    void restore(String requestId) throws Exception;

    /**
     * Resets request sending count.
     *
     * @throws Exception
     */
    void resetCount() throws Exception;

    QueueInfo getQueueInfo() throws Exception;

    /**
     * Returns list with requests ordered by orderNo of request.
     *
     * @return
     * @throws Exception
     */
    List<RequestInfo> getRequests() throws Exception;
}
