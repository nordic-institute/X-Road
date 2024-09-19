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

import java.util.function.Supplier;

public interface RpcCredentialsProvider {

    boolean isTlsEnabled();

    String getKeystore();

    char[] getKeystorePassword();

    String getTruststore();

    char[] getTruststorePassword();


    class Builder {
        private boolean tlsEnabled = true;
        private Supplier<String> keystore = () -> null;
        private Supplier<char[]> keystorePassword = () -> null;
        private Supplier<String> truststore = () -> null;
        private Supplier<char[]> truststorePassword = () -> null;

        public Builder tlsEnabled(boolean tlsEnabled) {
            this.tlsEnabled = tlsEnabled;
            return this;
        }

        public Builder keystore(Supplier<String> keystore) {
            this.keystore = keystore;
            return this;
        }

        public Builder keystorePassword(Supplier<char[]> keystorePassword) {
            this.keystorePassword = keystorePassword;
            return this;
        }

        public Builder truststore(Supplier<String> truststore) {
            this.truststore = truststore;
            return this;
        }

        public Builder truststorePassword(Supplier<char[]> truststorePassword) {
            this.truststorePassword = truststorePassword;
            return this;
        }

        public RpcCredentialsProvider build() {
            return new RpcCredentialsProvider() {

                @Override
                public boolean isTlsEnabled() {
                    return tlsEnabled;
                }

                @Override
                public String getKeystore() {
                    return keystore.get();
                }

                @Override
                public char[] getKeystorePassword() {
                    return keystorePassword.get();
                }

                @Override
                public String getTruststore() {
                    return truststore.get();
                }

                @Override
                public char[] getTruststorePassword() {
                    return truststorePassword.get();
                }
            };
        }
    }
}
