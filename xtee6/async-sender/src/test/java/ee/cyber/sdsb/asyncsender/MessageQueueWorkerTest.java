package ee.cyber.sdsb.asyncsender;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;

import org.eclipse.jetty.http.MimeTypes;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import ee.cyber.sdsb.asyncdb.MessageQueue;
import ee.cyber.sdsb.asyncdb.QueueInfo;
import ee.cyber.sdsb.asyncdb.SendingCtx;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;

import static ee.cyber.sdsb.asyncsender.TestUtils.getSimpleMessage;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class MessageQueueWorkerTest {

    private volatile int messagesSent;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        messagesSent = 0;
    }

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

    private static final Date getDate(int min, int sec) {
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
