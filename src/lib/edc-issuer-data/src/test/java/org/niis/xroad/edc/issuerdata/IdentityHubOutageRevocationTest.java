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

import org.eclipse.edc.iam.decentralizedclaims.spi.model.PresentationQueryMessage;
import org.eclipse.edc.iam.verifiablecredentials.revocation.RevocationServiceRegistryImpl;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialFormat;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialStatus;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredentialContainer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus;
import org.eclipse.edc.identityhub.core.services.query.CredentialQueryResolverImpl;
import org.eclipse.edc.identityhub.core.services.verifiablecredential.CredentialStatusCheckServiceImpl;
import org.eclipse.edc.identityhub.spi.transformation.ScopeToCriterionTransformer;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VcStatus;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.model.VerifiableCredentialResource;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.store.CredentialStore;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.test.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_CREDENTIAL;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_INDEX;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_PURPOSE;
import static org.eclipse.edc.iam.verifiablecredentials.spi.model.revocation.bitstringstatuslist.BitstringStatusListStatus.STATUS_LIST_SIZE;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityHubOutageRevocationTest {

    private static final String SUBJECT = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final String PARTICIPANT_CONTEXT = "DEV-COM-222";
    private static final String SCOPE = "org.eclipse.edc.vc.type:MembershipCredential:read";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final Duration WITHIN_DEADLINE = CACHE_TIME.plusSeconds(100);
    private static final Duration AFTER_DEADLINE = CACHE_TIME.plusSeconds(120);
    private static final int CLEAR_INDEX = 15;
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(2);
    private static final int REPEATED_CHECKS = 3;

    private final MutableClock clock = new MutableClock(START);
    private final ExecutorService refreshPool = Executors.newCachedThreadPool();
    private final CredentialStore credentialStore = mock(CredentialStore.class);
    private final ScopeToCriterionTransformer scopeTransformer = mock(ScopeToCriterionTransformer.class);
    private FakeIssuer issuer;
    private CredentialStatusCheckServiceImpl statusCheck;
    private CredentialQueryResolverImpl queryResolver;
    private VerifiableCredentialResource membershipCredential;

    @BeforeEach
    void setUp() {
        issuer = new FakeIssuer();
        issuer.publishStatusList(Set.of(), null);
        var stack = EdcTestStack.create();
        var contentTypes = List.of("*/*");
        var store = new InMemoryIssuerDataStore();
        var fetcher = new BitstringStatusListFetcher(stack.mapper(), contentTypes, stack.httpClient(),
                stack.tokenValidationService(), stack.didPublicKeyResolver());
        var source = TestSources.source(fetcher, clock, refreshPool, CACHE_TIME, Duration.ofSeconds(10),
                () -> Result.success(Duration.ofSeconds(120)), store);
        var registry = new RevocationServiceRegistryImpl(mock(Monitor.class));
        registry.addService(BitstringStatusListStatus.TYPE, new XRoadBitstringStatusListRevocationService(
                stack.mapper(), contentTypes, stack.httpClient(), stack.tokenValidationService(),
                stack.didPublicKeyResolver(), source));
        statusCheck = new CredentialStatusCheckServiceImpl(registry, clock);
        queryResolver = new CredentialQueryResolverImpl(credentialStore, scopeTransformer, registry,
                mock(Monitor.class), clock);
        membershipCredential = membershipCredential();
        when(scopeTransformer.transformScope(anyString(), anyString()))
                .thenReturn(Result.success(List.of(new Criterion("type", "=", "MembershipCredential"))));
        when(credentialStore.query(any())).thenReturn(StoreResult.success(List.of(membershipCredential)));
    }

    @AfterEach
    void tearDown() {
        refreshPool.shutdownNow();
        issuer.close();
    }

    @Test
    void shouldKeepCredentialIssuedUntilDeadlineAndFailAfterWhenIssuerIsDown() {
        assertThat(statusCheck.checkStatus(membershipCredential).getContent()).isEqualTo(VcStatus.ISSUED);
        issuer.down(true);

        clock.set(START.plus(WITHIN_DEADLINE));
        var withinDeadline = statusCheck.checkStatus(membershipCredential);
        assertThat(withinDeadline.succeeded()).isTrue();
        assertThat(withinDeadline.getContent()).isEqualTo(VcStatus.ISSUED);

        clock.set(START.plus(AFTER_DEADLINE));
        assertThat(statusCheck.checkStatus(membershipCredential).failed()).isTrue();
    }

    @Test
    void shouldPresentCredentialUntilDeadlineAndLeaveItOutAfterWhenIssuerIsDown() {
        assertThat(presentedCredentials()).hasSize(1);
        issuer.down(true);

        clock.set(START.plus(WITHIN_DEADLINE));
        assertThat(presentedCredentials()).hasSize(1);

        clock.set(START.plus(AFTER_DEADLINE));
        assertThat(presentedCredentials()).isEmpty();
    }

    @Test
    void shouldStartOneDownloadForRepeatedChecksDuringOutageWhenCopyIsPastItsCacheTime() {
        assertThat(statusCheck.checkStatus(membershipCredential).getContent()).isEqualTo(VcStatus.ISSUED);
        var requestsBeforeOutage = issuer.statusListRequests();
        issuer.down(true);
        clock.set(START.plus(CACHE_TIME).plusSeconds(1));
        assertThat(checkPromptly().getContent()).isEqualTo(VcStatus.ISSUED);
        await().atMost(CHECK_TIMEOUT).until(() -> issuer.statusListRequests() == requestsBeforeOutage + 1);
        for (var i = 0; i < REPEATED_CHECKS; i++) {
            assertThat(checkPromptly().getContent()).isEqualTo(VcStatus.ISSUED);
            assertThat(assertTimeoutPreemptively(CHECK_TIMEOUT, this::presentedCredentials)).hasSize(1);
        }
        assertThat(issuer.statusListRequests()).isEqualTo(requestsBeforeOutage + 1);
    }

    private Result<VcStatus> checkPromptly() {
        return assertTimeoutPreemptively(CHECK_TIMEOUT, () -> statusCheck.checkStatus(membershipCredential));
    }

    private List<VerifiableCredentialContainer> presentedCredentials() {
        var query = PresentationQueryMessage.Builder.newinstance().scope(SCOPE).build();
        var result = queryResolver.query(PARTICIPANT_CONTEXT, query, List.of(SCOPE));
        assertThat(result.succeeded()).isTrue();
        return result.getContent().toList();
    }

    private VerifiableCredentialResource membershipCredential() {
        var status = new CredentialStatus(SUBJECT, BitstringStatusListStatus.TYPE,
                Map.of(STATUS_LIST_PURPOSE, "revocation",
                        STATUS_LIST_INDEX, CLEAR_INDEX,
                        STATUS_LIST_SIZE, 1,
                        STATUS_LIST_CREDENTIAL, issuer.statusListUrl()));
        var credential = VerifiableCredential.Builder.newInstance()
                .type("MembershipCredential")
                .issuer(new Issuer(issuer.did()))
                .issuanceDate(START.minus(Duration.ofHours(1)))
                .credentialSubject(CredentialSubject.Builder.newInstance().id(SUBJECT).claim("claim", "value").build())
                .credentialStatus(status)
                .build();
        return VerifiableCredentialResource.Builder.newHolder()
                .participantContextId(PARTICIPANT_CONTEXT)
                .issuerId(issuer.did())
                .holderId(SUBJECT)
                .state(VcStatus.ISSUED)
                .credential(new VerifiableCredentialContainer("raw-credential", CredentialFormat.VC1_0_JWT, credential))
                .build();
    }
}
