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

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.niis.xroad.common.acme.AcmeKeyPurpose;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.config.AcmeProperties;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.repository.ConfigurationPropertyRepository;
import org.niis.xroad.serverconf.impl.entity.ConfigurationPropertyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AcmeService} detecting a missing keystore and empty password,
 * delegating to the real {@link DatabaseAccountKeystorePasswordProvider} backed by the real test database, and
 * then actually writing the account keystore file to disk with the password that provider generated and
 * persisted.
 */
public class AcmeServiceAccountKeystorePasswordProvisioningIntegrationTest extends AbstractFacadeMockingTestContext {

    private static final String PROPERTY_KEY = AdminServiceConfigKeys.ACME.key();
    private static final String CA_NAME = "testca";
    private static final String MEMBER_ID = "MEMBER1";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Autowired
    ConfigurationPropertyRepository configurationPropertyRepository;

    private WireMockServer wireMockServer;

    @Before
    public void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(head(urlEqualTo("/new-nonce"))
                .willReturn(aResponse().withStatus(200).withHeader("Replay-Nonce", "boot-nonce")));
    }

    @After
    public void tearDown() {
        wireMockServer.stop();
    }

    @Test
    public void ordersAccountKeyPairThroughRealProviderAndPersistsGeneratedPasswordToTheDatabase() throws Exception {
        assertTrue(configurationPropertyRepository.findConfigurationPropertyByPropertyKey(PROPERTY_KEY).isEmpty());

        Path keystorePath = tempFolder.getRoot().toPath().resolve("acme-accounts.p12");

        AcmeProperties acmeProperties = eabConfiguredProperties();
        DatabaseAccountKeystorePasswordProvider passwordProvider =
                new DatabaseAccountKeystorePasswordProvider(configurationPropertyRepository, acmeProperties);

        AcmeConfig acmeConfig = mock(AcmeConfig.class);
        when(acmeConfig.getAcmeAccountKeystorePath()).thenReturn(keystorePath.toString());
        when(acmeConfig.getAcmeKeyLength()).thenReturn(2048);
        when(acmeConfig.getAcmeCertificateAccountKeyPairExpiration()).thenReturn(30);

        AcmeService acmeService = new AcmeService(acmeProperties, acmeConfig, passwordProvider);

        ApprovedCAInfo caInfo = new ApprovedCAInfo(CA_NAME, false, null, null,
                wireMockServer.baseUrl() + "/directory", null, null, null);
        stubDirectory();
        stubNewAccount();

        boolean renewalInfoAdvertised = acmeService.hasRenewalInfo(MEMBER_ID, caInfo, AcmeKeyPurpose.SIGNING, List.of());

        assertEquals(false, renewalInfoAdvertised);
        assertTrue(Files.exists(keystorePath));

        String generatedPassword = persistedAccountKeystorePassword();
        assertArrayEquals(generatedPassword.toCharArray(), acmeProperties.getAccountKeystorePassword());

        KeyStore keyStore = CryptoUtils.loadPkcs12KeyStore(keystorePath.toFile(), generatedPassword.toCharArray());
        assertTrue(keyStore.containsAlias(MEMBER_ID));
    }

    private String persistedAccountKeystorePassword() {
        Optional<ConfigurationPropertyEntity> persisted =
                configurationPropertyRepository.findConfigurationPropertyByPropertyKey(PROPERTY_KEY);
        assertTrue(persisted.isPresent());
        Map<String, Object> document = new Yaml().load(persisted.get().getPropertyValue());
        return (String) document.get(AcmeConfigDocumentCodec.ACCOUNT_KEYSTORE_PASSWORD_FIELD);
    }

    private void stubDirectory() {
        String base = wireMockServer.baseUrl();
        String directory = "{"
                + "\"newNonce\":\"" + base + "/new-nonce\","
                + "\"newAccount\":\"" + base + "/new-account\","
                + "\"newOrder\":\"" + base + "/new-order\","
                + "\"meta\":{\"externalAccountRequired\":true}"
                + "}";
        wireMockServer.stubFor(get(urlEqualTo("/directory"))
                .willReturn(okJson(directory).withHeader("Replay-Nonce", "dir-nonce")));
    }

    private void stubNewAccount() {
        String base = wireMockServer.baseUrl();
        wireMockServer.stubFor(post(urlEqualTo("/new-account")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("Location", base + "/account/1")
                .withHeader("Replay-Nonce", "acct-nonce")
                .withBody("{\"status\":\"valid\"}")));
    }

    private static AcmeProperties eabConfiguredProperties() {
        AcmeProperties.Credentials credentials = new AcmeProperties.Credentials();
        credentials.setKid("test-kid");
        credentials.setMacKey("QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE");

        AcmeProperties.CA ca = new AcmeProperties.CA();
        ca.setMacKeyBase64Encoded(true);
        Map<String, AcmeProperties.Credentials> members = new HashMap<>();
        members.put(MEMBER_ID, credentials);
        ca.setMembers(members);

        AcmeProperties.EabCredentials eabCredentials = new AcmeProperties.EabCredentials();
        Map<String, AcmeProperties.CA> certificateAuthorities = new HashMap<>();
        certificateAuthorities.put(CA_NAME, ca);
        eabCredentials.setCertificateAuthorities(certificateAuthorities);

        AcmeProperties properties = new AcmeProperties();
        properties.setEabCredentials(eabCredentials);
        return properties;
    }
}
