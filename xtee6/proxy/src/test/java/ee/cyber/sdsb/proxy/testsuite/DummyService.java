package ee.cyber.sdsb.proxy.testsuite;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.util.StartStop;


@SuppressWarnings("unchecked")
class DummyService extends Server implements StartStop {
    private static final Logger LOG = LoggerFactory.getLogger(
            DummyService.class);

    DummyService() {
        super(ProxyTestSuite.SERVICE_PORT);
        setHandler(new ServiceHandler());
    }

    private class ServiceHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            LOG.debug("Service simulator received request {}, contentType={}",
                    target, request.getContentType());
            try {
                // check if the test case implements custom service response
                AbstractHandler handler = currentTestCase().getServiceHandler();
                if (handler != null) {
                    handler.handle(target, baseRequest, request, response);
                    return;
                }

                Message receivedRequest = new Message(
                        request.getInputStream(), request.getContentType());

                String encoding = request.getCharacterEncoding();
                LOG.debug("Request: encoding={}, soap={}", encoding,
                        receivedRequest.getSoap());

                currentTestCase().onReceiveRequest(receivedRequest);

                String responseFile = currentTestCase().getResponseFile();
                if (responseFile != null) {
                    try {
                        sendResponseFromFile(responseFile, response);
                    } catch (Exception e) {
                        LOG.error("An error has occurred when sending response " +
                                "from file '{}': {}", responseFile, e);
                    }
                } else {
                    LOG.error("Unknown request {}", target);
                    // TODO: throw exception????
                }
            } catch (Exception ex) {
                // TODO: do something?
                LOG.error("Error when reading request", ex);
            } finally {
                baseRequest.setHandled(true);
            }
        }

        private void sendResponseFromFile(String fileName,
                HttpServletResponse response) throws Exception {
            String responseContentType =
                    currentTestCase().getResponseContentType();

            LOG.debug("Sending response, content-type = {}",
                    responseContentType);

            response.setContentType(responseContentType);
            response.setStatus(HttpServletResponse.SC_OK);

            String file = MessageTestCase.QUERIES_DIR + '/' + fileName;

            try (InputStream fileIs = new FileInputStream(file);
                    InputStream responseIs =
                            currentTestCase().changeQueryId(fileIs)) {
                IOUtils.copy(responseIs, response.getOutputStream());
            }

            try (InputStream fis = currentTestCase().changeQueryId(
                    new FileInputStream(file))) {
                currentTestCase().onSendResponse(
                        new Message(fis, responseContentType));
            } catch (Exception e) {
                LOG.error("Error when sending response from file '{}': {}",
                        file, e.toString());
            }
        }
    }

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }
}
