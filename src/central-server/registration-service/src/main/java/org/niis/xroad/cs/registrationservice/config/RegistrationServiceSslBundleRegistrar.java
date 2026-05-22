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
package org.niis.xroad.cs.registrationservice.config;

import ee.ria.xroad.common.conf.InternalSSLKey;

import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.managementservice.AbstractManagementServiceSslBundleRegistrar;
import org.niis.xroad.common.vault.VaultClient;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

@Slf4j
public class RegistrationServiceSslBundleRegistrar extends AbstractManagementServiceSslBundleRegistrar {

    private final VaultClient vaultClient;
    private final Retry retryInstance;

    public RegistrationServiceSslBundleRegistrar(VaultClient vaultClient, Retry retryInstance) {
        this.vaultClient = vaultClient;
        this.retryInstance = retryInstance;

        retryInstance.getEventPublisher().onRetry(event -> log.warn("Retrying resolving tls credentials. Event: {}", event));
    }

    @Override
    protected InternalSSLKey resolveTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            return retryInstance.executeCheckedSupplier(vaultClient::getManagementServicesTlsCredentials);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException("Unexpected exception when resolving tls credentials", ex);
        }
    }
}
