/*
 * The MIT License
 *
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
package org.niis.xroad.edc.trust;

import org.eclipse.edc.spi.monitor.Monitor;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * A trust exception STRICTLY scoped to EDC's own OpenBao vault endpoint: the same singleton OkHttp client that
 * carries DataSpace traffic also carries EDC's Hashicorp vault client, whose serving certificate (often
 * self-signed in development and test environments) is unrelated to the DS TLS CA list a governing authority
 * curates. The exception is deliberately narrow — one exact host and port, resolved once at construction time
 * from configuration — so it cannot widen into a general-purpose trust anchor for arbitrary DataSpace peers.
 */
public final class VaultEndpointTrust {

    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final int DEFAULT_HTTP_PORT = 80;

    private final String host;
    private final int port;
    private final X509TrustManager delegate;

    private VaultEndpointTrust(String host, int port, X509TrustManager delegate) {
        this.host = host;
        this.port = port;
        this.delegate = delegate;
    }

    /**
     * @param vaultUrlSetting the value of the {@code edc.vault.hashicorp.url} setting, or {@code null}/blank if unset
     * @param caCertPath      path to the PEM file trusted for the vault endpoint (the same file the Quarkus vault
     *                        client uses), or {@code null}/blank if unset
     * @return the vault trust exception, or empty if the exception cannot be built for any reason (not configured,
     * an unparseable vault URL, or an unreadable/invalid CA file) — every such case degrades to relying on the
     * DataSpace TLS CA list alone, never to widening trust.
     */
    public static Optional<VaultEndpointTrust> from(String vaultUrlSetting, String caCertPath, Monitor monitor) {
        if (isBlank(vaultUrlSetting) || isBlank(caCertPath)) {
            return Optional.empty();
        }

        HostPort hostPort;
        try {
            hostPort = parseHostPort(vaultUrlSetting);
        } catch (RuntimeException e) {
            monitor.warning("Could not resolve an exact host and port from the OpenBao vault URL '%s'; DataSpace TLS "
                    .formatted(vaultUrlSetting)
                    + "clients will rely on the DS TLS CA list alone to reach the vault: %s".formatted(e.getMessage()));
            return Optional.empty();
        }

        X509TrustManager trustManager;
        try {
            trustManager = loadTrustManager(caCertPath);
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            monitor.warning("Could not load the OpenBao TLS CA certificate from '%s'; DataSpace TLS clients will rely on "
                    .formatted(caCertPath)
                    + "the DS TLS CA list alone to reach the vault: %s".formatted(e.getMessage()));
            return Optional.empty();
        }

        return Optional.of(new VaultEndpointTrust(hostPort.host(), hostPort.port(), trustManager));
    }

    public boolean matchesVaultEndpoint(String peerHost, int peerPort) {
        return peerPort == port && peerHost != null && host.equalsIgnoreCase(stripIpv6Brackets(peerHost));
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        delegate.checkServerTrusted(chain, authType);
    }

    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static HostPort parseHostPort(String vaultUrlSetting) {
        var uri = URI.create(vaultUrlSetting.strip());
        var host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("no host in vault URL '" + vaultUrlSetting + "'");
        }
        var port = uri.getPort();
        if (port == -1) {
            port = defaultPortFor(uri.getScheme());
        }
        // URI.getHost() keeps an IPv6 literal's brackets ("[::1]"), but the peer host JSSE actually
        // reports at handshake time is whatever OkHttp passed to the socket factory, and OkHttp's own
        // canonical host form for IPv6 is unbracketed ("::1") — verified against a real handshake, not
        // just read from documentation. Stored unbracketed so matchesVaultEndpoint's real-world case
        // (an unbracketed peer host) compares equal without per-call normalization on that side.
        return new HostPort(stripIpv6Brackets(host), port);
    }

    private static String stripIpv6Brackets(String host) {
        if (host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static int defaultPortFor(String scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("no scheme in vault URL, cannot resolve a default port");
        }
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "https" -> DEFAULT_HTTPS_PORT;
            case "http" -> DEFAULT_HTTP_PORT;
            default -> throw new IllegalArgumentException("unsupported vault URL scheme '" + scheme + "'");
        };
    }

    private static X509TrustManager loadTrustManager(String caCertPath) throws GeneralSecurityException, IOException {
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        var certificateFactory = CertificateFactory.getInstance("X.509");
        var index = 0;
        try (var in = new FileInputStream(caCertPath)) {
            for (var certificate : certificateFactory.generateCertificates(in)) {
                keyStore.setCertificateEntry("vault-ca-" + index++, certificate);
            }
        }
        if (index == 0) {
            throw new IllegalArgumentException("no certificates found in " + caCertPath);
        }

        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        return Arrays.stream(trustManagerFactory.getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The JVM's default TrustManagerFactory did not produce an X509TrustManager for the vault CA"));
    }

    private record HostPort(String host, int port) {
    }
}
