package ee.ria.xroad.signer.tokenmanager.token;

public interface WorkerWithLifecycle {

    /**
     * Stops the worker and underlying connections, context, etc.
     */
    default void stop() {
        //NO-OP
    }

    /**
     * Start the worker. This should fully prepare the worker.
     */
    default void start() {
        //NO-OP
    }

    /**
     * Reloads the worker. Reloaded instance should be similar to newly initialized worker.
     */
    default void reload() {
        //NO-OP
    }

    /**
     * Refreshes underlying worker.
     */
    default void refresh() {
        //NO-OP
    }
}
