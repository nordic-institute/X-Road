package ee.cyber.sdsb.proxy.testsuite;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.PortNumbers;
import ee.cyber.sdsb.common.util.StartStop;

@SuppressWarnings("unchecked")
class DummyServerProxy extends Server implements StartStop {
    private static final Logger LOG = LoggerFactory.getLogger(
            DummyServerProxy.class);

    DummyServerProxy() {
        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setName("ClientConnector");
        connector.setHost("127.0.0.2");
        connector.setPort(PortNumbers.PROXY_PORT);

        addConnector(connector);
        setHandler(new ServiceHandler());
    }

    private class ServiceHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            LOG.debug("Proxy simulator received request {}, contentType={}",
                    target, request.getContentType());

            // check if the test case implements custom service response
            AbstractHandler handler = currentTestCase().getServerProxyHandler();
            if (handler != null) {
                handler.handle(target, baseRequest, request, response);
                return;
            }

            // Read all of the request (and copy it to /dev/null).
            IOUtils.copy(request.getInputStream(), new NullOutputStream());

            if (currentTestCase().getResponseFile() != null) {
                createResponseFromFile(currentTestCase().getResponseFile(),
                        baseRequest, response);
            } else {
                LOG.error("Unknown request {}", target);
            }
        }

        private void createResponseFromFile(String fileName, Request baseRequest,
                HttpServletResponse response) {
            String file = MessageTestCase.QUERIES_DIR + '/' + fileName;
            try {
                response.setContentType(
                        currentTestCase().getResponseContentType());
                response.setStatus(HttpServletResponse.SC_OK);
                try (InputStream fileIs = new FileInputStream(file);
                        InputStream responseIs =
                                currentTestCase().changeQueryId(fileIs)) {
                    IOUtils.copy(responseIs, response.getOutputStream());
                }
            } catch (FileNotFoundException e) {
                LOG.error("Could not find answer file: " + file, e);
                return;
            } catch (Exception e) {
                LOG.error("An error has occured when sending response " +
                        "from file " + file, e);
            }

            baseRequest.setHandled(true);
        }
    }

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }
}
