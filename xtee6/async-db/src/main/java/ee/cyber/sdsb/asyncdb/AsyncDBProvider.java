package ee.cyber.sdsb.asyncdb;

import java.util.List;

import ee.cyber.sdsb.common.identifier.ClientId;

public interface AsyncDBProvider {

    /**
     * Returns message queues for all providers.
     * 
     * @return
     * @throws Exception
     */
    List<MessageQueue> getMessageQueues() throws Exception;

    /**
     * Returns message queue for particular provider
     * 
     * @param provider
     * @return
     * @throws Exception
     */
    MessageQueue getMessageQueue(ClientId provider) throws Exception;
}
