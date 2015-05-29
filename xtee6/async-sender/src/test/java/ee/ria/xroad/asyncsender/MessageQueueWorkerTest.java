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
