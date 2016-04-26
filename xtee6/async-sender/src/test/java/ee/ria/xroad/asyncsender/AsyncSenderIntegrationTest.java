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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import ee.ria.xroad.asyncdb.SendingCtx;
import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.asyncdb.messagequeue.QueueInfo;
import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.StartStop;

import static ee.ria.xroad.asyncsender.TestUtils.getSimpleMessage;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test class.
 */
public final class AsyncSenderIntegrationTest {

    private AsyncSenderIntegrationTest() {
    }

    /**
     * Entry point.
     * @param args arguments
     * @throws Exception if an error occurs
     */
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        System.setProperty(SystemProperties.ASYNC_DB_PATH, "build");

        DummyProxy proxy = new DummyProxy();
        proxy.start();

        // Create Queue 1 -----------------------------------------------------

        final NextAttempt attempts1 =
                new NextAttempt(getDate(0, 5), getDate(0, 12));

        QueueInfo info1 = mock(QueueInfo.class);
        when(info1.getName()).thenReturn(createServiceId("mockedQueue1"));

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return attempts1.getCurrent();
            }
        }).when(info1).getNextAttempt();

        MessageQueue queue1 = mock(MessageQueue.class);
        when(queue1.getQueueInfo()).thenReturn(info1);

        SendingCtx sendingCtx1 = createSendingCtx();
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                attempts1.next();
                return null;
            }
        }).when(sendingCtx1).success(any(String.class));

        // Create Queue 2 -----------------------------------------------------

        final NextAttempt attempts2 = new NextAttempt(getDate(0, 10));

        QueueInfo info2 = mock(QueueInfo.class);
        when(info2.getName()).thenReturn(createServiceId("mockedQueue2"));

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return attempts2.getCurrent();
            }
        }).when(info2).getNextAttempt();

        MessageQueue queue2 = mock(MessageQueue.class);
        when(queue2.getQueueInfo()).thenReturn(info2);

        SendingCtx sendingCtx2 = createSendingCtx();
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                attempts2.next();
                return null;
            }
        }).when(sendingCtx2).success(any(String.class));

        when(queue1.startSending()).thenReturn(sendingCtx1);
        when(queue2.startSending()).thenReturn(sendingCtx2);

        List<MessageQueue> queues = Arrays.asList(queue1, queue2);

        AsyncSender sender = Mockito.mock(AsyncSender.class);
        when(sender.getMessageQueues()).thenReturn(
                queues, new ArrayList<MessageQueue>());

        sender.startUp(true);

        proxy.stop();
        proxy.join();
    }

    private static ClientId createServiceId(String name) {
        return ClientId.create("EE", "klass", name);
    }

    private static SendingCtx createSendingCtx() throws Exception {
        SendingCtx sendingCtx = mock(SendingCtx.class);
        when(sendingCtx.getContentType()).thenReturn(MimeTypes.TEXT_XML);

        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return getSimpleMessage();
            }
        }).when(sendingCtx).getInputStream();

        return sendingCtx;
    }

    private static Date getDate(int min, int sec) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, min);
        cal.add(Calendar.SECOND, sec);
        return cal.getTime();
    }

    private static class NextAttempt {

        private final Date[] attempts;
        private int currentIdx = 0;

        NextAttempt(Date... attempts) {
            this.attempts = attempts;
        }

        Date getCurrent() {
            return currentIdx < attempts.length ? attempts[currentIdx] : null;
        }

        void next() {
            currentIdx++;
        }
    }

    private static class DummyProxy implements StartStop {

        private final Server server;

        DummyProxy() {
            server = new Server();

            Connector connector = new SelectChannelConnector();
            connector.setPort(PortNumbers.CLIENT_HTTP_PORT);
            connector.setHost("127.0.0.1");
            server.addConnector(connector);

            server.setHandler(new AbstractHandler() {
                @Override
                public void handle(String target, Request baseRequest,
                        HttpServletRequest request, HttpServletResponse response)
                        throws IOException, ServletException {
                    response.setContentType(MimeTypes.TEXT_XML);
                    response.setStatus(HttpServletResponse.SC_OK);

                    try (InputStream responseMessage =
                            TestUtils.getSimpleMessage()) {
                        IOUtils.copy(responseMessage,
                                response.getOutputStream());
                    } catch (Exception e) {
                        response.sendError(
                                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                e.getMessage());
                    } finally {
                        baseRequest.setHandled(true);
                    }
                }
            });
        }

        @Override
        public void start() throws Exception {
            server.start();
        }

        @Override
        public void stop() throws Exception {
            server.stop();
        }

        @Override
        public void join() throws InterruptedException {
            server.join();
        }
    }
}
