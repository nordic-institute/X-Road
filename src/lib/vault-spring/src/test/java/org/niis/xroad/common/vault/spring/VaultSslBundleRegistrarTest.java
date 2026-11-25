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
package org.niis.xroad.common.vault.spring;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.InternalSSLKey;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleRegistry;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VaultSslBundleRegistrarTest {

    private static final String BUNDLE = "test-bundle";

    private final SslBundleRegistry registry = mock(SslBundleRegistry.class);

    @Test
    void shouldRegisterTheResolvedCredentialsUnderTheBundleName() {
        var pkcs12 = TestCertUtil.getInternalKey();
        var registrar = new VaultSslBundleRegistrar(BUNDLE) {
            @Override
            protected InternalSSLKey resolveTlsCredentials() {
                return new InternalSSLKey(pkcs12.key, pkcs12.certChain);
            }
        };

        registrar.registerBundles(registry);

        verify(registry).registerBundle(eq(BUNDLE), any(SslBundle.class));
    }

    @Test
    void shouldRegisterNothingWhenTheCredentialsCannotBeResolved() {
        var missing = XrdRuntimeException.systemException(ErrorCode.MISSING_SECRET).details("absent").build();
        var registrar = new VaultSslBundleRegistrar(BUNDLE) {
            @Override
            protected InternalSSLKey resolveTlsCredentials() {
                throw missing;
            }
        };

        assertThatThrownBy(() -> registrar.registerBundles(registry))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(missing);

        verify(registry, never()).registerBundle(any(), any(SslBundle.class));
    }
}
