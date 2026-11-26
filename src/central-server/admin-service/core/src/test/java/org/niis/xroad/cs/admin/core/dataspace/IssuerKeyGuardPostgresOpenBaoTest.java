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
package org.niis.xroad.cs.admin.core.dataspace;

import ee.ria.xroad.common.util.TimeUtils;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.spring.SpringVaultClient;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.GlobalConfGenerationStatusService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.config.AdminServiceProperties;
import org.niis.xroad.cs.admin.core.service.NotificationServiceImpl;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyInfoProto;
import org.niis.xroad.signer.protocol.dto.TokenInfoProto;
import org.springframework.vault.VaultException;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.VaultTokenRequest;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.SUCCESS;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_INTERNAL;
import static org.niis.xroad.signer.protocol.dto.KeyUsageInfo.SIGNING;
import static org.niis.xroad.signer.protocol.dto.TokenStatusInfo.OK;

/**
 * Proves the Issuer Service key-missing guard against a real {@code ds-issuer-service} schema and a real
 * OpenBao, not the fakes {@link IssuerKeyGuardImplTest} uses: {@link IssuerKeyPairReaderImpl}'s native
 * query picks only the Signing row out of a real table, and {@link IssuerKeyGuardImpl} answers from a real
 * {@link SpringVaultClient} read of the {@code xrd-ds-secret} mount.
 *
 * <p>Both containers are started by hand, behind a Docker check, instead of through
 * {@code @Testcontainers}: that extension starts them before a {@code @BeforeAll} can skip, so a
 * Docker-less run would fail instead of skip.
 */
class IssuerKeyGuardPostgresOpenBaoTest {

    private static final String ISSUER_SCHEMA = "ds-issuer-service";
    private static final String PARTICIPANT = "issuer";
    private static final int SIGNING_STATE = 200;
    private static final int ROTATED_STATE = 300;
    private static final int OPENBAO_PORT = 8200;
    private static final String OPENBAO_ROOT_TOKEN = "test-root-token";
    private static final String INSTANCE_IDENTIFIER = "DEV";
    private static final String SOFTWARE_TOKEN_ID = "0";
    private static final String CONFIGURATION_SIGNING_KEY_ID = "configuration-signing-key";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final GenericContainer<?> OPENBAO = new GenericContainer<>("openbao/openbao:2.6.1")
            .withExposedPorts(OPENBAO_PORT)
            .withEnv("BAO_DEV_ROOT_TOKEN_ID", OPENBAO_ROOT_TOKEN)
            .withEnv("BAO_DEV_LISTEN_ADDRESS", "0.0.0.0:" + OPENBAO_PORT)
            .waitingFor(Wait.forHttp("/v1/sys/health").forPort(OPENBAO_PORT).withStartupTimeout(Duration.ofSeconds(60)));

    private static SessionFactory sessionFactory;
    private static VaultEndpoint vaultEndpoint;
    private static VaultTemplate vaultTemplate;

    private EntityManager entityManager;
    private IssuerKeyPairReaderImpl reader;
    private IssuerKeyGuardImpl guard;

    @BeforeAll
    static void startContainersAndMigrate() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres- and OpenBao-backed integration test");
        POSTGRES.start();
        createIssuerSchema();
        IssuerSchemaTestSupport.applyIssuerServiceChangelog(
                IssuerSchemaTestSupport.issuerSchemaUrl(POSTGRES.getJdbcUrl(), ISSUER_SCHEMA),
                POSTGRES.getUsername(), POSTGRES.getPassword(), ISSUER_SCHEMA);
        sessionFactory = buildSessionFactory();

        OPENBAO.start();
        vaultEndpoint = VaultEndpoint.from(URI.create("http://" + OPENBAO.getHost() + ":" + OPENBAO.getMappedPort(OPENBAO_PORT)));
        vaultTemplate = new VaultTemplate(vaultEndpoint, new TokenAuthentication(OPENBAO_ROOT_TOKEN));
        vaultTemplate.write("sys/mounts/" + VaultClient.DS_ISSUER_SERVICE_SECRET_MOUNT,
                Map.of("type", "kv", "options", Map.of("version", "2")));
    }

    @AfterAll
    static void stopContainers() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
        if (OPENBAO.isRunning()) {
            OPENBAO.stop();
        }
    }

    @BeforeEach
    void wireRealCollaboratorsAndClearTheTable() throws Exception {
        entityManager = sessionFactory.createEntityManager();
        reader = new IssuerKeyPairReaderImpl(entityManager);
        var vaultClient = new SpringVaultClient(null, dsIssuerServiceVaultVersionedOperations(), null, Duration.ZERO);
        guard = new IssuerKeyGuardImpl(reader, vaultClient);

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("DELETE FROM \"" + ISSUER_SCHEMA + "\".keypair_resource");
        }
    }

    @AfterEach
    void closeEntityManager() {
        if (entityManager != null) {
            entityManager.close();
        }
    }

    @Test
    void readerReturnsOnlyTheSigningAliasAmongARotatedAndANonDefaultActivatedRow() {
        insertKeypairRow("rotated-key", ROTATED_STATE, false);
        insertKeypairRow("published-key", SIGNING_STATE, false);
        insertKeypairRow("signing-key", SIGNING_STATE, true);

        var alias = withTransaction(() -> reader.findSigningKeyAlias());

        assertThat(alias).contains("signing-key");
    }

    @Test
    void guardAnswersYesWhenTheSigningKeysSecretExistsInOpenBao() {
        insertKeypairRow("signing-key-present", SIGNING_STATE, true);
        dsIssuerServiceVaultOperations().put("ds/signing-key-present", Map.of("payload", "irrelevant"));

        var answer = withTransaction(() -> guard.signingKeySecretPresent());

        assertThat(answer).isTrue();
    }

    @Test
    void guardAnswersNoWhenTheSigningKeysSecretIsAbsentFromOpenBao() {
        insertKeypairRow("signing-key-absent", SIGNING_STATE, true);

        var answer = withTransaction(() -> guard.signingKeySecretPresent());

        assertThat(answer).isFalse();
    }

    @Test
    void notificationServiceAlertAppearsWhenTheSigningKeysSecretIsRemovedAndClearsWhenItIsRestored() {
        insertKeypairRow("notification-signing-key", SIGNING_STATE, true);
        var secretPath = "ds/notification-signing-key";
        var notificationService = notificationServiceWithTheRealGuard();

        dsIssuerServiceVaultOperations().put(secretPath, Map.of("payload", "irrelevant"));
        assertThat(withTransaction(notificationService::getAlerts))
                .noneMatch(alert -> alert.getErrorCode().equals("status.issuer_service.key_missing"));

        dsIssuerServiceVaultOperations().delete(secretPath);
        assertThat(withTransaction(notificationService::getAlerts))
                .anyMatch(alert -> alert.getErrorCode().equals("status.issuer_service.key_missing"));

        dsIssuerServiceVaultOperations().put(secretPath, Map.of("payload", "irrelevant"));
        assertThat(withTransaction(notificationService::getAlerts))
                .noneMatch(alert -> alert.getErrorCode().equals("status.issuer_service.key_missing"));
    }

    @Test
    void guardAnswersNoWhenTheCurrentVersionOfTheSigningKeysSecretIsSoftDeleted() {
        insertKeypairRow("soft-deleted-signing-key", SIGNING_STATE, true);
        var path = "ds/soft-deleted-signing-key";
        dsIssuerServiceVaultOperations().put(path, Map.of("payload", "irrelevant"));
        dsIssuerServiceVaultOperations().delete(path);

        var answer = withTransaction(() -> guard.signingKeySecretPresent());

        assertThat(answer).isFalse();
    }

    @Test
    void guardAnswersNoWhenTheCurrentVersionOfTheSigningKeysSecretIsDestroyed() {
        insertKeypairRow("destroyed-signing-key", SIGNING_STATE, true);
        var path = "ds/destroyed-signing-key";
        var versionedOps = dsIssuerServiceVaultVersionedOperations();
        var version = versionedOps.put(path, Map.of("payload", "irrelevant")).getVersion();
        versionedOps.destroy(path, version);

        var answer = withTransaction(() -> guard.signingKeySecretPresent());

        assertThat(answer).isFalse();
    }

    @Test
    void guardAnswersYesWhenOnlyAnOlderVersionOfTheSigningKeysSecretIsDeleted() {
        insertKeypairRow("older-version-deleted-signing-key", SIGNING_STATE, true);
        var path = "ds/older-version-deleted-signing-key";
        var versionedOps = dsIssuerServiceVaultVersionedOperations();
        var firstVersion = versionedOps.put(path, Map.of("payload", "first")).getVersion();
        versionedOps.put(path, Map.of("payload", "second"));
        versionedOps.delete(path, firstVersion);

        var answer = withTransaction(() -> guard.signingKeySecretPresent());

        assertThat(answer).isTrue();
    }

    @Test
    void aTokenRestrictedToReadingOnlyTheMetadataPathGetsCorrectAnswersAndIsDeniedADataRead() {
        var path = "ds/metadata-only-policy-key";
        dsIssuerServiceVaultOperations().put(path, Map.of("payload", "irrelevant"));

        var policyName = "issuer-metadata-read-only";
        createPolicy(policyName, """
                path "xrd-ds-secret/metadata/ds/*" {
                  capabilities = ["read"]
                }
                """);
        var scopedToken = createTokenWithPolicy(policyName);
        var scopedClient = clientFor(scopedToken);

        assertThat(scopedClient.dsIssuerServiceKeypairSecretExists("metadata-only-policy-key")).isTrue();
        assertThat(scopedClient.dsIssuerServiceKeypairSecretExists("metadata-only-policy-key-missing")).isFalse();

        var deniedRead = keyValueOperationsFor(scopedToken);
        assertThatThrownBy(() -> deniedRead.get(path)).isInstanceOf(VaultException.class);
    }

    @Test
    void aFailedMetadataReadPropagatesAsAnErrorRatherThanReportingAbsent() {
        insertKeypairRow("unreadable-signing-key", SIGNING_STATE, true);
        dsIssuerServiceVaultOperations().put("ds/unreadable-signing-key", Map.of("payload", "irrelevant"));
        var tokenWithoutMetadataAccess = createTokenWithPolicy("default");
        var clientWithoutMetadataAccess = clientFor(tokenWithoutMetadataAccess);
        var guardWithoutMetadataAccess = new IssuerKeyGuardImpl(reader, clientWithoutMetadataAccess);

        assertThatThrownBy(() -> withTransaction(guardWithoutMetadataAccess::signingKeySecretPresent))
                .isInstanceOf(VaultException.class);
    }

    /**
     * Wires the real {@link NotificationServiceImpl} to the real {@link #guard} built in
     * {@link #wireRealCollaboratorsAndClearTheTable()} and a {@link DataspaceIssuerDidService} stub that
     * reports one bound DID, so the alert's only real dependency is the guard. The Central Server is mocked
     * as initialized (instance identifier, Central Server address, an initialized software token) so that
     * {@code getAlerts()} reaches the issuer key check, and every other collaborator is mocked just enough
     * that the checks run alongside it do not throw.
     */
    private NotificationServiceImpl notificationServiceWithTheRealGuard() {
        var systemParameterService = mock(SystemParameterService.class);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(systemParameterService.getCentralServerAddress()).thenReturn("https://cs");
        when(systemParameterService.getConfExpireIntervalSeconds()).thenReturn(600);

        var keyInfo = KeyInfoProto.newBuilder()
                .setAvailable(true)
                .setUsage(SIGNING)
                .setFriendlyName("")
                .setId(CONFIGURATION_SIGNING_KEY_ID)
                .setLabel("")
                .setPublicKey("")
                .setSignMechanismName("")
                .build();
        var softwareToken = new TokenInfo(TokenInfoProto.newBuilder()
                .setType("")
                .setFriendlyName("")
                .setId(SOFTWARE_TOKEN_ID)
                .setReadOnly(false)
                .setAvailable(true)
                .setActive(true)
                .setSerialNumber("")
                .setLabel("")
                .setStatus(OK)
                .addKeyInfo(keyInfo)
                .build());

        var signerProxyFacade = mock(SignerProxyFacade.class);
        when(signerProxyFacade.getTokens()).thenReturn(List.of(softwareToken));

        var configurationSigningKeysService = mock(ConfigurationSigningKeysService.class);
        var signingKey = new ConfigurationSigningKey();
        signingKey.setKeyIdentifier(CONFIGURATION_SIGNING_KEY_ID);
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_INTERNAL)).thenReturn(Optional.of(signingKey));

        var globalConfGenerationStatus = mock(GlobalConfGenerationStatusService.class);
        when(globalConfGenerationStatus.get()).thenReturn(new GlobalConfGenerationStatus(SUCCESS, TimeUtils.now()));

        var dsTlsCertificateService = mock(DsTlsCertificateService.class);
        when(dsTlsCertificateService.getEnrollmentStatus()).thenReturn(new DsTlsEnrollmentStatus(null, null, null));

        return new NotificationServiceImpl(
                configurationSigningKeysService,
                systemParameterService,
                mock(AdminServiceProperties.class),
                signerProxyFacade,
                globalConfGenerationStatus,
                dsTlsCertificateService,
                new OneBoundDidStub(),
                guard);
    }

    private static final class OneBoundDidStub implements DataspaceIssuerDidService {
        @Override
        public void register(String issuerDid, String host, int didPort, String issuanceUrl, String statuslistBaseUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> findAll() {
            return List.of("did:xrd:test");
        }
    }

    private VaultKeyValueOperations dsIssuerServiceVaultOperations() {
        return vaultTemplate.opsForKeyValue(VaultClient.DS_ISSUER_SERVICE_SECRET_MOUNT, KeyValueBackend.KV_2);
    }

    private VaultVersionedKeyValueOperations dsIssuerServiceVaultVersionedOperations() {
        return vaultTemplate.opsForVersionedKeyValue(VaultClient.DS_ISSUER_SERVICE_SECRET_MOUNT);
    }

    private void createPolicy(String name, String hclRules) {
        vaultTemplate.write("sys/policies/acl/" + name, Map.of("policy", hclRules));
    }

    private String createTokenWithPolicy(String policyName) {
        var request = VaultTokenRequest.builder().policies(List.of(policyName)).build();
        return vaultTemplate.opsForToken().create(request).getToken().getToken();
    }

    private VaultVersionedKeyValueOperations versionedOperationsFor(String token) {
        return vaultTemplateFor(token).opsForVersionedKeyValue(VaultClient.DS_ISSUER_SERVICE_SECRET_MOUNT);
    }

    private VaultKeyValueOperations keyValueOperationsFor(String token) {
        return vaultTemplateFor(token).opsForKeyValue(VaultClient.DS_ISSUER_SERVICE_SECRET_MOUNT, KeyValueBackend.KV_2);
    }

    private SpringVaultClient clientFor(String token) {
        return new SpringVaultClient(null, versionedOperationsFor(token), null, Duration.ZERO);
    }

    private VaultTemplate vaultTemplateFor(String token) {
        return new VaultTemplate(vaultEndpoint, new TokenAuthentication(token));
    }

    private <T> T withTransaction(Supplier<T> action) {
        entityManager.getTransaction().begin();
        try {
            var result = action.get();
            entityManager.getTransaction().commit();
            return result;
        } catch (RuntimeException e) {
            entityManager.getTransaction().rollback();
            throw e;
        }
    }

    private void insertKeypairRow(String alias, int state, boolean isDefaultPair) {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.prepareStatement(
                     "INSERT INTO \"" + ISSUER_SCHEMA + "\".keypair_resource "
                             + "(id, participant_context_id, \"timestamp\", key_id, is_default_pair, "
                             + "serialized_public_key, private_key_alias, state, usage) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, PARTICIPANT);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, alias);
            statement.setBoolean(5, isDefaultPair);
            statement.setString(6, "serialized-public-key");
            statement.setString(7, alias);
            statement.setInt(8, state);
            statement.setString(9, "signing");
            statement.execute();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static SessionFactory buildSessionFactory() {
        var configuration = new Configuration();
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl());
        configuration.setProperty("hibernate.connection.username", POSTGRES.getUsername());
        configuration.setProperty("hibernate.connection.password", POSTGRES.getPassword());
        configuration.setProperty("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
        configuration.setProperty("hibernate.hikari.maximumPoolSize", "5");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        return configuration.buildSessionFactory();
    }

    private static void createIssuerSchema() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + ISSUER_SCHEMA + "\"");
        }
    }
}
