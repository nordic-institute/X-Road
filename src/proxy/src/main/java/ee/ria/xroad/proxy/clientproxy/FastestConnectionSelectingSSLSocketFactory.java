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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.proxy.clientproxy.FastestSocketSelector.SocketInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.channels.UnresolvedAddressException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Enumeration;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;
import static ee.ria.xroad.proxy.clientproxy.AuthTrustVerifier.verify;

/**
 * This is a custom SSL socket factory that connects to the fastest target
 * address given a list of target addresses.
 *
 * The fastest target address is selected by initializing socket connection
 * to all provided addresses and choosing the first one to respond.
 *
 * If an SSL session already exists to one of the provided addresses, then
 * that address is selected immediately without previous selection algorithm.
 */
@Slf4j
class FastestConnectionSelectingSSLSocketFactory
        extends SSLConnectionSocketFactory {

    /**
     * The identifier of target addresses for the HttpContext attributes map.
     */
    public static final String ID_TARGETS =
            "ee.ria.xroad.serverproxy.targets";
    /**
     * The timeout when connecting to previously selected "fastest" provider
     */
    public static final int CACHED_TIMEOUT = 5000;

    /**
     * The identifier for the selected target address stored in the created SSL
     * session that is later used to determine the previously existing session.
     */
    private static final String ID_SELECTED_TARGET =
            "ee.ria.xroad.serverproxy.selectedtarget";

    private static final String ID_SELECTED_TARGET_TIMESTAMP =
            "ee.ria.xroad.serverproxy.selectedtargettimestamp";

    private final javax.net.ssl.SSLSocketFactory socketfactory;

    private final SSLContext sslContext;

    FastestConnectionSelectingSSLSocketFactory(SSLContext sslContext,
            String[] supportedCipherSuites) {
        super(sslContext, null, supportedCipherSuites, null);
        this.sslContext = sslContext;
        this.socketfactory = sslContext.getSocketFactory();
    }

    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        // create dummy socket that will be discarded
        return new Socket();
    }

    @Override
    public Socket connectSocket(int timeout, Socket socket, HttpHost host,
            InetSocketAddress remoteAddress, InetSocketAddress localAddress,
            HttpContext context) throws IOException {
        // Read target addresses from the context.
        URI[] addressesFromContext = getAddressesFromContext(context);
        log.trace("addresses from context {} current thread id {}", addressesFromContext,
                Thread.currentThread().getId());
        URI[] addresses = addressesFromContext;
        URI cachedSSLSessionURI = null;

        // Read from SSL session cache
        CachedSSLSessionHostInformation cachedHostInfo = getCachedSSLSessionHostURI(addresses);

        // If URI cache is enabled, check if we find the fastest provider address there
        if (SystemProperties.getClientProxyFastestConnectingSslUriCachePeriod() > 0) {
            log.trace("URI cache enabled, session {}", cachedHostInfo.getSslSession());
            cachedSSLSessionURI = cachedHostInfo.getSelectedAddress();
            if (cachedHostInfo.getSslSession() != null
                    && cachedHostInfo.getSslSession().getValue(ID_SELECTED_TARGET_TIMESTAMP) != null) {
                // Check if the stored URI is still valid
                LocalDateTime timestamp =
                        (LocalDateTime) cachedHostInfo.getSslSession().getValue(ID_SELECTED_TARGET_TIMESTAMP);
                LocalDateTime expire = timestamp
                        .plusSeconds(SystemProperties.getClientProxyFastestConnectingSslUriCachePeriod());
                if (LocalDateTime.now().isAfter(expire)) {
                    log.trace("URI cache expired");
                    cachedSSLSessionURI = null;
                    cachedHostInfo.clearCachedURIForSession();
                }
                if (cachedSSLSessionURI != null) {
                    // Success, use the cached URI
                    log.trace("Use cached URI {}", cachedSSLSessionURI);
                    addresses = new URI[] {cachedSSLSessionURI};
                }
            }
        }

        // Select the fastest address if more than one address is provided.
        SocketInfo selectedSocket = connect(addresses, context, cachedSSLSessionURI == null ? timeout : CACHED_TIMEOUT);
        if (selectedSocket == null) {
            if (cachedSSLSessionURI != null) {
                // could not connect to cached host, try all others.
                // .. and make sure the previous "fastest" host does not come up as "fastest" anymore
                log.trace("Could not connect to {}, clearing the URI cache", addresses);
                cachedSSLSessionURI = null;
                cachedHostInfo.clearCachedURIForSession();

                log.trace("Continue connecting to all providers {}", addressesFromContext);
                selectedSocket = connect(
                    //swap the failed address to the last
                    swap(addressesFromContext, 0, addressesFromContext.length - 1), context, timeout);
                if (selectedSocket == null) {
                    throw couldNotConnectException(addresses);
                }
            } else {
                throw couldNotConnectException(addresses);
            }
        }

        log.trace("Connected to {}", selectedSocket.getUri());

        configureSocket(selectedSocket.getSocket());

        updateOpMonitoringData(context, selectedSocket);

        SSLSocket sslSocket = wrapToSSLSocket(selectedSocket.getSocket());
        prepareAndVerify(sslSocket, selectedSocket.getUri(), context);

        // Store the fastest provider URI to SSL cache
        if (SystemProperties.getClientProxyFastestConnectingSslUriCachePeriod() > 0 && cachedSSLSessionURI == null) {
            log.trace("Store the fastest provider URI to SSL cache {}", selectedSocket.getUri());
            sslSocket.getSession().putValue(ID_SELECTED_TARGET, selectedSocket.getUri());
            sslSocket.getSession().putValue(ID_SELECTED_TARGET_TIMESTAMP, LocalDateTime.now());
        }

        return sslSocket;
    }

    private static <T> T[] swap(final T[] array, final int first, final int second) {
        final T tmp = array[first];
        array[first] = array[second];
        array[second] = tmp;
        return array;
    }

    private static void updateOpMonitoringData(HttpContext context,
            SocketInfo socketInfo) {
        try {
            OpMonitoringData opMonitoringData = (OpMonitoringData) context
                    .getAttribute(OpMonitoringData.class.getName());

            if (opMonitoringData != null) {
                opMonitoringData.setServiceSecurityServerAddress(
                        socketInfo.getUri().getHost());
            }
        } catch (Exception e) {
            log.error("Failed to assign op monitoring data field {}",
                    OpMonitoringData.SERVICE_SECURITY_SERVER_ADDRESS, e);
        }
    }

    /**
     * Configures socket with any options needed.
     * <p>
     * Normally, the client using this factory would call {@link #createSocket(HttpContext)} and then configure the
     * socket. And indeed, it ({@link org.apache.http.impl.conn.DefaultHttpClientConnectionOperator}) does.
     * However, that socket is thrown away in {@link #connectSocket(int, Socket, HttpHost, InetSocketAddress,
     * InetSocketAddress, HttpContext)})
     * So apply here any configurations you actually want enabled.
     *
     * @param socket The socket to be configured
     * @throws SocketException
     */
    private void configureSocket(Socket socket) throws SocketException {
        socket.setSoTimeout(SystemProperties.getClientProxyHttpClientTimeout());

        int linger = SystemProperties.getClientProxyHttpClientSoLinger();
        socket.setSoLinger(linger >= 0, linger);
    }

    private void prepareAndVerify(SSLSocket sslSocket, URI selectedAddress,
            HttpContext context) throws IOException {
        prepareSocket(sslSocket);
        verify(context, sslSocket.getSession(), selectedAddress);
    }

    private SocketInfo connect(URI[] addresses, HttpContext context,
            int timeout) throws IOException {
        log.trace("Connecting to hosts {} with timeout {}", Arrays.toString(addresses), timeout);
        if (addresses.length == 1) { // only one host, no need to select fastest
            return connect(addresses[0], context, timeout);
        } else {
            return new FastestSocketSelector(addresses, timeout).select();
        }
    }

    private SocketInfo connect(URI address, HttpContext context, int timeout)
            throws IOException {
        Socket socket = super.createSocket(context);
        try {
            socket.connect(toAddress(address), timeout);
            return new SocketInfo(address, socket);
        } catch (IOException | UnresolvedAddressException e) {
            log.error("Could not connect to '{}'", address, e);

            IOUtils.closeQuietly(socket);
            return null;
        }
    }

    private SSLSocket wrapToSSLSocket(Socket socket) throws IOException {
        if (socket instanceof SSLSocket) {
            return (SSLSocket) socket;
        }

        Socket sslSocket = socketfactory.createSocket(socket,
                socket.getInetAddress().getHostName(), socket.getPort(), SystemProperties.isUseSslSocketAutoClose());
        if (sslSocket instanceof SSLSocket) {
            return (SSLSocket) sslSocket;
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Failed to create SSL socket");
    }

    private CachedSSLSessionHostInformation getCachedSSLSessionHostURI(URI[] addresses) {
        SSLSessionContext sessionCtx = sslContext.getClientSessionContext();
        Enumeration<byte[]> ids = sessionCtx.getIds();
        while (ids.hasMoreElements()) {
            byte[] id = ids.nextElement();
            SSLSession session = sessionCtx.getSession(id);
            if (session != null) {
                for (URI address : addresses) {
                    if (isSessionHost(session, address)) {
                        log.trace("Found cached session for {}", address);
                        return new CachedSSLSessionHostInformation(address, session);
                    }
                }
            }
        }
        return CachedSSLSessionHostInformation.NONE;
    }

    /**
     * Simple value object for holding URI - SSLSession pairs
     */
    @Getter
    private static class CachedSSLSessionHostInformation {
        static final CachedSSLSessionHostInformation NONE = new CachedSSLSessionHostInformation();
        private final URI selectedAddress;
        private final SSLSession sslSession;

        CachedSSLSessionHostInformation(URI selectedAddress, SSLSession sslSession) {
            this.selectedAddress = selectedAddress;
            this.sslSession = sslSession;
        }

        CachedSSLSessionHostInformation() {
            this.selectedAddress = null;
            this.sslSession = null;
        }

        void clearCachedURIForSession() {
            if (sslSession != null) {
                sslSession.removeValue(ID_SELECTED_TARGET);
                sslSession.removeValue(ID_SELECTED_TARGET_TIMESTAMP);
            }
        }
    }

    private static URI[] getAddressesFromContext(HttpContext context) {
        Object targets = context.getAttribute(ID_TARGETS);
        if (targets == null || !(targets instanceof URI[])
                || ((URI[]) targets).length == 0) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Target hosts not specified in http context");
        }

        return (URI[]) targets;
    }

    private static boolean isSessionHost(SSLSession session, URI host) {
        try {
            URI sslHost = (URI) session.getValue(ID_SELECTED_TARGET);
            return sslHost != null && sslHost.equals(host);
        } catch (Exception e) {
            log.error("Error checking if host {} is in session ({}).", host,
                    session);
            log.error("Exception :{}", e);
        }

        return false;
    }

    private static InetSocketAddress toAddress(URI uri) {
        return new InetSocketAddress(uri.getHost(), uri.getPort());
    }

    private static CodedException couldNotConnectException(URI[] addresses) {
        log.error("Could not connect to any target host ({})",
                Arrays.toString(addresses));
        return new CodedException(X_NETWORK_ERROR, String.format(
                "Could not connect to any target host (%s)",
                Arrays.toString(addresses)));
    }
}
