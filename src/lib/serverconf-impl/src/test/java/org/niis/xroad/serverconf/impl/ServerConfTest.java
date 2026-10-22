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
package org.niis.xroad.serverconf.impl;

import ee.ria.xroad.common.ExpectedXrdRuntimeException;
import ee.ria.xroad.common.ServicePrioritizationStrategy;
import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.niis.xroad.common.CostType;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.dao.ServiceDAOImpl;
import org.niis.xroad.serverconf.model.Endpoint;
import org.niis.xroad.test.globalconf.TestGlobalConfFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_SERVICE;
import static org.niis.xroad.serverconf.impl.TestUtil.BASE64_CERT;
import static org.niis.xroad.serverconf.impl.TestUtil.CLIENT_CODE;
import static org.niis.xroad.serverconf.impl.TestUtil.CROSS_SS_GROUP_CODE;
import static org.niis.xroad.serverconf.impl.TestUtil.CROSS_SS_MEMBER_CODE;
import static org.niis.xroad.serverconf.impl.TestUtil.CROSS_SS_SUBSYSTEM_CODE;
import static org.niis.xroad.serverconf.impl.TestUtil.MEMBER_CLASS;
import static org.niis.xroad.serverconf.impl.TestUtil.MEMBER_CODE;
import static org.niis.xroad.serverconf.impl.TestUtil.NUM_CLIENTS;
import static org.niis.xroad.serverconf.impl.TestUtil.NUM_SERVICEDESCRIPTIONS;
import static org.niis.xroad.serverconf.impl.TestUtil.NUM_SERVICES;
import static org.niis.xroad.serverconf.impl.TestUtil.NUM_TSPS;
import static org.niis.xroad.serverconf.impl.TestUtil.SERVER_CODE;
import static org.niis.xroad.serverconf.impl.TestUtil.SERVICE_CODE;
import static org.niis.xroad.serverconf.impl.TestUtil.SERVICE_DISABLED_BLANK_NOTICE;
import static org.niis.xroad.serverconf.impl.TestUtil.SERVICE_DISABLED_NULL_NOTICE;
import static org.niis.xroad.serverconf.impl.TestUtil.SERVICE_TIMEOUT;
import static org.niis.xroad.serverconf.impl.TestUtil.SERVICE_URL;
import static org.niis.xroad.serverconf.impl.TestUtil.SERVICE_VERSION;
import static org.niis.xroad.serverconf.impl.TestUtil.SUBSYSTEM;
import static org.niis.xroad.serverconf.impl.TestUtil.XROAD_INSTANCE;
import static org.niis.xroad.serverconf.impl.TestUtil.addDisabledService;
import static org.niis.xroad.serverconf.impl.TestUtil.addRestGrant;
import static org.niis.xroad.serverconf.impl.TestUtil.client;
import static org.niis.xroad.serverconf.impl.TestUtil.createTestClientId;
import static org.niis.xroad.serverconf.impl.TestUtil.createTestServiceId;
import static org.niis.xroad.serverconf.impl.TestUtil.prepareDB;
import static org.niis.xroad.serverconf.impl.TestUtil.serverConfDbProperties;
import static org.niis.xroad.serverconf.impl.TestUtil.service;

/**
 * Tests server conf API.
 */
public class ServerConfTest {

    @Rule
    public ExpectedXrdRuntimeException thrown = ExpectedXrdRuntimeException.none();

    private static ServerConfProvider serverConfProvider;

    private static final DatabaseCtx DATABASE_CTX = new DatabaseCtx("serverconf", serverConfDbProperties.hibernate());

    /**
     * Creates test database.
     *
     * @throws Exception if an error occurs
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        var globalConfProvider = TestGlobalConfFactory.create("dummy-path-will-not-be-initialized");
        serverConfProvider = new ServerConfImpl(DATABASE_CTX, globalConfProvider, null);

        prepareDB(DATABASE_CTX);
        addRestGrant(DATABASE_CTX, createTestClientId(client(1)), "restBase", "GET", "**");
        addRestGrant(DATABASE_CTX, createTestClientId(client(1)), "restPublic", "GET", "/public");
        addDisabledService(DATABASE_CTX, createTestClientId(client(2)), SERVICE_DISABLED_NULL_NOTICE, null);
        addDisabledService(DATABASE_CTX, createTestClientId(client(2)), SERVICE_DISABLED_BLANK_NOTICE, " ");
    }

    @AfterClass
    public static void afterClass() {
        DATABASE_CTX.destroy();
    }

    /**
     * Begins transaction.
     */
    @Before
    public void beforeTest() {
        DATABASE_CTX.beginTransaction();
    }

    /**
     * Commits transaction.
     */
    @After
    public void afterTest() {
        DATABASE_CTX.commitTransaction();
    }

    /**
     * Test getting owner.
     */
    @Test
    public void getOwner() {
        assertEquals(createTestClientId(),
                serverConfProvider.getIdentifier().getOwner());
    }

    /**
     * Tests getting security server identififer.
     */
    @Test
    public void getIdentifier() {
        SecurityServerId.Conf expectedIdentifier =
                SecurityServerId.Conf.create(
                        XROAD_INSTANCE, MEMBER_CLASS, MEMBER_CODE, SERVER_CODE);
        assertEquals(expectedIdentifier, serverConfProvider.getIdentifier());
    }

    /**
     * Tests getting service address.
     */
    @Test
    public void getExistingServiceAddress() {
        ServiceId.Conf service = ServiceId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS,
                client(1), null, service(1, 1), SERVICE_VERSION);
        assertTrue(serverConfProvider.serviceExists(service));
        assertEquals(SERVICE_URL + 1, serverConfProvider.getServiceAddress(service));
        assertEquals(SERVICE_TIMEOUT, serverConfProvider.getServiceTimeout(service));

        service = ServiceId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS,
                client(1), null, service(1, NUM_SERVICES - 2), null);
        assertTrue(serverConfProvider.serviceExists(service));
    }

    /**
     * Tests getting all services.
     */
    @Test
    public void getAllServices() {
        ClientId serviceProvider = createTestClientId(client(1));

        List<ServiceId> expectedServices = new ArrayList<>();
        for (int i = 0; i < NUM_SERVICEDESCRIPTIONS; i++) {
            for (int j = 0; j < NUM_SERVICES; j++) {
                String version = j == NUM_SERVICES - 2 ? null : SERVICE_VERSION;
                expectedServices.add(createTestServiceId(serviceProvider,
                        service(i, j), version));
            }
        }

        assertEquals(expectedServices,
                serverConfProvider.getAllServices(serviceProvider));
    }

    /**
     * Tests getting allowed services.
     */
    @Test
    public void getAllowedServices() {
        ClientId serviceProvider = createTestClientId(client(1));
        ClientId client1 = createTestClientId(client(1));
        ClientId client2 = createTestClientId(client(2));

        List<ServiceId> expectedServices = List.of(
                createTestServiceId(serviceProvider,
                        service(1, 1), SERVICE_VERSION));

        assertEquals(expectedServices,
                serverConfProvider.getAllowedServices(serviceProvider, client1));
        assertTrue(serverConfProvider.getAllowedServices(serviceProvider,
                client2).isEmpty());
    }

    /**
     * Tests getting non-existing service.
     */
    @Test
    public void getNonExistingServiceAddress() {
        ServiceId service = createTestServiceId("foo", "bar");
        assertFalse(serverConfProvider.serviceExists(service));
        assertNull(serverConfProvider.getServiceAddress(service));
        assertNotEquals(SERVICE_TIMEOUT,
                serverConfProvider.getServiceTimeout(service));
    }

    /**
     * Tests getting service description disabled notice.
     */
    @Test
    public void getDisabledNotice() {
        ServiceId existingService = createTestServiceId(client(1),
                service(NUM_SERVICEDESCRIPTIONS - 1, NUM_SERVICES - 1), SERVICE_VERSION);
        ServiceId nonExistingService = createTestServiceId("foo", "bar");
        ServiceId enabledService = createTestServiceId(client(1), service(0, 0), SERVICE_VERSION);

        assertEquals("disabledNotice", serverConfProvider.getDisabledNotice(existingService));
        assertNull(serverConfProvider.getDisabledNotice(enabledService));
        assertNull(serverConfProvider.getDisabledNotice(nonExistingService));
    }

    /**
     * Tests that a disabled service description without notice text reports the default notice.
     */
    @Test
    public void getDisabledNoticeDefaultsWhenNoticeIsNullOrBlank() {
        ServiceId nullNoticeService = createTestServiceId(client(2), SERVICE_DISABLED_NULL_NOTICE, SERVICE_VERSION);
        ServiceId blankNoticeService = createTestServiceId(client(2), SERVICE_DISABLED_BLANK_NOTICE, SERVICE_VERSION);

        assertEquals(String.format("Service '%s' is disabled", nullNoticeService),
                serverConfProvider.getDisabledNotice(nullNoticeService));
        assertEquals(String.format("Service '%s' is disabled", blankNoticeService),
                serverConfProvider.getDisabledNotice(blankNoticeService));
    }

    /**
     * Tests query allowed.
     */
    @Test
    public void isQueryAllowed() {
        ClientId client1 = createTestClientId(client(1));
        ClientId clientX = createTestClientId(CLIENT_CODE + "X");
        ServiceId service1 = createTestServiceId(client1.getMemberCode(),
                service(1, 1), SERVICE_VERSION);
        ServiceId serviceX = createTestServiceId(client1.getMemberCode(),
                SERVICE_CODE + "X", SERVICE_VERSION + "X");
        ServiceId serviceRest = createTestServiceId(client1.getMemberCode(), "rest", null);

        assertTrue(serverConfProvider.isQueryAllowed(client1, service1));
        assertTrue(serverConfProvider.isQueryAllowed(client1, service1, "POST", "/"));
        assertFalse(serverConfProvider.isQueryAllowed(clientX, service1));
        assertFalse(serverConfProvider.isQueryAllowed(clientX, serviceX));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceX));

        assertTrue(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/api/foo"));

        assertTrue(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/test/foo"));
        assertTrue(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/t%65st/foo"));
        assertTrue(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/t%65st/foo%2dbar"));
        assertTrue(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/test/foo/../bar"));

        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/test%2Dbar"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/test/../bar"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/api/test/../../../api/test"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/test/foo/bar"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "DELETE", "/api/test"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest));

        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/%2e%2e/secret"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/api/%2e%2e/secret"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/api/test/%2e%2e/%2e%2e/secret"));
    }

    /**
     * Tests that ambiguous request paths are not allowed.
     */
    @Test
    public void isQueryAllowedRejectsAmbiguousPaths() {
        ClientId client1 = createTestClientId(client(1));
        ServiceId serviceRest = createTestServiceId(client1.getMemberCode(), "rest", null);
        ServiceId serviceBase = createTestServiceId(client1.getMemberCode(), "restBase", null);
        ServiceId servicePublic = createTestServiceId(client1.getMemberCode(), "restPublic", null);

        assertTrue(serverConfProvider.isQueryAllowed(client1, servicePublic, "GET", "/public"));
        assertTrue(serverConfProvider.isQueryAllowed(client1, serviceBase, "GET", "/secret"));

        assertFalse(serverConfProvider.isQueryAllowed(client1, servicePublic, "GET", "/public%3Fx"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/test/1%3F/../../admin"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/api/foo%23x"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceBase, "GET", "/%2F../secret"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/%2F../api/x"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/test/.;x/../y"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/api/test/;x/../../admin"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "POST", "/api/test%252Fbar"));
        assertFalse(serverConfProvider.isQueryAllowed(client1, serviceRest, "GET", "/api/foo bar"));
    }

    /**
     * Tests the owner-scoped local-group membership check DSP uses: it matches a group member by its
     * member-level identity regardless of whether that member is a client of this security server,
     * and it never falls back to a group some other client happens to own under the same code.
     */
    @Test
    public void isLocalGroupMember() {
        ClientId owner = createTestClientId(client(1));
        ClientId otherOwner = createTestClientId(client(2));
        LocalGroupId groupId = LocalGroupId.Conf.create(CROSS_SS_GROUP_CODE);

        ClientId crossSsMember = ClientId.Conf.create(XROAD_INSTANCE, MEMBER_CLASS, CROSS_SS_MEMBER_CODE);
        ClientId crossSsMemberOtherSubsystem = ClientId.Conf.create(
                XROAD_INSTANCE, MEMBER_CLASS, CROSS_SS_MEMBER_CODE, "otherSubsystem");
        ClientId otherMemberSameSubsystemCode = ClientId.Conf.create(
                XROAD_INSTANCE, MEMBER_CLASS, "notTheCrossSsMember", CROSS_SS_SUBSYSTEM_CODE);

        // Owner's group contains the consumer subsystem; the consumer is not a client of this SS.
        assertTrue(serverConfProvider.isLocalGroupMember(owner, groupId, crossSsMember));

        // Matches by member part only: a different subsystem of the same member still matches.
        assertTrue(serverConfProvider.isLocalGroupMember(owner, groupId, crossSsMemberOtherSubsystem));

        // A different member with the same subsystem code does not match.
        assertFalse(serverConfProvider.isLocalGroupMember(owner, groupId, otherMemberSameSubsystemCode));

        // otherOwner owns a group with the same code, but otherOwner itself is not a member of
        // owner's group: scoping to the owner must not fall back to the consumer's own groups.
        assertFalse(serverConfProvider.isLocalGroupMember(owner, groupId, otherOwner));

        // An owner id that is not a client of this security server fails closed.
        ClientId unknownOwner = createTestClientId(CLIENT_CODE + "Unknown");
        assertFalse(serverConfProvider.isLocalGroupMember(unknownOwner, groupId, crossSsMember));

        // A group code the owner does not have fails closed.
        assertFalse(serverConfProvider.isLocalGroupMember(owner, LocalGroupId.Conf.create("noSuchGroup"), crossSsMember));
    }

    /**
     * Tests getting the ACL endpoint entries a client holds for a service — the same entries
     * {@link #isQueryAllowed()} matches a request against.
     */
    @Test
    public void getAclEndpoints() {
        ClientId client1 = createTestClientId(client(1));
        ClientId clientX = createTestClientId(CLIENT_CODE + "X");
        ServiceId serviceRest = createTestServiceId(client1.getMemberCode(), "rest", null);

        List<Endpoint> endpoints = serverConfProvider.getAclEndpoints(client1, serviceRest);

        assertEquals(2, endpoints.size());
        assertTrue(endpoints.stream().anyMatch(e -> "GET".equals(e.getMethod()) && "/api/**".equals(e.getPath())));
        assertTrue(endpoints.stream().anyMatch(e -> "POST".equals(e.getMethod()) && "/api/test/*".equals(e.getPath())));

        assertTrue(serverConfProvider.getAclEndpoints(clientX, serviceRest).isEmpty());
    }

    /**
     * Tests getting IS authentication.
     */
    @Test
    public void getIsAuthentication() {
        assertEquals(IsAuthentication.SSLAUTH,
                serverConfProvider.getIsAuthentication(createTestClientId(client(1))));
        assertEquals(IsAuthentication.SSLNOAUTH,
                serverConfProvider.getIsAuthentication(createTestClientId(client(2))));
        assertEquals(IsAuthentication.NOSSL,
                serverConfProvider.getIsAuthentication(createTestClientId(client(3))));
    }

    /**
     * Tests getting IS certificates,
     */
    @Test
    public void getIsCerts() {
        List<X509Certificate> isCerts =
                serverConfProvider.getIsCerts(createTestClientId(client(1)));
        assertEquals(1, isCerts.size());
        assertEquals(CryptoUtils.readCertificate(BASE64_CERT), isCerts.getFirst());
    }

    /**
     * Tests getting SSL authentication.
     */
    @Test
    public void isSslAuthentication() {
        assertTrue(serverConfProvider.isSslAuthentication(
                createTestServiceId(client(1), service(1, 0),
                        SERVICE_VERSION)));
        assertFalse(serverConfProvider.isSslAuthentication(
                createTestServiceId(client(1), service(1, 1),
                        SERVICE_VERSION)));

        thrown.expectError(UNKNOWN_SERVICE.code());
        assertFalse(serverConfProvider.isSslAuthentication(
                createTestServiceId(client(1), service(1, NUM_SERVICES),
                        SERVICE_VERSION)));
    }

    /**
     * Tests getting members.
     */
    @Test
    public void getMembers() {
        List<ClientId.Conf> members = serverConfProvider.getMembers();
        assertNotNull(members);
        assertEquals(NUM_CLIENTS, members.size());
    }

    /**
     * Tests getting TSPs.
     */
    @Test
    public void getTsps() {
        List<String> tspUrls = serverConfProvider.getTspUrls();
        assertEquals(NUM_TSPS, tspUrls.size());
        for (int i = 0; i < NUM_TSPS; i++) {
            assertEquals(String.format("tspUrl%d", i), tspUrls.get(i));
        }
    }

    @Test
    public void getOrderedTspUrls() {
        List<String> tspUrls = serverConfProvider.getOrderedTspUrls(ServicePrioritizationStrategy.ONLY_FREE);
        assertThat(tspUrls).containsExactly(
                "tspUrl1",
                "tspUrl4");

        tspUrls = serverConfProvider.getOrderedTspUrls(ServicePrioritizationStrategy.FREE_FIRST);
        assertThat(tspUrls).containsExactly(
                "tspUrl1",
                "tspUrl4",
                "tspUrl0",
                "tspUrl3",
                "tspUrl2");

        tspUrls = serverConfProvider.getOrderedTspUrls(ServicePrioritizationStrategy.ONLY_PAID);
        assertThat(tspUrls).containsExactly(
                "tspUrl0",
                "tspUrl3");


        tspUrls = serverConfProvider.getOrderedTspUrls(ServicePrioritizationStrategy.PAID_FIRST);
        assertThat(tspUrls).containsExactly(
                "tspUrl0",
                "tspUrl3",
                "tspUrl1",
                "tspUrl4",
                "tspUrl2");

        tspUrls = serverConfProvider.getOrderedTspUrls(ServicePrioritizationStrategy.NONE);
        assertThat(tspUrls).containsExactly(
                "tspUrl0",
                "tspUrl1",
                "tspUrl2",
                "tspUrl3",
                "tspUrl4");
    }

    @Test
    public void getTspCostType() {
        CostType costType0 = serverConfProvider.getTspCostType("tspUrl0");
        CostType costType1 = serverConfProvider.getTspCostType("tspUrl1");
        CostType costType2 = serverConfProvider.getTspCostType("tspUrl2");
        assertEquals(CostType.PAID, costType0);
        assertEquals(CostType.FREE, costType1);
        assertEquals(CostType.UNDEFINED, costType2);
    }

    /**
     * Tests getting services.
     */
    @Test
    public void getServices() {
        ClientId serviceProvider = createTestClientId(client(1), null);

        List<ServiceId.Conf> allServices = getServices(serviceProvider);
        assertEquals(NUM_SERVICEDESCRIPTIONS * NUM_SERVICES, allServices.size());

        serviceProvider = createTestClientId(client(NUM_CLIENTS - 1), null);

        allServices = getServices(serviceProvider);
        assertEquals(0, allServices.size());

        serviceProvider = createTestClientId(client(NUM_CLIENTS - 1),
                SUBSYSTEM);

        allServices = getServices(serviceProvider);
        assertEquals(NUM_SERVICEDESCRIPTIONS * NUM_SERVICES, allServices.size());
    }

    private static List<ServiceId.Conf> getServices(ClientId serviceProviderId) {
        return new ServiceDAOImpl().getServices(DATABASE_CTX.getSession(), serviceProviderId);
    }
}
