package ee.cyber.sdsb.asyncdb;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.asyncdb.messagequeue.MessageQueue;
import ee.cyber.sdsb.common.identifier.ClientId;

public class AsyncDB {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncDB.class);

    public static final String GLOBAL_LOCK_FILE_NAME = ".globallock";

    private static final AsyncDBProvider instance = new AsyncDBImpl();

    public static List<MessageQueue> getMessageQueues() throws Exception {
        LOG.info("Getting all message queues...");

        List<MessageQueue> messageQueues = instance.getMessageQueues();
        LOG.info("Got following message queues: '{}'", messageQueues);

        return messageQueues;
    }

    public static MessageQueue getMessageQueue(ClientId provider)
            throws Exception {
        LOG.info("Getting message queue for provider '{}'", provider);

        MessageQueue messageQueue = instance.getMessageQueue(provider);
        LOG.info("Got following message queue: '{}'", messageQueue);

        return messageQueue;
    }
}
