package ee.ria.xroad.proxy.clientproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.UnresolvedAddressException;
import java.util.Arrays;
import java.util.Enumeration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.proxy.clientproxy.FastestSocketSelector.SocketInfo;

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
     * The identifier for the selected target address stored in the created SSL
     * session that is later used to determine the previously existing session.
     */
    private static final String ID_SELECTED_TARGET =
            "ee.ria.xroad.serverproxy.selectedtarget";

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
        URI[] addresses = addressesFromContext;

        // If the current SSL session cache contains a session to a target
        // then connect to that target immediately without host selection.
        URI cachedSSLSessionURI = getCachedSSLSessionHostURI(addresses);
        if (cachedSSLSessionURI != null) {
            addresses = new URI[] {cachedSSLSessionURI};
        }

        // Select the fastest address if more than one address is provided.
        SocketInfo selectedSocket = connect(addresses, context, timeout);
        if (selectedSocket == null) {
            if (cachedSSLSessionURI != null) {
                // could not connect to cached host, try all others.
                selectedSocket =
                        connect(addressesFromContext, context, timeout);
                if (selectedSocket == null) {
                    throw couldNotConnectException(addresses);
                }
            } else {
                throw couldNotConnectException(addresses);
            }
        }

        log.info("Connecting to {}", selectedSocket.getUri());

        SSLSocket sslSocket = wrapToSSLSocket(selectedSocket.getSocket());
        prepareAndVerify(sslSocket, selectedSocket.getUri(), context);

        return sslSocket;
    }

    private void prepareAndVerify(SSLSocket sslSocket, URI selectedAddress,
            HttpContext context) throws IOException {
        prepareSocket(sslSocket);

        sslSocket.getSession().putValue(ID_SELECTED_TARGET, selectedAddress);

        verify(context, sslSocket.getSession(), selectedAddress);
    }

    private SocketInfo connect(URI[] addresses, HttpContext context,
            int timeout) throws IOException {
        log.trace("Connecting to hosts: {}", Arrays.toString(addresses));

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
            log.error("Could not connect to '{}': {}", address, e);

            IOUtils.closeQuietly(socket);
            return null;
        }
    }

    private SSLSocket wrapToSSLSocket(Socket socket) throws IOException {
        if (socket instanceof SSLSocket) {
            return (SSLSocket) socket;
        }

        Socket sslSocket = socketfactory.createSocket(socket,
                socket.getInetAddress().getHostName(), socket.getPort(), false);
        if (sslSocket instanceof SSLSocket) {
            return (SSLSocket) sslSocket;
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Failed to create SSL socket");
    }

    private URI getCachedSSLSessionHostURI(URI[] addresses) {
        SSLSessionContext sessionCtx = sslContext.getClientSessionContext();

        Enumeration<byte[]> ids = sessionCtx.getIds();
        while (ids.hasMoreElements()) {
            byte[] id = ids.nextElement();

            SSLSession session = sessionCtx.getSession(id);
            if (session != null) {
                for (URI address : addresses) {
                    if (isSessionHost(session, address)) {
                        log.trace("Found cached session for {}", address);
                        return address;
                    }
                }
            }
        }

        return null;
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
            return sslHost != null ? sslHost.equals(host) : false;
        } catch (Exception e) {
            log.error("Error checking if host {} is in session ({})", host,
                    session);
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
