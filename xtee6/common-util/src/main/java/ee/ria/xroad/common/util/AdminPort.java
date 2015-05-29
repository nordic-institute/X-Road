package ee.ria.xroad.common.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.servlet.http.HttpServletResponse.*;

/**
 * Service that listens for administrative commands on a specific port.
 */
public class AdminPort implements StartStop {

    /**
     * Asynchronous AdminPort callback interface.
     */
    public interface AsynchronousCallback extends Runnable {
    }

    /**
     * Synchronous AdminPort callback interface.
     */
    public interface SynchronousCallback extends Runnable {
    }

    public static final String REQUEST_STOP = "/stop";

    private static final Logger LOG = LoggerFactory.getLogger(AdminPort.class);

    private static final String CONNECTOR_HOST = "127.0.0.1";

    private final int portNumber;

    private final Server server = new Server();

    private final Map<String, Runnable> handlers = new HashMap<>();

    /**
     * Constructs an AdminPort instance that listens for commands on the given port number.
     * @param portNumber the port number AdminPort will listen on
     */
    public AdminPort(int portNumber) {
        this.portNumber = portNumber;

        createAdminConnector();
    }

    @Override
    public void start() throws Exception {
        LOG.info("Started AdminPort on port {}", portNumber);

        server.start();
    }

    @Override
    public void join() throws InterruptedException {
        if (server.getThreadPool() != null) {
            server.join();
        }
    }

    @Override
    public void stop() throws Exception {
        server.stop();
    }

    /**
     * Registers the "stop" command on this AdminPort.
     * @param handler callback to be executed when the "stop" command is issued
     */
    public void addStopHandler(AsynchronousCallback handler) {
        addHandler(REQUEST_STOP, handler);
    }

    /**
     * Registers a shutdown hook to be executed when the application shuts down.
     * @param hook the runnable that should be run when the application shuts down
     */
    public void addShutdownHook(Runnable hook) {
        Runtime.getRuntime().addShutdownHook(new Thread(hook));
    }

    /**
     * Registers a synchronous callback for the given command string.
     * @param target the command string
     * @param handler the synchronous callback that should be executed when the command is issued
     */
    public void addHandler(String target, SynchronousCallback handler) {
        handlers.put(target, handler);
    }

    /**
     * Registers an asynchronous callback for the given command string.
     * @param target the command string
     * @param handler the asynchronous callback that should be executed when the command is issued
     */
    public void addHandler(String target, AsynchronousCallback handler) {
        handlers.put(target, handler);
    }

    private void createAdminConnector() {
        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setName("AdminPort");
        connector.setHost(CONNECTOR_HOST);
        connector.setPort(portNumber);

        server.addConnector(connector);

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.addHandler(new AdminHandler());

        server.setHandler(handlerCollection);
    }

    private class AdminHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest,
                HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            if (!CONNECTOR_HOST.equals(request.getRemoteAddr())) {
                response.setStatus(SC_FORBIDDEN);
                baseRequest.setHandled(true);
                return;
            }

            LOG.info("Admin request: {}", target);
            try {
                Runnable handler = handlers.get(target);
                if (handler != null) {
                    if (handler instanceof SynchronousCallback) {
                        handler.run();
                    } else {
                        new Thread(handler).start();
                    }
                } else {
                    response.setStatus(SC_NOT_FOUND);
                }
            } catch (Exception e) {
                LOG.error("Handler got error", e);

                response.setStatus(SC_INTERNAL_SERVER_ERROR);
                IOUtils.copy(new StringReader(e.toString()),
                        response.getOutputStream());
            } finally {
                baseRequest.setHandled(true);
            }
        }
    }
}
