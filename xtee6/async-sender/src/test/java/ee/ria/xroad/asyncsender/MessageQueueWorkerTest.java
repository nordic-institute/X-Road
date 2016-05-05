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
package ee.ria.xroad.asyncsender;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;

import org.eclipse.jetty.http.MimeTypes;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import ee.ria.xroad.asyncdb.SendingCtx;
import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.asyncdb.messagequeue.QueueInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;

import static ee.ria.xroad.asyncsender.TestUtils.getSimpleMessage;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests message queue worker.
 */
public class MessageQueueWorkerTest {

    private volatile int messagesSent;

    /**
     * Before.
     * @throws Exception if an error occurs
     */
    @Before
    public void setUp() throws Exception {
        messagesSent = 0;
    }

    /**
     * Test.
     * @throws Exception if an error occurs
     */
    @Test
    public void sendTwoMessagesToProxy() throws Exception {
        MessageQueue queue = mock(MessageQueue.class);
        ClientId provider = ClientId.create("EE", "klass", "mockedQueue");

        QueueInfo info = mock(QueueInfo.class);
        when(info.getName()).thenReturn(provider);

        // Set up two messages with zero delay (we don't want the unit test
        // to take too much time).
        // Null date indicates there are no more messages.
        when(info.getNextAttempt()).thenReturn(
                getDate(0, 0), getDate(0, 0), (Date) null);

        when(queue.getQueueInfo()).thenReturn(info);

        assertEquals(provider, queue.getQueueInfo().getName());

        SendingCtx sendingCtx = mock(SendingCtx.class);
        when(sendingCtx.getContentType()).thenReturn(MimeTypes.TEXT_XML);
        when(sendingCtx.getInputStream()).thenReturn(getSimpleMessage());

        when(queue.startSending()).thenReturn(sendingCtx);

        final SoapMessageImpl sendResult =
                createSoapMessage(TestUtils.getSimpleMessage());

        doAnswer(getMessageSentAnswer()).when(sendingCtx).success(
                sendResult.getXml());

        MessageQueueWorker worker = spy(new MessageQueueWorker(queue));
        doNothing().when(worker).sleep();
        doReturn(sendResult).when(worker).sendMessage(sendingCtx);

        worker.run();

        assertEquals(2, messagesSent);
    }

    private Answer<Object> getMessageSentAnswer() {
        return new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation)
                    throws Throwable {
                messagesSent++;
                return null;
            }
        };
    }

    private static Date getDate(int min, int sec) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, min);
        cal.add(Calendar.SECOND, sec);
        Date time = cal.getTime();
        return time;
    }

    private static SoapMessageImpl createSoapMessage(InputStream is)
            throws Exception {
        Soap soap = new SoapParserImpl().parse(is);
        if (soap instanceof SoapMessageImpl) {
            return (SoapMessageImpl) soap;
        }

        throw new RuntimeException("Unexpected SOAP: " + soap.getClass());
    }
}
