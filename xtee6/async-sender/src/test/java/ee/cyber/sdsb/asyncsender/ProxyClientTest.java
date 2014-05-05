package ee.cyber.sdsb.asyncsender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.util.StartStop;

import static ee.cyber.sdsb.asyncsender.TestUtils.getFaultMessage;
import static ee.cyber.sdsb.asyncsender.TestUtils.getSimpleMessage;

public class ProxyClientTest {

    private static ProxyClient client = ProxyClient.getInstance();
    private MockServer mockServer;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        client.start();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        client.stop();
        client.join();
    }

    @After
    public void tearDown() throws Exception {
        mockServer.stop();
        mockServer.join();
        mockServer = null;
    }

    @Test
    public void sendMessageAndExpectNoResponse() throws Exception {
        createMockServer(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                checkHeader(request);

                response.setContentType(MimeTypes.TEXT_PLAIN);
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
            }
        });

        client.send(MimeTypes.TEXT_XML, getSimpleMessage());
    }

    @Test
    public void sendMessageAndGetUnknownResponse() throws Exception {
        createMockServer(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                checkHeader(request);

                response.setContentType(MimeTypes.TEXT_PLAIN);
                response.setStatus(HttpServletResponse.SC_OK);

                response.getOutputStream().write(
                        "Hello world!".getBytes(StandardCharsets.UTF_8));

                baseRequest.setHandled(true);
            }
        });

        client.send(MimeTypes.TEXT_XML, getSimpleMessage());
    }

    @Test
    public void sendMessageAndGetFaultySoapResponse() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_SOAP);

        createMockServer(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                checkHeader(request);

                response.setContentType(MimeTypes.TEXT_XML);
                response.setStatus(HttpServletResponse.SC_OK);

                response.getOutputStream().write(
                        "Hello world!".getBytes(StandardCharsets.UTF_8));

                baseRequest.setHandled(true);
            }
        });

        client.send(MimeTypes.TEXT_XML, getSimpleMessage());
    }

    @Test
    public void sendMessageAndExpectFaultResponse() throws Exception {
        thrown.expectError(ErrorCodes.X_HTTP_ERROR);

        createMockServer(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest,
                    HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                checkHeader(request);
                try {
                    IOUtils.copy(getFaultMessage(
                            ErrorCodes.X_HTTP_ERROR, "bar", "baz", ""),
                                response.getOutputStream());
                    response.setContentType(MimeTypes.TEXT_XML);
                    response.setStatus(HttpServletResponse.SC_OK);
                } catch (Exception e) {
                    response.sendError(
                            HttpServletResponse.SC_NOT_FOUND, e.getMessage());
                } finally {
                    baseRequest.setHandled(true);
                }
            }
        });

        client.send(MimeTypes.TEXT_XML, getSimpleMessage());
    }

    private static void checkHeader(HttpServletRequest request) {
        if (request.getHeader(SoapMessageImpl.X_IGNORE_ASYNC) == null) {
            throw new CodedException(ErrorCodes.X_HTTP_ERROR,
                    "Request missing header " + SoapMessageImpl.X_IGNORE_ASYNC);
        }
    }

    private void createMockServer(AbstractHandler handler) throws Exception {
        mockServer = new MockServer(handler);
        mockServer.start();
    }

    private class MockServer implements StartStop {

        private final Server server;

        MockServer(AbstractHandler handler) {
            server = new Server();

            Connector connector = new SelectChannelConnector();
            connector.setPort(PortNumbers.CLIENT_HTTP_PORT);
            connector.setHost("127.0.0.1");
            server.addConnector(connector);

            server.setHandler(handler);
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
