package ee.ria.xroad.common.util;

/**
 * Interface for classes that require internal threads.
 */
public interface StartStop {

    /**
     * Start internal threads.
     * @throws Exception in case of any errors
     */
    void start() throws Exception;

    /**
     * Stop internal threads.
     * @throws Exception in case of any errors
     */
    void stop() throws Exception;

    /**
     * Join internal threads.
     * @throws InterruptedException if any internal thread gets interrupted
     */
    void join() throws InterruptedException;
}
