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
package ee.ria.xroad.wsdlvalidator;

import org.apache.cxf.tools.common.ToolConstants;
import org.apache.cxf.tools.common.ToolContext;
import org.apache.cxf.tools.common.ToolException;
import org.apache.cxf.tools.validator.internal.WSDL11Validator;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Adaptation of Apache CXF WSDLValidator command line interface for X-Road. Used by security server admin GUI when
 * adding a new service.
 *
 * Usage: java -jar wsdlvalidator.jar <i>wsdlUrl</i><br> Exits with code zero if the validation was successful, nonzero
 * otherwise.
 *
 * @see org.apache.cxf.tools.validator.WSDLValidator
 *
 * The original CLI always exists with code 0. This version does not support any of the wsdlvalidator command line
 * switches.
 */
public final class WSDLValidator {

    private static final String PROPERTY_INTERNAL_KEY_STORE = "ee.ria.xroad.internalKeyStore";
    private static final String PROPERTY_INTERNAL_KEY_STORE_PASSWORD = "ee.ria.xroad.internalKeyStorePassword";

    private WSDLValidator() {
    }

    static int executeValidator(String wsdlUrl, PrintStream msg) {
        try {
            ToolContext env = new ToolContext();
            env.put(ToolConstants.CFG_WSDLURL, wsdlUrl);
            final WSDL11Validator validator = new WSDL11Validator(null, env);
            if (validator.isValid()) {
                return 0;
            }
        } catch (ToolException ex) {
            msg.print(ex.getMessage());
            if ((ex.getCause() == null || ex.getCause() == ex) && ex.getMessage().contains(" Failures: 0,")) {
                return 0;
            }
        } catch (Exception ex) {
            msg.println(ex.getMessage());
        }
        return 1;
    }

    /**
     * WSDLValidator wrapper.
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("WSDLValidator Error : Missing argument: wsdlUrl");
            System.exit(1);
        }

        setupSSL();
        final int result = executeValidator(args[0], System.err);
        System.exit(result);
    }

    /*
     * Overrides the default HTTPSURLConnection SSL socket factory with one that trusts all server certificates and
     * does not verify host names. In addition, if a keystore file and password are provided, the socket factory uses
     * the keystore as a source for client certificates.
     *
     * The keystore file name and password are read from the following system properties
     * <ul>
     * <li>ee.ria.xroad.internalKeyStore</li>
     * <li>ee.ria.xroad.internalKeyStorePassword</li>
     * </ul>
     */
    @SuppressWarnings("squid:S3510")
    private static void setupSSL() throws IOException, GeneralSecurityException {
        final String keyStore = System.getProperty(PROPERTY_INTERNAL_KEY_STORE);
        final String keyStorePassword = System.getProperty(PROPERTY_INTERNAL_KEY_STORE_PASSWORD);

        KeyManager[] keyManagers = null;
        if (keyStore != null && keyStorePassword != null) {
            final char[] password = keyStorePassword.toCharArray();
            final KeyStore ks = KeyStore.getInstance("pkcs12");
            try (FileInputStream fis = new FileInputStream(keyStore)) {
                ks.load(fis, password);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);
            keyManagers = kmf.getKeyManagers();
        }

        final SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(keyManagers, new TrustManager[] {new NoopTrustManager()}, new SecureRandom());

        HttpsURLConnection.setDefaultHostnameVerifier((s, sslSession) -> true);
        HttpsURLConnection.setDefaultSSLSocketFactory(sslCtx.getSocketFactory());
    }

    @SuppressWarnings("squid:S4424")
    static class NoopTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
            //NOP
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
            //NOP
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
