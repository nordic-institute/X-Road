package ee.cyber.sdsb.common.conf.globalconf;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestCertUtil.PKCS12;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.common.util.CryptoUtils;

import static ee.cyber.sdsb.common.TestCertUtil.getCertChainCert;
import static java.util.Collections.singleton;
import static org.junit.Assert.*;

public class GlobalConfTest {

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        System.setProperty(SystemProperties.CONFIGURATION_PATH,
                        "../common-util/src/test/resources/globalconf_good");
        GlobalConf.reload();
    }

    @Test
    public void getInstanceIdentifiers() {
        assertEquals(Arrays.asList("EE", "bar", "foo"),
                GlobalConf.getInstanceIdentifiers());
    }

    @Test
    public void getGlobalGroupDescription() {
        assertEquals("Description", GlobalConf.getGlobalGroupDescription(
                GlobalGroupId.create("EE", "Test group")));
        assertNull("Description", GlobalConf.getGlobalGroupDescription(
                GlobalGroupId.create("EE", "foo")));
    }

    @Test
    public void getServiceId() throws Exception {
        CentralServiceId central1 = CentralServiceId.create("EE", "central1");

        ServiceId expectedServiceId = ServiceId.create("EE", "BUSINESS",
                "foobar", null, "bazservice");

        ServiceId actualServiceId = GlobalConf.getServiceId(central1);
        assertNotNull(actualServiceId);
        assertEquals(expectedServiceId, actualServiceId);

        actualServiceId = GlobalConf.getServiceId(expectedServiceId);
        assertEquals(expectedServiceId, actualServiceId);

        thrown.expectError(ErrorCodes.X_INTERNAL_ERROR);
        GlobalConf.getServiceId(CentralServiceId.create("XX", "yy"));
    }

    @Test
    public void getMembers() {
        List<ClientId> expected = Arrays.asList(
                newClientId("producer"), newClientId("consumer"),
                newClientId("foo"), newClientId("foo", "foosubsystem"));

        assertEquals(expected,
                GlobalConf.getMembers("EE").stream()
                    .map(i -> i.getId()).collect(Collectors.toList()));
    }

    @Test
    public void getCentralServices() {
        assertEquals(Arrays.asList(CentralServiceId.create("EE", "central1")),
                GlobalConf.getCentralServices("EE"));
    }

    @Test
    public void getProviderAddress() throws Exception {
        ClientId consumer = newClientId("consumer");
        ClientId producer = newClientId("producer");

        Set<String> expected = new HashSet<>();
        expected.add("https://www.foo.com/bar");
        expected.add("127.0.0.1");

        assertEquals(expected, GlobalConf.getProviderAddress(consumer));

        assertEquals(singleton("127.0.0.1"),
                GlobalConf.getProviderAddress(producer));
    }

    @Test
    public void getProviderAddressForAuthCert() throws Exception {
        String certBase64 =
        "MIIDiDCCAnCgAwIBAgIIVYNTWA8JcLwwDQYJKoZIhvcNAQEFBQAwNzERMA8GA1UE"
        + "AwwIQWRtaW5DQTExFTATBgNVBAoMDEVKQkNBIFNhbXBsZTELMAkGA1UEBhMCU0Uw"
        + "HhcNMTIxMTE5MDkxNDIzWhcNMTQxMTE5MDkxNDIzWjATMREwDwYDVQQDDAhwcm9k"
        + "dWNlcjCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALKNC381RiACCftv"
        + "ApBzk5HD5YHw0u9SOkwcIkn4cZ4eQWrlROnqHTpS9IVSBoOz6pjCx/FwxZTdpw0j"
        + "X+bRYpxnj11I2XKzHfhfa6BvL5VkaDtjGpOdSGMJUtrI6m9jFiYryEmYHWxPlL9V"
        + "pDK0KknevYm2BR23/xDHweBSZ7tkMENU1kXFWLunoBys+W0waR+Z8HH5WNuBLz8X"
        + "z2iz/6KQ5BoWSPJc9P5TXNOBB+5XyjBR2ogoAOtX53OJzu0wMgLpjuJGdfcpy1S9"
        + "ukU27B21i2MfZ6Tjhu9oKrAIgcMWJaHJ/gRX6iX1vXlfhUTkE1ACSfvhZdntKLzN"
        + "TZGEcxsCAwEAAaOBuzCBuDBYBggrBgEFBQcBAQRMMEowSAYIKwYBBQUHMAGGPGh0"
        + "dHA6Ly9pa3MyLXVidW50dS5jeWJlci5lZTo4MDgwL2VqYmNhL3B1YmxpY3dlYi9z"
        + "dGF0dXMvb2NzcDAdBgNVHQ4EFgQUUHtGmEl0Cuh/x/wj+UU5S7Wui48wDAYDVR0T"
        + "AQH/BAIwADAfBgNVHSMEGDAWgBR3LYkuA7b9+NJlOTE1ItBGGujSCTAOBgNVHQ8B"
        + "Af8EBAMCBeAwDQYJKoZIhvcNAQEFBQADggEBACJqqey5Ywoegq+Rjo4v89AN78Ou"
        + "tKtRzQZtuCZP9+ZhY6ivCPK4F8Ne6qpWZb63OLORyQosDAvj6m0iCFMsUZS3nC0U"
        + "DR0VyP2WrOihBOFC4CA7H2X4l7pkSyMN73ZC6icXkbj9H0ix5/Bv3Ug64DK9SixG"
        + "RxMwLxouIzk7WvePQ6ywlhGvZRTXxhr0DwvfZnPXxHDPB2q+9pKzC9h2txG1tyD9"
        + "ffohEC/LKdGrHSe6hnTRedQUN3hcMQqCTc5cHsaB8bh5EaHrib3RR0YsOhjAd6IC"
        + "ms33BZnfNWQuGVTXw74Eu/P1JkwR0ReO+XuxxMp3DW2epMfL44OHWTb6JGY=";

        byte[] certBytes = CryptoUtils.decodeBase64(certBase64);
        X509Certificate authCert = CryptoUtils.readCertificate(certBytes);

        String url = GlobalConf.getProviderAddress(authCert);
        assertEquals("https://foo.bar.baz", url);
    }

    @Test
    public void getCaCertForOrg() throws Exception {
        X509Certificate org = TestCertUtil.getProducer().cert;
        assertNotNull(org);

        X509Certificate x509 = GlobalConf.getCaCert("EE", org);
        assertNotNull(x509);
    }

    @Test
    public void getCertChain() throws Exception {
        X509Certificate org = getCertChainCert("user_3.p12");
        assertNotNull(org);

        CertChain certChain = GlobalConf.getCertChain("EE", org);
        List<X509Certificate> chain = certChain.getAllCerts();
        assertEquals(5, chain.size());

        assertEquals(getCertChainCert("root_ca.p12"), chain.get(4));
        assertEquals(getCertChainCert("ca_1.p12"), chain.get(3));
        assertEquals(getCertChainCert("ca_2.p12"), chain.get(2));
        assertEquals(getCertChainCert("ca_3.p12"), chain.get(1));
        assertEquals(getCertChainCert("user_3.p12"), chain.get(0));
    }

    @Test
    public void getAllOcspResponderCertificates() {
        List<X509Certificate> ocspResponderCerts =
                GlobalConf.getOcspResponderCertificates();
        for (X509Certificate cert : ocspResponderCerts) {
            assertNotNull("Got null certificate", cert);
        }

        assertEquals(12, ocspResponderCerts.size());
    }

    @Test
    public void getOcspResponderAddresses() throws Exception {
        // Does not matter which org exactly as long as CA is adminca1
        X509Certificate orgCert = TestCertUtil.getConsumer().cert;
        List<String> actualAddresses =
                GlobalConf.getOcspResponderAddresses(orgCert);
        List<String> expectedAddresses = Arrays.asList(
                "http://127.0.0.1:8082/ocsp",
                "http://www.example.net/ocsp");

        for (String address : expectedAddresses) {
            assertTrue(actualAddresses.contains(address));
        }
    }

    @Test
    public void authCertMatchesMember() throws Exception {
        X509Certificate producerCert = TestCertUtil.getProducer().cert;
        X509Certificate consumerCert = TestCertUtil.getConsumer().cert;
        ClientId producer = newClientId("producer");
        ClientId consumer = newClientId("consumer");
        assertTrue(GlobalConf.authCertMatchesMember(producerCert, producer));
        assertFalse(GlobalConf.authCertMatchesMember(consumerCert, producer));
        assertFalse(GlobalConf.authCertMatchesMember(producerCert, consumer));
        assertTrue(GlobalConf.authCertMatchesMember(consumerCert, consumer));
    }

    @Test
    public void getServerId() throws Exception {
        SecurityServerId server =
                SecurityServerId.create("EE", "BUSINESS", "foo",
                        "fooServerCode");
        X509Certificate cert = TestCertUtil.getProducer().cert;
        assertEquals(server, GlobalConf.getServerId(cert));
    }

    @Test
    public void isOcspResponderCert() {
        X509Certificate caCert = TestCertUtil.getCaCert();
        assertFalse(GlobalConf.isOcspResponderCert(caCert, caCert));

        PKCS12 ocspSigner = TestCertUtil.getOcspSigner();
        X509Certificate ocspCert = ocspSigner.cert;
        assertTrue(GlobalConf.isOcspResponderCert(caCert, ocspCert));
    }

    @Test
    public void getSubjectName() throws Exception {
        X509Certificate producerCert = TestCertUtil.getProducer().cert;

        ClientId expectedName = ClientId.create("EE", "BUSINESS", "producer");
        ClientId actualName = GlobalConf.getSubjectName("EE", producerCert);

        assertEquals(expectedName, actualName);
    }

    @Test
    public void getSubjectClientId() throws Exception {
        X509Certificate cert = TestCertUtil.getCertChainCert("user_5.p12");

        ClientId expectedName = ClientId.create("EE", "BUSINESS", "CnOfOrg");
        ClientId actualName = GlobalConf.getSubjectName("EE", cert);

        assertEquals(expectedName, actualName);
    }

    @Test
    public void getVerificationCaCerts() {
        List<X509Certificate> certs =
                GlobalConf.getInstance().getVerificationCaCerts();
        assertEquals(4, certs.size());
    }

    @Test
    public void getKnownAddresses() {
        Set<String> expectedAddresses = new HashSet<>(
                Arrays.asList(
                    "127.0.0.1",
                    "https://www.foo.com/bar",
                    "https://foo.bar.baz"));
        Set<String> actualAddresses = GlobalConf.getKnownAddresses();

        assertEquals(expectedAddresses, actualAddresses);
    }

    @Test
    public void getTspCerts() throws Exception {
        List<X509Certificate> tspCertificates = GlobalConf.getTspCertificates();
        assertEquals(3, tspCertificates.size());
    }

    @Test
    public void getGlobalSettings() {
        String serviceAddr = GlobalConf.getManagementRequestServiceAddress();
        assertEquals("http://mgmt.com:1234", serviceAddr);

        assertEquals(newClientId("servicemember2"),
                GlobalConf.getManagementRequestService());

        assertEquals(42, GlobalConf.getOcspFreshnessSeconds(true));
    }

    @Test
    public void isSecurityServerClient() {
        ClientId client1 = ClientId.create("EE", "BUSINESS", "consumer");
        ClientId client2 = ClientId.create("EE", "BUSINESS", "producer");
        ClientId client3 = ClientId.create("EE", "BUSINESS", "foo",
                "foosubsystem");
        ClientId client4 = ClientId.create("EE", "xx", "foo", "foosubsystem");

        SecurityServerId server1 =
                SecurityServerId.create("EE", "BUSINESS", "producer",
                        "producerServerCode");
        SecurityServerId server2 =
                SecurityServerId.create("EE", "BUSINESS", "producer",
                        "foo");
        SecurityServerId server3 =
                SecurityServerId.create("EE", "BUSINESS", "foo",
                        "FooBarServerCode");

        assertTrue(GlobalConf.isSecurityServerClient(client1, server1));
        assertTrue(GlobalConf.isSecurityServerClient(client2, server1));
        assertTrue(GlobalConf.isSecurityServerClient(client3, server1));
        assertFalse(GlobalConf.isSecurityServerClient(client4, server1));

        assertFalse(GlobalConf.isSecurityServerClient(client1, server2));
        assertFalse(GlobalConf.isSecurityServerClient(client2, server2));
        assertFalse(GlobalConf.isSecurityServerClient(client3, server2));

        assertFalse(GlobalConf.isSecurityServerClient(client3, server3));
    }

    @Test
    public void getSecurityServers() {
        String instanceIdentifier = "EE";

        SecurityServerId server1 =
                SecurityServerId.create("EE", "BUSINESS", "producer",
                        "producerServerCode");
        SecurityServerId server2 =
                SecurityServerId.create("EE", "BUSINESS", "consumer",
                        "consumerServerCode");
        SecurityServerId server3 =
                SecurityServerId.create("EE", "BUSINESS", "foo",
                        "fooServerCode");
        SecurityServerId server4 =
                SecurityServerId.create("EE", "BUSINESS", "foo",
                        "FooBarServerCode");
        List<SecurityServerId> expectedList = new ArrayList<SecurityServerId>();
        expectedList.add(server1);
        expectedList.add(server2);
        expectedList.add(server3);
        expectedList.add(server4);

        assertEquals(expectedList,
                GlobalConf.getSecurityServers(instanceIdentifier));
    }

    private static ClientId newClientId(String name) {
        return ClientId.create("EE", "BUSINESS", name);
    }

    private static ClientId newClientId(String name, String subsystem) {
        return ClientId.create("EE", "BUSINESS", name, subsystem);
    }
}
