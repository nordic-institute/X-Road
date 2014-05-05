package ee.cyber.sdsb.asyncdb;

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
     * @return
     */
    InputStream getInputStream();

    /**
     * Returns content type of the request to be sent.
     * 
     * @return
     */
    String getContentType();

    /**
     * After request is sent successfully starts modifying underlying queue
     * accordingly. At first, closes the input stream provided by method
     * 'getInputStream()'.
     * 
     * @throws Exception
     */
    void success(String lastSendResult) throws Exception;

    /**
     * After request sending has been failed, modifies underlying queue
     * accordingly. At first, closes the input stream provided by method
     * 'getInputStream()'.
     * 
     * @throws Exception
     */
    void failure(String fault, String lastSendResult) throws Exception;
}
