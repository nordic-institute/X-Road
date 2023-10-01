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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedException;
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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;
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

    public static final int MIN_TIMEOUT = 5000;

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
            InetSocketAddress localAddress, HttpContext context) {
        // Discard dummy socket.
        closeQuietly(socket);

        // Read target addresses from the context.
        final URI[] addressesFromContext = getAddressesFromContext(context);
        final boolean useCache = cachingEnabled && (addressesFromContext.length > 1);
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
                log.info("Using provider URI '{}' from cache", cachedURI);
                selector.add(cachedURI);
            }
        }

        if (selector.isEmpty()) {
            selector.addAll(addressesFromContext);
        }

        Exception deferredException = null;
        int connectTimeout = (cachedURI == null ? timeout : CACHED_TIMEOUT);
        while (!selector.isEmpty()) {
            SocketInfo selectedSocket = null;
            SSLSocket sslSocket = null;
            try {
                // Select the fastest address if more than one address is provided.
                // see also FastestSocketSelector
                selectedSocket = selector.select(connectTimeout);
                sslSocket = wrapToSSLSocket(selectedSocket.getSocket(), connectTimeout);
                prepareAndVerify(sslSocket, selectedSocket.getUri(), context);
                configureSocket(sslSocket);
                log.trace("Connected to {}", selectedSocket.getUri());
                updateOpMonitoringData(context, selectedSocket);

                if (useCache && cachedURI == null) {
                    log.info("Storing the fastest provider URI '{}' to cache", selectedSocket.getUri());
                    selectedHosts.put(cacheKey, selectedSocket.getUri());
                }
                return sslSocket;
            } catch (IOException | RuntimeException e) {
                deferredException = e;
                closeQuietly(sslSocket);
                if (selectedSocket != null) {
                    log.warn("Failed to connect to {}", selectedSocket.getUri(), e);
                    closeQuietly(selectedSocket.getSocket());
                } else {
                    log.warn("Failed to connect", e);
                }
                if (cachedURI != null) {
                    selectedHosts.asMap().remove(cacheKey, cachedURI);
                    selector.addAll(addressesFromContext);
                    selector.remove(cachedURI);
                    cachedURI = null;
                    connectTimeout = timeout;
                } else {
                    if (selectedSocket == null) {
                        //selection failed, bail out
                        break;
                    }
                    selector.remove(selectedSocket.getUri());
                    //if there are addresses left, try again but using reduced connection timeout.
                    connectTimeout = Math.max(MIN_TIMEOUT, connectTimeout / 2);
                }
            }
        }
        throw couldNotConnectException(addressesFromContext, deferredException);
    }

    @Override
    protected void prepareSocket(final SSLSocket socket) throws IOException {
        socket.setEnabledProtocols(new String[] {CryptoUtils.SSL_PROTOCOL});
        socket.setEnabledCipherSuites(SystemProperties.getXroadTLSCipherSuites());
    }

    static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                //ignore
            }
        }
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

        // Called explicitly to catch TLS handshake errors
        // Otherwise TLS handshake is initiated by SSLSocketImpl.getSession() which swallows any potential errors
        try {
            sslSocket.startHandshake();
        } catch (IOException e) {
            throw new CodedException(X_SSL_AUTH_FAILED, e, "TLS handshake failed");
        }

        verify(context, sslSocket.getSession(), selectedAddress);
    }

    private SSLSocket wrapToSSLSocket(Socket socket, int connectTimeout) throws IOException {
        if (socket instanceof SSLSocket) {
            return (SSLSocket)socket;
        }
        log.trace("Existing connection not over TLS, negotiating the use of TLS.");
        //XRDDEV-248: use connection timeout as read timeout during SSL handshake
        socket.setSoTimeout(connectTimeout);
        socket.setSoLinger(false, 0);
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

    private static CodedException couldNotConnectException(URI[] addresses, Exception cause) {
        log.error("Could not connect to any target host ({})", (Object)addresses);
        if (cause instanceof CodedException) {
            return (CodedException)cause;
        } else {
            return new CodedException(X_NETWORK_ERROR, cause, "Could not connect to any target host (%s)",
                    Arrays.toString(addresses));
        }
    }

    static final class CacheKey {
        private final URI[] addresses;
        private final int hash;

        CacheKey(URI[] addresses) {
            // address lists are small, using an sorted arraylist as a key
            // will have a lower overhead than using a hashset or treeset
            final URI[] tmp = addresses.clone();
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
