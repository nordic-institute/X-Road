package ee.ria.xroad.asyncdb;

import java.util.List;

import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.common.identifier.ClientId;

interface AsyncDBProvider {

    /**
     * Returns message queues for all providers.
     *
     * @return - all message queues
     * @throws Exception
     */
    List<MessageQueue> getMessageQueues() throws Exception;

    /**
     * Returns message queue for particular provider
     *
     * @param provider - provider to get message queue for
     * @return - message queue for the provider
     * @throws Exception
     */
    MessageQueue getMessageQueue(ClientId provider) throws Exception;
}
