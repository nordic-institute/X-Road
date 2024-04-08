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
package org.niis.xroad.ssl;

import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.AuthTrustManager;
import ee.ria.xroad.proxy.conf.AuthKeyManager;
import ee.ria.xroad.proxy.conf.KeyConf;

import lombok.experimental.UtilityClass;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.function.Supplier;

import static org.niis.xroad.ssl.EdcSSLConstants.SSL_PROTOCOL;

@UtilityClass
public class SSLContextBuilder {

    public static Result create() throws KeyManagementException, NoSuchAlgorithmException {
        return create(KeyConf::getAuthKey);
    }

    public static Result create(Supplier<AuthKey> authKeySupplier) throws KeyManagementException, NoSuchAlgorithmException {
        var trustManager = new AuthTrustManager();
        SSLContext ctx = SSLContext.getInstance(SSL_PROTOCOL);
        ctx.init(new KeyManager[]{new AuthKeyManager(authKeySupplier)}, new TrustManager[]{trustManager},
                new SecureRandom());
        return new Result(ctx, trustManager);
    }

    public record Result(SSLContext sslContext, X509TrustManager trustManager) {
    }
}
