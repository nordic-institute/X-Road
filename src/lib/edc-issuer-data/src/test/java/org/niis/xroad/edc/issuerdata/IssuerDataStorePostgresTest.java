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

package org.niis.xroad.edc.issuerdata;

import com.github.benmanes.caffeine.cache.Ticker;
import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.eclipse.edc.iam.did.spi.document.DidDocument;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListCredential;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.sql.DriverManagerConnectionFactory;
import org.eclipse.edc.sql.SqlQueryExecutor;
import org.eclipse.edc.sql.datasource.ConnectionFactoryDataSource;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.datasource.spi.DefaultDataSourceRegistry;
import org.eclipse.edc.transaction.local.LocalDataSourceRegistry;
import org.eclipse.edc.transaction.local.LocalTransactionContext;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the store and the last good source against a real Postgres with the real Liquibase changelogs of the control
 * plane and of the Identity Hub.
 */
class IssuerDataStorePostgresTest {

    private static final String CONTROL_PLANE_CHANGELOG = "liquibase/ds-control-plane-changelog.xml";
    private static final String IDENTITY_HUB_CHANGELOG = "liquibase/ds-identity-hub-changelog.xml";
    private static final String IDENTITY_HUB_DATABASE = "identity_hub";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final long MAXIMUM_ENTRIES = 128;
    private static final Duration IDLE_EXPIRY = Duration.ofSeconds(120);
    private static final Duration MAXIMUM_AGE = Duration.ofSeconds(120);
    private static final Duration REFRESH_WAIT = Duration.ofMillis(100);
    private static final Duration DID_DEADLINE = MAXIMUM_AGE;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static Database controlPlane;
    private static Database identityHub;

    private final String key = "https://localhost:7001/issuer/statuslist/" + UUID.randomUUID();
    private final RecordingMonitor monitor = new RecordingMonitor();
    private final MutableClock clock = new MutableClock(START);
    private final AtomicReference<Result<Duration>> maximumAge = new AtomicReference<>();
    private final ExecutorService refreshPool = Executors.newCachedThreadPool();

    @BeforeAll
    static void createDatabases() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres-backed test");
        POSTGRES.start();
        applyChangelog(POSTGRES.getJdbcUrl(), CONTROL_PLANE_CHANGELOG);
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + IDENTITY_HUB_DATABASE);
        }
        var identityHubUrl = "jdbc:postgresql://%s:%d/%s".formatted(POSTGRES.getHost(),
                POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), IDENTITY_HUB_DATABASE);
        applyChangelog(identityHubUrl, IDENTITY_HUB_CHANGELOG);
        controlPlane = new Database("control plane", POSTGRES.getJdbcUrl());
        identityHub = new Database("identity hub", identityHubUrl);
    }

    @AfterAll
    static void stopDatabases() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @BeforeEach
    void setUp() {
        maximumAge.set(Result.success(MAXIMUM_AGE));
    }

    @AfterEach
    void shutDownRefreshPool() {
        refreshPool.shutdownNow();
    }

    static Stream<Database> databases() {
        return Stream.of(controlPlane, identityHub);
    }

    @ParameterizedTest
    @MethodSource("databases")
    void shouldRoundTripAllColumnsAndHoldUtcInRawColumns(Database database) throws SQLException {
        var fetchedAt = Instant.parse("2026-11-18T10:00:00.123456Z");
        var data = new StoredIssuerData("payload", fetchedAt, Instant.parse("2026-11-18T10:03:00Z"));
        var defaultZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        try {
            assertThat(database.store.write(IssuerDataKind.STATUSLIST, key, data).succeeded()).isTrue();
            assertThat(database.store.read(IssuerDataKind.STATUSLIST, key).getContent()).isEqualTo(data);
            assertThat(database.rawColumn(key, "fetched_at")).isEqualTo("2026-11-18 10:00:00.123456");
            assertThat(database.rawColumn(key, "valid_until")).isEqualTo("2026-11-18 10:03:00");
        } finally {
            TimeZone.setDefault(defaultZone);
        }
    }

    @ParameterizedTest
    @MethodSource("databases")
    void shouldNeverMoveRowBackInTime(Database database) {
        var newer = new StoredIssuerData("newer", START.plusSeconds(20), START.plusSeconds(200));
        var older = new StoredIssuerData("older", START, START.plusSeconds(100));
        database.store.write(IssuerDataKind.STATUSLIST, key, newer);

        var skipped = database.store.write(IssuerDataKind.STATUSLIST, key, older);

        assertThat(skipped.succeeded()).isTrue();
        assertThat(database.store.read(IssuerDataKind.STATUSLIST, key).getContent()).isEqualTo(newer);
    }

    @Test
    void shouldReplaceRowWhenNewerCopyIsWritten() {
        var older = new StoredIssuerData("older", START, START.plusSeconds(100));
        var newer = new StoredIssuerData("newer", START.plusSeconds(20), START.plusSeconds(200));
        controlPlane.store.write(IssuerDataKind.STATUSLIST, key, older);

        controlPlane.store.write(IssuerDataKind.STATUSLIST, key, newer);

        assertThat(controlPlane.store.read(IssuerDataKind.STATUSLIST, key).getContent()).isEqualTo(newer);
    }

    @Test
    void shouldKeepOneUnchangedRowWhenSameCopyIsWrittenTwice() throws SQLException {
        var data = new StoredIssuerData("payload", START, START.plusSeconds(100));

        var first = controlPlane.store.write(IssuerDataKind.STATUSLIST, key, data);
        var second = controlPlane.store.write(IssuerDataKind.STATUSLIST, key, data);

        assertThat(first.succeeded()).isTrue();
        assertThat(second.succeeded()).isTrue();
        assertThat(controlPlane.rowCount(key)).isEqualTo(1);
        assertThat(controlPlane.store.read(IssuerDataKind.STATUSLIST, key).getContent()).isEqualTo(data);
    }

    @Test
    void shouldKeepKindsApartUnderTheSameKey() {
        var statusList = new StoredIssuerData("status list", START, START.plusSeconds(100));
        var did = new StoredIssuerData("did document", START, START.plusSeconds(100));

        controlPlane.store.write(IssuerDataKind.STATUSLIST, key, statusList);
        controlPlane.store.write(IssuerDataKind.DID, key, did);

        assertThat(controlPlane.store.read(IssuerDataKind.STATUSLIST, key).getContent()).isEqualTo(statusList);
        assertThat(controlPlane.store.read(IssuerDataKind.DID, key).getContent()).isEqualTo(did);
    }

    @Test
    void shouldReportNotFoundForMissingRow() {
        var result = controlPlane.store.read(IssuerDataKind.STATUSLIST, key);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailure().getReason()).isEqualTo(StoreFailure.Reason.NOT_FOUND);
    }

    @Test
    void shouldReturnFailuresInsteadOfThrowingWhenDatabaseIsUnreachable() {
        var unreachable = new Database("unreachable", "jdbc:postgresql://localhost:1/none");
        var data = new StoredIssuerData("payload", START, START.plusSeconds(100));

        assertThat(unreachable.store.write(IssuerDataKind.STATUSLIST, key, data).failed()).isTrue();
        assertThat(unreachable.store.read(IssuerDataKind.STATUSLIST, key).failed()).isTrue();
    }

    @Test
    void shouldReturnFailuresInsteadOfThrowingWhenDataSourceIsNotRegistered() {
        var store = new SqlIssuerDataStore(new DefaultDataSourceRegistry(), DataSourceRegistry.DEFAULT_DATASOURCE,
                new NoopTransactionContext(), new SqlQueryExecutor());
        var data = new StoredIssuerData("payload", START, START.plusSeconds(100));

        assertThat(store.write(IssuerDataKind.STATUSLIST, key, data).failed()).isTrue();
        assertThat(store.read(IssuerDataKind.STATUSLIST, key).failed()).isTrue();
    }

    @Test
    void shouldReplaceStoredCopyAfterEachSuccessfulRefresh() {
        var fetcher = new FakeFetcher();
        var source = fakeSource(fetcher, clock);
        source.lookup(key);
        assertThat(controlPlane.store.read(IssuerDataKind.STATUSLIST, key).getContent())
                .isEqualTo(new StoredIssuerData("payload-1", START, START.plus(CACHE_TIME).plus(MAXIMUM_AGE)));

        clock.advance(CACHE_TIME);
        source.lookup(key);

        assertThat(controlPlane.store.read(IssuerDataKind.STATUSLIST, key).getContent()).isEqualTo(
                new StoredIssuerData("payload-2", START.plus(CACHE_TIME), START.plus(CACHE_TIME).plus(CACHE_TIME).plus(MAXIMUM_AGE)));
    }

    @Test
    void shouldServeStoredCopyAfterRestartUntilOriginalDeadlineEvenWhenMaximumAgeIsRaised() {
        try (var issuer = new FakeIssuer()) {
            issuer.publishStatusList(Set.of(), null);
            var statusListUrl = issuer.statusListUrl();
            assertThat(realSource(clock).lookup(statusListUrl).succeeded()).isTrue();
            issuer.statusListDown(true);
            var restarted = realSource(clock);
            var deadline = START.plus(CACHE_TIME).plus(MAXIMUM_AGE);

            clock.set(deadline.minusSeconds(10));
            assertThat(restarted.lookup(statusListUrl).succeeded()).isTrue();

            clock.set(deadline.minusSeconds(4));
            assertThat(restarted.lookup(statusListUrl).failed()).isTrue();

            maximumAge.set(Result.success(Duration.ofHours(1)));
            assertThat(restarted.lookup(statusListUrl).failed()).isTrue();
        }
    }

    @Test
    void shouldServeStatusListAndDidDocumentAfterRestartDuringFullIssuerOutage() {
        try (var issuer = new FakeIssuer()) {
            issuer.publishStatusList(Set.of(), null);
            var statusListUrl = issuer.statusListUrl();
            assertThat(instanceOf(issuer).statusList().lookup(statusListUrl).succeeded()).isTrue();
            assertThat(controlPlane.store.read(IssuerDataKind.DID, issuer.did()).succeeded()).isTrue();
            issuer.down(true);
            var restarted = instanceOf(issuer);

            clock.set(START.plus(DID_DEADLINE).minusSeconds(20));
            assertThat(restarted.statusList().lookup(statusListUrl).succeeded()).isTrue();
            assertThat(restarted.did().lookup(issuer.did()).succeeded()).isTrue();

            clock.set(START.plus(DID_DEADLINE).minusSeconds(4));
            var expired = restarted.did().lookup(issuer.did());
            assertThat(expired.failed()).isTrue();
            assertThat(expired.isRetryable()).isTrue();
        }
    }

    @Test
    void shouldNotServeStatusListWithoutDidDocumentCopyAndReplaceItOnceIssuerAnswers() throws SQLException {
        try (var issuer = new FakeIssuer()) {
            issuer.publishStatusList(Set.of(), null);
            var statusListUrl = issuer.statusListUrl();
            assertThat(instanceOf(issuer).statusList().lookup(statusListUrl).succeeded()).isTrue();
            controlPlane.execute("DELETE FROM xroad_issuer_data WHERE kind = ? AND key = ?", IssuerDataKind.DID.name(), issuer.did());
            issuer.down(true);
            var restarted = instanceOf(issuer);

            clock.set(START.plusSeconds(10));
            assertThat(restarted.statusList().lookup(statusListUrl).failed()).isTrue();

            issuer.down(false);
            clock.set(START.plus(CACHE_TIME).plus(LastGoodSource.MAXIMUM_BACKOFF));
            assertThat(restarted.statusList().lookup(statusListUrl).succeeded()).isTrue();
            assertThat(controlPlane.store.read(IssuerDataKind.STATUSLIST, statusListUrl).getContent().fetchedAt())
                    .isEqualTo(clock.instant());
            assertThat(controlPlane.store.read(IssuerDataKind.DID, issuer.did()).succeeded()).isTrue();
        }
    }

    @Test
    void shouldTreatCorruptRowAsNoCopyAndReplaceItWithNextSuccessfulFetch() throws SQLException {
        fakeSource(new FakeFetcher(), clock).lookup(key);
        controlPlane.execute("UPDATE xroad_issuer_data SET payload = ? WHERE key = ?", FakeFetcher.CORRUPT, key);
        var fetcher = new FakeFetcher();
        fetcher.down = true;
        var restarted = fakeSource(fetcher, clock);
        clock.set(START.plusSeconds(30));

        assertThat(restarted.lookup(key).failed()).isTrue();
        assertThat(restarted.lookup(key).failed()).isTrue();

        var warnings = monitor.warnings().stream().filter(message -> message.contains("not usable")).toList();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst()).contains(key).doesNotContain(FakeFetcher.CORRUPT);

        fetcher.down = false;
        clock.set(START.plusSeconds(100));
        assertThat(restarted.lookup(key).succeeded()).isTrue();
        assertThat(controlPlane.store.read(IssuerDataKind.STATUSLIST, key).getContent())
                .extracting(StoredIssuerData::payload, StoredIssuerData::fetchedAt)
                .containsExactly("payload-1", START.plusSeconds(100));
    }

    @Test
    void shouldSkipWriteAfterNewerWriteOfOtherReplicaAndServeItsRowAtFirstFailedRefresh() {
        var clockA = new MutableClock(START.plusSeconds(20));
        var fetcherA = new FakeFetcher();
        fetcherA.downloads.set(9);
        fakeSource(fetcherA, clockA).lookup(key);

        var fetcherB = new FakeFetcher();
        var replicaB = fakeSource(fetcherB, clock);
        replicaB.lookup(key);

        assertThat(controlPlane.store.read(IssuerDataKind.STATUSLIST, key).getContent().payload()).isEqualTo("payload-10");

        fetcherB.down = true;
        clock.advance(CACHE_TIME.plusSeconds(1));

        assertThat(replicaB.lookup(key).getContent().payload()).isEqualTo("payload-10");
    }

    @Test
    void shouldLeaveOuterTransactionCommittableWhenStoreStatementFails() throws SQLException {
        var transactionContext = new LocalTransactionContext(monitor);
        var transactionalStore = controlPlane.storeIn(transactionContext);
        var source = sourceOnPool(transactionalStore);
        var markerKey = "marker-" + UUID.randomUUID();
        var oversizedKey = "k".repeat(2049);

        transactionContext.execute(() -> {
            transactionalStore.write(IssuerDataKind.DID, markerKey, new StoredIssuerData("marker", START, START.plus(CACHE_TIME)));
            assertThat(source.lookup(oversizedKey).succeeded()).isTrue();
        });

        assertThat(monitor.warnings()).anyMatch(message -> message.contains("could not be stored"));
        assertThat(controlPlane.rowCount(markerKey)).isEqualTo(1);
    }

    @Test
    void shouldHoldNoRowLockAfterCheckInsideOuterTransaction() {
        var transactionContext = new LocalTransactionContext(monitor);
        var source = sourceOnPool(controlPlane.storeIn(transactionContext));
        var rowVisible = new AtomicReference<Boolean>();

        transactionContext.execute(() -> {
            assertThat(source.lookup(key).succeeded()).isTrue();
            rowVisible.set(controlPlane.lockRowWithoutWaiting(key));
        });

        assertThat(rowVisible.get()).isTrue();
    }

    private LastGoodSource<String> sourceOnPool(SqlIssuerDataStore store) {
        return new LastGoodSource<>(new FakeFetcher(), clock, Ticker.disabledTicker(), refreshPool, CACHE_TIME,
                MAXIMUM_ENTRIES, IDLE_EXPIRY, Duration.ofSeconds(10), maximumAge::get, store, monitor);
    }

    private LastGoodSource<String> fakeSource(FakeFetcher fetcher, MutableClock sourceClock) {
        return new LastGoodSource<>(fetcher, sourceClock, Ticker.disabledTicker(), Runnable::run, CACHE_TIME, MAXIMUM_ENTRIES,
                IDLE_EXPIRY, REFRESH_WAIT, maximumAge::get, controlPlane.store, monitor);
    }

    private LastGoodSource<BitstringStatusListCredential> realSource(MutableClock sourceClock) {
        var stack = EdcTestStack.create();
        var fetcher = new BitstringStatusListFetcher(stack.mapper(), List.of("*/*"), stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver());
        return new LastGoodSource<>(fetcher, sourceClock, Ticker.disabledTicker(), Runnable::run, CACHE_TIME, MAXIMUM_ENTRIES,
                IDLE_EXPIRY, REFRESH_WAIT, maximumAge::get, controlPlane.store, monitor);
    }

    private Instance instanceOf(FakeIssuer issuer) {
        var globalConf = mock(GlobalConfProvider.class);
        when(globalConf.getInstanceIdentifier()).thenReturn("DEV");
        when(globalConf.getCredentialIssuerDids("DEV")).thenReturn(Set.of(issuer.did()));

        var didSource = new AtomicReference<LastGoodSource<DidDocument>>();
        var stack = EdcTestStack.create((stockResolver, didFetcher) -> {
            didSource.set(new LastGoodSource<>(didFetcher, clock, Ticker.disabledTicker(),
                    Runnable::run, Duration.ZERO, MAXIMUM_ENTRIES, IDLE_EXPIRY, REFRESH_WAIT, maximumAge::get,
                    controlPlane.store, monitor));
            return new IssuerDidResolver(stockResolver, didSource.get(), globalConf);
        });
        var fetcher = new BitstringStatusListFetcher(stack.mapper(), List.of("*/*"), stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver());
        var statusList = new LastGoodSource<>(fetcher, clock, Ticker.disabledTicker(), Runnable::run, CACHE_TIME,
                MAXIMUM_ENTRIES, IDLE_EXPIRY, REFRESH_WAIT, maximumAge::get, controlPlane.store, monitor);
        return new Instance(statusList, didSource.get());
    }

    private record Instance(LastGoodSource<BitstringStatusListCredential> statusList, LastGoodSource<DidDocument> did) {
    }

    private static void applyChangelog(String jdbcUrl, String changelog) throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", changelog);
            update.addArgumentValue("url", jdbcUrl);
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }

    private static final class Database {

        private final String name;
        private final String jdbcUrl;
        private final SqlIssuerDataStore store;

        private Database(String name, String jdbcUrl) {
            this.name = name;
            this.jdbcUrl = jdbcUrl;
            var registry = new DefaultDataSourceRegistry();
            registry.register(DataSourceRegistry.DEFAULT_DATASOURCE, dataSource());
            this.store = new SqlIssuerDataStore(registry, DataSourceRegistry.DEFAULT_DATASOURCE, new NoopTransactionContext(),
                    new SqlQueryExecutor());
        }

        private ConnectionFactoryDataSource dataSource() {
            var properties = new Properties();
            properties.put("user", POSTGRES.getUsername());
            properties.put("password", POSTGRES.getPassword());
            return new ConnectionFactoryDataSource(new DriverManagerConnectionFactory(), jdbcUrl, properties);
        }

        private SqlIssuerDataStore storeIn(LocalTransactionContext transactionContext) {
            var registry = new LocalDataSourceRegistry(transactionContext);
            registry.register(DataSourceRegistry.DEFAULT_DATASOURCE, dataSource());
            return new SqlIssuerDataStore(registry, DataSourceRegistry.DEFAULT_DATASOURCE, transactionContext,
                    new SqlQueryExecutor());
        }

        private boolean lockRowWithoutWaiting(String rowKey) {
            try (var connection = connect();
                 var statement = connection.prepareStatement("SELECT 1 FROM xroad_issuer_data WHERE key = ? FOR UPDATE NOWAIT")) {
                statement.setString(1, rowKey);
                try (var resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Row could not be locked", e);
            }
        }

        @Override
        public String toString() {
            return name;
        }

        private String rawColumn(String rowKey, String column) throws SQLException {
            try (var connection = connect();
                 var statement = connection.prepareStatement("SELECT %s::text FROM xroad_issuer_data WHERE key = ?".formatted(column))) {
                statement.setString(1, rowKey);
                try (var resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getString(1);
                }
            }
        }

        private int rowCount(String rowKey) throws SQLException {
            try (var connection = connect();
                 var statement = connection.prepareStatement("SELECT count(*) FROM xroad_issuer_data WHERE key = ?")) {
                statement.setString(1, rowKey);
                try (var resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getInt(1);
                }
            }
        }

        private void execute(String sql, String... parameters) throws SQLException {
            try (var connection = connect(); var statement = connection.prepareStatement(sql)) {
                for (var i = 0; i < parameters.length; i++) {
                    statement.setString(i + 1, parameters[i]);
                }
                statement.executeUpdate();
            }
        }

        private Connection connect() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
        }
    }
}
