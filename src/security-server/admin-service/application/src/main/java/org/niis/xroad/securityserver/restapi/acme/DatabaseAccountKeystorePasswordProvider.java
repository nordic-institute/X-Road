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
package org.niis.xroad.securityserver.restapi.acme;

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.acme.AccountKeystorePasswordProvider;
import org.niis.xroad.common.acme.AcmeDeviationMessage;
import org.niis.xroad.common.acme.AcmeServiceException;
import org.niis.xroad.common.acme.config.AcmeProperties;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a new ACME account keystore password and persists it into the same database-stored ACME
 * configuration document that also holds the EAB credentials, using {@link AcmeConfigDocumentCodec} to
 * leave every other field in that document untouched.
 * <p>
 * After persisting, the already-loaded {@link AcmeProperties} instance is updated in place so the new
 * password is usable by the current process without a restart.
 */
@RequiredArgsConstructor
public class DatabaseAccountKeystorePasswordProvider implements AccountKeystorePasswordProvider {

    private final ConfigurationPropertyRepository configurationPropertyRepository;
    private final AcmeProperties acmeProperties;

    /**
     * Runs in its own transaction, committed independently of whatever transaction the caller is running in.
     * Callers of this method (e.g. certificate ordering) can fail later in their own transaction without
     * rolling back the password that was already generated and persisted here.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public char[] createNewPassword() {
        try {
            String propertyKey = AdminServiceConfigKeys.ACME.key();
            var existing = configurationPropertyRepository.findConfigurationPropertyByPropertyKey(propertyKey);
            String currentDocument = existing.map(ConfigurationPropertyEntity::getPropertyValue).orElse(null);

            String newPassword = CryptoUtils.generateRandomPassword(AcmeProperties.ACCOUNT_KEYSTORE_PASSWORD_LENGTH);
            String updatedDocument = AcmeConfigDocumentCodec.setAccountKeystorePassword(currentDocument, newPassword);

            var entity = existing.orElseGet(() -> newConfigurationProperty(propertyKey));
            entity.setPropertyValue(updatedDocument);
            configurationPropertyRepository.saveOrUpdate(entity);

            acmeProperties.setAccountKeystorePassword(newPassword);
            return newPassword.toCharArray();
        } catch (AcmeServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AcmeServiceException(e, AcmeDeviationMessage.ACCOUNT_KEYSTORE_PASSWORD_GENERATION_FAILURE.build());
        }
    }

    private static ConfigurationPropertyEntity newConfigurationProperty(String propertyKey) {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(propertyKey);
        return entity;
    }
}
