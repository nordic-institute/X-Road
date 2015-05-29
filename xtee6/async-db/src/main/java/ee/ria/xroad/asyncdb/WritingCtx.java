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
