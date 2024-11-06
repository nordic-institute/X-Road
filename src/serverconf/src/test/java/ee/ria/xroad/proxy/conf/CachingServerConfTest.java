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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.CachingServerConfImpl;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.conf.serverconf.dao.ServiceDAOImpl;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.ria.xroad.proxy.conf.TestUtil.BASE64_CERT;
import static ee.ria.xroad.proxy.conf.TestUtil.CLIENT_CODE;
import static ee.ria.xroad.proxy.conf.TestUtil.MEMBER_CLASS;
import static ee.ria.xroad.proxy.conf.TestUtil.MEMBER_CODE;
import static ee.ria.xroad.proxy.conf.TestUtil.NUM_CLIENTS;
import static ee.ria.xroad.proxy.conf.TestUtil.NUM_SERVICEDESCRIPTIONS;
import static ee.ria.xroad.proxy.conf.TestUtil.NUM_SERVICES;
import static ee.ria.xroad.proxy.conf.TestUtil.NUM_TSPS;
import static ee.ria.xroad.proxy.conf.TestUtil.SERVER_CODE;
import static ee.ria.xroad.proxy.conf.TestUtil.SERVICE_CODE;
import static ee.ria.xroad.proxy.conf.TestUtil.SERVICE_TIMEOUT;
import static ee.ria.xroad.proxy.conf.TestUtil.SERVICE_URL;
import static ee.ria.xroad.proxy.conf.TestUtil.SERVICE_VERSION;
import static ee.ria.xroad.proxy.conf.TestUtil.SUBSYSTEM;
import static ee.ria.xroad.proxy.conf.TestUtil.XROAD_INSTANCE;
import static ee.ria.xroad.proxy.conf.TestUtil.client;
import static ee.ria.xroad.proxy.conf.TestUtil.createTestClientId;
import static ee.ria.xroad.proxy.conf.TestUtil.createTestServiceId;
import static ee.ria.xroad.proxy.conf.TestUtil.prepareDB;
import static ee.ria.xroad.proxy.conf.TestUtil.service;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests server conf API.
 */
public class CachingServerConfTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    private static GlobalConfProvider globalConfProvider;
    private static ServerConfProvider serverConfProvider;

    /**
     * Creates test database.
     *
     * @throws Exception if an error occurs
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        prepareDB();

        globalConfProvider = new EmptyGlobalConf() {
            @Override
            public boolean isSecurityServerClient(ClientId client, SecurityServerId securityServer) {
                return true;
            }
        };
        serverConfProvider = new CachingServerConfImpl(globalConfProvider);
    }

    /**
     * Begins transaction.
     */
    @Before
    public void beforeTest() {
        ServerConfDatabaseCtx.get().beginTransaction();
    }

    /**
     * Commits transaction.
     */
    @After
    public void afterTest() {
        ServerConfDatabaseCtx.get().commitTransaction();
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
        ClientId.Conf serviceProvider = createTestClientId(client(1));

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

        List<ServiceId> expectedServices = Arrays.asList(
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

        assertNotNull(serverConfProvider.getDisabledNotice(existingService));
        assertNull(serverConfProvider.getDisabledNotice(nonExistingService));
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
     *
     * @throws Exception if an error coccurs
     */
    @Test
    public void getIsCerts() throws Exception {
        List<X509Certificate> isCerts =
                serverConfProvider.getIsCerts(createTestClientId(client(1)));
        assertEquals(1, isCerts.size());
        assertEquals(CryptoUtils.readCertificate(BASE64_CERT), isCerts.get(0));
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

        thrown.expectError(X_UNKNOWN_SERVICE);
        assertFalse(serverConfProvider.isSslAuthentication(
                createTestServiceId(client(1), service(1, NUM_SERVICES),
                        SERVICE_VERSION)));
    }

    /**
     * Tests getting members.
     *
     * @throws Exception if an error coccurs
     */
    @Test
    public void getMembers() throws Exception {
        List<ClientId.Conf> members = serverConfProvider.getMembers();
        assertNotNull(members);
        assertEquals(NUM_CLIENTS, members.size());
    }

    /**
     * Tests getting TSPs.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getTsps() throws Exception {
        List<String> tspUrls = serverConfProvider.getTspUrl();
        assertEquals(NUM_TSPS, tspUrls.size());
    }

    /**
     * Tests getting services.
     *
     * @throws Exception if an error occurs
     */
    @Test
    public void getServices() throws Exception {
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

    private static List<ServiceId.Conf> getServices(ClientId serviceProvider) {
        return new ServiceDAOImpl().getServices(
                ServerConfDatabaseCtx.get().getSession(),
                serviceProvider);
    }
}
