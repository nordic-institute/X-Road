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

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * MessageRoomQueue
 */
@Slf4j
@SuppressWarnings("checkstyle:FinalClass")
public class MessageRoomQueue {

    private final ConcurrentLinkedQueue<MessageRoomRequest> queue;
    private static MessageRoomQueue instance;

    private MessageRoomQueue() {
        this.queue = new ConcurrentLinkedQueue<>();
    }

    public static synchronized MessageRoomQueue getInstance() {
        if (instance == null) {
            instance = new MessageRoomQueue();
        }
        return instance;
    }

    public void add(MessageRoomRequest message) throws InterruptedException {
        log.debug("Put new message into Message Room queue");
        queue.add(message);
    }

    public MessageRoomRequest get() throws InterruptedException {
        if (queue.isEmpty()) {
            log.debug("Message Room queue is empty");
            return null;
        }
        log.debug("Read message from Message Room queue");
        return queue.remove();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
