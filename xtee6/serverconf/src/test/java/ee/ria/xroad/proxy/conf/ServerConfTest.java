/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.ServerConfImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServiceDAOImpl;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.ria.xroad.common.ErrorCodes.X_UNKNOWN_SERVICE;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.proxy.conf.TestUtil.*;
import static org.junit.Assert.*;

/**
 * Tests server conf API.
 */
public class ServerConfTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Creates test database.
     * @throws Exception if an error occurs
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        prepareDB();

        ServerConf.reload(new ServerConfImpl());
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
                ServerConf.getIdentifier().getOwner());
    }

    /**
     * Tests getting security server identififer.
     */
    @Test
    public void getIdentifier() {
        SecurityServerId expectedIdentifier =
                SecurityServerId.create(
                        XROAD_INSTANCE, MEMBER_CLASS, MEMBER_CODE, SERVER_CODE);
        assertEquals(expectedIdentifier, ServerConf.getIdentifier());
    }

    /**
     * Tests getting service address.
     */
    @Test
    public void getExistingServiceAddress() {
        ServiceId service = ServiceId.create(XROAD_INSTANCE, MEMBER_CLASS,
                client(1), null, service(1, 1), SERVICE_VERSION);
        assertTrue(ServerConf.serviceExists(service));
        assertEquals(SERVICE_URL + 1, ServerConf.getServiceAddress(service));
        assertEquals(SERVICE_TIMEOUT, ServerConf.getServiceTimeout(service));

        service = ServiceId.create(XROAD_INSTANCE, MEMBER_CLASS,
                client(1), null, service(1, NUM_SERVICES - 2), null);
        assertTrue(ServerConf.serviceExists(service));
    }

    /**
     * Tests getting all services.
     */
    @Test
    public void getAllServices() {
        ClientId serviceProvider = createTestClientId(client(1));

        List<ServiceId> expectedServices = new ArrayList<>();
        for (int i = 0; i < NUM_WSDLS; i++) {
            for (int j = 0; j < NUM_SERVICES; j++) {
                String version = j == NUM_SERVICES - 2 ? null : SERVICE_VERSION;
                expectedServices.add(createTestServiceId(serviceProvider,
                        service(i, j), version));
            }
        }

        assertEquals(expectedServices,
                ServerConf.getAllServices(serviceProvider));
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
                ServerConf.getAllowedServices(serviceProvider, client1));
        assertTrue(ServerConf.getAllowedServices(serviceProvider,
                client2).isEmpty());
    }

    /**
     * Tests getting non-existing service.
     */
    @Test
    public void getNonExistingServiceAddress() {
        ServiceId service = createTestServiceId("foo", "bar");
        assertFalse(ServerConf.serviceExists(service));
        assertNull(ServerConf.getServiceAddress(service));
        assertNotEquals(SERVICE_TIMEOUT,
                ServerConf.getServiceTimeout(service));
    }

    /**
     * Tests getting WSDL disabled notice.
     */
    @Test
    public void getDisabledNotice() {
        ServiceId existingService = createTestServiceId(client(1),
                service(NUM_WSDLS - 1, NUM_SERVICES - 1), SERVICE_VERSION);
        ServiceId nonExistingService = createTestServiceId("foo", "bar");

        assertNotNull(ServerConf.getDisabledNotice(existingService));
        assertNull(ServerConf.getDisabledNotice(nonExistingService));
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

        assertTrue(ServerConf.isQueryAllowed(client1, service1));
        assertFalse(ServerConf.isQueryAllowed(clientX, service1));
        assertFalse(ServerConf.isQueryAllowed(clientX, serviceX));
        assertFalse(ServerConf.isQueryAllowed(client1, serviceX));

        // TODO tests with local and global groups
    }

    /**
     * Tests getting conntector host.
     */
    @Test
    public void getConnectorHost() {
        String defaultHost = SystemProperties.getConnectorHost();
        assertEquals("0.0.0.0", defaultHost);

        String alteredHost = "127.0.0.1";
        System.setProperty(SystemProperties.PROXY_CONNECTOR_HOST, alteredHost);
        String newHost = SystemProperties.getConnectorHost();
        assertEquals(alteredHost, newHost);
    }

    /**
     * Tests getting required categories.
     */
    @Test
    public void getRequiredCategories() {
        ServiceId service1 = createTestServiceId(client(1),
                service(1, 1), SERVICE_VERSION);
        Collection<SecurityCategoryId> securityCategories =
                ServerConf.getRequiredCategories(service1);
        assertEquals(1, securityCategories.size());
        assertEquals(SecurityCategoryId.create(XROAD_INSTANCE,
                SECURITY_CATEGORY + 1), securityCategories.iterator().next());
    }

    /**
     * Tests getting IS authentication.
     */
    @Test
    public void getIsAuthentication() {
        assertEquals(IsAuthentication.SSLAUTH,
                ServerConf.getIsAuthentication(createTestClientId(client(1))));
        assertEquals(IsAuthentication.SSLNOAUTH,
                ServerConf.getIsAuthentication(createTestClientId(client(2))));
        assertEquals(IsAuthentication.NOSSL,
                ServerConf.getIsAuthentication(createTestClientId(client(3))));
    }

    /**
     * Tests getting IS certificates,
     * @throws Exception if an error coccurs
     */
    @Test
    public void getIsCerts() throws Exception {
        List<X509Certificate> isCerts =
                ServerConf.getIsCerts(createTestClientId(client(1)));
        assertEquals(1, isCerts.size());
        assertEquals(readCertificate(BASE64_CERT), isCerts.get(0));
    }

    /**
     * Tests getting SSL authentication.
     */
    @Test
    public void isSslAuthentication() {
        assertTrue(ServerConf.isSslAuthentication(
                createTestServiceId(client(1), service(1, 0),
                        SERVICE_VERSION)));
        assertFalse(ServerConf.isSslAuthentication(
                createTestServiceId(client(1), service(1, 1),
                        SERVICE_VERSION)));

        thrown.expectError(X_UNKNOWN_SERVICE);
        assertFalse(ServerConf.isSslAuthentication(
                createTestServiceId(client(1), service(1, NUM_SERVICES),
                        SERVICE_VERSION)));
    }

    /**
     * Tests getting members.
     * @throws Exception if an error coccurs
     */
    @Test
    public void getMembers() throws Exception {
        List<ClientId> members = ServerConf.getMembers();
        assertNotNull(members);
        assertEquals(NUM_CLIENTS, members.size());
    }

    /**
     * Tests getting TSPs.
     * @throws Exception if an error occurs
     */
    @Test
    public void getTsps() throws Exception {
        List<String> tspUrls = ServerConf.getTspUrl();
        assertEquals(NUM_TSPS, tspUrls.size());
    }

    /**
     * Tests getting services.
     * @throws Exception if an error occurs
     */
    @Test
    public void getServices() throws Exception {
        ClientId serviceProvider = createTestClientId(client(1), null);

        List<ServiceId> allServices = getServices(serviceProvider);
        assertEquals(NUM_WSDLS * NUM_SERVICES, allServices.size());

        serviceProvider = createTestClientId(client(NUM_CLIENTS - 1), null);

        allServices = getServices(serviceProvider);
        assertEquals(0, allServices.size());

        serviceProvider = createTestClientId(client(NUM_CLIENTS - 1),
                SUBSYSTEM);

        allServices = getServices(serviceProvider);
        assertEquals(NUM_WSDLS * NUM_SERVICES, allServices.size());
    }

    private static List<ServiceId> getServices(ClientId serviceProvider) {
        return new ServiceDAOImpl().getServices(
                ServerConfDatabaseCtx.get().getSession(),
                serviceProvider);
    }
}
