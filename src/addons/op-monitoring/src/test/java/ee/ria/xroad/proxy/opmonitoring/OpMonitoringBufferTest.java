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
package ee.ria.xroad.proxy.opmonitoring;

import ee.ria.xroad.common.opmonitoring.OpMonitoringData;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests operational monitoring buffer.
 */
public class OpMonitoringBufferTest {
    private static final ActorSystem ACTOR_SYSTEM = ActorSystem.create();

    private static class TestOpMonitoringBuffer extends OpMonitoringBuffer {
        TestOpMonitoringBuffer() throws Exception {
            super();
        }

        @Override
        CloseableHttpClient createHttpClient() throws Exception {
            return null;
        }

        @Override
        ActorRef createSender() {
            return null;
        }

        @Override
        protected void store(OpMonitoringData data) throws Exception {
            buffer.put(getNextBufferIndex(), data);
        }
    }

    @Test
    public void bufferOverflow() throws Exception {
        System.setProperty("xroad.op-monitor-buffer.size", "2");

        final Props props = Props.create(TestOpMonitoringBuffer.class);
        final TestActorRef<TestOpMonitoringBuffer> testActorRef =
                TestActorRef.create(ACTOR_SYSTEM, props, "testActorRef");

        TestOpMonitoringBuffer opMonitoringBuffer =
                testActorRef.underlyingActor();

        OpMonitoringData opMonitoringData = new OpMonitoringData(
                OpMonitoringData.SecurityServerType.CLIENT, 100);

        opMonitoringBuffer.store(opMonitoringData);
        opMonitoringBuffer.store(opMonitoringData);
        opMonitoringBuffer.store(opMonitoringData);

        assertEquals(2, opMonitoringBuffer.buffer.size());
        assertEquals(true, opMonitoringBuffer.buffer.containsKey(2L));
        assertEquals(true, opMonitoringBuffer.buffer.containsKey(3L));
    }
}
