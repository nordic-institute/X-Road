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

import org.junit.Test;
import org.niis.xroad.common.acme.AccountKeystorePasswordProvider;
import org.niis.xroad.common.acme.config.AcmeProperties;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DatabaseAccountKeystorePasswordProviderIntegrationTest extends AbstractFacadeMockingTestContext {

    private static final String PROPERTY_KEY = AdminServiceConfigKeys.ACME.key();
    private static final String EAB_CREDENTIALS_DOCUMENT = """
            eab-credentials:
              certificate-authorities:
                test-ca:
                  mac-key-base64-encoded: true
                  members:
                    memberA:
                      kid: kid-a
                      mac-key: mac-key-a
            """;

    @Autowired
    ConfigurationPropertyRepository configurationPropertyRepository;

    @Autowired
    AccountKeystorePasswordProvider accountKeystorePasswordProvider;

    @Autowired
    AcmeProperties acmeProperties;

    @Test
    public void generatesAndPersistsPasswordWhenNoRowExists() {
        assertTrue(configurationPropertyRepository.findConfigurationPropertyByPropertyKey(PROPERTY_KEY).isEmpty());

        char[] password = accountKeystorePasswordProvider.createNewPassword();

        ConfigurationPropertyEntity persisted = getPersistedProperty();
        Map<String, Object> document = loadDocument(persisted.getPropertyValue());
        assertEquals(new String(password), document.get("account-keystore-password"));
        assertArrayEquals(password, acmeProperties.getAccountKeystorePassword());
    }

    @Test
    public void addsPasswordToExistingRowWithoutDisturbingOtherFields() {
        var entity = new ConfigurationPropertyEntity();
        entity.setPropertyKey(PROPERTY_KEY);
        entity.setPropertyValue(EAB_CREDENTIALS_DOCUMENT);
        configurationPropertyRepository.saveOrUpdate(entity);

        char[] password = accountKeystorePasswordProvider.createNewPassword();

        ConfigurationPropertyEntity persisted = getPersistedProperty();
        Map<String, Object> document = loadDocument(persisted.getPropertyValue());
        assertEquals(new String(password), document.get("account-keystore-password"));
        assertEquals(loadDocument(EAB_CREDENTIALS_DOCUMENT).get("eab-credentials"), document.get("eab-credentials"));
        assertArrayEquals(password, acmeProperties.getAccountKeystorePassword());
    }

    private ConfigurationPropertyEntity getPersistedProperty() {
        Optional<ConfigurationPropertyEntity> persisted =
                configurationPropertyRepository.findConfigurationPropertyByPropertyKey(PROPERTY_KEY);
        assertTrue(persisted.isPresent());
        assertNotNull(persisted.get().getPropertyValue());
        return persisted.get();
    }

    private static Map<String, Object> loadDocument(String document) {
        return new Yaml().load(document);
    }
}
