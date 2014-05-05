package ee.cyber.sdsb.asyncdb;

import ee.cyber.sdsb.common.message.SoapMessageConsumer;

/**
 * Provides necessary context for writing asynchronous request (with attachment)
 * to the database.
 */
public interface WritingCtx {
    /**
     * Returns object used for writing SOAP message and attachment into
     * temporary database branch.
     * 
     * @return
     */
    SoapMessageConsumer getConsumer();

    /**
     * Moves saved request into correct branch in the database.
     * 
     * @throws Exception
     */
    void commit() throws Exception;

    /**
     * Handles situation when writing request to database fails, recovering its
     * previous state.
     * 
     * @throws Exception
     */
    void rollback() throws Exception;
}
