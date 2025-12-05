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
package org.niis.xroad.proxy.core.test;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.conf.SigningCtx;
import org.niis.xroad.proxy.core.conf.SigningCtxImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains various test utility methods.
 */
public final class TestSigningCtxUtil {

    private static final String PASSWORD = "test";

    private static Map<String, TestCertUtil.PKCS12> cache = new HashMap<>();

    private TestSigningCtxUtil() {
    }

    /**
     * Load a certificate and private key from the PKC12 keystore with the given name.
     *
     * @param orgName the keystore name
     * @return the certificate and private key container
     */
    public static synchronized TestCertUtil.PKCS12 loadPKCS12(String orgName) {
        if (cache.containsKey(orgName)) {
            return cache.get(orgName);
        }

        TestCertUtil.PKCS12 pkcs12 =
                TestCertUtil.loadPKCS12(orgName + ".p12", "1", PASSWORD);
        cache.put(orgName, pkcs12);
        return pkcs12;
    }

    /**
     * @param orgName the keystore name
     * @return signing context for given organization, assuming that
     * keystore is named orgName.p12 and key in store is named
     * after the organization.
     */
    public static SigningCtx getSigningCtx(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider, String orgName) {
        TestCertUtil.PKCS12 pkcs12 = loadPKCS12(orgName);
        return getSigningCtx(globalConfProvider, keyConfProvider, pkcs12);
    }


    private static SigningCtx getSigningCtx(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider,
                                            TestCertUtil.PKCS12 pkcs12) {
        ClientId subject = ClientId.Conf.create("EE", "BUSINESS", "foo");
        return new SigningCtxImpl(globalConfProvider, keyConfProvider, DigestAlgorithm.SHA512, subject, new TestSigningKey(pkcs12.key),
                pkcs12.certChain[0]);
    }
}
