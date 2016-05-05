/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.monitor.test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.ConfigFactory;

/**
 * Test client for monitoring
 */
public final class MonitorTest {

    public static final int TIMES = 100;
    public static final int WAIT_SECONDS = 10;
    public static final int MS_IN_SECOND = 1000;

    /**
     * Program entry point
     */
    public static void main(String args[]) {

        ActorSystem actorSystem = ActorSystem.create("AkkaRemoteClient", ConfigFactory.load());
        ActorRef client = actorSystem.actorOf(Props.create(ClientActor.class));

        for (int i = 0; i < TIMES; i++) {
            client.tell("Start", ActorRef.noSender());
        }
        waitXSeconds(WAIT_SECONDS);

        actorSystem.shutdown();
    }

    private MonitorTest() {
    }

    private static void waitXSeconds(long x) {
        try {
            Thread.sleep(x * MS_IN_SECOND);
        } catch (InterruptedException e) {
            System.out.println("InterruptedException occurred while thread was sleeping");
        }
    }
}
