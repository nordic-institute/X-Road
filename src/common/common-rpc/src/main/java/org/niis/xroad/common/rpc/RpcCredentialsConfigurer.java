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
package org.niis.xroad.common.rpc;

import ee.ria.xroad.common.SystemProperties;

import io.grpc.ChannelCredentials;
import io.grpc.ServerCredentials;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RpcCredentialsConfigurer {

    public static ServerCredentials createServerCredentials() throws UnrecoverableKeyException, NoSuchAlgorithmException,
            KeyStoreException {
        TlsServerCredentials.Builder tlsBuilder = TlsServerCredentials.newBuilder()
                .keyManager(getKeyManagers())
                .trustManager(getTrustManagers())
                .clientAuth(TlsServerCredentials.ClientAuth.REQUIRE);

        return tlsBuilder.build();
    }

    public static ChannelCredentials createClientCredentials() throws NoSuchAlgorithmException, KeyStoreException,
            UnrecoverableKeyException {
        TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder()
                .keyManager(getKeyManagers())
                .trustManager(getTrustManagers());

        return tlsBuilder.build();
    }

    private static KeyManager[] getKeyManagers()
            throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
        final var path = SystemProperties.getGrpcInternalKeyStore();
        final var password = SystemProperties.getGrpcInternalKeyStorePassword();

        KeyStore keystore = getKeystore(path, password);
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keystore, password.toCharArray());
        return keyManagerFactory.getKeyManagers();
    }

    private static TrustManager[] getTrustManagers()
            throws NoSuchAlgorithmException, KeyStoreException {
        final var path = SystemProperties.getGrpcInternalTrustStore();
        final var password = SystemProperties.getGrpcInternalTruststorePassword();

        KeyStore truststore = getKeystore(path, password);
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(truststore);
        return trustManagerFactory.getTrustManagers();
    }

    private static KeyStore getKeystore(String filePath, String password) {
        log.trace("Loading keystore for RPC operation from path {}", filePath);
        Path path = Paths.get(filePath);
        KeyStore keystore = null;
        try (InputStream in = Files.newInputStream(path)) {
            keystore = KeyStore.getInstance("JKS");
            keystore.load(in, password.toCharArray());
        } catch (IOException | CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
            log.error("Failed to read gRPC keystore.", e);
        }
        return keystore;
    }
}
