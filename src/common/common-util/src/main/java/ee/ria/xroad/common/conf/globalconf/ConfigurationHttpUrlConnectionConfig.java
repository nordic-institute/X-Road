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

package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.NoopHostnameVerifier;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Slf4j
public final class ConfigurationHttpUrlConnectionConfig {

    private static final String TLS = "TLS";
    private static final SSLSocketFactory SSL_SOCKET_FACTORY;

    static {
        try {
            final SSLContext sslContext = SSLContext.getInstance(TLS);
            sslContext.init(null, new TrustManager[]{new NoopTrustManager()}, new SecureRandom());
            SSL_SOCKET_FACTORY = sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new IllegalStateException("FATAL: Unable to create socket factory", e);
        }
    }

    private ConfigurationHttpUrlConnectionConfig() {
    }

    static void apply(HttpURLConnection conn) {
        if (conn instanceof HttpsURLConnection httpsConn) {
            logSystemPropertiesInfo();

            if (!isHostNameVerificationEnabled()) {
                httpsConn.setHostnameVerifier(new NoopHostnameVerifier());
            }
            if (!isTlsCertificationVerificationEnabled()) {
                httpsConn.setSSLSocketFactory(SSL_SOCKET_FACTORY);
            }
        }
    }

    private static boolean isHostNameVerificationEnabled() {
        return SystemProperties.isConfigurationClientGlobalConfHostnameVerificationEnabled();
    }

    private static boolean isTlsCertificationVerificationEnabled() {
        return SystemProperties.isConfigurationClientGlobalConfTlsCertVerificationEnabled();
    }

    private static void logSystemPropertiesInfo() {
        log.info("Global conf download TLS certificate verification is " + isEnabled(isTlsCertificationVerificationEnabled())
                + ", hostname verification is " + isEnabled(isHostNameVerificationEnabled()));
    }

    private static String isEnabled(boolean paramValue) {
        return paramValue ? "enabled" : "disabled";
    }

    @SuppressWarnings("java:S4830") // Won't fix: Works as designed ("Server certificates should be verified in production environment")
    static class NoopTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            // The method gets never called
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            // Trust all
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
