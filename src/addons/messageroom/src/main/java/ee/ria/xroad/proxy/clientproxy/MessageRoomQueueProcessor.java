/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.MessageRoomSubscriptionType;

import akka.actor.UntypedAbstractActor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * MessageRoomQueueProcessor
 */
@Slf4j
public class MessageRoomQueueProcessor extends UntypedAbstractActor {

    @Override
    public void onReceive(Object o) throws Exception {
        if (o instanceof String) {
            process();
        } else {
            log.error("received unhandled message {}", o);
            unhandled(o);
        }
    }

    private void process() throws Exception {
        log.debug("Process Message Room messages");

        // Get next unprocessed Message Rooms message from the queue
        MessageRoomRequest msg = MessageRoomQueue.getInstance().get();

        while (msg != null) {
            log.debug("Process message to Message Room: {}", msg.getMessageRoomId().toString());

            // Get list of subscribers from the DB
            List<MessageRoomSubscriptionType> subscriptions =
                    ServerConf.getMessageRoomSubscriptions(msg.getMessageRoomId());
            log.debug("Subscriptions count: {}", subscriptions.size());

            // Send message to every subscriber
            for (MessageRoomSubscriptionType subscription: subscriptions) {
                log.debug("Send Message Room message to: {}", subscription.getSubscriberServiceId());
                MessageRoomRequestSender messageRoomRequestSender =
                        new MessageRoomRequestSender(msg, subscription.getSubscriberServiceId());
                messageRoomRequestSender.process();
            }

            // Get next unprocessed Message Rooms message from the queue
            msg = MessageRoomQueue.getInstance().get();
        }
        log.debug("Processing Message Room messages completed");
    }
}
