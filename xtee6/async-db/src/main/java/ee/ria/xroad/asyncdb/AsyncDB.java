package ee.ria.xroad.asyncdb;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.common.identifier.ClientId;

/**
 * Provides queues for asynchonous messages.
 */
public final class AsyncDB {
    private AsyncDB() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(AsyncDB.class);

    public static final String GLOBAL_LOCK_FILE_NAME = ".globallock";

    private static final AsyncDBProvider DB_PROVIDER = new AsyncDBImpl();

    /**
     * Returns queues for all providers that have asynchronous messages pending
     *
     * @return - queues for all providers.
     * @throws Exception - when there is error getting message queues
     */
    public static List<MessageQueue> getMessageQueues() throws Exception {
        LOG.info("Getting all message queues...");

        List<MessageQueue> messageQueues = DB_PROVIDER.getMessageQueues();
        LOG.info("Got following message queues: '{}'", messageQueues);

        return messageQueues;
    }

    /**
     * Returns queue of asynchronous messages for one particular service provider.
     *
     * @param provider - service provider to get queue for.
     * @return - message queue for given service provider
     * @throws Exception - when message queue for the provider cannot be got
     */
    public static MessageQueue getMessageQueue(ClientId provider)
            throws Exception {
        LOG.info("Getting message queue for provider '{}'", provider);

        MessageQueue messageQueue = DB_PROVIDER.getMessageQueue(provider);
        LOG.info("Got following message queue: '{}'", messageQueue);

        return messageQueue;
    }
}
