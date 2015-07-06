package ee.ria.xroad.asyncsender;

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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.message.SoapUtils;
import ee.ria.xroad.common.util.StartStop;

import static ee.ria.xroad.asyncsender.TestUtils.getFaultMessage;
import static ee.ria.xroad.asyncsender.TestUtils.getSimpleMessage;

/**
 * Tests proxy client.
 */
public class ProxyClientTest {

    private static ProxyClient client = ProxyClient.getInstance();
    private MockServer mockServer;

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * BeforeClass.
     * @throws Exception if an error occurs.
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        System.setProperty(SystemProperties.PROXY_CLIENT_HTTP_PORT, "8080");

        client.start();
    }

    /**
     * AfterClass.
     * @throws Exception if an error occurs.
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        client.stop();
        client.join();
    }

    /**
     * After.
     * @throws Exception if an error occurs.
     */
    @After
    public void tearDown() throws Exception {
        mockServer.stop();
        mockServer.join();
        mockServer = null;
    }

    /**
     * Test.
     * @throws Exception if an error occurs
     */
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

    /**
     * Test.
     * @throws Exception if an error occurs
     */
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

    /**
     * Test.
     * @throws Exception if an error occurs
     */
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

    /**
     * Test.
     * @throws Exception if an error occurs
     */
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
        if (request.getHeader(SoapUtils.X_IGNORE_ASYNC) == null) {
            throw new CodedException(ErrorCodes.X_HTTP_ERROR,
                    "Request missing header " + SoapUtils.X_IGNORE_ASYNC);
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
            connector.setPort(SystemProperties.getClientProxyHttpPort());
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
