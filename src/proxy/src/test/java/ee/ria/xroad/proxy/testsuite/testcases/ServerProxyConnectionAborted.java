/*
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
package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;

/**
 * Client sends normal message, SP aborts connection.
 * Result: CP responds with RequestFailed
 */
public class ServerProxyConnectionAborted extends MessageTestCase {
    private static final int STARTUP_DELAY = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(
            ServerProxyConnectionAborted.class);

    private Thread serverThread;

    /**
     * Constructs the test case.
     */
    public ServerProxyConnectionAborted() {
        requestFileName = "getstate.query";
    }

    @Override
    protected void startUp() throws Exception {
        serverThread = new Thread(new AbortingServer());
        serverThread.start();
        Thread.sleep(STARTUP_DELAY);
    }

    @Override
    protected void closeDown() throws Exception {
        serverThread.interrupt();
        serverThread.join();
    }

    @Override
    public String getProviderAddress(String providerName) {
        // We'll connect to local AbortingServer
        return "127.0.0.3";
    }

    @Override
    protected void validateFaultResponse(Message receivedResponse) {
        assertErrorCode(SERVER_CLIENTPROXY_X, X_NETWORK_ERROR);
    }

    private class AbortingServer implements Runnable {
        @Override
        public void run() {
            try {
                byte[] buffer = new byte[1024];
                int port = SystemProperties.getServerProxyPort();

                LOG.debug("Starting to listen at 127.0.0.3:{}", port);

                try (ServerSocket srvr = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.3"));
                     Socket skt = srvr.accept()) {

                    LOG.debug("Received connection from {}", skt.getRemoteSocketAddress());

                    // Read something.
                    skt.getInputStream().read(buffer);
                    //abort connection
                    LOG.debug("Closing the test socket");
                    skt.setSoLinger(true, 0);
                }
            } catch (Exception ex) {
                LOG.debug("Aborting server failed", ex);
            }
        }
    }
}
