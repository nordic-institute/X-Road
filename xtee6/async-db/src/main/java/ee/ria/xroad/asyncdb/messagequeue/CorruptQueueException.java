package ee.ria.xroad.asyncdb.messagequeue;

/**
 * Exception thrown whenever queue is not readable in normal way, for example if
 * metadata is not written correctly for some reason. This situation may be
 * caused by technical limitations of environment
 * (for example lack of disk space).
 */
public class CorruptQueueException extends Exception {
    /**
     * Creates Exception.
     * n
     * @param message - exception message
     */
    public CorruptQueueException(String message) {
        super(message);
    }
}
