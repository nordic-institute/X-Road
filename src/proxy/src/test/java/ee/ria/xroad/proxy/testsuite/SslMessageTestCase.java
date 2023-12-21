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
package ee.ria.xroad.proxy.testsuite;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.ssl.SSLContexts;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
/**
 * All test cases extending this class will be executed in a separate batch
 * where ClientProxy and ServerProxy are started in SSL mode.
 */
public class SslMessageTestCase extends MessageTestCase {

    @Override
    protected CloseableHttpAsyncClient getClient() throws Exception {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom();

        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout(getClientTimeout())
                .setSoTimeout(30000)
                .build();

        ConnectingIOReactor ioReactor =
                new DefaultConnectingIOReactor(ioReactorConfig);

        Registry<SchemeIOSessionStrategy> sessionStrategyRegistry = RegistryBuilder.<SchemeIOSessionStrategy>create()
                .register("http", NoopIOSessionStrategy.INSTANCE)
                .register("https", new SSLIOSessionStrategy(
                        SSLContexts.custom()
                                .loadKeyMaterial(
                                        getKeyStore(),
                                        getKeyStorePassword())
                                .loadTrustMaterial((chain, authType) -> {
                                    final X509Certificate[] internalKey = TestCertUtil.getInternalKey().certChain;
                                    if (internalKey.length != chain.length) return false;
                                    for (int i = 0; i < internalKey.length; i++) {
                                        if (!chain[i].equals(internalKey[i])) return false;
                                    }
                                    return true;
                                })
                                .build(),
                        NoopHostnameVerifier.INSTANCE))
                .build();

        PoolingNHttpClientConnectionManager connManager =
                new PoolingNHttpClientConnectionManager(ioReactor, sessionStrategyRegistry);

        connManager.setMaxTotal(1);
        connManager.setDefaultMaxPerRoute(1);

        builder.setConnectionManager(connManager);
        return builder.build();
    }

    @Override
    protected URI getClientUri() throws URISyntaxException {
        return new URI("https://localhost:" + SystemProperties.getClientProxyHttpsPort());
    }

    public KeyStore getKeyStore() {
        return TestCertUtil.getKeyStore("client");
    }

    public char[] getKeyStorePassword() {
        return TestCertUtil.getKeyStorePassword("client");
    }
}
