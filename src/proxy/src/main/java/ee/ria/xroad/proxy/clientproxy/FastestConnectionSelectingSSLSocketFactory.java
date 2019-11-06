/**
 * The MIT License
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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.clientproxy.FastestSocketSelector.SocketInfo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    public static final String ID_TARGETS = "ee.ria.xroad.serverproxy.targets";
    /**
     * The timeout when connecting to previously selected "fastest" provider
     */
    public static final int CACHED_TIMEOUT = 5000;

    public static final int MIN_TIMEOUT = 1000;

    public static final int CACHE_MAXIMUM_SIZE = 10000;

    private final javax.net.ssl.SSLSocketFactory socketfactory;

    private final Cache<CacheKey, URI> selectedHosts;
    private final boolean cachingEnabled;

    FastestConnectionSelectingSSLSocketFactory(SSLContext sslContext) {
        super(sslContext, null, SystemProperties.getXroadTLSCipherSuites(), (HostnameVerifier)null);
        this.socketfactory = sslContext.getSocketFactory();
        this.selectedHosts = CacheBuilder.newBuilder()
                .expireAfterWrite(SystemProperties.getClientProxyFastestConnectingSslUriCachePeriod(), TimeUnit.SECONDS)
                .maximumSize(CACHE_MAXIMUM_SIZE)
                .build();
        this.cachingEnabled = SystemProperties.getClientProxyFastestConnectingSslUriCachePeriod() > 0;
    }

    @Override
    public Socket createSocket(HttpContext context) throws IOException {
        // Create dummy socket that will be discarded.
        return new Socket();
    }

    @Override
    public Socket connectSocket(int timeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
            InetSocketAddress localAddress, HttpContext context) throws IOException {
        // Discard dummy socket.
        if (socket != null) {
            socket.close();
        }

        // Read target addresses from the context.
        final List<URI> addressesFromContext = Arrays.asList(getAddressesFromContext(context));
        final boolean useCache = cachingEnabled && (addressesFromContext.size() > 1);
        final FastestSocketSelector selector = new FastestSocketSelector();

        CacheKey cacheKey = null;
        URI cachedURI = null;

        if (log.isTraceEnabled()) {
            log.trace("addresses from context {} current thread id {}", addressesFromContext,
                    Thread.currentThread().getId());
        }

        // If URI cache is enabled, check for a previously selected host, avoiding the selection process.
        if (useCache) {
            cacheKey = new CacheKey(addressesFromContext);
            cachedURI = selectedHosts.getIfPresent(cacheKey);

            if (cachedURI != null) {
                log.trace("Use cached URI {}", cachedURI);
                selector.add(cachedURI);
            }
        }

        if (selector.isEmpty()) {
            selector.add(addressesFromContext);
        }

        Exception deferredException = null;
        int connectTimeout = (cachedURI == null ? timeout : CACHED_TIMEOUT);
        while (!selector.isEmpty()) {
            // Select the fastest address if more than one address is provided.
            // see also FastestSocketSelector
            SocketInfo selectedSocket = selector.select(connectTimeout);
            if (selectedSocket == null) {
                if (cachedURI != null) {
                    log.trace("Could not connect to {}, removing from cache", cachedURI);
                    selectedHosts.invalidate(cacheKey);
                    selector.add(addressesFromContext);
                    selector.remove(cachedURI);
                    cachedURI = null;
                    connectTimeout = timeout;
                    continue;
                } else {
                    break;
                }
            }

            try {
                final Socket s = selectedSocket.getSocket();
                //XRDDEV-248: use connection timeout as read timeout during SSL handshake
                s.setSoTimeout(connectTimeout);
                s.setSoLinger(false, 0);
                SSLSocket sslSocket = wrapToSSLSocket(s);
                prepareAndVerify(sslSocket, selectedSocket.getUri(), context);
                configureSocket(sslSocket);
                log.trace("Connected to {}", selectedSocket.getUri());
                updateOpMonitoringData(context, selectedSocket);

                if (useCache && cachedURI == null) {
                    log.trace("Store the fastest provider URI to cache {}", selectedSocket.getUri());
                    selectedHosts.put(cacheKey, selectedSocket.getUri());
                }
                return sslSocket;
            } catch (IOException | CodedException e) {
                selector.remove(selectedSocket.getUri());
                deferredException = e;
                try {
                    selectedSocket.getSocket().close();
                } catch (IOException ioe) {
                    //ignore
                }
                //if there are addresses left, try again but using reduced connection timeout.
                connectTimeout = Math.max(MIN_TIMEOUT, connectTimeout / 2);
            }
        }

        if (deferredException == null) {
            throw couldNotConnectException(addressesFromContext);
        } else if (deferredException instanceof IOException) {
            throw (IOException)deferredException;
        } else {
            throw ErrorCodes.translateException(deferredException);
        }
    }

    @Override
    protected void prepareSocket(final SSLSocket socket) throws IOException {
        socket.setEnabledProtocols(new String[] {CryptoUtils.SSL_PROTOCOL});
        socket.setEnabledCipherSuites(SystemProperties.getXroadTLSCipherSuites());
    }

    private static void updateOpMonitoringData(HttpContext context,
            SocketInfo socketInfo) {
        try {
            OpMonitoringData opMonitoringData = (OpMonitoringData)context
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
     * @param socket The socket to be configured
     * @throws SocketException
     */
    private void configureSocket(Socket socket) throws SocketException {
        socket.setSoTimeout(SystemProperties.getClientProxyHttpClientTimeout());

        int linger = SystemProperties.getClientProxyHttpClientSoLinger();
        socket.setSoLinger(linger >= 0, linger);

        socket.setKeepAlive(true);
    }

    private void prepareAndVerify(SSLSocket sslSocket, URI selectedAddress,
            HttpContext context) throws IOException {
        prepareSocket(sslSocket);
        verify(context, sslSocket.getSession(), selectedAddress);
    }

    private SSLSocket wrapToSSLSocket(Socket socket) throws IOException {
        if (socket instanceof SSLSocket) {
            return (SSLSocket)socket;
        }

        Socket sslSocket = socketfactory.createSocket(socket,
                socket.getInetAddress().getHostName(), socket.getPort(), SystemProperties.isUseSslSocketAutoClose());
        if (sslSocket instanceof SSLSocket) {
            return (SSLSocket)sslSocket;
        }

        throw new CodedException(X_INTERNAL_ERROR, "Failed to create SSL socket");
    }

    private static URI[] getAddressesFromContext(HttpContext context) {
        Object targets = context.getAttribute(ID_TARGETS);
        if (targets instanceof URI[] && ((URI[])targets).length > 0) {
            return (URI[])targets;
        }
        throw new CodedException(X_INTERNAL_ERROR, "Target hosts not specified in http context");
    }

    private static CodedException couldNotConnectException(List<URI> addresses) {
        log.error("Could not connect to any target host ({})", addresses);
        return new CodedException(X_NETWORK_ERROR,
                String.format("Could not connect to any target host (%s)", Arrays.toString(addresses.toArray())));
    }

    static final class CacheKey {
        private final URI[] addresses;
        private final int hash;

        CacheKey(List<URI> addresses) {
            // address lists are small, using an sorted arraylist as a key
            // will have a lower overhead than using a hashset or treeset
            final URI[] tmp = addresses.toArray(new URI[0]);
            Arrays.sort(tmp);
            this.addresses = tmp;
            this.hash = Arrays.hashCode(this.addresses);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;
            final CacheKey key = (CacheKey)o;
            if (hash != key.hash) return false;
            return Arrays.equals(addresses, key.addresses);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

}
