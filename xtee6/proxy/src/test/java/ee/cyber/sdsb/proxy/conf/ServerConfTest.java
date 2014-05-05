package ee.cyber.sdsb.proxy.conf;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.serverconf.ClientType;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static org.junit.Assert.*;

public class ServerConfTest {
    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Before
    public void setUp() throws Exception {
        System.setProperty(SystemProperties.KEY_CONFIGURATION_FILE,
                "src/test/keyconftest.xml");
        System.setProperty(SystemProperties.SERVER_CONFIGURATION_FILE,
                "src/test/serverconftest.xml");
        ServerConf.reload();
    }

    @Test
    public void getIdentifier() {
        SecurityServerId expectedIdentifier = SecurityServerId.create(
                "EE", "BUSINESS", "producer", "topSecret");
        assertEquals(expectedIdentifier, ServerConf.getIdentifier());
    }

    @Test
    public void getExistingServiceAddress() {
        ServiceId producerTestQuery = ServiceId.create("EE", "BUSINESS",
                "producer", "highSecurity", "testQuery");

        assertEquals("http://127.0.0.1:8081/testQuery",
                ServerConf.getServiceAddress(producerTestQuery));
    }

    @Test
    public void getNonExistingServiceAddress() {
        ServiceId naDoNotCreateDocument = newServiceId("N/A",
                "DO_NOT_createDocument");
        ServiceId doNotCreateDocument = newServiceId("-",
                "DO_NOT_createDocument");

        assertNull(ServerConf.getServiceAddress(naDoNotCreateDocument));
        assertNull(ServerConf.getServiceAddress(doNotCreateDocument));
    }

    @Test
    public void getOwner() {
        ClientId expectedOwner =
                ClientId.create("EE", "BUSINESS", "producer");

        assertEquals(expectedOwner, ServerConf.getIdentifier().getOwner());
    }

    @Test
    public void getMember() {
        TestServerConf sc = new TestServerConf(
                SystemProperties.getServerConfFile());

        ClientId expectedMember1 =
                ClientId.create("EE", "BUSINESS", "subwoofer");
        assertTrue(sc.hasMember(expectedMember1));

        ClientId expectedMember2 =
                ClientId.create("EE", "ettevõte", "õäö");
        assertTrue(sc.hasMember(expectedMember2));
    }

    @Test
    public void isQueryAllowed() {
        ClientId consumer = ClientId.create("EE", "BUSINESS", "consumer");
        ClientId vitaliFaktulin = ClientId.create("LV", "MEIECUNDIMEES",
                "vitaliFaktulin", "saldejums");
        ClientId randomClient = ClientId.create("MM", "MMM", "m", "m");

        ServiceId producerTestQuery = ServiceId.create("EE", "BUSINESS",
                "producer", "highSecurity", "testQuery");
        ServiceId producerArvutaSuurused = getProducerArvutaSuurused();
        ServiceId pihvivabrikArvutaSuurused = ServiceId.create("EE", "BUSINESS",
                "pihvivabrik", null, "arvutaSuurused");
        ServiceId randomOrgRandomQuery = ServiceId.create("US", "PRESIDENT",
                "hardcore", null, "haveFun");

        assertTrue(ServerConf.isQueryAllowed(consumer,
                producerTestQuery));
        assertTrue(ServerConf.isQueryAllowed(vitaliFaktulin,
                producerArvutaSuurused));
        assertFalse(ServerConf.isQueryAllowed(vitaliFaktulin,
                pihvivabrikArvutaSuurused));
        assertFalse(ServerConf.isQueryAllowed(randomClient,
                randomOrgRandomQuery));
    }


    @Test
    public void isQueryAllowedForClientInGroup() {
        ClientId natasha = ClientId.create("RU", "SPETSNAZ", "natasha");

        assertTrue(ServerConf.isQueryAllowed(natasha,
                getProducerArvutaSuurused()));
    }

    @Test
    public void getConnectorHost() {
        String defaultHost = ServerConf.getConnectorHost();
        assertEquals("0.0.0.0", defaultHost);

        String alteredHost = "127.0.0.1";
        System.setProperty(SystemProperties.PROXY_CONNECTOR_HOST, alteredHost);
        String newHost = ServerConf.getConnectorHost();
        assertEquals(alteredHost, newHost);
    }

    private static ServiceId newServiceId(String memberCode,
            String serviceCode) {
        return ServiceId.create("EE", "BUSINESS", memberCode, null,
                serviceCode);
    }

    private ServiceId getProducerArvutaSuurused() {
        return ServiceId.create("EE", "BUSINESS",
                "producer", "highSecurity", "arvutaSuurused");
    }

    private class TestServerConf extends ServerConfImpl {

        public TestServerConf(String confFileName) {
            super(confFileName);
        }

        private boolean hasMember(ClientId memberId) {
            for (ClientType member : confType.getClient()) {
                ClientId client = member.getIdentifier();
                if (memberId.equals(client) ||
                        client.subsystemContainsMember(memberId)) {
                    return true;
                }
            }

            return false;
        }
    }
}
