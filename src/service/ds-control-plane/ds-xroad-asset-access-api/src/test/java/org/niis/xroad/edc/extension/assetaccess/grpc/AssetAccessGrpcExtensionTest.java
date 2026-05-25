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
package org.niis.xroad.edc.extension.assetaccess.grpc;

import io.grpc.ServerCredentials;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessGrpcExtensionTest {

    @Mock
    private Monitor monitor;

    @Mock
    private RpcCredentialsConfigurer configurer;

    @Mock
    private ServerCredentials serverCredentials;

    private AssetAccessGrpcExtension extension;

    @BeforeEach
    void setUp() throws Exception {
        extension = new AssetAccessGrpcExtension();
        setField(extension, "monitor", monitor);
    }

    @Test
    void nameReturnsExtensionName() {
        assertThat(extension.name()).isEqualTo(AssetAccessGrpcExtension.EXTENSION_NAME);
    }

    @Test
    void resolveServerCredentialsWithWorkingSupplierReturnsCredentials() {
        when(configurer.createServerCredentials()).thenReturn(serverCredentials);
        Supplier<RpcCredentialsConfigurer> supplier = () -> configurer;

        var result = extension.resolveServerCredentials(supplier);

        assertThat(result).isSameAs(serverCredentials);
        verify(monitor, never()).severe(anyString(), any(Throwable.class));
    }

    @Test
    void startFailsFastWhenCdiConfigurerMissing() {
        var cause = new IllegalStateException("no CDI");
        Supplier<RpcCredentialsConfigurer> failingSupplier = () -> {
            throw cause;
        };

        assertThatThrownBy(() -> extension.resolveServerCredentials(failingSupplier))
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(AssetAccessGrpcExtension.EXTENSION_NAME)
                .hasMessageContaining("RpcCredentialsConfigurer")
                .hasCause(cause);

        verify(monitor).severe(contains("failed to resolve"), any(Throwable.class));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
