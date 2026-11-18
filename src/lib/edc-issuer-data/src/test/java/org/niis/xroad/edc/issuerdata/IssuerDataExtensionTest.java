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

import org.eclipse.edc.iam.verifiablecredentials.revocation.RevocationServiceRegistryImpl;
import org.eclipse.edc.iam.verifiablecredentials.revocation.bitstring.BitstringStatusListRevocationService;
import org.eclipse.edc.iam.verifiablecredentials.spi.RevocationListService;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.RevocationServiceRegistry;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.statuslist2021.StatusList2021Status;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_CREDENTIAL;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_INDEX;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_PURPOSE;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_SIZE;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IssuerDataExtensionTest {

    private static final String INSTANCE = "DEV";
    private static final String SUBJECT = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final int CLEAR_INDEX = 15;

    private final RevocationServiceRegistryImpl registry = new RevocationServiceRegistryImpl(mock(Monitor.class));
    private final RevocationListService statusList2021Service = mock(RevocationListService.class);
    private final GlobalConfProvider globalConf = mock(GlobalConfProvider.class);
    private final DataSourceRegistry dataSourceRegistry = mock(DataSourceRegistry.class);
    private final List<IssuerDataExtension> extensions = new ArrayList<>();
    private FakeIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        issuer.publishStatusList(Set.of(), null);
        when(globalConf.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConf.getIssuerDataFreshnessSeconds(INSTANCE)).thenReturn(60);
        registry.addService(StatusList2021Status.TYPE, statusList2021Service);
        initializeExtension();
    }

    @AfterEach
    void tearDown() {
        extensions.forEach(IssuerDataExtension::shutdown);
        issuer.close();
    }

    @Test
    void shouldVerifyBitstringCredentialFromLastGoodCopyWhenIssuerGoesDown() {
        assertThat(registry.checkValidity(credentialWith(bitstringStatus())).succeeded()).isTrue();
        issuer.down(true);

        assertThat(registry.checkValidity(credentialWith(bitstringStatus())).succeeded()).isTrue();
    }

    @Test
    void shouldBootWithoutTouchingDatabaseOrGlobalConf() {
        var freshGlobalConf = mock(GlobalConfProvider.class);
        var freshDataSources = mock(DataSourceRegistry.class);
        var freshRegistry = new RevocationServiceRegistryImpl(mock(Monitor.class));
        var stack = EdcTestStack.create();
        var typeManager = mock(TypeManager.class);
        when(typeManager.getMapper()).thenReturn(stack.mapper());
        var context = mock(ServiceExtensionContext.class);
        when(context.getMonitor()).thenReturn(mock(Monitor.class));
        when(context.getSetting(eq(IssuerDataExtension.CACHE_VALIDITY_SETTING), anyLong())).thenReturn(0L);
        when(context.getSetting(eq(IssuerDataExtension.CONTENT_TYPES_SETTING), anyString()))
                .thenReturn(IssuerDataExtension.DEFAULT_CONTENT_TYPES);
        issuer.down(true);
        var fresh = newExtension(freshRegistry, typeManager, stack, freshGlobalConf, freshDataSources);
        extensions.add(fresh);
        fresh.initialize(context);
        verifyNoInteractions(freshDataSources, freshGlobalConf);
    }

    @Test
    void shouldLeaveStatusList2021ToTheStockService() {
        var status = new CredentialStatus(SUBJECT, StatusList2021Status.TYPE, Map.of());
        when(statusList2021Service.checkValidity(status)).thenReturn(Result.success());

        var result = registry.checkValidity(credentialWith(status));

        assertThat(result.succeeded()).isTrue();
        verify(statusList2021Service).checkValidity(status);
    }

    @Test
    void shouldReadMaximumAgeOfOwnInstanceInSeconds() {
        var age = IssuerDataExtension.maximumAge(globalConf).get();

        assertThat(age.getContent()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldTreatNegativeMaximumAgeAsZero() {
        when(globalConf.getIssuerDataFreshnessSeconds(INSTANCE)).thenReturn(-5);

        var age = IssuerDataExtension.maximumAge(globalConf).get();

        assertThat(age.getContent()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldReportFailureWhenGlobalConfIsUnreadable() {
        when(globalConf.getInstanceIdentifier()).thenThrow(new IllegalStateException("no globalconf yet"));

        var age = IssuerDataExtension.maximumAge(globalConf).get();

        assertThat(age.failed()).isTrue();
    }

    private void initializeExtension() {
        var stack = EdcTestStack.create();
        var typeManager = mock(TypeManager.class);
        when(typeManager.getMapper()).thenReturn(stack.mapper());
        var context = mock(ServiceExtensionContext.class);
        when(context.getMonitor()).thenReturn(mock(Monitor.class));
        when(context.getSetting(eq(IssuerDataExtension.CACHE_VALIDITY_SETTING), anyLong())).thenReturn(0L);
        when(context.getSetting(eq(IssuerDataExtension.CONTENT_TYPES_SETTING), anyString()))
                .thenReturn(IssuerDataExtension.DEFAULT_CONTENT_TYPES);

        registry.addService(BitstringStatusListStatus.TYPE, new BitstringStatusListRevocationService(stack.mapper(), 0,
                List.of(IssuerDataExtension.DEFAULT_CONTENT_TYPES), stack.httpClient(), stack.tokenValidationService(),
                stack.didPublicKeyResolver()));

        var extension = newExtension(registry, typeManager, stack, globalConf, dataSourceRegistry);
        extensions.add(extension);
        extension.initialize(context);
    }
    private static IssuerDataExtension newExtension(RevocationServiceRegistry revocationServiceRegistry,
                                                    TypeManager typeManager, EdcTestStack stack,
                                                    GlobalConfProvider globalConfProvider,
                                                    DataSourceRegistry dataSources) {
        return new IssuerDataExtension(revocationServiceRegistry, typeManager, stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver(), globalConfProvider, dataSources,
                new NoopTransactionContext(), mock(QueryExecutor.class), ExecutorInstrumentation.noop(), null);
    }

    private CredentialStatus bitstringStatus() {
        return new CredentialStatus(SUBJECT, BitstringStatusListStatus.TYPE,
                Map.of(STATUS_LIST_PURPOSE, "revocation",
                        STATUS_LIST_INDEX, CLEAR_INDEX,
                        STATUS_LIST_SIZE, 1,
                        STATUS_LIST_CREDENTIAL, issuer.statusListUrl()));
    }

    private VerifiableCredential credentialWith(CredentialStatus status) {
        return VerifiableCredential.Builder.newInstance()
                .type("VerifiableCredential")
                .issuer(new Issuer(issuer.did()))
                .issuanceDate(Instant.now())
                .credentialSubject(CredentialSubject.Builder.newInstance().id(SUBJECT).claim("claim", "value").build())
                .credentialStatus(status)
                .build();
    }
}
