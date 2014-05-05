package ee.cyber.sdsb.proxy.clientproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.net.URI;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.Arrays;
import java.util.Enumeration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;

import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_NETWORK_ERROR;

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
class FastestConnectionSelectingSSLSocketFactory
        extends SSLConnectionSocketFactory {

    /**
     * The identifier of target addresses for the HttpContext attributes map.
     */
    public static final String ID_TARGETS =
            "ee.cyber.sdsb.serverproxy.targets";

    /**
     * The identifier for the selected target address stored in the created SSL
     * session that is later used to determine the previously existing session.
     */
    private static final String ID_SELECTED_TARGET =
            "ee.cyber.sdsb.serverproxy.selectedtarget";

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    FastestConnectionSelectingSSLSocketFactory.class);

    private final javax.net.ssl.SSLSocketFactory socketfactory;

    private final SSLContext sslContext;

    FastestConnectionSelectingSSLSocketFactory(SSLContext sslContext) {
        super(sslContext, null);

        this.sslContext = sslContext;
        this.socketfactory = sslContext.getSocketFactory();
    }

    @Override
    public Socket connectSocket(int timeout, Socket socket,
            HttpHost host, InetSocketAddress remoteAddress,
            InetSocketAddress localAddress, HttpContext context)
                    throws IOException {
        // Read target addresses from the context.
        Object targets = context.getAttribute(ID_TARGETS);
        if (targets == null || !(targets instanceof URI[]) ||
                ((URI[]) targets).length == 0) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Target hosts not specified in http context");
        }

        URI[] hosts = (URI[]) targets;

        // If the current SSL session cache contains a session to a target
        // then connect to that target immediately without host selection.
        URI cachedSSLSessionURI = getCachedSSLSessionHostURI(hosts);
        if (cachedSSLSessionURI != null) {
            hosts = new URI[] { cachedSSLSessionURI };
        }

        LOG.trace("Connecting to hosts: {}", Arrays.toString(hosts));

        // Select the fastest address if more than one address is provided.
        FastestSocketSelector.SocketInfo socketInfo =
                connect(hosts, localAddress, context, timeout);
        if (socketInfo == null) {
            LOG.error("Could not connect to any target host ({})",
                    Arrays.toString(hosts));
            throw new CodedException(X_NETWORK_ERROR, String.format(
                    "Could not connect to any target host (%s)",
                    Arrays.toString(hosts)));
        }

        URI selectedAddress = socketInfo.getUri();
        Socket selectedSock = socketInfo.getSocket();

        LOG.debug("Selected connection to {}", selectedAddress);

        // Create the SSL socket and store the selected target address.
        selectedSock = wrapToSSLSocket(selectedSock);
        if (selectedSock instanceof SSLSocket) {
            SSLSocket sslSock = (SSLSocket) selectedSock;
            prepareSocket(sslSock);

            sslSock.getSession().putValue(ID_SELECTED_TARGET, selectedAddress);

            AuthTrustVerifier.verify(context, sslSock.getSession());

            return sslSock;
        }

        throw new CodedException(X_INTERNAL_ERROR,
                "Failed to create SSL socket");
    }

    private FastestSocketSelector.SocketInfo connect(URI[] addresses,
            InetSocketAddress localAddress, HttpContext context, int timeout)
                    throws IOException {
        if (addresses.length == 1) { // only one host, no need to select fastest
            InetSocketAddress remoteAddress =
                    new InetSocketAddress(addresses[0].getHost(),
                            addresses[0].getPort());

            SocketChannel socketChannel = SocketChannel.open();
            socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true);
            socketChannel.configureBlocking(true);
            try {
                socketChannel.connect(remoteAddress);
                return new FastestSocketSelector.SocketInfo(addresses[0],
                        socketChannel.socket());
            } catch (UnresolvedAddressException e) {
                // The thrown exception does not contain anything useful,
                // so we just throw a new CodedException instead.
                throw new CodedException(X_NETWORK_ERROR,
                        "Could not connect to '%s'", addresses[0]);
            }
        } else {
            return new FastestSocketSelector(addresses, timeout).select();
        }
    }

    private Socket wrapToSSLSocket(Socket sock) throws IOException {
        return socketfactory.createSocket(sock,
                sock.getInetAddress().getHostName(), sock.getPort(),
                false);
    }

    private URI getCachedSSLSessionHostURI(URI[] hosts) {
        SSLSessionContext sessionCtx = sslContext.getClientSessionContext();

        Enumeration<byte[]> ids = sessionCtx.getIds();
        while (ids.hasMoreElements()) {
            byte[] id = ids.nextElement();

            SSLSession session = sessionCtx.getSession(id);
            if (session != null) {
                for (URI host : hosts) {
                    if (isSessionHost(session, host)) {
                        LOG.trace("Found cached session for {}", host);
                        return host;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isSessionHost(SSLSession session, URI host) {
        try {
            URI sslHost = (URI) session.getValue(ID_SELECTED_TARGET);
            return sslHost != null ? sslHost.equals(host) : false;
        } catch (Exception e) {
            LOG.error("Error checking if host {} is in session ({})", host,
                    session);
        }

        return false;
    }

}
