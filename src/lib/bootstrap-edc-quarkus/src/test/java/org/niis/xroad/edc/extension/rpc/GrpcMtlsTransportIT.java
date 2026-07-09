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
package org.niis.xroad.edc.extension.rpc;

import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.nio.NioEventLoopGroup;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.security.auth.x500.X500Principal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrpcMtlsTransportIT {

    private static final int RSA_KEY_SIZE = 2048;
    private static final int CERT_VALIDITY_DAYS = 1;

    private static RpcServer rpcServer;
    private static int serverPort;

    private static AdvancedTlsX509TrustManager clientTrustManager;
    private static AdvancedTlsX509KeyManager clientKeyManager;

    @BeforeAll
    static void startServer() throws Exception {
        var caKeyPair = generateKeyPair();
        var caCert = buildCaCert(caKeyPair);

        var serverKeyPair = generateKeyPair();
        var serverCert = buildLeafCert("CN=grpc-test-server",
                serverKeyPair.getPublic(), caKeyPair, caCert, true, false);

        var clientKeyPair = generateKeyPair();
        var clientCert = buildLeafCert("CN=grpc-test-client",
                clientKeyPair.getPublic(), caKeyPair, caCert, false, true);

        var serverKeyManager = new AdvancedTlsX509KeyManager();
        serverKeyManager.updateIdentityCredentials(
                new X509Certificate[]{serverCert, caCert}, serverKeyPair.getPrivate());

        clientKeyManager = new AdvancedTlsX509KeyManager();
        clientKeyManager.updateIdentityCredentials(
                new X509Certificate[]{clientCert, caCert}, clientKeyPair.getPrivate());

        var serverTrustManager = buildTrustManager(caCert);
        clientTrustManager = buildTrustManager(caCert);

        var configurer = new TlsRpcCredentialsConfigurer(
                new StaticVaultKeyProvider(serverKeyManager, serverTrustManager));

        rpcServer = new RpcServer("localhost", 0, configurer.createServerCredentials(),
                builder -> builder.addService(new EchoService()));
        rpcServer.init();
        serverPort = extractPort(rpcServer);
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (rpcServer != null) {
            rpcServer.destroy();
        }
    }

    @Test
    void callWithValidClientCertSucceeds() {
        var creds = TlsChannelCredentials.newBuilder()
                .keyManager(clientKeyManager)
                .trustManager(clientTrustManager)
                .build();
        var channel = openChannel(creds);
        try {
            var response = pingServer(channel);
            assertThat(response).isEqualTo("pong");
        } finally {
            closeChannel(channel);
        }
    }

    @Test
    void callWithoutClientCertIsRejectedAtTransport() {
        var noClientCertCreds = TlsChannelCredentials.newBuilder()
                .trustManager(clientTrustManager)
                .build();
        var channel = openChannel(noClientCertCreds);
        try {
            assertThatThrownBy(() -> pingServer(channel))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(ex -> {
                        var code = ((StatusRuntimeException) ex).getStatus().getCode();
                        assertThat(code).as("mTLS rejection must surface as a transport-layer status, not a handler code")
                                .isIn(Status.UNAVAILABLE.getCode(), Status.UNKNOWN.getCode());
                    });
        } finally {
            closeChannel(channel);
        }
    }

    private static KeyPair generateKeyPair() throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(RSA_KEY_SIZE);
        return gen.generateKeyPair();
    }

    private static X509Certificate buildCaCert(KeyPair caKeyPair) throws Exception {
        var subject = new X500Principal("CN=grpc-test-ca");
        var now = Instant.now();
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        var holder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.ONE,
                Date.from(now),
                Date.from(now.plus(CERT_VALIDITY_DAYS, ChronoUnit.DAYS)),
                subject,
                caKeyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .addExtension(Extension.keyUsage, true,
                        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign | KeyUsage.digitalSignature))
                .build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static X509Certificate buildLeafCert(
            String dn, PublicKey subjectKey,
            KeyPair caKeyPair, X509Certificate caCert,
            boolean serverAuth, boolean clientAuth) throws Exception {
        var now = Instant.now();
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        var builder = new JcaX509v3CertificateBuilder(
                caCert.getSubjectX500Principal(),
                BigInteger.TWO,
                Date.from(now),
                Date.from(now.plus(CERT_VALIDITY_DAYS, ChronoUnit.DAYS)),
                new X500Principal(dn),
                subjectKey)
                .addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        if (serverAuth) {
            builder.addExtension(
                    Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        }
        if (clientAuth) {
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
        }
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static AdvancedTlsX509TrustManager buildTrustManager(X509Certificate caCert) throws Exception {
        var tm = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_ONLY_VERIFICATION)
                .build();
        tm.updateTrustCredentials(new X509Certificate[]{caCert});
        return tm;
    }

    private static int extractPort(RpcServer server) throws Exception {
        var field = RpcServer.class.getDeclaredField("server");
        field.setAccessible(true);
        return ((Server) field.get(server)).getPort();
    }

    private static ManagedChannel openChannel(ChannelCredentials credentials) {
        return NettyChannelBuilder.forAddress("127.0.0.1", serverPort, credentials)
                .channelType(NioSocketChannel.class)
                .channelFactory(NioSocketChannel::new)
                .eventLoopGroup(new NioEventLoopGroup(1))
                .build();
    }

    private static void closeChannel(ManagedChannel channel) {
        channel.shutdownNow();
        try {
            channel.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String pingServer(ManagedChannel channel) {
        return ClientCalls.blockingUnaryCall(channel, EchoService.PING_METHOD, CallOptions.DEFAULT, "ping");
    }

    static final class StaticVaultKeyProvider implements VaultKeyProvider {

        private final AdvancedTlsX509KeyManager keyManager;
        private final AdvancedTlsX509TrustManager trustManager;

        StaticVaultKeyProvider(AdvancedTlsX509KeyManager km, AdvancedTlsX509TrustManager tm) {
            this.keyManager = km;
            this.trustManager = tm;
        }

        @Override
        public KeyManager getKeyManager() {
            return keyManager;
        }

        @Override
        public TrustManager getTrustManager() {
            return trustManager;
        }

        @Override
        public void init() {
        }

        @Override
        public void shutdown() {
        }
    }

    static final class EchoService implements BindableService {

        static final MethodDescriptor<String, String> PING_METHOD = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test.Echo/Ping")
                .setRequestMarshaller(StringMarshaller.INSTANCE)
                .setResponseMarshaller(StringMarshaller.INSTANCE)
                .build();

        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("test.Echo")
                    .addMethod(PING_METHOD, ServerCalls.asyncUnaryCall(
                            (_, observer) -> {
                                observer.onNext("pong");
                                observer.onCompleted();
                            }))
                    .build();
        }
    }

    static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {

        static final StringMarshaller INSTANCE = new StringMarshaller();

        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to parse gRPC string value", e);
            }
        }
    }
}
