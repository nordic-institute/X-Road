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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.niis.xroad.common.acme.AcmeServiceException;
import org.niis.xroad.common.acme.config.AcmeProperties;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseAccountKeystorePasswordProviderTest {

    private static final String PROPERTY_KEY = AdminServiceConfigKeys.ACME.key();

    private final ConfigurationPropertyRepository configurationPropertyRepository = mock(ConfigurationPropertyRepository.class);
    private final AcmeProperties acmeProperties = new AcmeProperties();
    private final DatabaseAccountKeystorePasswordProvider provider =
            new DatabaseAccountKeystorePasswordProvider(configurationPropertyRepository, acmeProperties);

    @Test
    void createsRowAndUpdatesInMemoryPasswordWhenNoDocumentExists() {
        when(configurationPropertyRepository.findConfigurationPropertyByPropertyKey(PROPERTY_KEY))
                .thenReturn(Optional.empty());

        char[] password;
        try (MockedStatic<AcmeConfigDocumentCodec> codec = mockStatic(AcmeConfigDocumentCodec.class)) {
            codec.when(() -> AcmeConfigDocumentCodec.setAccountKeystorePassword(any(), any()))
                    .thenReturn("updated-document");

            password = provider.createNewPassword();

            codec.verify(() -> AcmeConfigDocumentCodec.setAccountKeystorePassword(null, new String(password)));
        }

        ArgumentCaptor<ConfigurationPropertyEntity> captor = ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);
        verify(configurationPropertyRepository).saveOrUpdate(captor.capture());
        assertThat(captor.getValue().getPropertyKey()).isEqualTo(PROPERTY_KEY);
        assertThat(captor.getValue().getPropertyValue()).isEqualTo("updated-document");

        assertThat(acmeProperties.getAccountKeystorePassword()).isEqualTo(password);
    }

    @Test
    void reusesExistingRowAndPreservesOtherFieldsWhenDocumentAlreadyExists() {
        var existingEntity = new ConfigurationPropertyEntity();
        existingEntity.setPropertyKey(PROPERTY_KEY);
        existingEntity.setPropertyValue("eab-credentials: {}");
        when(configurationPropertyRepository.findConfigurationPropertyByPropertyKey(PROPERTY_KEY))
                .thenReturn(Optional.of(existingEntity));

        char[] password;
        try (MockedStatic<AcmeConfigDocumentCodec> codec = mockStatic(AcmeConfigDocumentCodec.class)) {
            codec.when(() -> AcmeConfigDocumentCodec.setAccountKeystorePassword(any(), any()))
                    .thenReturn("updated-document");

            password = provider.createNewPassword();

            codec.verify(() -> AcmeConfigDocumentCodec.setAccountKeystorePassword("eab-credentials: {}", new String(password)));
        }

        ArgumentCaptor<ConfigurationPropertyEntity> captor = ArgumentCaptor.forClass(ConfigurationPropertyEntity.class);
        verify(configurationPropertyRepository).saveOrUpdate(captor.capture());
        assertThat(captor.getValue()).isSameAs(existingEntity);
        assertThat(captor.getValue().getPropertyValue()).isEqualTo("updated-document");

        assertThat(acmeProperties.getAccountKeystorePassword()).isEqualTo(password);
    }

    @Test
    void wrapsRepositoryReadFailureAsAcmeServiceException() {
        when(configurationPropertyRepository.findConfigurationPropertyByPropertyKey(eq(PROPERTY_KEY)))
                .thenThrow(new RuntimeException("db unavailable"));

        assertThatThrownBy(provider::createNewPassword)
                .isInstanceOf(AcmeServiceException.class);

        verify(configurationPropertyRepository, never()).saveOrUpdate(any());
        assertThat(acmeProperties.getAccountKeystorePassword()).isNull();
    }

    @Test
    void wrapsRepositoryWriteFailureAsAcmeServiceExceptionAndLeavesInMemoryPasswordUntouched() {
        when(configurationPropertyRepository.findConfigurationPropertyByPropertyKey(PROPERTY_KEY))
                .thenReturn(Optional.empty());
        doThrow(new RuntimeException("db write failed"))
                .when(configurationPropertyRepository).saveOrUpdate(any());

        assertThatThrownBy(provider::createNewPassword)
                .isInstanceOf(AcmeServiceException.class);

        assertThat(acmeProperties.getAccountKeystorePassword()).isNull();
    }
}
